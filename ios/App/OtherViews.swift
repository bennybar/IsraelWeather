import SwiftUI
import WidgetKit

/// Places: city cards (swipe to delete, drag to reorder) and search to add.
struct PlacesView: View {
    @EnvironmentObject var model: AppModel
    @Binding var tab: Int
    @State private var query = ""
    @State private var results: [ECMWF.GeoPlace] = []
    @State private var allIMS: [Place] = []

    var body: some View {
        NavigationStack {
            List {
                if query.isEmpty {
                    ForEach(model.savedPlaces, id: \.self) { id in
                        PlaceCard(id: id).listRowBackground(Color.clear).listRowSeparator(.hidden)
                    }
                    .onDelete { model.remove(at: $0) }
                    .onMove { model.move(from: $0, to: $1) }
                } else {
                    let english = Fmt.english
                    let ims = allIMS.filter { $0.he.contains(query) || $0.en.localizedCaseInsensitiveContains(query) }.prefix(10)
                    ForEach(Array(ims)) { p in
                        Button(p.name(english: english)) { model.add(ims: p); query = "" }
                    }
                    if !results.isEmpty {
                        Section(L("more_places")) {
                            ForEach(results.filter { r in !ims.contains { $0.he == r.he } }) { g in
                                Button {
                                    Task { await model.add(geo: g); query = "" }
                                } label: {
                                    VStack(alignment: .leading) {
                                        Text(english ? g.en : g.he)
                                        if let r = g.region { Text(r).font(.caption).foregroundStyle(.secondary) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle(L("tab_places"))
            .searchable(text: $query, prompt: L("search_prompt"))
            .task { allIMS = await Repository.shared.places() }
            .task(id: query) {
                guard query.count >= 2 else { results = []; return }
                try? await Task.sleep(for: .milliseconds(350))
                results = (try? await ECMWF.search(query)) ?? []
            }
            .toolbar { EditButton() }
        }
    }
}

struct PlaceCard: View {
    @EnvironmentObject var model: AppModel
    let id: Int

    var body: some View {
        let f = model.forecasts[id]?.0
        let now = f?.currentHour()
        let today = f?.days(1).first
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(model.places[id]?.name(english: Fmt.english) ?? "…").font(.title3.bold())
                Spacer()
                Text(now.map { WeatherCodes.describe($0.code) } ?? "").font(.footnote).opacity(0.85)
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text(Fmt.temp(now?.temp)).font(.system(size: 44, weight: .light))
                if let today { Text("\(L("high")):\(Fmt.temp(today.max)) \(L("low")):\(Fmt.temp(today.min))").font(.footnote).opacity(0.85) }
            }
        }
        .foregroundStyle(.white)
        .padding(16)
        .frame(height: 104)
        .background(SkyBackground(code: now?.code ?? 1250, night: now.map { WeatherCodes.isNight($0.t) } ?? false).clipShape(.rect(cornerRadius: 22)))
    }
}

/// Active IMS warnings for the places in use.
struct AlertsView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        let regions = Set(model.places.values.map(\.rid))
        let active = model.warnings.filter { $0.isActive && !Set($0.regions).isDisjoint(with: regions) }
        NavigationStack {
            List {
                if active.isEmpty {
                    ContentUnavailableView(L("no_warnings"), systemImage: "checkmark.shield")
                } else {
                    ForEach(active) { w in
                        VStack(alignment: .leading, spacing: 6) {
                            Label(w.typeName?.get(english: Fmt.english) ?? L("warning"), systemImage: "exclamationmark.triangle.fill")
                                .font(.headline).foregroundStyle(w.severity >= 3 ? .red : .orange)
                            Text(w.text(english: Fmt.english)).font(.subheadline)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle(L("tab_alerts"))
            .refreshable { await model.refreshAll(force: true) }
        }
    }
}

/// Settings: a native grouped list.
struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var fahrenheit = AppSettings.shared.fahrenheit
    @State private var windMS = AppSettings.shared.windMS
    @State private var useLocation = AppSettings.shared.useLocation
    @State private var source = AppSettings.shared.source
    @State private var key = AppSettings.shared.forecaKey
    @State private var keyStatus: Bool?

    var body: some View {
        NavigationStack {
            Form {
                Section(L("section_display")) {
                    Picker(L("temperature"), selection: $fahrenheit) { Text("°C").tag(false); Text("°F").tag(true) }
                    Picker(L("wind"), selection: $windMS) { Text(L("unit_kmh")).tag(false); Text(L("unit_ms")).tag(true) }
                }
                Section {
                    Picker(L("section_source"), selection: $source) {
                        Text(L("source_ims_ecmwf")).tag(ForecastSource.imsEcmwf)
                        Text(L("source_ecmwf")).tag(ForecastSource.ecmwf)
                        Text("Foreca").tag(ForecastSource.foreca)
                    }
                    .pickerStyle(.inline)
                    if source == .foreca {
                        TextField(L("foreca_key"), text: $key).textInputAutocapitalization(.never).autocorrectionDisabled()
                        HStack {
                            Button(L("foreca_check")) { Task { keyStatus = await Foreca.check(key) } }.disabled(key.isEmpty)
                            Spacer()
                            if let ok = keyStatus { Text(L(ok ? "foreca_ok" : "foreca_bad")).foregroundStyle(ok ? .green : .red) }
                        }
                        Link(L("foreca_signup"), destination: URL(string: "https://developer.foreca.com/checkout?plan=Freemium-EUR-Monthly&mode=paid")!)
                    }
                } header: { Text(L("section_source")) } footer: { Text(L(source == .foreca ? "source_foreca_hint" : source == .ecmwf ? "source_ecmwf_hint" : "source_ims_ecmwf_hint")) }
                Section(L("section_location")) {
                    Toggle(L("use_location"), isOn: $useLocation)
                }
                Section(L("section_about")) {
                    Text(L("about_body")).font(.footnote).foregroundStyle(.secondary)
                    Link("GitHub", destination: URL(string: "https://github.com/bennybar/IsraelWeather")!)
                }
            }
            .navigationTitle(L("settings"))
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("done")) { dismiss() } } }
            .onDisappear {
                let s = AppSettings.shared
                let changed = s.fahrenheit != fahrenheit || s.windMS != windMS || s.useLocation != useLocation || s.source != source || s.forecaKey != key
                s.fahrenheit = fahrenheit
                s.windMS = windMS
                s.useLocation = useLocation
                s.source = source
                s.forecaKey = key
                if changed { model.settingsChanged() }
            }
        }
    }
}
