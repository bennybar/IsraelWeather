import Foundation

let appGroup = "group.com.bennybar.israelweather"

enum ForecastSource: String, CaseIterable, Codable { case imsEcmwf, ecmwf, foreca, gfs, best }

/// The sky behind the app: day or night by sunset, or always one of them (Settings → Background).
enum SkyMode: String, CaseIterable { case automatic, day, night }

/// Extra lines under each of the next hours (Settings → Next hours).
/// The forecast page's cards below the header, which can be reordered and hidden.
enum ForecastCard: String, CaseIterable {
    case hourly, week, details
}

enum HourDetail: String, CaseIterable {
    case rain, wind, feels, humidity, uv

    var icon: String {
        switch self {
        case .rain: return "drop.fill"
        case .wind: return "wind"
        case .feels: return "thermometer.medium"
        case .humidity: return "humidity.fill"
        case .uv: return "sun.max.fill"
        }
    }
}

/// Settings, in the App Group so the widgets read the same values (and the last location).
final class AppSettings {
    static let shared = AppSettings()
    let defaults: UserDefaults

    init(defaults: UserDefaults = UserDefaults(suiteName: appGroup) ?? .standard) { self.defaults = defaults }

    var fahrenheit: Bool {
        get { defaults.bool(forKey: "fahrenheit") }
        set { defaults.set(newValue, forKey: "fahrenheit") }
    }
    var windMS: Bool {
        get { defaults.bool(forKey: "windMS") }
        set { defaults.set(newValue, forKey: "windMS") }
    }
    /// Weather icons for the app and the widgets (as on Android: one setting for both).
    var iconStyle: IconStyle {
        get { IconStyle(rawValue: defaults.string(forKey: "iconStyle") ?? "") ?? .apple }
        set { defaults.set(newValue.rawValue, forKey: "iconStyle") }
    }
    /// What the forecast page's next hours show under each hour's temperature.
    var hourDetails: Set<HourDetail> {
        get { Set((defaults.array(forKey: "hourDetails") as? [String] ?? [HourDetail.rain.rawValue]).compactMap(HourDetail.init)) }
        set { defaults.set(HourDetail.allCases.filter(newValue.contains).map(\.rawValue), forKey: "hourDetails") }
    }
    var cards: [ForecastCard] {
        get {
            let saved = (defaults.array(forKey: "cards") as? [String] ?? []).compactMap(ForecastCard.init)
            return saved + ForecastCard.allCases.filter { !saved.contains($0) }
        }
        set { defaults.set(newValue.map(\.rawValue), forKey: "cards") }
    }
    var hiddenCards: Set<ForecastCard> {
        get { Set((defaults.array(forKey: "hiddenCards") as? [String] ?? []).compactMap(ForecastCard.init)) }
        set { defaults.set(newValue.map(\.rawValue), forKey: "hiddenCards") }
    }
    /// A notification about an hour before rain at my location (scheduled when its forecast refreshes).
    var rainAlerts: Bool {
        get { defaults.bool(forKey: "rainAlerts") }
        set { defaults.set(newValue, forKey: "rainAlerts") }
    }
    /// When the last rain alert was set to go off: a later one for the same spell is skipped.
    var rainAlertFire: Date? {
        get { defaults.object(forKey: "rainAlertFire") as? Date }
        set { defaults.set(newValue, forKey: "rainAlertFire") }
    }
    var skyMode: SkyMode {
        get { SkyMode(rawValue: defaults.string(forKey: "skyMode") ?? "") ?? .automatic }
        set { defaults.set(newValue.rawValue, forKey: "skyMode") }
    }
    var useLocation: Bool {
        get { defaults.object(forKey: "useLocation") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "useLocation") }
    }
    var source: ForecastSource {
        get { ForecastSource(rawValue: defaults.string(forKey: "source") ?? "") ?? .imsEcmwf }
        set { defaults.set(newValue.rawValue, forKey: "source") }
    }
    var forecaKey: String {
        get { defaults.string(forKey: "forecaKey") ?? "" }
        set { defaults.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "forecaKey") }
    }

    /// Saved places in order: IMS ids, or custom (searched) places' negative ids.
    var places: [Int] {
        get { defaults.array(forKey: "places") as? [Int] ?? [] }
        set { defaults.set(newValue, forKey: "places") }
    }
    var customPlaces: [CustomPlace] {
        get { (defaults.data(forKey: "customPlaces")).flatMap { try? JSONDecoder().decode([CustomPlace].self, from: $0) } ?? [] }
        set { defaults.set(try? JSONEncoder().encode(newValue), forKey: "customPlaces") }
    }

    // The device's last fix (rounded) and the IMS point nearest it, written by the app for the widgets.
    var currentLat: Double? {
        get { defaults.object(forKey: "currentLat") as? Double }
        set { defaults.set(newValue, forKey: "currentLat") }
    }
    var currentLon: Double? {
        get { defaults.object(forKey: "currentLon") as? Double }
        set { defaults.set(newValue, forKey: "currentLon") }
    }
    var currentPlace: Int? {
        get { defaults.object(forKey: "currentPlace") as? Int }
        set { defaults.set(newValue, forKey: "currentPlace") }
    }
    var currentKm: Double? {
        get { defaults.object(forKey: "currentKm") as? Double }
        set { defaults.set(newValue, forKey: "currentKm") }
    }
    /// The ~1 km square [currentTown] was looked up for.
    var currentTownCell: String? {
        get { defaults.string(forKey: "currentTownCell") }
        set { defaults.set(newValue, forKey: "currentTownCell") }
    }
    var currentTown: Localized? {
        get { (defaults.data(forKey: "currentTown")).flatMap { try? JSONDecoder().decode(Localized.self, from: $0) } }
        set { defaults.set(try? JSONEncoder().encode(newValue), forKey: "currentTown") }
    }

    /// When a widget refresh started (the widget shows its refresh arrow for up to 30 s).
    var refreshStarted: Date? {
        get { defaults.object(forKey: "refreshStarted") as? Date }
        set { defaults.set(newValue, forKey: "refreshStarted") }
    }
    var refreshing: Bool { refreshStarted.map { Date().timeIntervalSince($0) < 30 } ?? false }

    var usesIMS: Bool { source == .imsEcmwf }

    /// "My location": the nearest IMS point within 3 km (with IMS), else the exact spot.
    var currentForecastID: Int? {
        guard let place = currentPlace else { return nil }
        if !usesIMS, currentLat != nil { return currentID }
        if let km = currentKm, km > imsRadiusKm { return currentID }
        return place
    }

    func custom(_ id: Int) -> CustomPlace? { customPlaces.first { $0.id == id } }

    /// The forecast behind a saved place (a searched place's nearby IMS point, when using IMS).
    func forecastID(for id: Int) -> Int {
        if let c = custom(id) { return usesIMS ? (c.ims ?? c.id) : c.id }
        return id
    }
}
