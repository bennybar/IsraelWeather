#if DEBUG
import SwiftUI

/// Debug-only: renders the Spectrum widget (small and medium, every color) with live data to PNGs in
/// Documents/widgets, since the simulator can't add widgets from a script. Launch with -renderWidgets.
@MainActor
enum WidgetSnapshots {
    static func render() async {
        let s = AppSettings.shared
        try? await Task.sleep(for: .seconds(6)) // let location and the first fetch land
        guard let fid = s.currentForecastID ?? s.places.first.map(s.forecastID(for:)),
              let (f, at) = await Repository.shared.forecast(fid) else { print("WIDGETS: no forecast"); return }
        let name = await Repository.shared.place(for: s.currentForecastID != nil ? currentID : fid)?.name(english: Fmt.english) ?? ""
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("widgets")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        // Each layout in a color that suits it (Classic twice: with wind capsules, and in English days).
        let looks: [(String, WidgetOptions)] = [
            ("sky", WidgetOptions(layout: .sky)),
            ("capsules", WidgetOptions(layout: .capsules)),
            ("classic", WidgetOptions(layout: .classic, color: .lavender, days: 3)),
            ("classic-wind", WidgetOptions(layout: .classic, color: .orange, days: 4, stats: false, wind: true, windCapsules: true, equalPills: true)),
            ("refined", WidgetOptions(layout: .refined, color: .sky)),
            ("tiles", WidgetOptions(layout: .tiles, color: .mint, days: 3)),
            ("tiles5", WidgetOptions(layout: .tiles, color: .rose, days: 5)),
            ("aurora", WidgetOptions(layout: .aurora, color: .aurora, days: 4)),
            ("bold", WidgetOptions(layout: .bold, color: .twilight, days: 4)),
            ("boldLines", WidgetOptions(layout: .boldLines, color: .glass, days: 4)),
        ]
        for (name2, o) in looks {
            for small in [true, false] {
                let entry = SpectrumEntry(date: Date(), place: name, forecast: f, updated: at, options: o)
                let content = SpectrumWidgetView(entry: entry, small: small)
                // containerBackground isn't drawn outside a widget: approximate it for the snapshot.
                let night = f.currentHour().map { WeatherCodes.isNight($0.t) } ?? false
                let bg: [Color] = o.layout == .capsules
                    ? (night ? [Color(hex: 0x1C2142), Color(hex: 0x2A1E4A)] : [Color(hex: 0x33478A), Color(hex: 0x3A2A66)])
                    : o.layout == .sky ? (night ? [Color(hex: 0x23305A), Color(hex: 0x2B2552)] : [Color(hex: 0x5294EC), Color(hex: 0x2552B0)])
                    : Palette.of(o.color).bg
                let view = content
                    .padding(16)
                    .frame(width: small ? 170 : 364, height: 170)
                    .background(LinearGradient(colors: bg, startPoint: .topLeading, endPoint: .bottomTrailing))
                    .clipShape(.rect(cornerRadius: 24))
                let r = ImageRenderer(content: view)
                r.scale = 3
                if let png = r.uiImage?.pngData() {
                    try? png.write(to: dir.appendingPathComponent("\(name2)-\(small ? "small" : "medium").png"))
                }
            }
        }
        print("WIDGETS: \(dir.path)")
    }
}
#endif
