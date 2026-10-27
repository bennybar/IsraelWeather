import SwiftUI
import WidgetKit

// The Spectrum widget, as on Android: layouts (Sky Glass and Glass Capsules are iOS's own), colors,
// days, stats pills, wind and rain pills. Medium carries the layouts; small shows now and a few days.

/// The widget's layout (Edit Widget → Layout).
enum SpectrumLayout: String, CaseIterable, Sendable {
    case sky, capsules, classic, refined, tiles, aurora, bold, boldLines
}

/// The widget's colors, for the classic layouts (their AppEnum conformance lives in the widget).
enum SpectrumColor: String, CaseIterable, Sendable {
    case lavender, orange, twilight, sky, mint, rose, glass, aurora
}

/// Glass Capsules' small widget: two days (A), next hours (C), rain watch (D), big now (E), stacked days (F).
enum CapsulesSmall: String, CaseIterable, Sendable {
    case days, hours, rain, now, rows
}

/// One widget's settings.
struct WidgetOptions: Sendable {
    var layout: SpectrumLayout = .sky
    var capsulesSmall: CapsulesSmall = .days
    var color: SpectrumColor = .lavender
    var days = 4
    /// Feels-like, humidity and wind under the header (Classic, Refined, Tiles).
    var stats = true
    /// Each day's wind (Classic; the other layouts always show it).
    var wind = false
    /// Wind as a capsule with an arrow, instead of plain text.
    var windCapsules = false
    /// Rain pills all the same width.
    var equalPills = false

    /// Days a layout shows: Android's choices, within what a medium iOS widget fits.
    var dayCount: Int {
        switch layout {
        case .refined: return 3
        case .tiles, .sky, .capsules: return min(max(days, 3), 5)
        case .aurora, .bold, .boldLines: return min(max(days, 3), 4)
        case .classic: return min(max(days, 2), stats ? 4 : 5)
        }
    }
}

struct SpectrumEntry: TimelineEntry {
    let date: Date
    let place: String
    let forecast: Forecast?
    let updated: Date?
    var options = WidgetOptions()
}

/// Rainy enough for the solid pill.
let rainyPct = 30

// MARK: palettes (Android's)

struct Palette {
    var bg: [Color]
    var today: Color
    var soft: Color
    var ink: Color
    var muted: Color
    var track: Color
    var ring: Color
    var gradient: [Color]
    var chip: Color
    var chipBorder: Color
    var chipInk: Color
    var chipStrong: Color
    /// A dark card: white clouds and drops.
    var dark = false
    /// Text on today's tile when it needs its own (Aurora's light mint).
    var todayInk: Color?
    /// The soft shape behind Aurora's big icon.
    var blob: Color?
    /// Liquid glass: a light sheen over the card.
    var sheen = false
    /// Text on an accent-colored pill (Bold lines' today).
    var ringInk: Color = .white

    init(bg: [Color], today: Color, soft: Color? = nil, ink: Color, muted: Color, track: Color, ring: Color, gradient: [Color],
         chip: Color, chipBorder: Color, chipInk: Color, chipStrong: Color, dark: Bool = false, todayInk: Color? = nil, blob: Color? = nil,
         sheen: Bool = false, ringInk: Color = .white) {
        self.bg = bg; self.today = today; self.soft = soft ?? today.opacity(0.55); self.ink = ink; self.muted = muted
        self.track = track; self.ring = ring; self.gradient = gradient; self.chip = chip; self.chipBorder = chipBorder
        self.chipInk = chipInk; self.chipStrong = chipStrong; self.dark = dark; self.todayInk = todayInk; self.blob = blob
        self.sheen = sheen; self.ringInk = ringInk
    }

    /// Clouds: grey on light cards (white vanishes there), white on dark ones.
    var cloud: Color { dark ? .white : Color(hex: 0x9AA3B5) }

