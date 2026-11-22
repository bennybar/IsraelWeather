import SwiftUI
import UIKit
import WidgetKit

// "Israel Weather": small widgets in Apple's own widget style (system background and colors,
// SF type), following the Home Screen's light or dark look, and iOS's liquid glass in Clear
// and Tinted modes, where iOS removes the background and tints the accented parts.

/// Which small widget: Up next (C), Hourly list (D), Three days (H).
enum SystemStyle: String, CaseIterable, Sendable {
    case upNext, hourly, threeDays
}

/// The widget's appearance: the Home Screen's, or pinned. (Transparent glass is the Home
/// Screen's own Clear mode: iOS gives a widget no way to drop its background by itself.)
enum SystemAppearance: String, CaseIterable, Sendable {
    case automatic, light, dark
}

struct SystemEntry: TimelineEntry {
    let date: Date
    let place: String
    let forecast: Forecast?
    var style: SystemStyle = .upNext
    var appearance: SystemAppearance = .automatic
}

/// Apple's widget background: white in light mode, black in dark mode (iOS adds its glass sheen
/// on top, which is what makes Apple's dark widgets the near-black they are).
let systemWidgetBackground = Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? .black : .white })

struct SystemWidgetView: View {
    let entry: SystemEntry
    @Environment(\.colorScheme) private var homeScheme
    @Environment(\.widgetRenderingMode) private var mode

    /// A pinned look applies in full color only: in Clear and Tinted, iOS draws the glass.
    private var scheme: ColorScheme {
        guard mode == .fullColor else { return homeScheme }
        switch entry.appearance {
        case .automatic: return homeScheme
        case .light: return .light
        case .dark: return .dark
        }
    }

    var body: some View {
        Group {
            if let f = entry.forecast, let now = f.currentHour() {
                switch entry.style {
                case .upNext: SystemUpNext(f: f, now: now)
                case .hourly: SystemHourly(place: entry.place, f: f, now: now)
                case .threeDays: SystemThreeDays(place: entry.place, f: f, now: now)
                }
            } else {
                Text(L("widget_open_app")).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
        }
        .environment(\.colorScheme, scheme)
        .containerBackground(for: .widget) {
            switch entry.appearance {
            case .automatic: systemWidgetBackground
            case .light: Color.white
            case .dark: Color.black
            }
        }
    }
}

/// A weather icon colored like Apple's: yellow sun, grey clouds on white, white clouds on dark.
struct SystemGlyph: View {
    let code: Int
    var night = false
    let size: CGFloat
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        WeatherGlyph(code: code, night: night, size: size, cloud: scheme == .dark ? .white : Color(uiColor: .systemGray3))
    }
}

/// The small "Yavne ➤" heading.
private struct PlaceHead: View {
    let place: String
    var color: Color = .primary

    var body: some View {
        HStack(spacing: 3) {
            Text(place).lineLimit(1)
            Image(systemName: "location.fill").font(.system(size: 9))
        }
        .font(.system(size: 13, weight: .semibold))
        .foregroundStyle(color)
    }
}

// MARK: C · Up next

/// Like Calendar's Up Next: the day in red, then what's coming as entries with colored bars.
struct SystemUpNext: View {
    let f: Forecast
    let now: Hour

