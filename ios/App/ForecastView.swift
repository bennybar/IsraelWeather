import SwiftUI

/// Pages for my location and each saved place, swiped sideways.
struct ForecastView: View {
    @EnvironmentObject var model: AppModel
    @State private var page = 0
    @State private var showSettings = false

    var body: some View {
        let pages = model.pages
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
                Image(systemName: "gearshape.fill").font(.title3).foregroundStyle(.white).padding(10)
            }
            .buttonStyle(.glass)
            .padding(.trailing, 16)
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

    private func header(_ f: Forecast?, _ now: Hour?) -> some View {
        let today = f?.days(1).first
        return VStack(spacing: 2) {
            if current { Text(L("my_location").uppercased()).font(.footnote.weight(.semibold)).opacity(0.9) }
            Text(model.places[id]?.name(english: Fmt.english) ?? "").font(.system(size: 32, weight: .medium))
            Text(Fmt.temp(now?.temp)).font(.system(size: 96, weight: .thin)).padding(.leading, 18)
            Text(now.map { WeatherCodes.describe($0.code) } ?? "").font(.title3.weight(.medium))
            if let today {
                Text("\(L("high")):\(Fmt.temp(today.max))  \(L("low")):\(Fmt.temp(today.min))").font(.title3.weight(.medium))
            }
        }
        .padding(.top, 60)
        .padding(.bottom, 10)
    }

    private func hourly(_ f: Forecast) -> some View {
        Card {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 18) {
                    ForEach(Array(f.upcoming(12).enumerated()), id: \.offset) { i, h in
                        VStack(spacing: 8) {
                            Text(i == 0 ? L("now") : Fmt.hour(h.date)).font(.subheadline.weight(.semibold))
                            Image(systemName: WeatherCodes.symbol(h.code, night: WeatherCodes.isNight(h.t)))
                                .symbolRenderingMode(.multicolor).font(.title3).frame(height: 26)
                            if let c = h.rainChance, c >= 20 {
                                Text("\(Int(c))%").font(.caption2.weight(.semibold)).foregroundStyle(Color(hex: 0x9FD2FF))
                            }
                            Text(Fmt.temp(h.temp)).font(.headline)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    private func week(_ f: Forecast) -> some View {
        let days = f.days(7)
        let lo = days.compactMap(\.min).min() ?? 0
        let hi = max(days.compactMap(\.max).max() ?? lo, lo + 1)
        return Card(title: L("week_title"), icon: "calendar") {
            VStack(spacing: 0) {
                ForEach(Array(days.enumerated()), id: \.offset) { i, d in
                    if i > 0 { Divider().overlay(.white.opacity(0.2)) }
                    HStack(spacing: 10) {
                        Text(Fmt.dayLabel(d.date)).font(.body.weight(.semibold)).frame(width: 52, alignment: .leading)
                        Image(systemName: WeatherCodes.symbol(d.code)).symbolRenderingMode(.multicolor).frame(width: 26)
                        Text(f.rainChance(d).flatMap { $0 >= 20 ? "\($0)%" : nil } ?? "")
                            .font(.caption.weight(.semibold)).foregroundStyle(Color(hex: 0x9FD2FF)).frame(width: 34)
                        Text(Fmt.temp(d.min)).opacity(0.7).frame(width: 34, alignment: .trailing)
                        RangeBarView(lo: lo, hi: hi, from: d.min ?? lo, to: d.max ?? hi, dot: i == 0 ? f.currentHour()?.temp : nil).frame(height: 5)
                        Text(Fmt.temp(d.max)).frame(width: 34, alignment: .leading)
                    }
                    .padding(.vertical, 9)
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

/// The cool→warm range bar on the week's shared scale, with today's "now" dot.
struct RangeBarView: View {
    let lo: Double, hi: Double, from: Double, to: Double, dot: Double?

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let x = { (v: Double) in CGFloat(min(max((v - lo) / (hi - lo), 0), 1)) * w }
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.22))
                LinearGradient(colors: [Color(hex: 0x8EC5FF), Color(hex: 0xFFD66B), Color(hex: 0xFF9A5A)], startPoint: .leading, endPoint: .trailing)
                    .frame(width: w)
                    .mask(alignment: .leading) { Capsule().frame(width: max(x(to) - x(from), h)).offset(x: x(from)) }
                if let dot {
                    Circle().fill(.white).overlay(Circle().stroke(.black.opacity(0.25), lineWidth: 2))
                        .frame(width: h + 5, height: h + 5).offset(x: min(max(x(dot) - (h + 5) / 2, 0), w - h - 5))
                }
            }
        }
    }
}
