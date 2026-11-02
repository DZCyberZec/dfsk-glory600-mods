import Foundation
import Security

/// Talks to the CyberZec Set web panel exactly like Safari does: PIN or QR login, session cookie, CSRF token.
@MainActor
final class CarClient: ObservableObject {
    @Published var panel: Panel?
    @Published var busy = false
    @Published var error: String?
    @Published var address: String = UserDefaults.standard.string(forKey: "address") ?? ""

    private let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 6
        c.httpCookieStorage = .shared
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: c)
    }()

    var base: URL? { CarAddress.base(from: address) }
    var configured: Bool { base != nil }

    /// Stores address and PIN, then logs in.
    func connect(address text: String, pin: String) async {
        guard let url = CarAddress.base(from: text) else { error = "Indirizzo non valido"; return }
        address = url.host.map { "\($0):\(url.port ?? 8091)" } ?? text
        UserDefaults.standard.set(address, forKey: "address")
        if !pin.isEmpty { Keychain.pin = pin }
        if let token = CarAddress.pairToken(from: text) {
            await run { try await self.get(url.appending(queryItems: [URLQueryItem(name: "pair", value: token)])) }
        } else {
            await refresh()
        }
    }

    func refresh() async {
        await run { try await self.get(self.base!) }
    }

    func send(_ action: String, _ value: String) async {
        guard let csrf = panel?.csrf else { await refresh(); return }
        await run { try await self.post("a", ["csrf": csrf, "a": action, "v": value]) }
    }

    func forget() {
        address = ""
        UserDefaults.standard.removeObject(forKey: "address")
        Keychain.pin = nil
        HTTPCookieStorage.shared.cookies?.forEach { HTTPCookieStorage.shared.deleteCookie($0) }
        panel = nil
    }

    /// One request; if the car answers with the login page and a PIN is saved, logs in once and shows the result.
    private func run(_ request: @escaping () async throws -> Panel) async {
        guard base != nil else { return }
        busy = true
        defer { busy = false }
        do {
            var p = try await request()
            if !p.isLogin && p.csrf == nil { p = try await self.get(self.base!) }   // "session expired" page: reload to get the login page
            if p.isLogin, p.message.isEmpty, let pin = Keychain.pin {
                p = try await post("login", ["pin": pin])
                if p.isLogin && p.message.contains("PIN") { Keychain.pin = nil }   // stale PIN: stop retrying (5 fails = 5 min lockout)
            }
            panel = p
            error = nil
            if let park = p.status.first(where: { $0.key == Parking.statusKey })?.value, Parking(park) != nil {
                UserDefaults.standard.set(park, forKey: "park")   // shown even when the car is not reachable
            }
        } catch {
            self.error = "Auto non raggiungibile. Controlla che auto e iPhone siano nella stessa rete (hotspot o Wi-Fi), che in CyberZec Set sia acceso \"Pannello web\" e che per quest'app sia consentita la Rete locale (Impostazioni iPhone)."
        }
    }

    private func get(_ url: URL) async throws -> Panel {
        let (data, _) = try await session.data(from: url)
        return Panel.parse(String(decoding: data, as: UTF8.self))
    }

    private func post(_ path: String, _ form: [String: String]) async throws -> Panel {
        var r = URLRequest(url: base!.appendingPathComponent(path))
        r.httpMethod = "POST"
        r.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        r.httpBody = form.map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: allowed) ?? "")" }
            .joined(separator: "&").data(using: .utf8)
        let (data, _) = try await session.data(for: r)   // the panel answers 303 -> GET /, followed automatically
        return Panel.parse(String(decoding: data, as: UTF8.self))
    }
}

/// The 6-digit panel PIN, kept in the iPhone Keychain.
enum Keychain {
    private static let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                               kSecAttrService as String: "it.cyberzec.dfskremote",
                                               kSecAttrAccount as String: "pin"]

    static var pin: String? {
        get {
            var q = query
            q[kSecReturnData as String] = true
            var out: AnyObject?
            guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
            return String(data: d, encoding: .utf8)
        }
        set {
            SecItemDelete(query as CFDictionary)
            guard let v = newValue, let d = v.data(using: .utf8) else { return }
            var q = query
            q[kSecValueData as String] = d
            q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(q as CFDictionary, nil)
        }
    }
}
