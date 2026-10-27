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
        for color in [SpectrumColor.lavender, .orange, .twilight, .sky] {
            for small in [true, false] {
                let entry = SpectrumEntry(date: Date(), place: name, forecast: f, updated: at, color: color)
                let p = Palette.of(color)
                let view = SpectrumWidgetView(entry: entry, small: small)
                    .padding(16)
                    .frame(width: small ? 170 : 364, height: 170)
                    .background(LinearGradient(colors: p.bg, startPoint: .topLeading, endPoint: .bottomTrailing))
                    .clipShape(.rect(cornerRadius: 24))
                let r = ImageRenderer(content: view)
                r.scale = 3
                if let png = r.uiImage?.pngData() {
                    try? png.write(to: dir.appendingPathComponent("\(color.rawValue)-\(small ? "small" : "medium").png"))
                }
            }
        }
        print("WIDGETS: \(dir.path)")
    }
}
#endif