    static func of(_ c: SpectrumColor) -> Palette {
        switch c {
        case .lavender:
            return Palette(bg: [Color(hex: 0xEADFF9), Color(hex: 0xE2D6F6)], today: Color(hex: 0xF7F3FD), ink: Color(hex: 0x1E1633), muted: Color(hex: 0x5B5072),
                           track: Color(hex: 0xD7CCEE), ring: Color(hex: 0x7259CC), gradient: [Color(hex: 0x8FA8FF), Color(hex: 0xB48CFF), Color(hex: 0xFF9F6B)],
                           chip: Color(hex: 0xE6EBFC), chipBorder: Color(hex: 0xC3CEF4), chipInk: Color(hex: 0x2F5FC8), chipStrong: Color(hex: 0x2F5FC8))
        case .orange:
            return Palette(bg: [Color(hex: 0xFFE8CC), Color(hex: 0xFFDEB9)], today: Color(hex: 0xFFF7EE), ink: Color(hex: 0x2A1B0E), muted: Color(hex: 0x6A4A2A),
                           track: Color(hex: 0xF5CFA6), ring: Color(hex: 0xD5620B), gradient: [Color(hex: 0x8FB4FF), Color(hex: 0xFFC07A), Color(hex: 0xF0703A)],
                           chip: Color(hex: 0xE8EEFB), chipBorder: Color(hex: 0xC6D3F2), chipInk: Color(hex: 0x1554B8), chipStrong: Color(hex: 0x1554B8))
        case .twilight:
            return Palette(bg: [Color(hex: 0x2B2158), Color(hex: 0x5B45A8)], today: .white.opacity(0.12), soft: .white.opacity(0.066), ink: Color(hex: 0xF3EFFF), muted: Color(hex: 0xC9BFEA),
                           track: .white.opacity(0.14), ring: Color(hex: 0xB79CFF), gradient: [Color(hex: 0x7FA2FF), Color(hex: 0xC9A8FF), Color(hex: 0xFFB38A)],
                           chip: Color(hex: 0x8FA8FF).opacity(0.18), chipBorder: Color(hex: 0x8FA8FF).opacity(0.36), chipInk: Color(hex: 0xB9CCFF), chipStrong: Color(hex: 0x6F8BFF), dark: true)
        case .sky:
            return Palette(bg: [Color(hex: 0xE4F0FC), Color(hex: 0xD6E7F8)], today: Color(hex: 0xF4F9FF), ink: Color(hex: 0x10243A), muted: Color(hex: 0x4E6178),
                           track: Color(hex: 0xC7DAF0), ring: Color(hex: 0x2F6FD0), gradient: [Color(hex: 0x7FB0F0), Color(hex: 0xA79AEE), Color(hex: 0xF2A07C)],
                           chip: Color(hex: 0xE2EBFB), chipBorder: Color(hex: 0xBCCFF0), chipInk: Color(hex: 0x1F56B8), chipStrong: Color(hex: 0x1F56B8))
        case .mint:
            return Palette(bg: [Color(hex: 0xE3F4EA), Color(hex: 0xD7EEE1)], today: Color(hex: 0xF4FBF7), ink: Color(hex: 0x10291C), muted: Color(hex: 0x4C6656),
                           track: Color(hex: 0xC3E2D0), ring: Color(hex: 0x2E8A5C), gradient: [Color(hex: 0x86B4EE), Color(hex: 0x9BCFB4), Color(hex: 0xF0A57E)],
                           chip: Color(hex: 0xE2EEF9), chipBorder: Color(hex: 0xBED2EA), chipInk: Color(hex: 0x2A5DAE), chipStrong: Color(hex: 0x2F62B8))
        case .rose:
            return Palette(bg: [Color(hex: 0xFBE7EC), Color(hex: 0xF6DCE3)], today: Color(hex: 0xFFF5F7), ink: Color(hex: 0x34151F), muted: Color(hex: 0x75505C),
                           track: Color(hex: 0xEFC9D3), ring: Color(hex: 0xC2416E), gradient: [Color(hex: 0x93A9EE), Color(hex: 0xC79BE0), Color(hex: 0xF0907E)],
                           chip: Color(hex: 0xE5EAFA), chipBorder: Color(hex: 0xC6D0F0), chipInk: Color(hex: 0x2E57AE), chipStrong: Color(hex: 0x3D5FBF))
        case .glass:
            // Smoky glass. A widget can't show the wallpaper through itself, so the smoke is opaque.
            return Palette(bg: [Color(hex: 0x3A4256), Color(hex: 0x1E2433)], today: .white.opacity(0.22), ink: Color(hex: 0xFBFBFF), muted: Color(hex: 0xE6E9F2).opacity(0.85),
                           track: .white.opacity(0.14), ring: Color(hex: 0xBFD5FF), gradient: [Color(hex: 0x9DB8FF), Color(hex: 0xD2B5FF), Color(hex: 0xFFB892)],
                           chip: .white.opacity(0.16), chipBorder: .white.opacity(0.25), chipInk: Color(hex: 0xE3ECFF), chipStrong: Color(hex: 0x5B8CFF), dark: true,
                           sheen: true, ringInk: Color(hex: 0x1D2548))
        case .aurora:
            return Palette(bg: [Color(hex: 0x1D2548), Color(hex: 0x232C55)], today: Color(hex: 0xBDF4D4), soft: Color(hex: 0x2F3768), ink: Color(hex: 0xF1F2FF), muted: Color(hex: 0xB4B7E2),
                           track: Color(hex: 0x3A4275), ring: Color(hex: 0xBDF4D4), gradient: [Color(hex: 0x8FA8FF), Color(hex: 0xB9A6F5), Color(hex: 0xFFB48C)],
                           chip: .white.opacity(0.16), chipBorder: .white.opacity(0.25), chipInk: Color(hex: 0xDDE4FF), chipStrong: Color(hex: 0x5B8CFF), dark: true,
                           todayInk: Color(hex: 0x1D2548), blob: Color(hex: 0x465089), ringInk: Color(hex: 0x1D2548))
        }
    }

