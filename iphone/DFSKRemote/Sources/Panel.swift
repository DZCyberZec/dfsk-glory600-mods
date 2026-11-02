import Foundation

/// One page of the CyberZec Set (ex DFSK Helper) web panel (port 8091), read from its HTML.
/// The panel is the car's own UI: the app shows whatever commands the car offers, so new commands
/// added to the Helper later appear here without changing the app.
struct Panel: Equatable {
    struct Command: Equatable, Identifiable {
        var id: String { action }
        let title: String
        let action: String
        var options: [(value: String, label: String)] = []
        /// Text form (`class=t`): one free field instead of buttons.
        var isText = false
        var maxLength = 0
        var current = ""

        static func == (a: Command, b: Command) -> Bool {
            a.title == b.title && a.action == b.action && a.isText == b.isText && a.maxLength == b.maxLength && a.current == b.current
                && a.options.map { $0.value + "|" + $0.label } == b.options.map { $0.value + "|" + $0.label }
        }
    }

    var isLogin = false
    var message = ""
    var status: [(key: String, value: String)] = []
    var commands: [Command] = []
    var csrf: String?

    static func == (a: Panel, b: Panel) -> Bool {
        a.isLogin == b.isLogin && a.message == b.message && a.csrf == b.csrf && a.commands == b.commands
            && a.status.map { $0.key + "|" + $0.value } == b.status.map { $0.key + "|" + $0.value }
    }

    static func parse(_ html: String) -> Panel {
        var p = Panel()
        p.isLogin = html.contains("name=pin")
        p.message = first(#"<p class=f>(.*?)</p>"#, in: html).map(unescape) ?? ""
        if p.isLogin && p.message.isEmpty, let locked = first(#"</h1><p>(.*?)</p>"#, in: html) { p.message = unescape(locked) }
        p.csrf = first(#"name=csrf value="([0-9a-f]+)""#, in: html)
        for m in all(#"<tr><th>(.*?)</th><td>(.*?)</td></tr>"#, in: html) {
            p.status.append((unescape(m[0]), unescape(m[1])))
        }
        // one pass keeps page order; the group is "( class=t)" or empty (never unmatched, NSRegularExpression would crash)
        for m in all(#"<h2>([^<]*)</h2><form method=post action=/a((?: class=t)?)>(.*?)</form>"#, in: html) {
            guard let action = first(#"name=a value=([A-Za-z0-9_]+)>"#, in: m[2]) else { continue }
            var c = Command(title: unescape(m[0]), action: action)
            if m[1].isEmpty {
                c.options = all(#"<button name=v value="([^"]*)">(.*?)</button>"#, in: m[2]).map { (unescape($0[0]), unescape($0[1])) }
            } else {
                guard let len = first(#"<input name=v maxlength=([0-9]+)"#, in: m[2]).flatMap(Int.init) else { continue }
                c.isText = true
                c.maxLength = len
                c.current = first(#"<input name=v [^>]*value="([^"]*)""#, in: m[2]).map(unescape) ?? ""
            }
            p.commands.append(c)
        }
        return p
    }

    /// Value for `cityset`: "Nome|lat|lon", name 1-40 chars without | and ;, 4 decimals (POSIX dot).
    static func cityValue(name: String, lat: Double, lon: Double) -> String? {
        let n = String(name.filter { $0 != "|" && $0 != ";" }.trimmingCharacters(in: .whitespaces).prefix(40))
        guard !n.isEmpty, (-90...90).contains(lat), (-180...180).contains(lon) else { return nil }
        return n + "|" + String(format: "%.4f", lat) + "|" + String(format: "%.4f", lon)
    }

    static func unescape(_ s: String) -> String {
        s.replacingOccurrences(of: "&lt;", with: "<").replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"").replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&amp;", with: "&")
    }

    private static func all(_ pattern: String, in s: String) -> [[String]] {
        guard let re = try? NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators]) else { return [] }
        let ns = s as NSString
        return re.matches(in: s, range: NSRange(location: 0, length: ns.length)).map { m in
            (1..<m.numberOfRanges).map { ns.substring(with: m.range(at: $0)) }
        }
    }

    private static func first(_ pattern: String, in s: String) -> String? {
        all(pattern, in: s).first?.first
    }
}

/// Where the car was parked: the Set status row "Parcheggio" = "lat,lon,epochMs" (from the Home's last GPS fix).
struct Parking: Equatable {
    static let statusKey = "Parcheggio"
    let lat: Double
    let lon: Double
    let at: Date

    init?(_ raw: String) {
        let f = raw.split(separator: ",", omittingEmptySubsequences: false)
        guard f.count == 3, let lat = Double(f[0]), let lon = Double(f[1]), let ms = Int64(f[2]), ms > 0,
              (-90...90).contains(lat), (-180...180).contains(lon) else { return nil }
        self.lat = lat; self.lon = lon; at = Date(timeIntervalSince1970: Double(ms) / 1000)
    }

    init?(panel: Panel) {
        guard let v = panel.status.first(where: { $0.key == Self.statusKey })?.value else { return nil }
        self.init(v)
    }

    var mapsURL: URL { URL(string: "http://maps.apple.com/?q=Auto&ll=\(lat),\(lon)")! }

    /// "oggi 16:10", "ieri 18:02", otherwise "dd/MM HH:mm".
    func when(now: Date = Date(), calendar: Calendar = .current) -> String {
        let f = DateFormatter()
        f.calendar = calendar; f.timeZone = calendar.timeZone; f.locale = Locale(identifier: "it_IT")
        f.dateFormat = "HH:mm"
        let time = f.string(from: at)
        if calendar.isDate(at, inSameDayAs: now) { return "oggi " + time }
        if let y = calendar.date(byAdding: .day, value: -1, to: now), calendar.isDate(at, inSameDayAs: y) { return "ieri " + time }
        f.dateFormat = "dd/MM HH:mm"
        return f.string(from: at)
    }
}

/// Address typed by hand or read from the Helper's QR ("http://IP:8091/?pair=TOKEN").
enum CarAddress {
    static func base(from text: String) -> URL? {
        var t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return nil }
        if !t.contains("://") { t = "http://" + t }
        guard var c = URLComponents(string: t), c.scheme == "http", let host = c.host, !host.isEmpty else { return nil }
        if c.port == nil { c.port = 8091 }
        c.path = "/"
        c.query = nil
        return c.url
    }

    static func pairToken(from text: String) -> String? {
        URLComponents(string: text)?.queryItems?.first { $0.name == "pair" }?.value
    }
}
