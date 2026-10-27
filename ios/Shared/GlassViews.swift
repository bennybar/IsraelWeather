import SwiftUI
import WidgetKit

/// A liquid-glass surface drawn by the widget itself: a translucent white fill, a rim that is
/// bright on top and faint below, and a soft highlight.
struct GlassSurface: ViewModifier {
    var radius: CGFloat
    var fill = 0.16

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        content
            .background(shape.fill(.white.opacity(fill)))
            .overlay(shape.fill(LinearGradient(colors: [.white.opacity(0.14), .clear], startPoint: .top, endPoint: .center)).allowsHitTesting(false))
            .overlay(shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.55), .white.opacity(0.08), .white.opacity(0.2)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
    }
}

extension View {
    func glass(_ radius: CGFloat, fill: Double = 0.16) -> some View { modifier(GlassSurface(radius: radius, fill: fill)) }
}

struct GlassSpectrumView: View {
    let entry: SpectrumEntry
    var small: Bool
    private var style: SpectrumLayout { entry.options.layout }
    private var count: Int { entry.options.dayCount }
    @Environment(\.refreshWrap) private var refreshWrap

    /// The current-weather icon, tappable to refresh (location and forecast). While that runs it
    /// shows a refresh arrow; iOS dims the rest until the new timeline lands.
    private func nowIcon(_ now: Hour, _ night: Bool, size: CGFloat, bubble: CGFloat? = nil) -> AnyView {
        let glyph = Group {
            if entry.refreshing {
                Image(systemName: "arrow.triangle.2.circlepath").font(.system(size: size * 0.85, weight: .semibold))
            } else {
                icon(now.code, night: night, size: size)
            }
        }
        let shaped: AnyView = bubble.map { b in AnyView(glyph.frame(width: b, height: b).glass(b / 2)) } ?? AnyView(glyph)
        return refreshWrap(entry.placeID, shaped)
    }

    var body: some View {
        let now = entry.forecast?.currentHour()
        let night = now.map { WeatherCodes.isNight($0.t) } ?? false
        let rainy = now.map { WeatherCodes.isRainy($0.code) } ?? false
        Group {
            if let f = entry.forecast, let now, f.days(count).count >= 3 {
                switch (style, small) {
                case (.capsules, true): capsulesSmall(f, now, night)
                case (.capsules, false): capsulesMedium(f, now, night)
                case (_, true): skySmall(f, now, night)
                default: skyMedium(f, now, night)
                }
            } else {
                Text(L("widget_open_app")).font(.footnote).multilineTextAlignment(.center)
            }
        }
        .foregroundStyle(.white)
        .invalidatableContent()
        .containerBackground(for: .widget) {
            ZStack {
                LinearGradient(colors: background(night: night, rainy: rainy), startPoint: .topLeading, endPoint: .bottomTrailing)
                // The sheet's own highlight, top-left.
                LinearGradient(colors: [.white.opacity(0.18), .clear, .clear, .white.opacity(0.06)], startPoint: .topLeading, endPoint: .bottomTrailing)
            }
        }
    }

    private func background(night: Bool, rainy: Bool) -> [Color] {
        if style == .capsules {
            return rainy ? [Color(hex: 0x3B4559), Color(hex: 0x222A3A)]
                : night ? [Color(hex: 0x1C2142), Color(hex: 0x2A1E4A)]
                : [Color(hex: 0x33478A), Color(hex: 0x3A2A66)]
        }
        return rainy ? [Color(hex: 0x6F819C), Color(hex: 0x404D66)]
            : night ? [Color(hex: 0x23305A), Color(hex: 0x2B2552)]
            : [Color(hex: 0x5294EC), Color(hex: 0x2552B0)]
    }

    private func icon(_ code: Int, night: Bool = false, size: CGFloat) -> some View {
        WeatherGlyph(code: code, night: night, size: size, cloud: .white)
    }

    private var updated: String { entry.updated.map { " · " + Fmt.time($0) } ?? "" }

    private func highLow(_ d: Day?) -> String {
        "\(L("high")):\(Fmt.temp(d?.max)) \(L("low")):\(Fmt.temp(d?.min))"
    }

    // MARK: C · Sky Glass