    /// Liquid glass on a light Home Screen: frosted white with dark text.
    static let glassLight = Palette(bg: [Color(hex: 0xF7F9FE), Color(hex: 0xE6ECF8)], today: .white, soft: .white.opacity(0.6), ink: Color(hex: 0x1B2240),
                                    muted: Color(hex: 0x56607F), track: Color(hex: 0x1B2240).opacity(0.1), ring: Color(hex: 0x3A6FE0),
                                    gradient: [Color(hex: 0x8FA8FF), Color(hex: 0xB48CFF), Color(hex: 0xFF9F6B)],
                                    chip: Color(hex: 0xE8EEFB), chipBorder: Color(hex: 0xC9D5F2), chipInk: Color(hex: 0x2F5FC8), chipStrong: Color(hex: 0x2F5FC8), sheen: true)

    /// The palette for [c] on this Home Screen: Liquid glass follows its light / dark / clear look.
    static func of(_ c: SpectrumColor, scheme: ColorScheme, mode: WidgetRenderingMode) -> Palette {
        c == .glass && scheme == .light && mode == .fullColor ? glassLight : of(c)
    }

    static let gradient = [Color(hex: 0x8FA8FF), Color(hex: 0xB48CFF), Color(hex: 0xFF9F6B)]
}

extension Color {
    init(hex: UInt32) { self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255) }
}

// MARK: the widget

