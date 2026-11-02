import Foundation

// End-to-end check of CarClient (the app's real network code) against iphone_app/fake_car/run.sh.
func check(_ ok: Bool, _ what: String) { if !ok { print("FAIL: \(what)"); exit(1) } }

@MainActor func runAll() async {
    let car = CarClient()
    car.forget()
    await car.connect(address: "127.0.0.1", pin: "000000")
    check(car.error == nil && car.panel?.isLogin == true && car.panel?.message.contains("PIN") == true, "wrong PIN shows login + message")
    await car.connect(address: "127.0.0.1", pin: "123456")
    check(car.panel?.isLogin == false && (car.panel?.commands.count ?? 0) >= 5, "login with PIN shows commands")
    print("commands:", car.panel!.commands.map { "\($0.action)(\($0.options.map(\.label).joined(separator: "/")))" }.joined(separator: " "))
    await car.send("mic", "3")
    check(car.panel?.message.contains("mic = 3") == true, "command result shown")
    check(car.panel?.status.first { $0.key == "Microfono CarPlay" }?.value == "3", "status updated")
    HTTPCookieStorage.shared.cookies?.forEach { HTTPCookieStorage.shared.deleteCookie($0) }   // session lost
    await car.refresh()
    check(car.panel?.isLogin == false, "auto re-login with saved PIN")
    car.forget()
    await car.connect(address: "10.255.255.1", pin: "123456")
    check(car.error != nil, "unreachable car shows error")
    car.forget()
    print("LiveTests OK")
}
Task { await runAll(); exit(0) }
RunLoop.main.run()