    private func skySmall(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(entry.place).font(.system(size: 14, weight: .semibold)).lineLimit(1)
            Text(Fmt.temp(now.temp)).font(.system(size: 42, weight: .light))
            Spacer(minLength: 0)
            icon(now.code, night: night, size: 16)
            Text(WeatherCodes.describe(now.code)).font(.system(size: 12, weight: .semibold)).lineLimit(1).padding(.top, 3)
            Text(highLow(f.days(1).first)).font(.system(size: 12)).opacity(0.85)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    private func skyMedium(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        let days = f.days(count)
        let lo = days.compactMap(\.min).min() ?? 0
        let hi = max(days.compactMap(\.max).max() ?? lo, lo + 1)
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text(entry.place).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                Text(Fmt.temp(now.temp)).font(.system(size: 42, weight: .light))
                Spacer(minLength: 0)
                icon(now.code, night: night, size: 16)
                Text(WeatherCodes.describe(now.code)).font(.system(size: 12, weight: .semibold)).lineLimit(1).padding(.top, 3)
                Text(highLow(days.first) + updated).font(.system(size: 11)).opacity(0.85).lineLimit(1).minimumScaleFactor(0.8)
            }
            .frame(width: 118, alignment: .leading)
            VStack(spacing: 0) {
                ForEach(Array(days.enumerated()), id: \.offset) { i, d in
                    HStack(spacing: 6) {
                        Text(Fmt.dayLabel(d.date)).font(.system(size: 12, weight: .semibold)).frame(width: 38, alignment: .leading).lineLimit(1).minimumScaleFactor(0.8)
                        icon(d.code, size: 12).frame(width: 16)
                        RangeBar(lo: lo, hi: hi, from: d.min ?? lo, to: d.max ?? hi, dot: i == 0 ? now.temp : nil,
                                 track: .white.opacity(0.2), ring: .black.opacity(0.2), colors: skyGradient)
                            .frame(height: 5)
                        Text(Fmt.temp(d.max)).font(.system(size: 12, weight: .semibold)).frame(width: 26, alignment: .trailing)
                    }
                    .frame(maxHeight: .infinity)
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 4)
            .glass(18)
        }
    }

    // MARK: B · Glass Capsules

    @ViewBuilder
    private func capsulesSmall(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        switch entry.options.capsulesSmall {
        case .hours: capsulesHours(f, now, night)
        case .rain:
            // Rain watch only when rain is coming; on dry days, the two days.
            if let wet = f.upcoming(12).first(where: { ($0.rainChance ?? 0) >= Double(rainyPct) }) {
                capsulesRain(f, now, night, wet)
            } else {
                capsulesDays(f, now, night)
            }
        case .now: capsulesNow(f, now, night)
        case .rows: capsulesRows(f, now, night)
        case .days: capsulesDays(f, now, night)
        }
    }

    private var place: some View { Text(entry.place).font(.system(size: 14, weight: .semibold)).lineLimit(1) }

