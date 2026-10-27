import SwiftUI

/// The weather icon sets (Settings → Weather icons): Apple's symbols, or Android's bundled sets.
enum IconStyle: String, CaseIterable, Sendable {
    case apple, materialWeather, googleMaps, googleWeather, glass, oneui, fluent, noto, meteocons

    /// The asset name prefix of a bundled set.
    var prefix: String? {
        switch self {
        case .apple: return nil
        case .materialWeather: return "wi_m3"
        case .googleMaps: return "wi_gmaps"
        case .googleWeather: return "wi_gweather"
        case .glass: return "wi_glass"
        case .oneui: return "wi_oneui"
        case .fluent: return "wi_fluent"
        case .noto: return "wi_noto"
        case .meteocons: return "wi_mfill"
        }
    }

    var label: String {
        switch self {
        case .apple: return "Apple"
        case .materialWeather: return "Material Weather"
        case .googleMaps: return "Google Maps"
        case .googleWeather: return "Google Weather"
        case .glass: return L("icons_glass")
        case .oneui: return L("icons_oneui")
        case .fluent: return "Fluent"
        case .noto: return "Noto"
        case .meteocons: return "Meteocons"
        }
    }
}

/// A weather icon at [size] points in the chosen set. Apple's symbols are multicolor in the app, or
/// fixed colors in the widgets ([cloud] for the clouds, so they show on light cards).
struct WeatherGlyph: View {
    let code: Int
    var night = false
    var size: CGFloat
    var cloud: Color? = nil
    var style = AppSettings.shared.iconStyle

    var body: some View {
        if let prefix = style.prefix {
            // The bundled icons have a little padding of their own: a slightly bigger box.
            Image("\(prefix)_\(WeatherCodes.family(code, night: night))").resizable().scaledToFit()
                .frame(width: size * 1.25, height: size * 1.25)
        } else if let cloud {
            WeatherIcon(code: code, night: night, cloud: cloud).font(.system(size: size))
        } else {
            Image(systemName: WeatherCodes.symbol(code, night: night)).symbolRenderingMode(.multicolor).font(.system(size: size))
        }
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
