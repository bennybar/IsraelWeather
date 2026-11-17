import SwiftUI
import WidgetKit

/// Places: my location first, then the saved places (swipe to delete, drag to reorder), and search
/// to add more. Tapping one opens it on the Forecast tab.
struct PlacesView: View {
    @EnvironmentObject var model: AppModel
    @Binding var tab: Int
    @State private var query = ""
    @State private var results: [ECMWF.GeoPlace] = []
    @State private var allIMS: [Place] = []
    @FocusState private var searching: Bool

    var body: some View {
        NavigationStack {
            List {
                if query.isEmpty {
                    if let mine = model.pages.first(where: \.current) {
                        Button { model.page = 0; tab = 0 } label: { PlaceCard(id: mine.id, current: true) }
                            .buttonStyle(.plain)
                            .listRowBackground(Color.clear).listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 6, leading: 14, bottom: 6, trailing: 14))
                    }
                    ForEach(model.savedPlaces, id: \.self) { id in
                        Button {
                            if let i = model.pages.firstIndex(where: { !$0.current && $0.id == id }) { model.page = i }
                            tab = 0
                        } label: { PlaceCard(id: id) }
                        .buttonStyle(.plain)
                        .listRowBackground(Color.clear).listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 6, leading: 14, bottom: 6, trailing: 14))
                    }
                    .onDelete { model.remove(at: $0) }
                    .onMove { model.move(from: $0, to: $1) }
                    if model.savedPlaces.isEmpty {
                        Label(L("places_hint"), systemImage: "magnifyingglass")
                            .font(.subheadline).foregroundStyle(.white.opacity(0.85))
                            .listRowBackground(Color.clear).listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 6, leading: 14, bottom: 6, trailing: 14))
                    }
                } else {
                    let english = Fmt.english
                    let ims = allIMS.filter { $0.he.contains(query) || $0.en.localizedCaseInsensitiveContains(query) }.prefix(10)
                    ForEach(Array(ims)) { p in
                        Button(p.name(english: english)) { model.add(ims: p); query = ""; searching = false }
                            .foregroundStyle(.white)
                            .listRowBackground(Color.white.opacity(0.1))
                    }
                    if !results.isEmpty {
                        Section {
                            ForEach(results.filter { r in !ims.contains { $0.he == r.he } }) { g in
                                Button {
                                    Task { await model.add(geo: g); query = ""; searching = false }
                                } label: {
                                    VStack(alignment: .leading) {
                                        Text(english ? g.en : g.he)
                                        if let r = g.region { Text(r).font(.caption).opacity(0.75) }
                                    }
                                    .foregroundStyle(.white)
                                }
                                .listRowBackground(Color.white.opacity(0.1))
                            }
                        } header: { Text(L("more_places")).foregroundStyle(.white.opacity(0.8)) }
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(SkyBackground(code: model.sky.code, night: model.sky.night))
            .navigationTitle(L("tab_places"))
            .toolbarColorScheme(.dark, for: .navigationBar)
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: L("search_prompt"))
            .searchFocused($searching)
            .task { allIMS = await Repository.shared.places() }
            .task(id: query) {
                guard query.count >= 2 else { results = []; return }
                try? await Task.sleep(for: .milliseconds(350))
                results = (try? await ECMWF.search(query)) ?? []
            }
            .toolbar { EditButton().foregroundStyle(.white) }
        }
        // Light-on-sky title, search field and buttons.
        .environment(\.colorScheme, .dark)
    }
}

struct PlaceCard: View {
    @EnvironmentObject var model: AppModel
    let id: Int
    var current = false

    var body: some View {
        let f = model.forecasts[id]?.0
        let now = f?.currentHour()
        let today = f?.days(1).first
        let name = model.places[id]?.name(english: Fmt.english) ?? "…"
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                if current {
                    MyLocationLabel().opacity(0.85)
                }
                Text(name).font(.title2.weight(.semibold)).lineLimit(1)
                Spacer()
                HStack(spacing: 6) {
                    if let now { WeatherGlyph(code: now.code, night: WeatherCodes.isNight(now.t), size: 15) }
                    Text(now.map { WeatherCodes.describe($0.code) } ?? "").font(.subheadline.weight(.medium))
                }
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text(Fmt.temp(now?.temp)).font(.system(size: 44, weight: .thin))
                if let today { Text(String(format: L("high_low"), Fmt.temp(today.max), Fmt.temp(today.min))).font(.subheadline.weight(.medium)) }
            }
        }
        .foregroundStyle(.white)
        .padding(14)
        .frame(height: 104)
        .glassCard()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([current ? L("my_location") : nil, name, now.map { WeatherCodes.describe($0.code) }, Fmt.temp(now?.temp)]
            .compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(.isButton)
    }
}

