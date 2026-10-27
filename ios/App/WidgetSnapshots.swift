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
        for style in SpectrumStyle.allCases {
            for small in [true, false] {
                let entry = SpectrumEntry(date: Date(), place: name, forecast: f, updated: at, color: .lavender, style: style)
                let p = Palette.of(.lavender)
                let content: AnyView = style == .classic ? AnyView(SpectrumWidgetView(entry: entry, small: small))
                    : AnyView(GlassSpectrumView(entry: entry, small: small, style: style))
                // containerBackground isn't drawn outside a widget: approximate it for the snapshot.
                let night = f.currentHour().map { WeatherCodes.isNight($0.t) } ?? false
                let bg: [Color] = style == .classic ? p.bg : style == .capsules
                    ? (night ? [Color(hex: 0x1C2142), Color(hex: 0x2A1E4A)] : [Color(hex: 0x33478A), Color(hex: 0x3A2A66)])
                    : (night ? [Color(hex: 0x23305A), Color(hex: 0x2B2552)] : [Color(hex: 0x5294EC), Color(hex: 0x2552B0)])
                let view = content
                    .padding(16)
                    .frame(width: small ? 170 : 364, height: 170)
                    .background(LinearGradient(colors: bg, startPoint: .topLeading, endPoint: .bottomTrailing))
                    .clipShape(.rect(cornerRadius: 24))
                let r = ImageRenderer(content: view)
                r.scale = 3
                if let png = r.uiImage?.pngData() {
                    try? png.write(to: dir.appendingPathComponent("\(style.rawValue)-\(small ? "small" : "medium").png"))
                }
            }
        }
        print("WIDGETS: \(dir.path)")
    }
}
#endif
