import SwiftUI
import VisionKit

@main
struct DFSKRemoteApp: App {
    @StateObject private var car = CarClient()

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                if car.configured { PanelView() } else { ConnectView() }
            }
            .environmentObject(car)
            .tint(CommandRow.lime)
            .preferredColorScheme(.dark)
        }
    }
}

struct ConnectView: View {
    @EnvironmentObject var car: CarClient
    @State private var address = ""
    @State private var pin = ""
    @State private var scanning = false

    var body: some View {
        Form {
            ParkingSection()
            Section {
                Text("In auto apri **CyberZec Set** e accendi **Pannello web**: compaiono un QR, l'indirizzo e il PIN.")
                    .font(.callout)
            }
            if DataScannerViewController.isSupported {
                Section {
                    Button { scanning = true } label: {
                        Label("Inquadra il QR", systemImage: "qrcode.viewfinder").font(.title3.bold())
                    }
                }
            }
            Section("Oppure a mano") {
                TextField("Indirizzo, es. 172.20.10.3", text: $address)
                    .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField("PIN (6 cifre)", text: $pin).keyboardType(.numberPad)
                Button("Collega") { Task { await car.connect(address: address, pin: pin) } }
                    .disabled(CarAddress.base(from: address) == nil || pin.count != 6)
            }
            if let e = car.error { Section { Text(e).foregroundStyle(.orange) } }
        }
        .navigationTitle("CyberZec Remote")
        .sheet(isPresented: $scanning) {
            QRScanner { text in
                scanning = false
                Task { await car.connect(address: text, pin: pin) }
            }
            .ignoresSafeArea()
        }
    }
}

struct PanelView: View {
    @EnvironmentObject var car: CarClient
    @State private var pin = ""