/// Active IMS warnings for the places in use, as glass cards on the sky.
struct AlertsView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        let regions = Set(model.places.values.map(\.rid))
        let active = model.warnings.filter { $0.isActive && !Set($0.regions).isDisjoint(with: regions) }
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    if active.isEmpty {
                        Card {
                            HStack(spacing: 12) {
                                Image(systemName: "checkmark.shield.fill").font(.title)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(L("no_warnings")).font(.headline)
                                    Text(L("no_warnings_body")).font(.subheadline).opacity(0.8)
                                }
                            }
                        }
                    } else {
                        ForEach(active) { w in
                            Card {
                                Label(w.typeName?.get(english: Fmt.english) ?? L("warning"), systemImage: "exclamationmark.triangle.fill")
                                    .font(.headline).foregroundStyle(w.severity >= 3 ? Color(hex: 0xFF8A80) : Color(hex: 0xFFC46B))
                                Text(w.text(english: Fmt.english)).font(.subheadline)
                            }
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }
            .foregroundStyle(.white)
            .background(SkyBackground(code: model.sky.code, night: model.sky.night))
            .navigationTitle(L("tab_alerts"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .refreshable { await model.refreshAll(force: true) }
        }
        .environment(\.colorScheme, .dark)
    }
}

/// Settings: a native grouped list.
struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var fahrenheit = AppSettings.shared.fahrenheit
    @State private var windMS = AppSettings.shared.windMS
    @State private var useLocation = AppSettings.shared.useLocation
    @State private var iconStyle = AppSettings.shared.iconStyle
    @State private var hourDetails = AppSettings.shared.hourDetails
    @State private var source = AppSettings.shared.source
    @State private var key = AppSettings.shared.forecaKey
    @State private var keyStatus: Bool?
    @State private var refetch = false

    var body: some View {
        NavigationStack {
            Form {
                Section(L("section_display")) {
                    Picker(L("temperature"), selection: $fahrenheit) { Text("°C").tag(false); Text("°F").tag(true) }
                    Picker(L("wind"), selection: $windMS) { Text(L("unit_kmh")).tag(false); Text(L("unit_ms")).tag(true) }
                    Picker(L("icon_style"), selection: $iconStyle) {
                        ForEach(IconStyle.allCases, id: \.self) { style in
                            HStack {
                                ForEach([1250, 1220, 1140], id: \.self) { WeatherGlyph(code: $0, size: 16, style: style) }
                                Text(style.label)
                            }
                            .tag(style)
                        }
                    }
                    .pickerStyle(.navigationLink)
                }
                Section {
                    ForEach(HourDetail.allCases, id: \.self) { d in
                        Toggle(isOn: Binding(get: { hourDetails.contains(d) },
                                             set: { on in if on { hourDetails.insert(d) } else { hourDetails.remove(d) } })) {
                            Label(L("detail_\(d.rawValue)"), systemImage: d.icon)
                        }
                    }
                } header: { Text(L("next_hours")) } footer: { Text(L("next_hours_footer")) }
                Section {
                    Picker(L("section_source"), selection: $source) {
                        Text(L("source_ims_ecmwf")).tag(ForecastSource.imsEcmwf)
                        Text(L("source_ecmwf")).tag(ForecastSource.ecmwf)
                        Text(L("source_gfs")).tag(ForecastSource.gfs)
                        Text("Foreca").tag(ForecastSource.foreca)
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                    if source == .foreca {
                        TextField(L("foreca_key"), text: $key).textInputAutocapitalization(.never).autocorrectionDisabled()
                        HStack {
                            Button(L("foreca_check")) { Task { keyStatus = await Foreca.check(key) } }.disabled(key.isEmpty)
                            Spacer()
                            if let ok = keyStatus { Text(L(ok ? "foreca_ok" : "foreca_bad")).foregroundStyle(ok ? .green : .red) }
                        }
                        Link(L("foreca_signup"), destination: URL(string: "https://developer.foreca.com/checkout?plan=Freemium-EUR-Monthly&mode=paid")!)
                    }
                } header: { Text(L("section_source")) } footer: { Text(L(source == .foreca ? "source_foreca_hint" : source == .ecmwf ? "source_ecmwf_hint" : source == .gfs ? "source_gfs_hint" : "source_ims_ecmwf_hint")) }
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
            // Each change is saved as it's made (a swiped-away sheet must not lose it). Display
            // changes just redraw; a new source, key or location setting refetches when the sheet closes.
            .onChange(of: fahrenheit) { _, v in AppSettings.shared.fahrenheit = v; model.displayChanged() }
            .onChange(of: windMS) { _, v in AppSettings.shared.windMS = v; model.displayChanged() }
            .onChange(of: iconStyle) { _, v in AppSettings.shared.iconStyle = v; model.displayChanged() }
            .onChange(of: hourDetails) { _, v in AppSettings.shared.hourDetails = v; model.displayChanged() }
            .onChange(of: useLocation) { _, v in AppSettings.shared.useLocation = v; refetch = true }
            .onChange(of: source) { _, v in AppSettings.shared.source = v; refetch = true }
            .onChange(of: key) { _, v in AppSettings.shared.forecaKey = v; refetch = true }
            .onDisappear { if refetch { model.settingsChanged() } }
        }
    }
}
