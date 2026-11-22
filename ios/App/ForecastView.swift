import SwiftUI

/// A day screen to open: the page (forecast) it belongs to and the day.
struct DayRoute: Hashable {
    let page: Int
    let day: String
}

/// Pages for my location and each saved place, swiped sideways; a day opens its own screen.
struct ForecastView: View {
    @EnvironmentObject var model: AppModel
    @State private var showSettings = false
    /// The page shown, kept here so a swipe redraws only this screen, not every view watching the
    /// app model; it's copied to the model (for the sky and Places) once the swipe settles.
    @State private var page = 0

    var body: some View {
        let pages = model.pages
        NavigationStack {
            Group {
                if pages.isEmpty {
                    ZStack(alignment: .topTrailing) {
                        SkyBackground(code: model.sky.code, night: model.sky.night)
                        VStack(spacing: 12) {
                            Image(systemName: "location.slash").font(.largeTitle)
                            Text(L("no_location")).multilineTextAlignment(.center)
                        }
                        .foregroundStyle(.white).padding()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        SettingsButton { showSettings = true }.padding(.trailing, 16)
                    }
                } else {
                    // The pages fill the whole screen, so they run on under the floating tab bar
                    // (its glass shows them). The page view clips to its own frame and handles safe
                    // areas badly, so each page gets the top inset and keeps clear of it itself.
                    GeometryReader { geo in
                        TabView(selection: $page) {
                            ForEach(Array(pages.enumerated()), id: \.offset) { i, p in
                                ForecastPage(id: p.id, current: p.current, index: i, count: pages.count, topInset: geo.safeAreaInsets.top, showSettings: $showSettings).tag(i)
                            }
                        }
                        // The page dots live in the header card (the system's float over the content).
                        .tabViewStyle(.page(indexDisplayMode: .never))
                        .ignoresSafeArea()
                    }
                    .background(SkyBackground(code: model.sky.code, night: model.sky.night))
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: DayRoute.self) { DayView(route: $0) }
            .onAppear { page = model.page }
            .onChange(of: page) { _, p in if model.page != p { model.page = p } }
            .onChange(of: model.page) { _, p in if page != p { page = p } }
        }
        .sheet(isPresented: $showSettings) { SettingsView() }
    }
}

struct ForecastPage: View {
    @EnvironmentObject var model: AppModel
    let id: Int
    let current: Bool
    var index = 0
    var count = 1
    var topInset: CGFloat = 0
    @Binding var showSettings: Bool

    /// The status bar's height, from the window (the page view hands its pages no safe area).
    private var statusBar: CGFloat {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.keyWindow?.safeAreaInsets.top ?? 59
    }