    var body: some View {
        let today = Fmt.dayString(Date())
        let (weekday, number) = weekdayAndNumber(today)
        // The date block, then the entries, with the free height shared evenly between them.
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: -2) {
                    Text(weekday.uppercased()).font(.system(size: 11, weight: .bold)).foregroundStyle(.red).widgetAccentable()
                    Text(number).font(.system(size: 32, weight: .medium))
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 3) {
                    SystemGlyph(code: now.code, night: WeatherCodes.isNight(now.t), size: 17)
                    Text(Fmt.temp(now.temp)).font(.system(size: 17, weight: .semibold, design: .rounded))
                }
            }
            ForEach(Array(events.enumerated()), id: \.offset) { _, e in
                Spacer(minLength: 4)
                HStack(alignment: .top, spacing: 6) {
                    RoundedRectangle(cornerRadius: 1.5).fill(e.color).frame(width: 3).widgetAccentable()
                    VStack(alignment: .leading, spacing: 0) {
                        Text(e.title).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                        Text(e.detail).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func weekdayAndNumber(_ day: String) -> (String, String) {
        guard let d = Fmt.parseDay(day) else { return ("", "") }
        let f = DateFormatter()
        f.locale = Locale(identifier: Fmt.english ? "en_US" : "he_IL")
        f.timeZone = israelZone
        f.dateFormat = "EEEE"
        let n = DateFormatter()
        n.locale = Locale(identifier: "en_US_POSIX")
        n.timeZone = israelZone
        n.dateFormat = "d"
        return (f.string(from: d), n.string(from: d))
    }

    /// The next 24 hours as two entries: rain (or a dry day), and the warmest (or coolest) hour.
    private var events: [(color: Color, title: String, detail: String)] {
        let next = f.upcoming(24)
        var out: [(Color, String, String)] = []
        let wet = next.filter(\.wet)
        if let first = wet.first {
            let run = next.drop { $0.t < first.t }.prefix(while: \.wet)
            let end = (run.last ?? first).date.addingTimeInterval(3600)
            let peak = Int((run.compactMap(\.rainChance).max() ?? 0).rounded())
            out.append((.blue, peak >= rainyPct ? "\(L("rain")) \(peak)%" : L("rain"), "\(Fmt.time(first.date)) – \(Fmt.time(end))"))
        } else {
            out.append((.green, L("dry_next"), L("no_rain_day")))
        }
        let day = next.prefix(14)
        if let warm = day.filter({ $0.temp != nil }).max(by: { ($0.temp ?? 0) < ($1.temp ?? 0) }), (warm.temp ?? 0) > (now.temp ?? 0) + 0.5 {
            out.append((.orange, String(format: L("warmest"), Fmt.temp(warm.temp)), Fmt.time(warm.date)))
        } else if let cool = next.filter({ $0.temp != nil }).min(by: { ($0.temp ?? 0) < ($1.temp ?? 0) }) {
            out.append((.indigo, String(format: L("coolest"), Fmt.temp(cool.temp)), Fmt.time(cool.date)))
        }
        return out
    }
}

// MARK: D · Hourly list

/// A Reminders-style list: the place and temperature in blue, then the next four hours.
struct SystemHourly: View {
    let place: String
    let f: Forecast
    let now: Hour

    var body: some View {
        let hours = [now] + Array(f.upcoming(4).dropFirst()).prefix(3)
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                PlaceHead(place: place, color: .blue).widgetAccentable()
                Spacer(minLength: 2)
                Text(Fmt.temp(now.temp)).font(.system(size: 24, weight: .semibold, design: .rounded)).foregroundStyle(.blue).widgetAccentable()
            }
            Spacer(minLength: 4)
            VStack(spacing: 5) {
                ForEach(Array(hours.enumerated()), id: \.offset) { i, h in
                    HStack(spacing: 6) {
                        Text(i == 0 ? L("now") : Fmt.time(h.date)).frame(width: 40, alignment: .leading)
                        SystemGlyph(code: h.code, night: WeatherCodes.isNight(h.t), size: 15).frame(width: 18)
                        Spacer(minLength: 0)
                        let c = Int((h.rainChance ?? 0).rounded())
                        if c >= 20 {
                            Text("\(c)%").font(.system(size: 11, weight: .semibold)).foregroundStyle(.blue)
                        }
                        Text(Fmt.temp(h.temp)).font(.system(size: 13, weight: .medium, design: .rounded)).frame(width: 28, alignment: .trailing)
                    }
                    .font(.system(size: 13, weight: i == 0 ? .semibold : .regular))
                }
            }
        }
    }
}

// MARK: H · Three days

/// Three day columns: day, icon, high, an upright range bar on the days' shared scale, low.
struct SystemThreeDays: View {
    let place: String
    let f: Forecast
    let now: Hour

    var body: some View {
        let days = f.days(3)
        let lo = days.compactMap(\.min).min() ?? 0
        let hi = max(days.compactMap(\.max).max() ?? lo, lo + 1)
        VStack(spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                PlaceHead(place: place)
                Spacer(minLength: 2)
                Text(Fmt.temp(now.temp)).font(.system(size: 17, weight: .semibold, design: .rounded))
            }
            HStack(spacing: 0) {
                ForEach(Array(days.enumerated()), id: \.offset) { i, d in
                    VStack(spacing: 3) {
                        Text(Fmt.dayLabel(d.date)).font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(i == 0 ? .primary : .secondary).lineLimit(1).minimumScaleFactor(0.8)
                        SystemGlyph(code: d.code, size: 15).frame(height: 19)
                        Text(Fmt.temp(d.max)).font(.system(size: 14, weight: .semibold, design: .rounded))
                        UprightRangeBar(lo: lo, hi: hi, from: d.min ?? lo, to: d.max ?? hi)
                            .frame(width: 5)
                            .widgetAccentable()
                        Text(Fmt.temp(d.min)).font(.system(size: 12, weight: .medium, design: .rounded)).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(Fmt.dayLabel(d.date)), \(WeatherCodes.describe(d.code)), \(String(format: L("high_low"), Fmt.temp(d.max), Fmt.temp(d.min)))")
                }
            }
        }
    }
}

/// A min→max bar standing up: warm on top, cool below, on a shared scale.
struct UprightRangeBar: View {
    let lo: Double, hi: Double, from: Double, to: Double

    var body: some View {
        GeometryReader { g in
            let h = g.size.height
            let y = { (v: Double) in CGFloat(1 - (v - lo) / (hi - lo)).clamped() * h }
            ZStack(alignment: .top) {
                Capsule().fill(Color(uiColor: .systemFill))
                LinearGradient(colors: [Color(uiColor: .systemOrange), Color(uiColor: .systemYellow), Color(uiColor: .systemCyan)], startPoint: .top, endPoint: .bottom)
                    .mask(alignment: .top) { Capsule().frame(height: max(y(from) - y(to), g.size.width)).offset(y: y(to)) }
            }
        }
    }
}
