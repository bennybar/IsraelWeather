import SwiftUI
import WidgetKit

/// The widget's colors (its AppEnum conformance lives in the widget only, so iOS sees one definition).
enum SpectrumColor: String, CaseIterable, Sendable {
    case lavender, orange, twilight, sky
}

struct SpectrumEntry: TimelineEntry {
    let date: Date
    let place: String
    let forecast: Forecast?
    let updated: Date?
    let color: SpectrumColor
}


// MARK: palettes (as on Android)

struct Palette {
    let bg: [Color], today: Color, ink: Color, muted: Color, track: Color, ring: Color, chip: Color, chipInk: Color, strong: Color
    /// Clouds: grey on light palettes (white vanishes there), white on dark ones.
    var cloud: Color { bg.first == Color(hex: 0x2B2158) || bg.first == Color(hex: 0x4F8FE8) ? .white : Color(hex: 0x9AA3B5) }

    static func of(_ c: SpectrumColor) -> Palette {
        switch c {
        case .lavender: return Palette(bg: [Color(hex: 0xEADFF9), Color(hex: 0xE0D3F5)], today: Color(hex: 0xF7F3FD), ink: Color(hex: 0x1E1633), muted: Color(hex: 0x5B5072), track: Color(hex: 0xD7CCEE), ring: Color(hex: 0x7259CC), chip: Color(hex: 0xE6EBFC), chipInk: Color(hex: 0x2F5FC8), strong: Color(hex: 0x2F5FC8))
        case .orange: return Palette(bg: [Color(hex: 0xFFE8CC), Color(hex: 0xFFDDB8)], today: Color(hex: 0xFFF7EE), ink: Color(hex: 0x2A1B0E), muted: Color(hex: 0x6A4A2A), track: Color(hex: 0xF5CFA6), ring: Color(hex: 0xD5620B), chip: Color(hex: 0xE8EEFB), chipInk: Color(hex: 0x1554B8), strong: Color(hex: 0x1554B8))
        case .twilight: return Palette(bg: [Color(hex: 0x2B2158), Color(hex: 0x5B45A8)], today: .white.opacity(0.13), ink: Color(hex: 0xF3EFFF), muted: Color(hex: 0xC9BFEA), track: .white.opacity(0.15), ring: Color(hex: 0xB79CFF), chip: Color(hex: 0x8FA8FF).opacity(0.2), chipInk: Color(hex: 0xB9CCFF), strong: Color(hex: 0x6F8BFF))
        case .sky: return Palette(bg: [Color(hex: 0x4F8FE8), Color(hex: 0x2353AD)], today: .white.opacity(0.16), ink: .white, muted: .white.opacity(0.8), track: .white.opacity(0.22), ring: .white, chip: .white.opacity(0.18), chipInk: Color(hex: 0xD7E8FF), strong: Color(hex: 0x8EC5FF).opacity(0.55))
        }
    }

    static let gradient = [Color(hex: 0x8FA8FF), Color(hex: 0xB48CFF), Color(hex: 0xFF9F6B)]
}

extension Color {
    init(hex: UInt32) { self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255) }
}

// MARK: views

struct SpectrumWidgetView: View {
    let entry: SpectrumEntry
    var small: Bool

    var body: some View {
        let p = Palette.of(entry.color)
        Group {
            if let f = entry.forecast, let now = f.currentHour(), !f.days(2).isEmpty {
                if small { small(f, now, p) } else { medium(f, now, p) }
            } else {
                Text(L("widget_open_app")).font(.footnote).foregroundStyle(p.ink).multilineTextAlignment(.center)
            }
        }
        .containerBackground(for: .widget) { LinearGradient(colors: p.bg, startPoint: .topLeading, endPoint: .bottomTrailing) }
    }

    private func icon(_ code: Int, night: Bool = false, size: CGFloat) -> some View {
        let p = Palette.of(entry.color)
        return WeatherIcon(code: code, night: night, cloud: p.cloud).font(.system(size: size))
    }

