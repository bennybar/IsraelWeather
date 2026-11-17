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

    var body: some View {
        let pages = model.pages
        NavigationStack {
            Group {
                if pages.isEmpty {
                    ZStack(alignment: .topTrailing) {
                        SkyBackground(code: 1250, night: false)
                        VStack(spacing: 12) {
                            Image(systemName: "location.slash").font(.largeTitle)
                            Text(L("no_location")).multilineTextAlignment(.center)
                        }
                        .foregroundStyle(.white).padding()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        SettingsButton { showSettings = true }.padding(.trailing, 16)
                    }
                } else {
                    TabView(selection: $model.page) {
                        ForEach(Array(pages.enumerated()), id: \.offset) { i, p in
                            ForecastPage(id: p.id, current: p.current, index: i, count: pages.count) { showSettings = true }.tag(i)
                        }
                    }
                    // The page dots live in the header card (the system's float over the content).
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .background(SkyBackground(code: 1250, night: false))
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: DayRoute.self) { DayView(route: $0) }
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
    var openSettings: () -> Void

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
                        hourly(f)
                        week(f)
                        details(now)
                        sourceLine(f, updated: entry?.1)
                    } else {
                        ProgressView().tint(.white).padding(40)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 24)
            }
            // Content fades under the status bar as it scrolls (iOS's own edge effect).
            .scrollEdgeEffectStyle(.soft, for: .top)
            .refreshable { await model.refreshAll(force: true) }
        }
        .foregroundStyle(.white)
    }

    /// The place and now, in a card: name and settings on top, then the temperature with the
    /// condition, high/low and feels-like beside it.
    private func header(_ f: Forecast?, _ now: Hour?) -> some View {
        let today = f?.days(1).first
        let feels = Sun.apparent(temp: now?.temp, rh: now?.rh, windKmh: now?.wind)
        let night = now.map { WeatherCodes.isNight($0.t) } ?? false
        return Card {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 1) {
                    if current {
                        Label(L("my_location"), systemImage: "location.fill").font(.caption.weight(.semibold)).opacity(0.8)
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
                SettingsButton(action: openSettings)
            }
            HStack(alignment: .center, spacing: 16) {
                Text(Fmt.temp(now?.temp)).font(.system(size: 72, weight: .thin)).lineLimit(1)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        if let now { WeatherGlyph(code: now.code, night: night, size: 18) }
                        Text(now.map { WeatherCodes.describe($0.code) } ?? "").font(.headline)
                    }
                    if let today {
                        Text(String(format: L("high_low"), Fmt.temp(today.max), Fmt.temp(today.min))).font(.subheadline.weight(.medium))
                    }
                    Text("\(L("feels_like")) \(Fmt.temp(feels))").font(.subheadline).opacity(0.8)
                }
                Spacer(minLength: 0)
            }
        }
        .padding(.top, 12)
    }

    /// A line about the next hours, then 24 hours with the details chosen in Settings.
    private func hourly(_ f: Forecast) -> some View {
        let details = model.settings.hourDetails
        return Card {
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
            Tile(title: L("wind"), icon: "wind", value: "\(Fmt.wind(now?.wind)) \(Fmt.windUnit())")
            Tile(title: L("uv"), icon: "sun.max", value: now?.uv.map { "\(Int($0.rounded()))" } ?? "–")
        }
    }

    private func sourceLine(_ f: Forecast, updated: Date?) -> some View {
        let src: String
        switch f.source {
        case Forecast.sourceECMWF: src = L("source_ecmwf_line")
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
                    HStack(spacing: 2) {
                        if let dir = h.windDir {
                            Image(systemName: "location.north.fill").font(.system(size: 8)).rotationEffect(.degrees(dir + 180))
                        }
                        Text(Fmt.wind(h.wind))
                    }
                    .font(.caption.weight(.semibold))
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
        HStack(spacing: 2) {
            Image(systemName: icon).font(.system(size: 9))
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
        // Light frosted glass: enough darkening for white text to read on any sky, no heavier.
        .background {
            RoundedRectangle(cornerRadius: 22).fill(.ultraThinMaterial).environment(\.colorScheme, .dark).opacity(0.55)
        }
        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.white.opacity(0.18), lineWidth: 1))
    }
}

struct Tile: View {
    let title: String, icon: String, value: String

    var body: some View {
        Card(title: title, icon: icon) {
            Text(value).font(.title.weight(.medium))
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