struct SpectrumWidgetView: View {
    let entry: SpectrumEntry
    var small: Bool
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetRenderingMode) private var mode

    var body: some View {
        let o = entry.options
        if o.layout == .sky || o.layout == .capsules {
            GlassSpectrumView(entry: entry, small: small)
        } else {
            let p = Palette.of(o.color, scheme: scheme, mode: mode)
            Group {
                if let f = entry.forecast, let now = f.currentHour(), f.days(o.dayCount).count >= 2 {
                    SpectrumLayouts(c: SpectrumContext(entry: entry, f: f, now: now, p: p, small: small))
                } else {
                    Text(L("widget_open_app")).font(.footnote).foregroundStyle(p.ink).multilineTextAlignment(.center)
                }
            }
            .containerBackground(for: .widget) {
                ZStack {
                    LinearGradient(colors: p.bg, startPoint: .topLeading, endPoint: .bottomTrailing)
                    if p.sheen {
                        LinearGradient(colors: [.white.opacity(0.2), .clear, .clear, .white.opacity(0.07)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    }
                }
            }
        }
    }
}

/// What every layout needs: the forecast, the days on one temperature scale, colors, settings.
struct SpectrumContext {
    let entry: SpectrumEntry
    let f: Forecast
    let now: Hour
    let p: Palette
    let small: Bool

    var o: WidgetOptions { entry.options }
    var days: [Day] { f.days(small ? min(o.dayCount, 3) : o.dayCount) }
    var night: Bool { WeatherCodes.isNight(now.t) }
    var lo: Double { days.compactMap(\.min).min() ?? 0 }
    var hi: Double { max(days.compactMap(\.max).max() ?? lo, lo + 1) }
    var time: String { entry.updated.map { Fmt.time($0) } ?? "" }
}

struct SpectrumLayouts: View {
    let c: SpectrumContext
    private var p: Palette { c.p }

    var body: some View {
        Group {
            if c.small {
                switch c.o.layout {
                case .refined: smallRefined
                case .tiles: smallTiles
                case .aurora: smallAurora
                case .bold: smallBold(lines: false)
                case .boldLines: smallBold(lines: true)
                default: small
                }
            } else {
                switch c.o.layout {
                case .refined: refined
                case .tiles: tiles
                case .aurora: aurora
                case .bold: bold(lines: false)
                case .boldLines: bold(lines: true)
                default: classic
                }
            }
        }
        .foregroundStyle(p.ink)
    }

    // MARK: parts

    private func glyph(_ code: Int, night: Bool = false, _ size: CGFloat) -> some View {
        WeatherGlyph(code: code, night: night, size: size, cloud: p.cloud)
    }

    private func bar(_ d: Day, today: Bool) -> RangeBar {
        RangeBar(lo: c.lo, hi: c.hi, from: d.min ?? c.lo, to: d.max ?? c.hi, dot: today ? c.now.temp : nil, track: p.track, ring: p.ring, colors: p.gradient)
    }

    /// An arrow toward where the wind blows (north stays up in Hebrew too).
    private func arrow(_ dir: Double, _ size: CGFloat) -> some View {
        Image(systemName: "location.north.fill").font(.system(size: size)).foregroundStyle(p.ring)
            .rotationEffect(.degrees(dir + 180))
    }

    private func wind(_ d: Day, size: CGFloat, unit: Bool) -> some View {
        let (w, dir) = c.f.wind(d)
        return HStack(spacing: 2) {
            if let dir, w != nil { arrow(dir, size - 2) }
            Text(w != nil ? Fmt.wind(w) : "–").font(.system(size: size)).foregroundStyle(p.muted)
            if unit, w != nil { Text(Fmt.windUnit()).font(.system(size: size - 3)).foregroundStyle(p.muted) }
        }
        .lineLimit(1)
    }

    /// Classic's wind capsule: arrow and speed, like the rain pills.
    private func windCapsule(_ d: Day, size: CGFloat) -> some View {
        let (w, dir) = c.f.wind(d)
        return HStack(spacing: 2) {
            if let dir { arrow(dir, size - 2) }
            Text(w != nil ? Fmt.wind(w) : "–").font(.system(size: size, weight: .bold))
        }
        .padding(.horizontal, 5).padding(.vertical, 1)
        .background(Capsule().fill(p.track))
        .overlay(Capsule().strokeBorder(p.muted.opacity(0.3), lineWidth: 1))
        .lineLimit(1)
    }

    /// Rain chance: pale under 30%, solid blue from it.
    private func rainPill(_ d: Day, size: CGFloat, width: CGFloat?) -> some View {
        let chance = c.f.rainChance(d)
        let strong = (chance ?? 0) >= rainyPct
        return HStack(spacing: 2) {
            Image(systemName: "drop.fill").font(.system(size: size - 3))
                .foregroundStyle(strong || p.dark ? .white : p.chipInk)
            Text(Fmt.pct(chance)).font(.system(size: size, weight: strong ? .bold : .semibold))
                .foregroundStyle(strong ? .white : p.chipInk)
        }
        .lineLimit(1).minimumScaleFactor(0.8)
        .padding(.horizontal, 5).padding(.vertical, 1.5)
        .frame(width: width)
        .background(Capsule().fill(strong ? p.chipStrong : p.chip))
        .overlay(Capsule().strokeBorder(strong ? p.chipStrong : p.chipBorder, lineWidth: 1))
    }

    private func dayName(_ d: Day, _ size: CGFloat, today: Bool, color: Color? = nil) -> some View {
        Text(Fmt.dayLabel(d.date)).font(.system(size: size, weight: today ? .bold : .regular))
            .foregroundStyle(color ?? p.ink).lineLimit(1).minimumScaleFactor(0.75)
    }

    private var feels: Double? { Sun.apparent(temp: c.now.temp, rh: c.now.rh, windKmh: c.now.wind) }

    /// "Feels 24°", "Humidity 60%", "Wind ↗ 12": Classic's soft pills.
    private var statPills: some View {
        HStack(spacing: 5) {
            if let feels { statPill(L("w_feels"), Fmt.temp(feels)) }
            if let rh = c.now.rh { statPill(L("humidity"), "\(Int(rh.rounded()))%") }
            if let w = c.now.wind { statPill(L("wind"), Fmt.wind(w), dir: c.now.windDir) }
            Spacer(minLength: 0)
        }
    }

    private func statPill(_ label: String, _ value: String, dir: Double? = nil) -> some View {
        HStack(spacing: 3) {
            Text(label).foregroundStyle(p.muted)
            if let dir { arrow(dir, 8) }
            Text(value).fontWeight(.bold)
        }
        .font(.system(size: 10.5)).lineLimit(1)
        .padding(.horizontal, 7).padding(.vertical, 2)
        .background(Capsule().fill(p.today))
    }

    /// Refined and Tiles: the same stats as one quiet line.
    private var statsLine: some View {
        HStack(spacing: 0) {
            if let feels { stat(L("w_feels"), Fmt.temp(feels)) }
            Spacer(minLength: 4)
            if let rh = c.now.rh { stat(L("humidity"), "\(Int(rh.rounded()))%") }
            Spacer(minLength: 4)
            if let w = c.now.wind { stat(L("wind"), Fmt.wind(w) + " " + Fmt.windUnit(), dir: c.now.windDir) }
        }
        .font(.system(size: 11)).lineLimit(1)
        .padding(.horizontal, 2)
    }

    private func stat(_ label: String, _ value: String, dir: Double? = nil) -> some View {
        HStack(spacing: 3) {
            Text(label).foregroundStyle(p.muted)
            if let dir { arrow(dir, 8) }
            Text(value).fontWeight(.bold)
        }
    }

    /// Place and condition (and update time); current icon and temperature.
    private var header: some View {
        HStack(spacing: 6) {
            VStack(alignment: .leading, spacing: 0) {
                Text(c.entry.place).font(.system(size: 16, weight: .bold)).lineLimit(1)
                Text(WeatherCodes.describe(c.now.code) + (c.time.isEmpty ? "" : " · " + c.time))
                    .font(.system(size: 11.5)).foregroundStyle(p.muted).lineLimit(1)
            }
            Spacer(minLength: 4)
            glyph(c.now.code, night: c.night, 24)
            Text(Fmt.temp(c.now.temp)).font(.system(size: 28, weight: .bold)).lineLimit(1)
        }
    }

    // MARK: small

    private var small: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(c.entry.place).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                    Text(Fmt.temp(c.now.temp)).font(.system(size: 30, weight: .medium)).foregroundStyle(c.o.layout == .aurora ? p.ring : p.ink)
                }
                Spacer(minLength: 0)
                glyph(c.now.code, night: c.night, 22)
            }
            Spacer(minLength: 0)
            ForEach(Array(c.days.enumerated()), id: \.offset) { i, d in
                HStack(spacing: 4) {
                    dayName(d, 11, today: i == 0, color: i == 0 ? p.todayInk : nil).frame(width: 34, alignment: .leading)
                    glyph(d.code, 11).frame(width: 16)
                    bar(d, today: i == 0).frame(height: 5)
                    Text(Fmt.temp(d.max)).font(.system(size: 11, weight: .semibold)).frame(width: 24, alignment: .trailing)
                        .foregroundStyle(i == 0 ? p.todayInk ?? p.ink : p.ink)
                }
                .padding(.horizontal, 5).padding(.vertical, 2)
                .background(RoundedRectangle(cornerRadius: 8).fill(i == 0 ? p.today : .clear))
            }
        }
    }

    /// The small widgets' top: place and temperature, the icon in the corner.
    private var smallHeader: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: -2) {
                Text(c.entry.place).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                Text(Fmt.temp(c.now.temp)).font(.system(size: 28, weight: .bold))
            }
            Spacer(minLength: 0)
            glyph(c.now.code, night: c.night, 20)
        }
    }

    /// Refined, small: three rows of day, icon, high/low and the rain pill.
    private var smallRefined: some View {
        VStack(spacing: 2) {
            smallHeader
            Spacer(minLength: 0)
            ForEach(Array(c.days.prefix(3).enumerated()), id: \.offset) { i, d in
                HStack(spacing: 4) {
                    dayName(d, 10.5, today: i == 0).frame(width: 30, alignment: .leading)
                    glyph(d.code, 10).frame(width: 13)
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text(Fmt.temp(d.max)).font(.system(size: 11, weight: .bold))
                        Text(Fmt.temp(d.min)).font(.system(size: 9.5)).foregroundStyle(p.muted)
                    }
                    .fixedSize()
                    Spacer(minLength: 0)
                    rainPill(d, size: 9, width: nil).fixedSize()
                }
                .padding(.horizontal, 4).padding(.vertical, 2.5)
                .background(RoundedRectangle(cornerRadius: 8).fill(i == 0 ? p.today : .clear))
            }
        }
    }

    /// Tiles, small: today and tomorrow as two tiles.
    private var smallTiles: some View {
        VStack(spacing: 6) {
            smallHeader
            HStack(spacing: 5) {
                ForEach(Array(c.days.prefix(2).enumerated()), id: \.offset) { i, d in
                    VStack(spacing: 2) {
                        dayName(d, 11, today: i == 0, color: i == 0 ? p.ring : p.ink)
                        glyph(d.code, 15)
                        HStack(alignment: .firstTextBaseline, spacing: 2) {
                            Text(Fmt.temp(d.max)).font(.system(size: 14, weight: .bold))
                            Text(Fmt.temp(d.min)).font(.system(size: 10.5)).foregroundStyle(p.muted)
                        }
                        .lineLimit(1)
                        bar(d, today: i == 0).frame(height: 4).padding(.bottom, 2)
                        rainPill(d, size: 9.5, width: nil)
                    }
                    .padding(.horizontal, 5).padding(.vertical, 4)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: 12).fill(i == 0 ? p.today : p.soft))
                }
            }
        }
    }

    /// Aurora, small: the big accent temperature and the icon on its blob; today and tomorrow below.
    private var smallAurora: some View {
        VStack(spacing: 5) {
            HStack(spacing: 4) {
                VStack(alignment: .leading, spacing: -3) {
                    Text(Fmt.temp(c.now.temp)).font(.system(size: 34, weight: .bold)).foregroundStyle(p.ring)
                    HStack(spacing: 2) {
                        Image(systemName: "location.fill").font(.system(size: 8))
                        Text(c.entry.place).font(.system(size: 12, weight: .bold)).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                ZStack {
                    Blob().fill(p.blob ?? p.track)
                    glyph(c.now.code, night: c.night, 20)
                }
                .frame(width: 46, height: 38)
            }
            HStack(spacing: 5) {
                ForEach(Array(c.days.prefix(2).enumerated()), id: \.offset) { i, d in
                    let ink = i == 0 ? p.todayInk ?? p.ink : p.ink
                    VStack(alignment: .leading, spacing: 1) {
                        Text(Fmt.dayLabel(d.date)).font(.system(size: 11, weight: .bold)).lineLimit(1).minimumScaleFactor(0.8)
                        HStack(spacing: 2) {
                            glyph(d.code, 16)
                            Spacer(minLength: 0)
                            VStack(alignment: .trailing, spacing: -2) {
                                Text(Fmt.temp(d.max)).font(.system(size: 14, weight: .bold))
                                Text(Fmt.temp(d.min)).font(.system(size: 11, weight: .bold)).opacity(0.75)
                            }
                        }
                        HStack(spacing: 2) {
                            Image(systemName: "drop.fill").font(.system(size: 7))
                            Text(Fmt.pct(c.f.rainChance(d)))
                        }
                        .font(.system(size: 9.5))
                    }
                    .foregroundStyle(ink)
                    .padding(.horizontal, 6).padding(.vertical, 4)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: 13).fill(i == 0 ? p.today : p.soft))
                }
            }
        }
    }

    /// Bold, small: two columns led by the big high (Bold lines: no tiles, a hairline, today's pill).
    private func smallBold(lines: Bool) -> some View {
        VStack(spacing: 4) {
            smallHeader
            HStack(spacing: lines ? 0 : 5) {
                ForEach(Array(c.days.prefix(2).enumerated()), id: \.offset) { i, d in
                    if i > 0 && lines {
                        Rectangle().fill(p.chipBorder).frame(width: 1).padding(.vertical, 6).padding(.horizontal, 5)
                    }
                    let today = i == 0
                    let ink = today && !lines ? p.todayInk ?? p.ink : p.ink
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(spacing: 2) {
                            if today && lines {
                                Text(Fmt.dayLabel(d.date)).font(.system(size: 10, weight: .bold)).foregroundStyle(p.ringInk).fixedSize()
                                    .padding(.horizontal, 4).padding(.vertical, 1).background(Capsule().fill(p.ring))
                            } else {
                                Text(Fmt.dayLabel(d.date)).font(.system(size: 11, weight: .bold)).foregroundStyle(today ? p.ring : ink)
                            }
                            Spacer(minLength: 0)
                            glyph(d.code, 13)
                        }
                        .lineLimit(1).minimumScaleFactor(0.8)
                        Text(Fmt.temp(d.max)).font(.system(size: 20, weight: .bold))
                        Text("▾ " + Fmt.temp(d.min)).font(.system(size: 10.5, weight: .bold)).foregroundStyle(p.muted)
                        bar(d, today: today).frame(height: 4).padding(.top, 3)
                    }
                    .foregroundStyle(ink)
                    .padding(.horizontal, 6).padding(.vertical, 4)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: 13).fill(lines ? .clear : today ? p.today : p.soft))
                }
            }
        }
    }

    // MARK: Classic

    private var classic: some View {
        let days = c.days
        let font: CGFloat = days.count <= 3 ? 13 : 12
        let today = days.first
        return VStack(spacing: 0) {
            HStack(spacing: 6) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(c.entry.place).font(.system(size: 15, weight: .bold)).lineLimit(1)
                    Text(WeatherCodes.describe(c.now.code) + (c.time.isEmpty ? "" : " · " + c.time))
                        .font(.system(size: 11)).foregroundStyle(p.muted).lineLimit(1)
                }
                Spacer(minLength: 4)
                glyph(c.now.code, night: c.night, 24)
                VStack(spacing: -2) {
                    Text(Fmt.temp(c.now.temp)).font(.system(size: 26, weight: .bold))
                    Text("↑\(Fmt.temp(today?.max)) ↓\(Fmt.temp(today?.min))").font(.system(size: 10)).foregroundStyle(p.muted)
                }
                .lineLimit(1)
            }
            if c.o.stats { statPills.padding(.top, 5) }
            VStack(spacing: 1) {
                ForEach(Array(days.enumerated()), id: \.offset) { i, d in classicRow(d, today: i == 0, font: font) }
            }
            .padding(.top, 4)
        }
    }

    private func classicRow(_ d: Day, today: Bool, font: CGFloat) -> some View {
        HStack(spacing: 5) {
            dayName(d, font, today: today).frame(width: 38, alignment: .leading)
            glyph(d.code, font + 2).frame(width: 20)
            Text(Fmt.temp(d.min)).font(.system(size: font)).foregroundStyle(p.muted).frame(width: 26, alignment: .trailing)
            bar(d, today: today).frame(height: 6)
            Text(Fmt.temp(d.max)).font(.system(size: font, weight: .bold)).frame(width: 28, alignment: .leading)
            if c.o.wind {
                Group {
                    if c.o.windCapsules { windCapsule(d, size: font - 2) } else { wind(d, size: font - 2, unit: false) }
                }
                .frame(width: c.o.windCapsules ? 46 : 38, alignment: .trailing)
            }
            rainPill(d, size: font - 2, width: c.o.equalPills ? 46 : nil).frame(width: 48, alignment: .trailing)
        }
        .padding(.horizontal, 6)
        .frame(maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 10).fill(today ? p.today : .clear))
    }

    // MARK: Refined (three days)

    private var refined: some View {
        VStack(spacing: 0) {
            header
            if c.o.stats { statsLine.padding(.top, 3) }
            VStack(spacing: 2) {
                ForEach(Array(c.days.enumerated()), id: \.offset) { i, d in refinedRow(d, today: i == 0) }
            }
            .padding(.top, 5)
        }
    }

    private func refinedRow(_ d: Day, today: Bool) -> some View {
        HStack(spacing: 5) {
            dayName(d, 13, today: today).frame(width: 38, alignment: .leading)
            glyph(d.code, 14).frame(width: 20)
            Text(Fmt.temp(d.min)).font(.system(size: 12)).foregroundStyle(p.muted).frame(width: 26, alignment: .trailing)
            bar(d, today: today).frame(height: 5)
            Text(Fmt.temp(d.max)).font(.system(size: 13, weight: .bold)).frame(width: 28, alignment: .leading)
            wind(d, size: 11, unit: false).frame(width: 38, alignment: .leading)
            rainPill(d, size: 11, width: 44)
        }
        .padding(.horizontal, 6)
        .frame(maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 10).fill(today ? p.today : .clear))
    }

    // MARK: Tiles (3–5)

    private var tiles: some View {
        let narrow = c.days.count >= 4
        return VStack(spacing: 0) {
            header
            if c.o.stats && !narrow { statsLine.padding(.top, 3) }
            HStack(spacing: narrow ? 4 : 6) {
                ForEach(Array(c.days.enumerated()), id: \.offset) { i, d in tile(d, today: i == 0, narrow: narrow) }
            }
            .padding(.top, 6)
        }
    }

    private func tile(_ d: Day, today: Bool, narrow: Bool) -> some View {
        VStack(spacing: narrow ? 1 : 2) {
            if narrow {
                dayName(d, 11, today: today, color: today ? p.ring : p.ink)
                glyph(d.code, 15)
            } else {
                HStack(spacing: 4) {
                    dayName(d, 12, today: today, color: today ? p.ring : p.ink)
                    glyph(d.code, 15)
                }
            }
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(Fmt.temp(d.max)).font(.system(size: narrow ? 14 : 16, weight: .bold))
                Text(Fmt.temp(d.min)).font(.system(size: narrow ? 10.5 : 12)).foregroundStyle(p.muted)
            }
            .lineLimit(1)
            bar(d, today: today).frame(height: 5).padding(.vertical, 1)
            if narrow {
                wind(d, size: 10, unit: false)
                rainPill(d, size: 10, width: nil)
            } else {
                HStack(spacing: 5) {
                    wind(d, size: 10.5, unit: false)
                    rainPill(d, size: 10.5, width: nil)
                }
            }
        }
        .padding(.horizontal, narrow ? 3 : 6).padding(.vertical, 3)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: narrow ? 12 : 14).fill(today ? p.today : p.soft))
    }

    // MARK: Aurora

    private var aurora: some View {
        VStack(spacing: 6) {
            HStack(spacing: 8) {
                Text(Fmt.temp(c.now.temp)).font(.system(size: 40, weight: .bold)).foregroundStyle(p.ring).lineLimit(1)
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 3) {
                        Image(systemName: "location.fill").font(.system(size: 10))
                        Text(c.entry.place).font(.system(size: 15, weight: .bold)).lineLimit(1)
                    }
                    Text(WeatherCodes.describe(c.now.code) + (c.time.isEmpty ? "" : " · " + c.time))
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(p.muted).lineLimit(1)
                }
                Spacer(minLength: 0)
                ZStack {
                    Blob().fill(p.blob ?? p.track)
                    glyph(c.now.code, night: c.night, 26)
                }
                .frame(width: 60, height: 48)
            }
            HStack(spacing: 6) {
                ForEach(Array(c.days.enumerated()), id: \.offset) { i, d in auroraTile(d, today: i == 0) }
            }
        }
    }

    private func auroraTile(_ d: Day, today: Bool) -> some View {
        let ink = today ? p.todayInk ?? p.ink : p.ink
        let muted = today ? p.todayInk ?? p.muted : p.muted
        let roomy = c.days.count <= 3
        let (w, _) = c.f.wind(d)
        return VStack(alignment: .leading, spacing: 1) {
            Text(Fmt.dayLabel(d.date)).font(.system(size: 12, weight: .bold)).lineLimit(1).minimumScaleFactor(0.8)
            HStack(spacing: 2) {
                glyph(d.code, roomy ? 22 : 18)
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: -1) {
                    Text(Fmt.temp(d.max)).font(.system(size: roomy ? 17 : 15, weight: .bold))
                    Text(Fmt.temp(d.min)).font(.system(size: roomy ? 13 : 12, weight: .bold)).foregroundStyle(muted)
                }
            }
            HStack(spacing: 2) {
                Image(systemName: "wind").font(.system(size: 9))
                Text((w != nil ? Fmt.wind(w) : "–") + (roomy && w != nil ? " " + Fmt.windUnit() : ""))
                Spacer(minLength: 2)
                Image(systemName: "drop.fill").font(.system(size: 8))
                Text(Fmt.pct(c.f.rainChance(d)))
            }
            .font(.system(size: 9.5)).lineLimit(1).minimumScaleFactor(0.8)
        }
        .foregroundStyle(ink)
        .padding(.horizontal, 7).padding(.vertical, 5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 16).fill(today ? p.today : p.soft))
    }

    // MARK: Bold / Bold lines

    private func bold(lines: Bool) -> some View {
        VStack(spacing: 5) {
            header
            HStack(spacing: lines ? 0 : 6) {
                ForEach(Array(c.days.enumerated()), id: \.offset) { i, d in
                    if i > 0 && lines {
                        Rectangle().fill(p.chipBorder).frame(width: 1).padding(.vertical, 8).padding(.horizontal, 3)
                    }
                    boldTile(d, today: i == 0, framed: !lines)
                }
            }
        }
    }

    private func boldTile(_ d: Day, today: Bool, framed: Bool) -> some View {
        let ink = today && framed ? p.todayInk ?? p.ink : p.ink
        let muted = today && framed ? p.todayInk ?? p.muted : p.muted
        let roomy = c.days.count <= 3
        let (w, dir) = c.f.wind(d)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 3) {
                if today && !framed {
                    // Bold lines: today is a small pill in the accent color.
                    Text(Fmt.dayLabel(d.date)).font(.system(size: 10.5, weight: .bold)).foregroundStyle(p.ringInk)
                        .padding(.horizontal, 5).padding(.vertical, 1).background(Capsule().fill(p.ring))
                } else {
                    Text(Fmt.dayLabel(d.date)).font(.system(size: 11.5, weight: .bold)).foregroundStyle(today ? p.ring : ink)
                }
                Spacer(minLength: 2)
                glyph(d.code, framed ? 15 : 14)
            }
            .lineLimit(1).minimumScaleFactor(0.8)
            Text(Fmt.temp(d.max)).font(.system(size: 20, weight: .bold))
            Text("▾ " + Fmt.temp(d.min)).font(.system(size: 11, weight: .bold)).foregroundStyle(muted)
            bar(d, today: today).frame(height: 5).padding(.vertical, 3)
            HStack(spacing: 2) {
                if let dir, w != nil { arrow(dir, 8) }
                Text(w != nil ? Fmt.wind(w) : "–").font(.system(size: 10, weight: .bold))
                Spacer(minLength: 2)
                if roomy {
                    Rectangle().fill(p.chipBorder).frame(width: 1, height: 10)
                    Spacer(minLength: 2)
                    Image(systemName: "drop.fill").font(.system(size: 8)).foregroundStyle(muted)
                }
                Text(Fmt.pct(c.f.rainChance(d))).font(.system(size: 10))
            }
            .lineLimit(1)
        }
        .foregroundStyle(ink)
        .padding(.horizontal, 7).padding(.vertical, 4)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 16).fill(!framed ? .clear : today ? p.today : p.soft))
    }
}

