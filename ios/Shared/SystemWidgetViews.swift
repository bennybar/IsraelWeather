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
        VStack(alignment: .leading, spacing: 0) {
            Text(weekday.uppercased()).font(.system(size: 11, weight: .bold)).foregroundStyle(.red).widgetAccentable()
            HStack(alignment: .firstTextBaseline) {
                Text(number).font(.system(size: 30, weight: .medium))
                Spacer(minLength: 0)
                Text(Fmt.temp(now.temp)).font(.system(size: 17, weight: .semibold, design: .rounded))
                SystemGlyph(code: now.code, night: WeatherCodes.isNight(now.t), size: 18)
            }
            Spacer(minLength: 4)
            ForEach(Array(events.enumerated()), id: \.offset) { _, e in
                HStack(alignment: .top, spacing: 6) {
                    RoundedRectangle(cornerRadius: 1.5).fill(e.color).frame(width: 3).widgetAccentable()
                    VStack(alignment: .leading, spacing: 0) {
                        Text(e.title).font(.system(size: 12, weight: .semibold)).lineLimit(1)
                        Text(e.detail).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 5)
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
        let wet = next.filter { ($0.rainChance ?? 0) >= Double(rainyPct) }
        if let first = wet.first {
            let run = next.drop { $0.t < first.t }.prefix { ($0.rainChance ?? 0) >= Double(rainyPct) }
            let end = (run.last ?? first).date.addingTimeInterval(3600)
            let peak = Int((run.compactMap(\.rainChance).max() ?? 0).rounded())
            out.append((.blue, "\(L("rain")) \(peak)%", "\(Fmt.time(first.date)) – \(Fmt.time(end))"))
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

/// The Weather widget's forecast rows in a small widget: day, icon, low, range bar, high.
struct SystemThreeDays: View {
    let place: String
    let f: Forecast
    let now: Hour

    var body: some View {
        let days = f.days(3)
        let lo = days.compactMap(\.min).min() ?? 0
        let hi = max(days.compactMap(\.max).max() ?? lo, lo + 1)
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                PlaceHead(place: place)
                Spacer(minLength: 2)
                Text(Fmt.temp(now.temp)).font(.system(size: 20, weight: .medium, design: .rounded))
            }
            Spacer(minLength: 4)
            VStack(spacing: 9) {
                ForEach(Array(days.enumerated()), id: \.offset) { i, d in
                    HStack(spacing: 4) {
                        Text(Fmt.dayLabel(d.date)).font(.system(size: 12, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.75)
                            .frame(width: 34, alignment: .leading)
                        SystemGlyph(code: d.code, size: 13).frame(width: 16)
                        Text(Fmt.temp(d.min)).font(.system(size: 12, weight: .medium, design: .rounded)).foregroundStyle(.secondary)
                            .lineLimit(1).fixedSize()
                        RangeBar(lo: lo, hi: hi, from: d.min ?? lo, to: d.max ?? hi, dot: i == 0 ? now.temp : nil,
                                 track: Color(uiColor: .systemFill), ring: .primary,
                                 colors: [Color(uiColor: .systemCyan), Color(uiColor: .systemYellow), Color(uiColor: .systemOrange)])
                            .frame(height: 4)
                            .widgetAccentable()
                        Text(Fmt.temp(d.max)).font(.system(size: 12, weight: .semibold, design: .rounded))
                            .lineLimit(1).fixedSize()
                    }
                }
            }
        }
    }
}
