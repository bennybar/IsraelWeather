import SwiftUI

/// A day screen to open: the page (forecast) it belongs to and the day.
struct DayRoute: Hashable {
    let page: Int
    let day: String
}

/// Pages for my location and each saved place, swiped sideways; a day opens its own screen.
struct ForecastView: View {
    @EnvironmentObject var model: AppModel
    @State private var page = 0
    @State private var showSettings = false

    var body: some View {
        let pages = model.pages
        NavigationStack {
            ZStack(alignment: .topTrailing) {
                if pages.isEmpty {
                    ZStack {
                        SkyBackground(code: 1250, night: false)
                        VStack(spacing: 12) {
                            Image(systemName: "location.slash").font(.largeTitle)
                            Text(L("no_location")).multilineTextAlignment(.center)
                        }.foregroundStyle(.white).padding()
                    }
                } else {
                    TabView(selection: $page) {
                        ForEach(Array(pages.enumerated()), id: \.offset) { i, p in
                            ForecastPage(id: p.id, current: p.current).tag(i)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: pages.count > 1 ? .automatic : .never))
                    .ignoresSafeArea()
                }
                Button { showSettings = true } label: {
                    Image(systemName: "gearshape.fill").font(.title3).foregroundStyle(.white).padding(12)
                        .background(.ultraThinMaterial, in: .circle)
                        .environment(\.colorScheme, .dark)
                }
                .accessibilityLabel(L("settings"))
                .padding(.trailing, 16)
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
                .padding(.bottom, 30)
            }
            .refreshable { await model.refreshAll(force: true) }
        }
        .foregroundStyle(.white)
    }

    /// Compact: the place, then the temperature with the condition, high/low and feels-like beside it.
    private func header(_ f: Forecast?, _ now: Hour?) -> some View {
        let today = f?.days(1).first
        let feels = Sun.apparent(temp: now?.temp, rh: now?.rh, windKmh: now?.wind)
        return VStack(alignment: .leading, spacing: 6) {
            VStack(alignment: .leading, spacing: 0) {
                if current { Text(L("my_location").uppercased()).font(.caption.weight(.semibold)).opacity(0.8) }
                Text(model.places[id]?.name(english: Fmt.english) ?? "").font(.title.weight(.semibold)).lineLimit(1)
            }
            // Room for the settings button at the top corner.
            .padding(.trailing, 56)
            HStack(alignment: .center, spacing: 14) {
                Text(Fmt.temp(now?.temp)).font(.system(size: 76, weight: .thin)).lineLimit(1)
                VStack(alignment: .leading, spacing: 3) {
                    Text(now.map { WeatherCodes.describe($0.code) } ?? "").font(.title3.weight(.semibold))
                    if let today {
                        Text("\(L("high")):\(Fmt.temp(today.max))  \(L("low")):\(Fmt.temp(today.min))").font(.subheadline.weight(.medium))
                    }
                    Text("\(L("feels_like")) \(Fmt.temp(feels))").font(.subheadline).opacity(0.8)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 58)
        .padding(.bottom, 4)
    }

    /// A line about the next hours, then 24 hours with the details chosen in Settings.
    private func hourly(_ f: Forecast) -> some View {
        let details = model.settings.hourDetails
        return Card {
            Text(DaySummary.nextHours(f)).font(.subheadline)
            Divider().overlay(.white.opacity(0.3))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 14) {
                    ForEach(Array(f.upcoming(24).enumerated()), id: \.offset) { i, h in
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
                            Text(Fmt.dayLabel(d.date)).font(.title3.weight(.semibold)).frame(width: 66, alignment: .leading)
                            VStack(spacing: 1) {
                                WeatherGlyph(code: d.code, size: 22)
                                if let c = f.rainChance(d), c >= 20 {
                                    Text("\(c)%").font(.caption2.weight(.bold)).foregroundStyle(Color(hex: 0x9FD2FF))
                                }
                            }
                            .frame(width: 38)
                            Text(Fmt.temp(d.min)).font(.title3).opacity(0.7).frame(width: 42, alignment: .trailing)
                            RangeBar(lo: lo, hi: hi, from: d.min ?? lo, to: d.max ?? hi, dot: i == 0 ? f.currentHour()?.temp : nil,
                                     track: .white.opacity(0.22), ring: .black.opacity(0.25), colors: DaySummary.warmth)
                                .frame(height: 6)
                            Text(Fmt.temp(d.max)).font(.title3.weight(.semibold)).frame(width: 42, alignment: .leading)
                        }
                        .frame(minHeight: 58)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func details(_ now: Hour?) -> some View {
        let feels = Sun.apparent(temp: now?.temp, rh: now?.rh, windKmh: now?.wind)
        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            Tile(title: L("feels_like"), icon: "thermometer.medium", value: Fmt.temp(feels))
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
        // Dark frosted glass (like the Weather app), so the white text reads on any sky.
        .background(.ultraThinMaterial, in: .rect(cornerRadius: 22))
        .environment(\.colorScheme, .dark)
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
