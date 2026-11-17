import Charts
import SwiftUI

/// One day in full: a date strip to switch days, the day in a line, its temperature through the
/// day, when it rains, wind and sun, and every hour.
struct DayView: View {
    @EnvironmentObject var model: AppModel
    let page: Int
    @State private var day: String

    init(route: DayRoute) {
        page = route.page
        _day = State(initialValue: route.day)
    }

    var body: some View {
        let f = model.forecasts[page]?.0
        let days = f?.days(7) ?? []
        let d = days.first { $0.date == day }
        ZStack {
            SkyBackground(code: d?.code ?? 1250, night: false)
            ScrollView {
                VStack(spacing: 12) {
                    dateStrip(days)
                    if let f, let d {
                        summary(f, d)
                        temperature(f, d)
                        rain(f, d)
                        tiles(f, d)
                        hours(f, d)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 30)
            }
        }
        .foregroundStyle(.white)
        .navigationTitle(Fmt.longDay(day))
        .navigationBarTitleDisplayMode(.inline)
        .toolbarColorScheme(.dark, for: .navigationBar)
    }

    private func dateStrip(_ days: [Day]) -> some View {
        HStack(spacing: 0) {
            ForEach(days, id: \.date) { d in
                let (weekday, number) = Fmt.dayParts(d.date)
                let selected = d.date == day
                Button { day = d.date } label: {
                    VStack(spacing: 4) {
                        Text(weekday).font(.caption.weight(.semibold)).opacity(0.8)
                        Text(number).font(.title3.weight(selected ? .bold : .semibold))
                            .frame(width: 40, height: 40)
                            .background(Circle().fill(selected ? .white : .clear))
                            .foregroundStyle(selected ? Color(hex: 0x24304A) : .white)
                    }
                    .frame(maxWidth: .infinity, minHeight: 60)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Fmt.longDay(d.date))
            }
        }
        .padding(.top, 4)
    }

    private func summary(_ f: Forecast, _ d: Day) -> some View {
        HStack(alignment: .center, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(Fmt.temp(d.max)).font(.system(size: 52, weight: .light))
                Text(Fmt.temp(d.min)).font(.system(size: 26, weight: .light)).opacity(0.75)
            }
            WeatherGlyph(code: d.code, size: 30)
            Text(DaySummary.day(f, d)).font(.subheadline).fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }

    private func dayHours(_ f: Forecast, _ d: Day) -> [Hour] { f.hours(on: d.date) }

    /// The temperature through the day, with its high and low marked.
    private func temperature(_ f: Forecast, _ d: Day) -> some View {
        let hours = dayHours(f, d).filter { $0.temp != nil }
        let temps = hours.compactMap(\.temp)
        let lo = temps.min() ?? 0, hi = temps.max() ?? 1
        let top = hours.first { $0.temp == hi }, bottom = hours.first { $0.temp == lo }
        return Card(title: L("temperature"), icon: "thermometer.medium") {
            Chart(hours, id: \.t) { h in
                AreaMark(x: .value("Hour", h.date), yStart: .value("Base", lo - 3), yEnd: .value("Temp", h.temp ?? lo))
                    .foregroundStyle(LinearGradient(colors: [Color(hex: 0xFFD66B).opacity(0.35), Color(hex: 0xFFD66B).opacity(0.03)], startPoint: .top, endPoint: .bottom))
                    .interpolationMethod(.catmullRom)
                LineMark(x: .value("Hour", h.date), y: .value("Temp", h.temp ?? lo))
                    .foregroundStyle(Color(hex: 0xFFD66B))
                    .lineStyle(StrokeStyle(lineWidth: 3.5, lineCap: .round))
                    .interpolationMethod(.catmullRom)
                if h.t == top?.t {
                    PointMark(x: .value("Hour", h.date), y: .value("Temp", hi))
                        .foregroundStyle(.white)
                        .annotation(position: .top) { Text("\(L("high")) \(Fmt.temp(hi))").font(.caption.weight(.bold)) }
                }
                if h.t == bottom?.t {
                    PointMark(x: .value("Hour", h.date), y: .value("Temp", lo))
                        .foregroundStyle(.white)
                        .annotation(position: .bottom) { Text("\(L("low")) \(Fmt.temp(lo))").font(.caption.weight(.bold)) }
                }
            }
            .chartYScale(domain: (lo - 3)...(hi + 3))
            .chartYAxis(.hidden)
            .chartXAxis { hourAxis }
            .frame(height: 130)
        }
    }

    /// When it rains: the peak chance, the rainy hours and how much, and a bar per hour.
    private func rain(_ f: Forecast, _ d: Day) -> some View {
        let hours = dayHours(f, d)
        let peak = Int((hours.compactMap(\.rainChance).max() ?? 0).rounded())
        let wet = hours.filter { ($0.rainChance ?? 0) >= Double(rainyPct) }
        let total = hours.compactMap(\.rain).reduce(0, +)
        return Card(title: L("rain_chance"), icon: "drop.fill") {
            HStack(alignment: .firstTextBaseline) {
                Text("\(peak)%").font(.title2.weight(.semibold))
                Text(peak >= 20 ? L("at_peak") : L("no_rain_day")).font(.subheadline).opacity(0.8)
                Spacer()
                if let first = wet.first, let last = wet.last {
                    Text("\(Fmt.time(first.date))–\(Fmt.time(last.date.addingTimeInterval(3600)))" + (total >= 0.1 ? String(format: " · %.1f mm", total) : ""))
                        .font(.subheadline).opacity(0.8)
                }
            }
            // A dry day needs no chart.
            if peak >= 10 {
            Chart(hours, id: \.t) { h in
                BarMark(x: .value("Hour", h.date, unit: .hour), y: .value("Chance", max(h.rainChance ?? 0, 2)))
                    .foregroundStyle(Color(hex: 0x8EC5FF))
                    .clipShape(.rect(cornerRadius: 2))
            }
            .chartYScale(domain: 0...100)
            .chartYAxis(.hidden)
            .chartXAxis { hourAxis }
            .frame(height: 70)
            }
        }
    }

    private var hourAxis: some AxisContent {
        AxisMarks(values: .stride(by: .hour, count: 6)) { _ in
            AxisValueLabel(format: .dateTime.hour(.twoDigits(amPM: .omitted)), centered: false)
                .foregroundStyle(.white.opacity(0.8))
        }
    }

    private func tiles(_ f: Forecast, _ d: Day) -> some View {
        let hours = dayHours(f, d)
        let (wind, dir) = f.wind(d)
        let gust = hours.compactMap(\.gust).max()
        let place = model.places[page]
        let sun = place.flatMap { Sun.times(day: d.date, lat: $0.lat, lon: $0.lon) }
        let rh = hours.compactMap(\.rh)
        return HStack(spacing: 12) {
            Card(title: L("wind"), icon: "wind") {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(Fmt.wind(wind)) ").font(.title.weight(.medium)) + Text(Fmt.windUnit()).font(.subheadline)
                    if let dir { Text(String(format: L("from_dir"), Fmt.compass(dir))).font(.subheadline) }
                    if let gust { Text(String(format: L("gusts_up_to"), Fmt.wind(gust), Fmt.windUnit())).font(.caption).opacity(0.75) }
                }
            }
            Card(title: L("sun"), icon: "sunrise.fill") {
                VStack(alignment: .leading, spacing: 2) {
                    Text(sun.map { Fmt.time($0.0) } ?? "–").font(.title.weight(.medium))
                    Text("\(L("sunset")) \(sun.map { Fmt.time($0.1) } ?? "–")").font(.subheadline)
                    Text("UV \(d.uv.map { "\(Int($0.rounded()))" } ?? "–") · \(L("humidity")) \(rh.isEmpty ? "–" : "\(Int(rh.reduce(0, +) / Double(rh.count)))%")")
                        .font(.caption).opacity(0.75)
                }
            }
        }
    }

