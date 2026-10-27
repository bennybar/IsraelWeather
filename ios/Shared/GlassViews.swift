import SwiftUI
import WidgetKit

/// The widget's look: Sky Glass (C), Glass Capsules (B), or the classic Spectrum colors.
enum SpectrumStyle: String, CaseIterable, Sendable {
    case sky, capsules, classic
}

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
    var style: SpectrumStyle

    var body: some View {
        let now = entry.forecast?.currentHour()
        let night = now.map { WeatherCodes.isNight($0.t) } ?? false
        let rainy = now.map { WeatherCodes.isRainy($0.code) } ?? false
        Group {
            if let f = entry.forecast, let now, f.days(4).count >= 3 {
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
        WeatherIcon(code: code, night: night).font(.system(size: size))
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
        let days = f.days(4)
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

    private func capsulesSmall(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        let days = Array(f.days(3).dropFirst())
        return VStack(spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(entry.place).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                    Text(Fmt.temp(now.temp)).font(.system(size: 38, weight: .light))
                }
                Spacer(minLength: 0)
                icon(now.code, night: night, size: 20).frame(width: 40, height: 40).glass(20)
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                ForEach(days, id: \.date) { d in capsule(f, d, today: false, compact: true) }
            }
        }
    }

    private func capsulesMedium(_ f: Forecast, _ now: Hour, _ night: Bool) -> some View {
        let days = f.days(4)
        return VStack(spacing: 8) {
            HStack(spacing: 10) {
                icon(now.code, night: night, size: 22).frame(width: 44, height: 44).glass(22)
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