    var body: some View {
        let entry = model.forecasts[id]
        let f = entry?.0
        let now = f?.currentHour()
        let night = now.map { WeatherCodes.isNight($0.t) } ?? false
        ZStack {
            SkyBackground(code: now?.code ?? 1250, night: night)
            ScrollView {
                VStack(spacing: 12) {
                    header(f, now)
                    if let f {
                        // The cards in the order chosen in Settings, without the hidden ones.
                        let hidden = model.settings.hiddenCards
                        ForEach(model.settings.cards.filter { !hidden.contains($0) }, id: \.self) { card in
                            switch card {
                            case .hourly: hourly(f)
                            case .week: week(f)
                            case .details: details(now)
                            }
                        }
                        sourceLine(f, updated: entry?.1)
                    } else {
                        ProgressView().tint(.white).padding(40)
                    }
                }
                .padding(.horizontal, 14)
                // Room to scroll the last card clear of the tab bar.
                .padding(.bottom, 110)
            }
            // Clear of the status bar; the band below fades content out under it as it scrolls (the
            // page fills the screen, so iOS's own edge effect doesn't apply here).
            .safeAreaPadding(.top, topInset)
            .refreshable { await model.refreshAll(force: true) }
            VStack {
                // Solid sky behind the status bar, fading out just below it.
                let top = SkyBackground.colors(code: now?.code ?? 1250, night: night)[0]
                LinearGradient(stops: [.init(color: top, location: 0), .init(color: top, location: statusBar / (statusBar + 22)),
                                       .init(color: top.opacity(0), location: 1)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: statusBar + 22)
                Spacer()
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }
        .foregroundStyle(.white)
    }

    /// The place and now, in a card: name and settings on top, then the temperature with the
    /// condition, high/low and feels-like beside it.
    private func header(_ f: Forecast?, _ now: Hour?) -> some View {
        let today = f?.days(1).first
        // The nearest station's reading when it's recent, else the forecast's.
        let obs = model.observations[id].flatMap { $0.isRecent && $0.temp != nil ? $0 : nil }
        let temp = obs?.temp ?? now?.temp
        let feels = Sun.apparent(temp: temp, rh: obs?.rh ?? now?.rh, windKmh: obs?.wind ?? now?.wind)
        let night = now.map { WeatherCodes.isNight($0.t) } ?? false
        return Card {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 1) {
                    if current {
                        MyLocationLabel().opacity(0.8)
                    }
                    Text(model.places[id]?.name(english: Fmt.english) ?? "").font(.title2.weight(.semibold)).lineLimit(1)
                    if count > 1 {
                        // Which page this is: swipe sideways for the others.
                        HStack(spacing: 5) {
                            ForEach(0..<count, id: \.self) { i in
                                Circle().fill(.white.opacity(i == index ? 1 : 0.35)).frame(width: 6, height: 6)
                            }
                        }
                        .padding(.top, 3)
                        .accessibilityLabel("\(index + 1) / \(count)")
                    }
                }
                Spacer(minLength: 8)
                SettingsButton { showSettings = true }
            }
            HStack(alignment: .center, spacing: 16) {
                Text(Fmt.temp(temp)).font(.system(size: 72, weight: .thin)).lineLimit(1)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        if let now { WeatherGlyph(code: now.code, night: night, size: 18) }
                        Text(now.map { WeatherCodes.describe($0.code) } ?? "").font(.headline)
                    }
                    if let today {
                        Text(String(format: L("high_low"), Fmt.temp(today.max), Fmt.temp(today.min))).font(.subheadline.weight(.medium))
                    }
                    Text("\(L("feels_like")) \(Fmt.temp(feels))").font(.subheadline).opacity(0.8)
                    if let obs {
                        Label(String(format: L("measured_at"), obs.station.name), systemImage: "sensor.fill")
                            .font(.caption).opacity(0.75).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .padding(.top, 12)
    }

    /// A line about the next hours, then 24 hours with the details chosen in Settings.
    private func hourly(_ f: Forecast) -> some View {
        let details = model.settings.hourDetails
        return Card(title: L("hourly_title"), icon: "clock") {
            if let today = f.days(1).first {
                NavigationLink(value: DayRoute(page: id, day: today.date)) {
                    HStack(spacing: 6) {
                        Text(DaySummary.nextHours(f)).font(.subheadline).multilineTextAlignment(.leading)
                        Spacer(minLength: 4)
                        Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).opacity(0.7)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHint(L("see_day"))
            } else {
                Text(DaySummary.nextHours(f)).font(.subheadline)
            }
            Divider().overlay(.white.opacity(0.3))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 14) {
                    // "Now" is the same reading as the header (current conditions), then the hour slots.
                    let next = f.upcoming(25)
                    let now = f.currentHour() ?? next.first
                    ForEach(Array(([now].compactMap { $0 } + next.dropFirst()).enumerated()), id: \.offset) { i, h in
                        HourColumn(hour: h, label: i == 0 ? L("now") : Fmt.hour(h.date), details: details)
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    /// The week: big rows, each opening its day.
    private func week(_ f: Forecast) -> some View {
        let days = f.days(7)
        let lo = days.compactMap(\.min).min() ?? 0
        let hi = max(days.compactMap(\.max).max() ?? lo, lo + 1)
        return Card(title: L("week_title"), icon: "calendar") {
            VStack(spacing: 0) {
                ForEach(Array(days.enumerated()), id: \.offset) { i, d in
                    if i > 0 { Divider().overlay(.white.opacity(0.25)) }
                    NavigationLink(value: DayRoute(page: id, day: d.date)) {
                        HStack(spacing: 10) {
                            Text(Fmt.dayLabel(d.date)).font(.body.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.7).frame(width: 58, alignment: .leading)
                            VStack(spacing: 1) {
                                WeatherGlyph(code: d.code, size: 19)
                                if let c = f.rainChance(d), c >= 20 {
                                    Text("\(c)%").font(.caption2.weight(.bold)).foregroundStyle(Color(hex: 0x9FD2FF))
                                }
                            }
                            .frame(width: 38)
                            Text(Fmt.temp(d.min)).font(.body).opacity(0.7).lineLimit(1).minimumScaleFactor(0.6).frame(width: 38, alignment: .trailing)
                            RangeBar(lo: lo, hi: hi, from: d.min ?? lo, to: d.max ?? hi, dot: i == 0 ? f.currentHour()?.temp : nil,
                                     track: .white.opacity(0.22), ring: .black.opacity(0.25), colors: DaySummary.warmth)
                                .frame(height: 6)
                            Text(Fmt.temp(d.max)).font(.body.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.6).frame(width: 38, alignment: .leading)
                        }
                        .frame(minHeight: 52)
                        .contentShape(Rectangle())
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(rowLabel(f, d))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// "Thursday, 19 November, Sunny, H:24° L:14°, Rain chance 40%" for VoiceOver.
    private func rowLabel(_ f: Forecast, _ d: Day) -> String {
        var parts = [Fmt.longDay(d.date), WeatherCodes.describe(d.code), String(format: L("high_low"), Fmt.temp(d.max), Fmt.temp(d.min))]
        if let c = f.rainChance(d), c >= 20 { parts.append("\(L("rain_chance")) \(c)%") }
        return parts.joined(separator: ", ")
    }

    private func details(_ now: Hour?) -> some View {
        let place = model.places[id]
        let sun = place.flatMap { Sun.times(day: Fmt.dayString(Date()), lat: $0.lat, lon: $0.lon) }
        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            // Sunrise – sunset, one line so the tile matches its neighbours' height.
            Card(title: L("sun"), icon: "sunrise.fill") {
                Text(sun.map { "\(Fmt.time($0.0))–\(Fmt.time($0.1))" } ?? "–")
                    .font(.title.weight(.medium)).lineLimit(1).minimumScaleFactor(0.5)
                    .accessibilityLabel(sun.map { "\(L("sunrise")) \(Fmt.time($0.0)), \(L("sunset")) \(Fmt.time($0.1))" } ?? "")
            }
            Tile(title: L("humidity"), icon: "humidity", value: now?.rh.map { "\(Int($0))%" } ?? "–")
            Tile(title: L("wind"), icon: "wind", value: Fmt.wind(now?.wind), unit: Fmt.windUnit())
            Tile(title: L("uv"), icon: "sun.max", value: now?.uv.map { "\(Int($0.rounded()))" } ?? "–")
        }
    }

    private func sourceLine(_ f: Forecast, updated: Date?) -> some View {
        let src: String
        switch f.source {
        case Forecast.sourceECMWF: src = L("source_ecmwf_line")
        case Forecast.sourceGFS: src = L("source_gfs_line")
        case Forecast.sourceBest: src = L("source_best_line")
        case Forecast.sourceForeca: src = L("source_foreca_line")
        default: src = L("source_ims_line")
        }
        return Text(src + (updated.map { " · \(L("updated")) " + Fmt.time($0) } ?? ""))
            .font(.caption).opacity(0.75).multilineTextAlignment(.center).padding(.top, 6)
    }
}

/// One of the next hours: time, icon, temperature, and the chosen details under it.
struct HourColumn: View {
    let hour: Hour
    let label: String
    let details: Set<HourDetail>

    var body: some View {
        let h = hour
        VStack(spacing: 7) {
            Text(label).font(.subheadline.weight(.semibold))
            WeatherGlyph(code: h.code, night: WeatherCodes.isNight(h.t), size: 20).frame(height: 26)
            Text(Fmt.temp(h.temp)).font(.title3.weight(.semibold))
            VStack(spacing: 4) {
                if details.contains(.rain) {
                    let c = Int((h.rainChance ?? 0).rounded())
                    detail("drop.fill", "\(c)%").foregroundStyle(Color(hex: 0x9FD2FF)).opacity(c >= 20 ? 1 : 0.55)
                }
                if details.contains(.wind) {
                    HStack(spacing: 3) {
                        Image(systemName: "location.north.fill").font(.system(size: 9)).frame(width: 11)
                            .rotationEffect(.degrees((h.windDir ?? 0) + 180)).opacity(h.windDir == nil ? 0 : 1)
                        Text(Fmt.wind(h.wind))
                    }
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                }
                if details.contains(.feels) {
                    detail("thermometer.medium", Fmt.temp(Sun.apparent(temp: h.temp, rh: h.rh, windKmh: h.wind)))
                }
                if details.contains(.humidity) {
                    detail("humidity.fill", h.rh.map { "\(Int($0.rounded()))%" } ?? "–")
                }
                if details.contains(.uv) {
                    detail("sun.max.fill", h.uv.map { "\(Int($0.rounded()))" } ?? "–")
                }
            }
            .opacity(0.9)
        }
        .frame(minWidth: 46)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([label, WeatherCodes.describe(h.code), Fmt.temp(h.temp),
                             h.rainChance.map { "\(L("rain_chance")) \(Int($0.rounded()))%" }].compactMap { $0 }.joined(separator: ", "))
    }

    private func detail(_ icon: String, _ value: String) -> some View {
        HStack(spacing: 3) {
            Image(systemName: icon).font(.system(size: 9)).frame(width: 11)
            Text(value)
        }
        .font(.caption.weight(.semibold))
        .lineLimit(1)
    }
}

/// A dark frosted card with an optional small caps title.
struct Card<Content: View>: View {
    var title: String? = nil
    var icon: String? = nil
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let title {
                Label(title.uppercased(), systemImage: icon ?? "").font(.caption.weight(.semibold)).opacity(0.75)
            }
            content
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }
}

struct Tile: View {
    let title: String, icon: String, value: String
    var unit: String? = nil

    var body: some View {
        Card(title: title, icon: icon) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(value).font(.title.weight(.medium))
                if let unit { Text(unit).font(.subheadline) }
            }
        }
    }
}

/// The round settings button (in the header card, or alone when there are no pages).
struct SettingsButton: View {
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "gearshape.fill").font(.body).foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(.white.opacity(0.15), in: .circle)
        }
        .accessibilityLabel(L("settings"))
    }
}

extension View {
    /// The app's card: light frosted glass with a thin rim, so white text reads on any sky.
    func glassCard(radius: CGFloat = 22) -> some View {
        background {
            // A translucent fill, not a live blur: behind the cards is a smooth gradient, so a blur
            // looked the same but re-rendered every card on every frame of a page swipe.
            RoundedRectangle(cornerRadius: radius).fill(.white.opacity(0.06))
        }
        .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(.white.opacity(0.18), lineWidth: 1))
    }
}

/// "My Location" with its small arrow, above a place's name.
struct MyLocationLabel: View {
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "location.fill").font(.system(size: 9))
            Text(L("my_location")).font(.caption.weight(.semibold))
        }
    }
}