    var body: some View {
        List {
            ParkingSection()
            if let e = car.error {
                Section { Text(e).foregroundStyle(.orange) }
            }
            if let p = car.panel {
                if !p.message.isEmpty { Section { Text(p.message).font(.headline) } }
                if p.isLogin {
                    Section("PIN") {
                        SecureField("PIN (6 cifre)", text: $pin).keyboardType(.numberPad).font(.title2)
                        Button("Entra") { Task { await car.connect(address: car.address, pin: pin) } }
                            .disabled(pin.count != 6)
                    }
                } else {
                    // "Scrivi in auto" first, right under the parking card
                    ForEach(p.commands.filter { $0.action == "type" } + p.commands.filter { $0.action != "type" }) { c in
                        Section { CommandRow(command: c, current: current(for: c, in: p)) }
                    }
                    if !p.status.isEmpty {
                        Section("Stato") {
                            ForEach(p.status.indices.filter { p.status[$0].key != Parking.statusKey }, id: \.self) { i in
                                LabeledContent(p.status[i].key, value: p.status[i].value)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("CyberZec Remote")
        .overlay { if car.busy && car.panel == nil { ProgressView() } }
        .refreshable { await car.refresh() }
        .task { await car.refresh() }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("Aggiorna", systemImage: "arrow.clockwise") { Task { await car.refresh() } }
                    Button("Cambia auto", systemImage: "car", role: .destructive) { car.forget() }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
    }

    /// The status line with the same title tells which option is active (e.g. "Microfono CarPlay: 1").
    private func current(for c: Panel.Command, in p: Panel) -> String? {
        let s = p.status.first { $0.key.caseInsensitiveCompare(c.title) == .orderedSame }
            ?? p.status.first { $0.key.localizedCaseInsensitiveContains(c.title) || c.title.localizedCaseInsensitiveContains($0.key) }
        guard let v = s?.value else { return nil }
        // prefix, not "contains": "disattivato" must not light up "Attivo"
        return c.options.first { v.range(of: $0.label, options: [.caseInsensitive, .anchored]) != nil }?.value
    }
}

/// "Dove ho parcheggiato": last spot saved by CarClient, so it also shows when the car is off or out of reach.
struct ParkingSection: View {
    @AppStorage("park") private var raw = ""
    @Environment(\.openURL) private var openURL
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        if let p = Parking(raw) {
            Section {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Dove ho parcheggiato").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary).accessibilityAddTraits(.isHeader)
                    Label(p.when(), systemImage: "parkingsign.circle").font(.title3.bold())
                        .accessibilityLabel("Parcheggiata \(p.when())")
                    Button { openURL(p.mapsURL) } label: { CommandRow.pill("Apri in Mappe", on: true, large: typeSize.isAccessibilitySize) }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Apri il parcheggio in Mappe")
                }
                .padding(.vertical, 6)
            }
        }
    }
}

/// One card per command: title, then equal-width one-line pills (selected = lime).
struct CommandRow: View {
    @EnvironmentObject var car: CarClient
    let command: Panel.Command
    let current: String?
    @Environment(\.dynamicTypeSize) private var typeSize

    static let lime = Color(red: 0.77, green: 0.96, blue: 0.42)

    var body: some View {
        if command.action == "cityset" { CitySearchRow(command: command) }
        else if command.action == "type" { TypeInCarRow(command: command) }
        else if command.isText { TextCommandRow(command: command) }
        else { buttons }
    }

    /// The pill look shared by all commands.
    static func pill(_ text: String, on: Bool = false, large: Bool) -> some View {
        Text(text)
            .font(.subheadline.weight(.semibold))
            .lineLimit(large ? 3 : 1).minimumScaleFactor(0.7)
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity).frame(minHeight: 40)
            .background(Capsule().fill(on ? lime : Color(.tertiarySystemFill)))
            .foregroundStyle(on ? .black : .primary)
            .contentShape(Capsule())
    }

    @ViewBuilder private var buttons: some View {
        // 4 options as 2x2, otherwise up to 3 per row: every pill same width and height.
        let cols = typeSize.isAccessibilitySize ? 1 : command.options.count == 4 ? 2 : max(1, min(command.options.count, 3))
        VStack(alignment: .leading, spacing: 10) {
            Text(command.title).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary).accessibilityAddTraits(.isHeader)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: cols), spacing: 8) {
                ForEach(command.options, id: \.value) { o in
                    let on = o.value == current
                    Button { Task { await car.send(command.action, o.value) } } label: {
                        Self.pill(o.label, on: on, large: typeSize.isAccessibilitySize)
                    }
                    .buttonStyle(.plain) // plain: inside a List only the pill is tappable, not the whole row
                    .accessibilityLabel("\(command.title): \(o.label)")
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .disabled(car.busy)
            .opacity(car.busy ? 0.6 : 1)
        }
        .padding(.vertical, 6)
    }
}

/// Text command (e.g. "Nome nel saluto"): field prefilled with the car's value + "Salva" pill.
struct TextCommandRow: View {
    @EnvironmentObject var car: CarClient
    let command: Panel.Command
    @State private var text = ""
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(command.title).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary).accessibilityAddTraits(.isHeader)
            TextField(command.title, text: $text)
                .textFieldStyle(.roundedBorder)
                .submitLabel(.done)
                .onChange(of: text) { if text.count > command.maxLength { text = String(text.prefix(command.maxLength)) } }
                .accessibilityHint("Massimo \(command.maxLength) caratteri")
            Button { Task { await car.send(command.action, text) } } label: {
                CommandRow.pill("Salva", on: text != command.current, large: typeSize.isAccessibilitySize)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(command.title): salva")
            .disabled(car.busy || text == command.current)
            .opacity(car.busy ? 0.6 : 1)
        }
        .padding(.vertical, 6)
        .task(id: command.current) { text = command.current }   // refill after the car answers
    }
}

/// "Scrivi in auto": the text goes into the focused field in the car (Tastiera CyberZec) followed by Enter.
struct TypeInCarRow: View {
    @EnvironmentObject var car: CarClient
    let command: Panel.Command
    @State private var text = ""
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(command.title).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary).accessibilityAddTraits(.isHeader)
            TextField("Es. via Roma 1, Ancona", text: $text)
                .textFieldStyle(.roundedBorder)
                .submitLabel(.send)
                .onSubmit(send)
                .onChange(of: text) { if text.count > command.maxLength { text = String(text.prefix(command.maxLength)) } }
                .accessibilityLabel(command.title)
                .accessibilityHint("Massimo \(command.maxLength) caratteri")
            Text("Apri prima la ricerca in auto").font(.footnote).foregroundStyle(.secondary)
            Button(action: send) { CommandRow.pill("Invia", on: true, large: typeSize.isAccessibilitySize) }
                .buttonStyle(.plain)
                .accessibilityLabel("\(command.title): invia")
                .disabled(car.busy || text.isEmpty)
                .opacity(car.busy || text.isEmpty ? 0.6 : 1)
        }
        .padding(.vertical, 6)
    }

    private func send() {
        let t = text
        guard !t.isEmpty else { return }
        text = ""
        Task { await car.send(command.action, t) }
    }
}

