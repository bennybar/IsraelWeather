import Foundation

let appGroup = "group.com.bennybar.israelweather"

enum ForecastSource: String, CaseIterable, Codable { case imsEcmwf, ecmwf, foreca }

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
    var currentTown: Localized? {
        get { (defaults.data(forKey: "currentTown")).flatMap { try? JSONDecoder().decode(Localized.self, from: $0) } }
        set { defaults.set(try? JSONEncoder().encode(newValue), forKey: "currentTown") }
    }

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