/// Aurora's soft, slightly wobbly blob behind the big icon.
struct Blob: Shape {
    func path(in r: CGRect) -> Path {
        var path = Path()
        for i in 0...120 {
            let a = 2 * Double.pi * Double(i) / 120
            let k = 1 + 0.035 * sin(3 * a + 0.6) + 0.02 * cos(5 * a)
            let pt = CGPoint(x: r.midX + r.width / 2 * 0.92 * k * cos(a), y: r.midY + r.height / 2 * 0.92 * k * sin(a))
            if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
        }
        path.closeSubpath()
        return path
    }
}

/// A min→max bar on the shared scale, cool→warm, with an optional "now" dot. Cool sits where
/// reading starts: left in English, right in Hebrew.
struct RangeBar: View {
    let lo: Double, hi: Double, from: Double, to: Double, dot: Double?
    let track: Color, ring: Color
    var colors: [Color] = Palette.gradient
    @Environment(\.layoutDirection) private var direction

    var body: some View {
        let rtl = direction == .rightToLeft
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let x = { (v: Double) -> CGFloat in
                let f = CGFloat((v - lo) / (hi - lo)).clamped()
                return (rtl ? 1 - f : f) * w
            }
            let a = min(x(from), x(to)), b = max(x(from), x(to))
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                LinearGradient(colors: rtl ? colors.reversed() : colors, startPoint: .leading, endPoint: .trailing)
                    .frame(width: w)
                    .mask(alignment: .leading) { Capsule().frame(width: max(b - a, h)).offset(x: a) }
                if let dot {
                    Circle().fill(.white).overlay(Circle().stroke(ring, lineWidth: 2))
                        .frame(width: h + 5, height: h + 5).offset(x: min(max(x(dot) - (h + 5) / 2, 0), w - h - 5))
                }
            }
            // The positions above are absolute (left to right).
            .environment(\.layoutDirection, .leftToRight)
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
        let hour = Hour(t: Fmt.slot(Date()), code: 1220, temp: 22, rainChance: 0, rh: 60, wind: 12, windDir: 270)
        let rain = days.enumerated().map { i, d in Hour(t: d.date + " 12:00", code: d.code, temp: 20, rainChance: [0, 0, 10, 70, 90][i], wind: 14, windDir: 250) }
        return Forecast(id: 0, hourly: [hour] + rain, daily: days, source: today.isEmpty ? "" : Forecast.sourceIMS, now: hour)
    }
}