    /// C · the next three hours, with rain chance when it matters.
    private func capsulesHours(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        let hours = Array(f.upcoming(4).dropFirst())
        let today = f.days(1).first
        return VStack(spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    place
                    Text(Fmt.temp(now.temp)).font(.system(size: 34, weight: .light))
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 2) {
                    nowIcon(now, night, size: 18)
                    Text(highLow(today)).font(.system(size: 10)).opacity(0.8).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            HStack(spacing: 5) {
                ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                    VStack(spacing: 2) {
                        Text(Fmt.hour(h.date)).font(.system(size: 11, weight: .semibold))
                        icon(h.code, night: WeatherCodes.isNight(h.t), size: 14).frame(height: 16)
                        Text(Fmt.temp(h.temp)).font(.system(size: 11, weight: .semibold))
                        Text((h.rainChance ?? 0) >= 20 ? "\(Int(h.rainChance ?? 0))%" : " ")
                            .font(.system(size: 9.5, weight: .bold)).foregroundStyle(Color(hex: 0xA9D4FF))
                    }
                    .padding(.vertical, 5)
                    .frame(maxWidth: .infinity)
                    .glass(14)
                }
            }
        }
    }

    /// D · when rain is coming: when, how likely, and the next hours as bars.
    private func capsulesRain(_ f: Forecast, _ now: Hour, _ night: Bool, _ wet: Hour) -> some View {
        let next = f.upcoming(8)
        return VStack(spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    place
                    Text(Fmt.temp(now.temp)).font(.system(size: 32, weight: .light))
                }
                Spacer(minLength: 0)
                nowIcon(now, night, size: 20, bubble: 38)
            }
            Spacer(minLength: 0)
            VStack(alignment: .leading, spacing: 2) {
                Text(L("rain_from")).font(.system(size: 10.5)).opacity(0.8)
                Text("\(Fmt.time(wet.date)) · \(Int(wet.rainChance ?? 0))%").font(.system(size: 17, weight: .semibold))
                HStack(alignment: .bottom, spacing: 3) {
                    ForEach(Array(next.enumerated()), id: \.offset) { _, h in
                        RoundedRectangle(cornerRadius: 2).fill(Color(hex: 0xA9D4FF))
                            .frame(height: max(2, 16 * CGFloat(h.rainChance ?? 0) / 100))
                    }
                }
                .frame(height: 16, alignment: .bottom)
            }
            .padding(.horizontal, 10).padding(.vertical, 7)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glass(16)
        }
    }

    /// E · just now, in a big glass bubble, with today's high and low.
    private func capsulesNow(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        let today = f.days(1).first
        return VStack(spacing: 4) {
            place.frame(maxWidth: .infinity, alignment: .leading)
            VStack(spacing: 0) {
                icon(now.code, night: night, size: 20)
                Text(Fmt.temp(now.temp)).font(.system(size: 30, weight: .light))
            }
            .frame(width: 88, height: 88)
            .glass(44)
            Spacer(minLength: 0)
            HStack(spacing: 5) {
                Text("↑ " + Fmt.temp(today?.max)).padding(.horizontal, 8).padding(.vertical, 2).glass(10)
                Text("↓ " + Fmt.temp(today?.min)).padding(.horizontal, 8).padding(.vertical, 2).glass(10)
            }
            .font(.system(size: 11, weight: .semibold))
        }
    }

    /// F · three days as capsule rows with range bars.
    private func capsulesRows(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        let days = Array(f.days(4).dropFirst())
        let all = f.days(4)
        let lo = all.compactMap(\.min).min() ?? 0
        let hi = max(all.compactMap(\.max).max() ?? lo, lo + 1)
        return VStack(spacing: 4) {
            HStack {
                Text(entry.place).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                Text(Fmt.temp(now.temp)).font(.system(size: 15, weight: .light))
                Spacer(minLength: 0)
                nowIcon(now, night, size: 16)
            }
            Spacer(minLength: 0)
            ForEach(days, id: \.date) { d in
                HStack(spacing: 5) {
                    Text(Fmt.dayLabel(d.date)).font(.system(size: 11, weight: .bold)).frame(width: 30, alignment: .leading).lineLimit(1).minimumScaleFactor(0.8)
                    icon(d.code, size: 11).frame(width: 15)
                    RangeBar(lo: lo, hi: hi, from: d.min ?? lo, to: d.max ?? hi, dot: nil, track: .white.opacity(0.2), ring: .clear, colors: skyGradient)
                        .frame(height: 5)
                    Text(Fmt.temp(d.max)).font(.system(size: 11, weight: .bold)).frame(width: 24, alignment: .trailing)
                }
                .padding(.horizontal, 8).padding(.vertical, 5)
                .glass(12)
            }
        }
    }

    /// A · now on top, the next two days in capsules.
    private func capsulesDays(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        let days = Array(f.days(3).dropFirst())
        return VStack(spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(entry.place).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                    Text(Fmt.temp(now.temp)).font(.system(size: 38, weight: .light))
                }
                Spacer(minLength: 0)
                nowIcon(now, night, size: 20, bubble: 40)
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                ForEach(days, id: \.date) { d in capsule(f, d, today: false, compact: true) }
            }
        }
    }

    private func capsulesMedium(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        let days = f.days(count)
        return VStack(spacing: 8) {
            HStack(spacing: 10) {
                nowIcon(now, night, size: 22, bubble: 44)
                VStack(alignment: .leading, spacing: 1) {
                    Text(entry.place).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                    Text(WeatherCodes.describe(now.code) + " · " + highLow(days.first) + updated)
                        .font(.system(size: 11)).opacity(0.85).lineLimit(1).minimumScaleFactor(0.8)
                }
                Spacer(minLength: 4)
                Text(Fmt.temp(now.temp)).font(.system(size: 34, weight: .light))
            }
            HStack(spacing: 6) {
                ForEach(Array(days.enumerated()), id: \.offset) { i, d in capsule(f, d, today: i == 0, compact: false) }
            }
        }
    }

    private func capsule(_ f: Forecast, _ d: Day, today: Bool, compact: Bool) -> some View {
        let chance = f.rainChance(d) ?? 0
        return VStack(spacing: compact ? 2 : 3) {
            Text(today ? L("today") : Fmt.dayLabel(d.date)).font(.system(size: 11, weight: today ? .bold : .semibold)).lineLimit(1).minimumScaleFactor(0.8)
            icon(d.code, size: compact ? 14 : 16).frame(height: compact ? 16 : 19)
            HStack(spacing: 3) {
                Text(Fmt.temp(d.max)).fontWeight(.semibold)
                Text(Fmt.temp(d.min)).opacity(0.65)
            }
            .font(.system(size: 11))
            if !compact && chance >= 20 {
                Text("\(chance)%").font(.system(size: 9.5, weight: .bold)).foregroundStyle(Color(hex: 0xA9D4FF))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .glass(compact ? 15 : 17, fill: today ? 0.28 : 0.16)
    }

    private var skyGradient: [Color] { [Color(hex: 0x8EC5FF), Color(hex: 0xFFD66B), Color(hex: 0xFF9A5A)] }
}
