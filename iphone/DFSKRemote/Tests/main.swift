import Foundation

// Run on the Mac: Tests/run.sh (needs the fake car running for the live part)
func check(_ ok: Bool, _ what: String) { if !ok { print("FAIL: \(what)"); exit(1) } }

let login = "<!doctype html><html lang=it><head></head><body><h1>DFSK Helper</h1><p class=f>PIN errato.</p><form method=post action=/login><label>PIN<br><input name=pin></label></form></body></html>"
let lp = Panel.parse(login)
check(lp.isLogin && lp.message == "PIN errato." && lp.commands.isEmpty, "login page")

let panel = """
<body><h1>DFSK Helper</h1><p class=f>Fatto: mic = 2</p><h2>Stato</h2><table><tr><th>Versione</th><td>&lt;1.3&gt; &amp; co</td></tr><tr><th>ADB Wi-Fi</th><td>spento</td></tr></table>\
<h2>Microfono CarPlay</h2><form method=post action=/a><input type=hidden name=csrf value="ab12"><input type=hidden name=a value=mic><button name=v value="1">1</button><button name=v value="2">2</button></form>\
<h2>Musica in retromarcia</h2><form method=post action=/a><input type=hidden name=csrf value="ab12"><input type=hidden name=a value=reverse><button name=v value="2">Più bassa</button></form>\
<form method=post action=/logout><input type=hidden name=csrf value="ab12"><button class=o>Esci</button></form></body>
"""
let p = Panel.parse(panel)
check(!p.isLogin && p.message == "Fatto: mic = 2" && p.csrf == "ab12", "panel header")
check(p.status.count == 2 && p.status[0].value == "<1.3> & co", "status unescape")
check(p.commands.count == 2 && p.commands[0].action == "mic" && p.commands[0].options.count == 2, "commands")
check(p.commands[1].title == "Musica in retromarcia" && p.commands[1].options[0].label == "Più bassa", "labels")

// text forms (class=t) next to button forms: exact HTML from the contract
let texts = """
<h2>Cielo animato</h2><form method=post action=/a><input type=hidden name=csrf value="ab12"><input type=hidden name=a value=sky><button name=v value="on">Acceso</button><button name=v value="off">Spento</button></form><h2>Nome nel saluto</h2><form method=post action=/a class=t><input type=hidden name=csrf value="ab12"><input type=hidden name=a value=name><input name=v maxlength=20 value="Dani &amp; co"><button>Salva</button></form><h2>Cerca città</h2><form method=post action=/a class=t><input type=hidden name=csrf value="ab12"><input type=hidden name=a value=cityset><input name=v maxlength=60 value=""><button>Salva</button></form><h2>Meteo a ore</h2><form method=post action=/a><input type=hidden name=csrf value="ab12"><input type=hidden name=a value=hours><button name=v value="on">Sì</button></form><h2>Scrivi in auto</h2><form method=post action=/a class=t><input type=hidden name=csrf value="ab12"><input type=hidden name=a value=type><input name=v maxlength=200 value=""><button>Salva</button></form>
"""
let tp = Panel.parse(texts)
check(tp.commands.map(\.action) == ["sky", "name", "cityset", "hours", "type"], "text forms in page order")
check(tp.commands[4].title == "Scrivi in auto" && tp.commands[4].isText && tp.commands[4].maxLength == 200 && tp.commands[4].current == "", "type text form")
check(!tp.commands[0].isText && tp.commands[0].options.count == 2 && !tp.commands[3].isText, "button forms still buttons")
check(tp.commands[1].isText && tp.commands[1].maxLength == 20 && tp.commands[1].current == "Dani & co" && tp.commands[1].options.isEmpty, "name text form")
check(tp.commands[2].isText && tp.commands[2].maxLength == 60 && tp.commands[2].current == "", "cityset text form")
check(Panel.cityValue(name: "Reggio | Emilia;", lat: 44.698261, lon: 10.631249) == "Reggio  Emilia|44.6983|10.6312", "city value")
check(Panel.cityValue(name: "|;", lat: 1, lon: 1) == nil && Panel.cityValue(name: "X", lat: 91, lon: 0) == nil, "city value invalid")

// parking row from CyberZec Set ("lat,lon,epochMs")
let pk = Panel.parse("<table><tr><th>Meteo</th><td>x</td></tr><tr><th>Parcheggio</th><td>45.46421,-9.19003,1793632200000</td></tr></table>")
let park = Parking(panel: pk)
check(park?.lat == 45.46421 && park?.lon == -9.19003 && park?.at == Date(timeIntervalSince1970: 1793632200), "parking parse")
check(park?.mapsURL.absoluteString == "http://maps.apple.com/?q=Auto&ll=45.46421,-9.19003", "parking maps url")
check(Parking("45.1,9.2") == nil && Parking("x,9.2,1") == nil && Parking("91.0,9.2,1") == nil && Parking("") == nil && Parking(panel: tp) == nil, "parking invalid")
var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "Europe/Rome")!
let at = Parking("45.0,9.0,1793632200000")!
let atTime = { let f = DateFormatter(); f.timeZone = cal.timeZone; f.dateFormat = "HH:mm"; return f.string(from: at.at) }()
check(at.when(now: at.at.addingTimeInterval(60), calendar: cal) == "oggi " + atTime, "parking today")
check(at.when(now: cal.date(byAdding: .day, value: 1, to: at.at)!, calendar: cal) == "ieri " + atTime, "parking yesterday")
let atFull = { let f = DateFormatter(); f.timeZone = cal.timeZone; f.dateFormat = "dd/MM HH:mm"; return f.string(from: at.at) }()
check(at.when(now: cal.date(byAdding: .day, value: 3, to: at.at)!, calendar: cal) == atFull, "parking older date")

check(CarAddress.base(from: "172.20.10.3")?.absoluteString == "http://172.20.10.3:8091/", "bare ip")
check(CarAddress.base(from: "http://192.168.1.20:8091/?pair=abc")?.absoluteString == "http://192.168.1.20:8091/", "qr url")
check(CarAddress.pairToken(from: "http://192.168.1.20:8091/?pair=abc") == "abc", "pair token")
check(CarAddress.base(from: "") == nil && CarAddress.base(from: "https://x") == nil, "bad input")

// live check against the fake car (iphone_app/fake_car/run.sh), if it is running
if let live = try? String(contentsOf: URL(string: "http://127.0.0.1:8091/")!, encoding: .utf8) {
    check(Panel.parse(live).isLogin, "live login page")
    print("live fake car: ok")
}
print("PanelTests OK")