/// "Cerca città": searches Open-Meteo geocoding on the iPhone, a tap sends "Nome|lat|lon" to the car.
struct CitySearchRow: View {
    @EnvironmentObject var car: CarClient
    let command: Panel.Command
    @State private var query = ""
    @State private var results: [City] = []
    @State private var failed = false
    @Environment(\.dynamicTypeSize) private var typeSize

    struct City: Decodable {
        let name: String
        let latitude: Double
        let longitude: Double
        let admin1: String?
        let admin2: String?
        let population: Int?
        var label: String { [name, admin2 ?? admin1].compactMap { $0 }.joined(separator: ", ") }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(command.title).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary).accessibilityAddTraits(.isHeader)
            TextField("Scrivi almeno 3 lettere", text: $query)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
                .accessibilityLabel(command.title)
            if failed { Text("Ricerca non riuscita: serve internet sull'iPhone.").font(.footnote).foregroundStyle(.orange) }
            ForEach(results.indices, id: \.self) { i in
                let c = results[i]
                Button {
                    guard let v = Panel.cityValue(name: c.name, lat: c.latitude, lon: c.longitude) else { return }
                    Task { await car.send(command.action, v); query = ""; results = [] }
                } label: { CommandRow.pill(c.label, large: typeSize.isAccessibilitySize) }
                .buttonStyle(.plain)
                .accessibilityLabel("Scegli \(c.label)")
            }
            .disabled(car.busy)
            .opacity(car.busy ? 0.6 : 1)
        }
        .padding(.vertical, 6)
        .task(id: query) {   // a new letter cancels this task: 0.5 s debounce
            let q = query.trimmingCharacters(in: .whitespaces)
            guard q.count >= 3 else { results = []; failed = false; return }
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }
            do {
                var found = try await Self.search(q, italyOnly: true)
                if found.isEmpty { found = try await Self.search(q, italyOnly: false) }
                guard !Task.isCancelled else { return }
                results = found.sorted { ($0.population ?? 0) > ($1.population ?? 0) }
                failed = false
            } catch {
                if !Task.isCancelled { failed = true }
            }
        }
    }

    static func search(_ name: String, italyOnly: Bool) async throws -> [City] {
        var u = URLComponents(string: "https://geocoding-api.open-meteo.com/v1/search")!
        u.queryItems = [.init(name: "name", value: name), .init(name: "count", value: "6"), .init(name: "language", value: "it")]
        if italyOnly { u.queryItems!.append(.init(name: "countryCode", value: "IT")) }
        let (data, _) = try await URLSession.shared.data(from: u.url!)
        struct Answer: Decodable { let results: [City]? }   // no "results" key when nothing is found
        return try JSONDecoder().decode(Answer.self, from: data).results ?? []
    }
}

/// Camera QR reader (real iPhone only).
struct QRScanner: UIViewControllerRepresentable {
    let found: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let vc = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])], isHighlightingEnabled: true)
        vc.delegate = context.coordinator
        return vc
    }

    // start once the view is on screen (also after the first camera-permission answer)
    func updateUIViewController(_ vc: DataScannerViewController, context: Context) {
        if !vc.isScanning { try? vc.startScanning() }
    }

    func makeCoordinator() -> Coordinator { Coordinator(found: found) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let found: (String) -> Void
        private var done = false
        init(found: @escaping (String) -> Void) { self.found = found }

        func dataScanner(_ s: DataScannerViewController, didAdd items: [RecognizedItem], allItems: [RecognizedItem]) {
            for case .barcode(let b) in items {
                if !done, let text = b.payloadStringValue, CarAddress.base(from: text) != nil {
                    done = true
                    found(text)
                }
            }
        }
    }
}