    /// Small: now on top, then three days as compact range rows.
    private func small(_ f: Forecast, _ now: Hour, _ p: Palette) -> some View {
        let days = f.days(3)
        return VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(entry.place).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                    Text(Fmt.temp(now.temp)).font(.system(size: 30, weight: .medium)).minimumScaleFactor(0.8)
                }
                Spacer(minLength: 0)
                icon(now.code, night: WeatherCodes.isNight(now.t), size: 22)
            }
            ForEach(days, id: \.date) { d in
                rangeRow(f, d, p, compact: true, today: d.date == days.first?.date)
            }
        }
        .foregroundStyle(p.ink)
    }

    /// Medium: header (place, condition · time, icon, temperature), then four day rows with
    /// shared-scale bars, rain chips and today's dot.
    private func medium(_ f: Forecast, _ now: Hour, _ p: Palette) -> some View {
        let days = f.days(4)
        return VStack(spacing: 3) {
            HStack(spacing: 6) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(entry.place).font(.system(size: 15, weight: .bold)).lineLimit(1)
                    Text(WeatherCodes.describe(now.code) + (entry.updated.map { " · " + Fmt.time($0) } ?? ""))
                        .font(.system(size: 11)).foregroundStyle(p.muted).lineLimit(1)
                }
                Spacer(minLength: 4)
                icon(now.code, night: WeatherCodes.isNight(now.t), size: 22)
                Text(Fmt.temp(now.temp)).font(.system(size: 28, weight: .semibold))
            }
            .padding(.bottom, 2)
            ForEach(days, id: \.date) { d in
                rangeRow(f, d, p, compact: false, today: d.date == days.first?.date)
            }
        }
        .foregroundStyle(p.ink)
    }

    private func rangeRow(_ f: Forecast, _ d: Day, _ p: Palette, compact: Bool, today: Bool) -> some View {
        let all = f.days(compact ? 3 : 4)
        let lo = all.compactMap(\.min).min() ?? 0
        let hi = max(all.compactMap(\.max).max() ?? lo, lo + 1)
        let chance = f.rainChance(d)
        return HStack(spacing: compact ? 4 : 6) {
            Text(Fmt.dayLabel(d.date)).font(.system(size: compact ? 11 : 12, weight: today ? .bold : .regular))
                .frame(width: compact ? 38 : 40, alignment: .leading).lineLimit(1).minimumScaleFactor(0.8)
            icon(d.code, size: compact ? 11 : 13).frame(width: compact ? 14 : 18)
            if !compact {
                Text(Fmt.temp(d.min)).font(.system(size: 12)).foregroundStyle(p.muted).frame(width: 28, alignment: .trailing)
            }
            RangeBar(lo: lo, hi: hi, from: d.min ?? lo, to: d.max ?? hi, dot: today ? f.currentHour()?.temp : nil, track: p.track, ring: p.ring)
                .frame(height: compact ? 5 : 6)
            Text(Fmt.temp(d.max)).font(.system(size: compact ? 11 : 12, weight: .semibold)).frame(width: compact ? 24 : 28, alignment: .leading)
            if !compact {
                let strong = (chance ?? 0) >= 30
                Text("\(Image(systemName: "drop.fill")) \(Fmt.pct(chance))").font(.system(size: 10, weight: strong ? .bold : .regular))
                    .foregroundStyle(strong ? .white : p.chipInk)
                    .frame(width: 46).padding(.vertical, 2)
                    .background(Capsule().fill(strong ? p.strong : p.chip))
            }
        }
        .padding(.horizontal, today ? 6 : 6).padding(.vertical, compact ? 1 : 2)
        .background(RoundedRectangle(cornerRadius: 9).fill(today ? p.today : .clear))
    }
}

/// A min→max bar on the shared scale, cool→warm, with an optional "now" dot. Mirrors in RTL.
struct RangeBar: View {
    let lo: Double, hi: Double, from: Double, to: Double, dot: Double?
    let track: Color, ring: Color

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let x = { (v: Double) in CGFloat((v - lo) / (hi - lo)).clamped() * w }
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                LinearGradient(colors: Palette.gradient, startPoint: .leading, endPoint: .trailing)
                    .frame(width: w)
                    .mask(alignment: .leading) { Capsule().frame(width: max(x(to) - x(from), h)).offset(x: x(from)) }
                if let dot {
                    Circle().fill(.white).overlay(Circle().stroke(ring, lineWidth: 2))
                        .frame(width: h + 5, height: h + 5).offset(x: min(max(x(dot) - (h + 5) / 2, 0), w - h - 5))
                }
            }
        }
    }
}

extension CGFloat {
    func clamped() -> CGFloat { Swift.min(Swift.max(self, 0), 1) }
}


/// For the widget gallery preview (no network there).
enum SampleData {
    static var forecast: Forecast {
        let today = Fmt.dayString(Date())
        let cal = Calendar.current
        let days = (0..<5).map { i -> Day in
            let d = Fmt.dayString(cal.date(byAdding: .day, value: i, to: Date())!)
            let codes = [1220, 1250, 1220, 1140, 1020]
            return Day(date: d, code: codes[i], min: [17, 16, 15, 14, 13][i], max: [26, 27, 26, 21, 19][i])
        }
        let hour = Hour(t: Fmt.slot(Date()), code: 1220, temp: 22, rainChance: 0)
        let rain = days.enumerated().map { i, d in Hour(t: d.date + " 12:00", code: d.code, temp: 20, rainChance: [0, 0, 10, 70, 90][i]) }
        return Forecast(id: 0, hourly: [hour] + rain, daily: days, source: today.isEmpty ? "" : Forecast.sourceIMS, now: hour)
    }
}

/// A weather SF Symbol in fixed colors: [cloud] for clouds, yellow sun, pale moon, blue rain/snow.
struct WeatherIcon: View {
    let code: Int
    var night = false
    var cloud: Color = .white

    var body: some View {
        let name = WeatherCodes.symbol(code, night: night)
        let accent: Color = name.contains("sun") ? Color(hex: 0xFFC53D)
            : name.contains("moon") ? Color(hex: 0xE8E4C9)
            : name.contains("bolt") ? Color(hex: 0xFFC53D)
            : Color(hex: 0x4D9BFF)
        if name.hasPrefix("sun.max") || name.hasPrefix("moon") {
            Image(systemName: name).foregroundStyle(accent)
        } else if name.hasPrefix("cloud") || name.hasPrefix("sun.") {
            // Two-layer symbols: the cloud (or dust) first, then the sun / moon / drops.
            Image(systemName: name).symbolRenderingMode(.palette).foregroundStyle(name.hasPrefix("sun.") ? accent : cloud, name.hasPrefix("sun.") ? cloud : accent)
        } else {
            Image(systemName: name).foregroundStyle(cloud)
        }
    }
}
