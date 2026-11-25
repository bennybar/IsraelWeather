import CoreLocation
import Foundation

/// Forecasts by id, from the right source, cached on disk in the App Group (the widgets read it too).
actor Repository {
    static let shared = Repository()
    private let settings = AppSettings.shared
    private var placesCache: [Place]?
    private static let ttl: TimeInterval = 20 * 60
    /// Downloads under way, so widgets asking for the same forecast at once share one.
    private var inFlight: [Int: Task<(Forecast, Date)?, Never>] = [:]

    private var dir: URL {
        let base = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)
            ?? FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let d = base.appendingPathComponent("cache", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    private struct Stamped<T: Codable>: Codable { let fetchedAt: Date; let data: T }

    private func read<T: Codable>(_ name: String, _ type: T.Type) -> Stamped<T>? {
        guard let data = try? Data(contentsOf: dir.appendingPathComponent(name)) else { return nil }
        return try? JSONDecoder().decode(Stamped<T>.self, from: data)
    }

    private func write<T: Codable>(_ name: String, _ value: T) {
        if let data = try? JSONEncoder().encode(Stamped(fetchedAt: Date(), data: value)) {
            try? data.write(to: dir.appendingPathComponent(name), options: .atomic)
        }
    }

    // MARK: places

    func places() async -> [Place] {
        if let p = placesCache { return p }
        // IMS's points barely change: the saved list serves for a day (the widget's process restarts often).
        if let saved = read("places", [Place].self), !saved.data.isEmpty, Date().timeIntervalSince(saved.fetchedAt) < 86400 {
            placesCache = saved.data
            return saved.data
        }
        if let fresh = try? await IMS.places() {
            placesCache = fresh
            write("places", fresh)
            return fresh
        }
        let cached = read("places", [Place].self)?.data ?? []
        placesCache = cached.isEmpty ? nil : cached
        return cached
    }

    /// The place a forecast id stands for: an IMS point, a searched place, or "my location".
    func place(for id: Int) async -> Place? {
        let all = await places()
        if let c = settings.custom(id) {
            return Place(id: c.id, he: c.he, en: c.en, lat: c.lat, lon: c.lon, rid: all.first { $0.id == c.nearest }?.rid ?? 0)
        }
        if id != currentID { return all.first { $0.id == id } }
        guard let nearest = all.first(where: { $0.id == settings.currentPlace }) else { return nil }
        let town = settings.currentTown
        return Place(id: currentID, he: town?.he ?? nearest.he, en: town?.en ?? nearest.en,
                     lat: settings.currentLat ?? nearest.lat, lon: settings.currentLon ?? nearest.lon, rid: nearest.rid)
    }

    // MARK: forecasts

    func cached(_ id: Int) -> (Forecast, Date)? {
        read("forecast-\(id)", Forecast.self).map { ($0.data, $0.fetchedAt) }
    }

    /// The forecast for [id], fetched when the cache is older than 20 minutes (or [force]).
    @discardableResult
    func forecast(_ id: Int, force: Bool = false) async -> (Forecast, Date)? {
        if !force, let c = cached(id), Date().timeIntervalSince(c.1) < Self.ttl { return c }
        if let running = inFlight[id] { return await running.value }
        let task = Task { await self.download(id, fresh: force) }
        inFlight[id] = task
        let result = await task.value
        inFlight[id] = nil
        return result
    }

    private func download(_ id: Int, fresh: Bool) async -> (Forecast, Date)? {
        do {
            let f = try await fetchForecast(id, fresh: fresh)
            write("forecast-\(id)", f)
            // My location's forecast: set (or clear) the rain alert from it.
            if settings.useLocation, id == settings.currentForecastID, settings.rainAlerts {
                RainAlert.update(f, place: await place(for: currentID)?.name(english: Fmt.english))
            }
            return (f, Date())
        } catch {
            return cached(id)
        }
    }

    private func fetchForecast(_ id: Int, fresh: Bool) async throws -> Forecast {
        if id == currentID {
            guard let lat = settings.currentLat, let lon = settings.currentLon else { throw HTTPError.status(0) }
            return try await spot(lat, lon, id, fresh)
        }
        if let c = settings.custom(id) { return try await spot(c.lat, c.lon, id, fresh) }
        guard let p = await places().first(where: { $0.id == id }) else { throw HTTPError.status(404) }
        if !settings.usesIMS { return try await spot(p.lat, p.lon, id, fresh) }
        var f = try await IMS.forecast(id, fresh: fresh)
        // One ECMWF request (no UV) for "now" (IMS's hourly sky lags the real one) and the days
        // after IMS's 7. "Now" keeps the rain chance, amount and UV of IMS's hour, as before.
        if let e = try? await ECMWF.forecast(lat: p.lat, lon: p.lon, id: id, fresh: fresh, uv: false) {
            if var now = e.now {
                let slot = f.hourly.last { $0.t <= now.t }
                now.rainChance = slot?.rainChance; now.rain = slot?.rain; now.uv = slot?.uv
                f.now = now
            }
            let lastHour = f.hourly.last?.t ?? "", lastDay = f.daily.last?.date ?? ""
            f.hourly += e.hourly.filter { $0.t > lastHour }
            f.daily += e.daily.filter { $0.date > lastDay }
        }
        return f
    }

    /// An exact spot: Foreca when chosen and keyed (ECMWF if it fails), else ECMWF.
    private func spot(_ lat: Double, _ lon: Double, _ id: Int, _ fresh: Bool) async throws -> Forecast {
        if settings.source == .foreca, !settings.forecaKey.isEmpty,
           let f = try? await Foreca.forecast(lat: lat, lon: lon, key: settings.forecaKey, id: id) {
            return f
        }
        return try await ECMWF.forecast(lat: lat, lon: lon, id: id, fresh: fresh,
                                        model: settings.source == .gfs ? ECMWF.gfsModel : settings.source == .best ? ECMWF.bestModel : ECMWF.ecmwfModel)
    }

    // MARK: warnings

    func warnings(force: Bool = false) async -> [Warning] {
        // The proxy itself refreshes warnings every 5 minutes: no point asking more often.
        if !force, let c = read("warnings", [Warning].self), Date().timeIntervalSince(c.fetchedAt) < 300 { return c.data }
        if let w = try? await IMS.warnings() {
            write("warnings", w)
            return w
        }
        return read("warnings", [Warning].self)?.data ?? []
    }

    /// Saves a new device location for "my location": rounded to ~100 m (the exact spot stays out
    /// of storage), with its nearest IMS point and, away from IMS points, the town's name.
    func updateLocation(_ loc: CLLocation) async {
        let lat = (loc.coordinate.latitude * 1000).rounded() / 1000
        let lon = (loc.coordinate.longitude * 1000).rounded() / 1000
        guard let (place, km) = await nearest(lat: lat, lon: lon) else { return }
        // Moved more than about a kilometer: the exact-spot forecast saved for "my location" is for
        // the old spot, so drop it (it would otherwise be reused for up to 20 minutes).
        if let oldLat = settings.currentLat, let oldLon = settings.currentLon, Self.km(oldLat, oldLon, lat, lon) > 1 {
            try? FileManager.default.removeItem(at: dir.appendingPathComponent("forecast-\(currentID)"))
        }
        settings.currentLat = lat
        settings.currentLon = lon
        settings.currentPlace = place.id
        settings.currentKm = km
        // The town's name for every new ~1 km square, near an IMS point or not: "my location" is
        // named after it with any source (only skipping it near IMS points left the old name).
        let cell = "\((lat * 100).rounded()),\((lon * 100).rounded())"
        if settings.currentTownCell != cell || settings.currentTown == nil {
            settings.currentTown = await Self.town(loc)
            settings.currentTownCell = cell
        }
    }

    private static func town(_ loc: CLLocation) async -> Localized? {
        let geo = CLGeocoder()
        let he = try? await geo.reverseGeocodeLocation(loc, preferredLocale: Locale(identifier: "he_IL")).first?.locality
        let en = try? await geo.reverseGeocodeLocation(loc, preferredLocale: Locale(identifier: "en_US")).first?.locality
        if he == nil && en == nil { return nil }
        return Localized(he: he ?? en, en: en ?? he)
    }

    /// Distance (km) between two points, roughly (good enough within Israel).
    static func km(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let k = cos(lat1 * .pi / 180)
        return ((lat2 - lat1) * (lat2 - lat1) + (lon2 - lon1) * k * (lon2 - lon1) * k).squareRoot() * 111
    }

    /// The IMS point nearest a spot, within 40 km (else we're outside Israel).
    func nearest(lat: Double, lon: Double) async -> (Place, Double)? {
        guard let best = await places().min(by: { Self.km(lat, lon, $0.lat, $0.lon) < Self.km(lat, lon, $1.lat, $1.lon) }) else { return nil }
        let d = Self.km(lat, lon, best.lat, best.lon)
        return d <= 40 ? (best, d) : nil
    }
}
