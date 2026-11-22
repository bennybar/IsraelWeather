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
    /// The forecast page shown (Places opens one by tapping it).
    @Published var page = 0
    /// The tab shown (a warning badge on the forecast opens Alerts).
    @Published var tab = 0
    /// Live station readings by page id (only while the proxy has an IMS token).
    @Published var observations: [Int: Observation] = [:]
    /// No readings until then: the proxy has no IMS token, so don't ask on every refresh.
    private var stationsOffUntil = Date.distantPast

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

    /// The sky behind every screen: the shown page's current weather, and night after sunset.
    var sky: (code: Int, night: Bool) {
        let shown = pages.indices.contains(page) ? pages[page] : pages.first
        if let now = shown.flatMap({ forecasts[$0.id]?.0.currentHour() }) {
            return (now.code, WeatherCodes.isNight(now.t))
        }
        return (1250, WeatherCodes.isNight(Fmt.slot(Date())))
    }

    func start() {
        if settings.useLocation {
            if location.authorizationStatus == .notDetermined {
                location.requestWhenInUseAuthorization()
            } else if let last = location.location, Date().timeIntervalSince(last.timestamp) < 600 {
                // A fix under 10 minutes old is good enough: opening the app again soon after
                // doesn't wake the location hardware.
                Task { await apply(last) }
                return
            } else {
                location.requestLocation()
            }
        }
        Task { await refreshAll() }
    }

    func refreshAll(force: Bool = false) async {
        loading = true
        let before = forecasts.mapValues(\.1)
        for page in pages {
            let fid = page.current ? page.id : settings.forecastID(for: page.id)
            await load(page.id, forecastID: fid, force: force)
        }
        warnings = await Repository.shared.warnings(force: force)
        await loadObservations()
        loading = false
        // Widgets reload only when a forecast was actually downloaded (not on every app open:
        // reloads count against the widgets' daily budget and wake their process).
        if force || forecasts.mapValues(\.1) != before { WidgetCenter.shared.reloadAllTimelines() }
    }

    /// The nearest station's reading for each page, by its nearest IMS point. Only the app asks
    /// (widgets don't), the proxy caches it for 5 minutes, and a missing token pauses it for 6 hours.
    private func loadObservations() async {
        guard Date() > stationsOffUntil else { return }
        for page in pages {
            let ims = page.current ? settings.currentPlace : settings.custom(page.id).map { $0.nearest } ?? page.id
            guard let ims, ims > 0 else { continue }
            do {
                observations[page.id] = try await IMS.now(ims)
            } catch HTTPError.status(503) {
                stationsOffUntil = Date().addingTimeInterval(6 * 3600)
                observations = [:]
                return
            } catch {}
        }
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

    /// Rain alerts were switched on: schedule one from my location's forecast as it is.
    func rainAlertsChanged() {
        guard settings.useLocation, let fid = settings.currentForecastID, let f = forecasts[fid]?.0 else { return }
        RainAlert.update(f, place: places[fid]?.name(english: Fmt.english))
    }

    /// Units, icons or the hours' details changed: redraw the app and the widgets, no refetch.
    func displayChanged() {
        settingsVersion += 1
        WidgetCenter.shared.reloadAllTimelines()
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

    /// A fix can fail (no signal yet, or "location unknown"): try once more a few seconds later.
    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            guard !retriedFix else { return }
            retriedFix = true
            try? await Task.sleep(for: .seconds(5))
            manager.requestLocation()
        }
    }

    private var retriedFix = false

    private func apply(_ loc: CLLocation) async {
        retriedFix = false
        await Repository.shared.updateLocation(loc)
        await refreshAll()
    }
}
