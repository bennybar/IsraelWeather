import AppIntents
import CoreLocation
import WidgetKit

/// Tapping a widget's weather icon: a fresh location fix (for "my location") and a fresh forecast.
struct RefreshIntent: AppIntent {
    static var title: LocalizedStringResource = "Refresh weather"
    static var isDiscoverable = false

    @Parameter(title: "Place") var placeID: Int

    init() {}
    init(placeID: Int) { self.placeID = placeID }

    func perform() async throws -> some IntentResult {
        let s = AppSettings.shared
        // Shows the refresh arrow right away; the widget reloads again when this returns.
        s.refreshStarted = Date()
        WidgetCenter.shared.reloadTimelines(ofKind: "Spectrum")
        defer { s.refreshStarted = nil }
        if placeID == currentID, s.useLocation, let loc = await LocationFix.get() {
            await Repository.shared.updateLocation(loc)
        }
        let id = placeID == currentID ? s.currentForecastID : s.forecastID(for: placeID)
        if let id { await Repository.shared.forecast(id, force: true) }
        return .result()
    }
}

/// One location fix, or nil after 10 s (or without permission for widgets).
@MainActor
final class LocationFix: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var done: CheckedContinuation<CLLocation?, Never>?

    static func get() async -> CLLocation? {
        let fix = LocationFix()
        return await fix.run()
    }

    private func run() async -> CLLocation? {
        guard manager.isAuthorizedForWidgetUpdates else { return nil }
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        return await withCheckedContinuation { c in
            done = c
            manager.requestLocation()
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(10))
                self.finish(nil)
            }
        }
    }

    private func finish(_ loc: CLLocation?) {
        done?.resume(returning: loc)
        done = nil
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let loc = locations.last
        Task { @MainActor in self.finish(loc) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.finish(nil) }
    }
}