    /// Every hour of the day: time, icon, temperature, rain chance and wind.
    private func hours(_ f: Forecast, _ d: Day) -> some View {
        Card(title: L("hour_by_hour"), icon: "clock") {
            VStack(spacing: 0) {
                ForEach(Array(dayHours(f, d).enumerated()), id: \.offset) { i, h in
                    if i > 0 { Divider().overlay(.white.opacity(0.2)) }
                    HStack(spacing: 10) {
                        Text(Fmt.time(h.date)).font(.body.weight(.semibold)).monospacedDigit().frame(width: 54, alignment: .leading)
                        WeatherGlyph(code: h.code, night: WeatherCodes.isNight(h.t), size: 18).frame(width: 30)
                        Text(Fmt.temp(h.temp)).font(.body.weight(.semibold)).frame(width: 40, alignment: .leading)
                        Spacer(minLength: 0)
                        let c = Int((h.rainChance ?? 0).rounded())
                        Text(c >= 10 ? "\(c)%" : "").font(.subheadline.weight(.semibold)).foregroundStyle(Color(hex: 0x9FD2FF)).frame(width: 44, alignment: .trailing)
                        HStack(spacing: 3) {
                            if let dir = h.windDir {
                                Image(systemName: "location.north.fill").font(.system(size: 9)).rotationEffect(.degrees(dir + 180))
                            }
                            Text("\(Fmt.wind(h.wind)) \(Fmt.windUnit())")
                        }
                        .font(.subheadline).opacity(0.85).frame(width: 84, alignment: .trailing)
                    }
                    .frame(minHeight: 40)
                }
            }
        }
    }
}

/// Plain-language lines about the coming hours and about a day.
enum DaySummary {
    static let warmth = [Color(hex: 0x8EC5FF), Color(hex: 0xFFD66B), Color(hex: 0xFF9A5A)]

    private static func wet(_ h: Hour) -> Bool { (h.rainChance ?? 0) >= Double(rainyPct) }

    /// "Rain eases around 10:00.", "Rain likely from 15:00.", or the day's sky.
    static func nextHours(_ f: Forecast) -> String {
        let next = f.upcoming(12)
        if let first = next.first, wet(first) {
            if let dry = next.first(where: { !wet($0) }) { return String(format: L("sum_rain_eases"), Fmt.time(dry.date)) }
            return WeatherCodes.describe(first.code) + "."
        }
        if let rain = next.first(where: wet) { return String(format: L("sum_rain_from"), Fmt.time(rain.date)) }
        // The sky most of the coming hours share.
        let counts = Dictionary(next.map { ($0.code, 1) }, uniquingKeysWith: +)
        let sky = counts.max { $0.value < $1.value }?.key ?? 1250
        return String(format: L("sum_dry"), WeatherCodes.describe(sky))
    }

    /// The day in a line: when it rains (or that it stays dry), and wind when it's strong.
    static func day(_ f: Forecast, _ d: Day) -> String {
        let hours = f.hours(on: d.date)
        let rainy = hours.filter(wet)
        var s: String
        if let first = rainy.first, let last = rainy.last {
            s = String(format: L("sum_rain_window"), Fmt.time(first.date), Fmt.time(last.date.addingTimeInterval(3600)))
        } else {
            s = String(format: L("sum_dry"), WeatherCodes.describe(d.code))
        }
        if let w = f.wind(d).0, w >= 30 { s += String(format: L("sum_windy"), Fmt.wind(w), Fmt.windUnit()) }
        return s
    }
}
