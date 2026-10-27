import CoreLocation
import Foundation
import SwiftUI
import WidgetKit

/// App state: the pages (my location + saved places), their forecasts, warnings, and location.
@MainActor
final class AppModel: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var forecasts: [Int: (Forecast, Date)] = [:]
    @Published var places: [Int: Place] = [:]
    @Published var warnings: [Warning] = []
    @Published var savedPlaces: [Int] = AppSettings.shared.places
    @Published var locationStatus: CLAuthorizationStatus = .notDetermined
    @Published var loading = false
    @Published var settingsVersion = 0

    let settings = AppSettings.shared
    private let location = CLLocationManager()

    override init() {
        super.init()
        location.delegate = self
        location.desiredAccuracy = kCLLocationAccuracyKilometer
        locationStatus = location.authorizationStatus
    }

    /// The pages in order: my location (when used), then saved places' forecast ids.
    var pages: [(id: Int, current: Bool)] {
        var out: [(Int, Bool)] = []
        if settings.useLocation, let cur = settings.currentForecastID { out.append((cur, true)) }
        for p in savedPlaces where !(settings.useLocation && settings.forecastID(for: p) == settings.currentForecastID) {
            out.append((p, false))
        }
        return out.map { (id: $0.0, current: $0.1) }
    }

    func start() {
        if settings.useLocation {
            if location.authorizationStatus == .notDetermined { location.requestWhenInUseAuthorization() } else { location.requestLocation() }
        }
        Task { await refreshAll() }
    }

    func refreshAll(force: Bool = false) async {
        loading = true
        for page in pages {
            let fid = page.current ? page.id : settings.forecastID(for: page.id)
            await load(page.id, forecastID: fid, force: force)
        }
        warnings = await Repository.shared.warnings()
        loading = false
        WidgetCenter.shared.reloadAllTimelines()
    }

    func load(_ id: Int, forecastID: Int, force: Bool = false) async {
        if let p = await Repository.shared.place(for: id) { places[id] = p }
        if let f = await Repository.shared.forecast(forecastID, force: force) { forecasts[id] = f }
    }

    // MARK: places

    func add(ims p: Place) {
        guard !savedPlaces.contains(p.id) else { return }
        savedPlaces.append(p.id)
        settings.places = savedPlaces
        Task { await refreshAll() }
    }

    func add(geo g: ECMWF.GeoPlace) async {
        let near = await Repository.shared.nearest(lat: g.lat, lon: g.lon)
        let custom = CustomPlace(id: -g.id, he: g.he, en: g.en, lat: g.lat, lon: g.lon,
                                 ims: near.flatMap { $0.1 <= imsRadiusKm ? $0.0.id : nil }, nearest: near?.0.id)
        settings.customPlaces = settings.customPlaces.filter { $0.id != custom.id } + [custom]
        if !savedPlaces.contains(custom.id) {
            savedPlaces.append(custom.id)
            settings.places = savedPlaces
        }
        await refreshAll()
    }

    func remove(at offsets: IndexSet) {
        savedPlaces.remove(atOffsets: offsets)
        settings.places = savedPlaces
        WidgetCenter.shared.reloadAllTimelines()
    }

    func move(from: IndexSet, to: Int) {
        savedPlaces.move(fromOffsets: from, toOffset: to)
        settings.places = savedPlaces
    }

    func settingsChanged() {
        settingsVersion += 1
        Task { await refreshAll(force: true) }
    }

    // MARK: location

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            locationStatus = manager.authorizationStatus
            if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways { manager.requestLocation() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        Task { @MainActor in await apply(loc) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}

    private func apply(_ loc: CLLocation) async {
        await Repository.shared.updateLocation(loc)
        await refreshAll()
    }
}
