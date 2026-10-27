import Foundation

private let session: URLSession = {
    let c = URLSessionConfiguration.default
    c.timeoutIntervalForRequest = 20
    c.httpAdditionalHeaders = ["User-Agent": "IsraelWeather-iOS/1.0 (github.com/bennybar/IsraelWeather)"]
    return URLSession(configuration: c)
}()

private func fetch<T: Decodable>(_ url: URL, _ type: T.Type, bearer: String? = nil, fresh: Bool = false) async throws -> T {
    var req = URLRequest(url: url)
    if fresh { req.cachePolicy = .reloadIgnoringLocalCacheData }
    if let bearer { req.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization") }
    let (data, res) = try await session.data(for: req)
    if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) { throw HTTPError.status(http.statusCode) }
    return try JSONDecoder().decode(T.self, from: data)
}

private func f4(_ v: Double) -> String { String(format: "%.4f", locale: Locale(identifier: "en_US_POSIX"), v) }

/// The Israel Weather proxy (proxy/ in the repo): IMS places, forecasts and warnings.
enum IMS {
    static let base = "https://israel-weather-proxy.israel-weather-proxy.workers.dev"
    static func places() async throws -> [Place] { try await fetch(URL(string: base + "/v1/locations")!, PlacesResponse.self).locations }
    static func forecast(_ id: Int, fresh: Bool) async throws -> Forecast { try await fetch(URL(string: base + "/v1/forecast/\(id)")!, Forecast.self, fresh: fresh) }
    static func warnings() async throws -> [Warning] { try await fetch(URL(string: base + "/v1/warnings")!, WarningsResponse.self).warnings }
}

/// ECMWF's 9 km IFS through Open-Meteo, with the same sky fixes as Android.
enum ECMWF {
    private struct Hourly: Decodable {
        let time: [String]
        let temperature_2m: [Double?]
        let precipitation_probability: [Double?]
        let precipitation: [Double?]
        let weather_code: [Int?]
        let relative_humidity_2m: [Double?]
        let wind_speed_10m: [Double?]
        let wind_direction_10m: [Double?]
        let wind_gusts_10m: [Double?]
        let cloud_cover_low: [Double?]
        let cloud_cover_mid: [Double?]
        let cloud_cover_high: [Double?]
    }
    private struct Daily: Decodable {
        let time: [String]
        let weather_code: [Int?]
        let temperature_2m_max: [Double?]
        let temperature_2m_min: [Double?]
    }
    private struct Current: Decodable {
        let time: String
        let temperature_2m: Double?
        let relative_humidity_2m: Double?
        let weather_code: Int?
        let wind_speed_10m: Double?
        let wind_direction_10m: Double?
        let wind_gusts_10m: Double?
        let cloud_cover_low: Double?
        let cloud_cover_mid: Double?
        let cloud_cover_high: Double?
    }
    private struct Response: Decodable { let hourly: Hourly; let daily: Daily; let current: Current? }
    private struct UV: Decodable {
        struct H: Decodable { let uv_index: [Double?] }
        struct D: Decodable { let uv_index_max: [Double?] }
        let hourly: H
        let daily: D
    }
    private struct CurrentOnly: Decodable { let current: Current }

    private static let currentFields = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover_low,cloud_cover_mid,cloud_cover_high"

    static func forecast(lat: Double, lon: Double, id: Int, fresh: Bool) async throws -> Forecast {
        let url = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=\(f4(lat))&longitude=\(f4(lon))" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,relative_humidity_2m," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover_low,cloud_cover_mid,cloud_cover_high" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&current=\(currentFields)" +
            "&models=ecmwf_ifs&timezone=Asia%2FJerusalem&forecast_days=7")!
        let r = try await fetch(url, Response.self, fresh: fresh)
        // ECMWF has no UV index: take it from Open-Meteo's default blend.
        let uvURL = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=\(f4(lat))&longitude=\(f4(lon))&hourly=uv_index&daily=uv_index_max&timezone=Asia%2FJerusalem&forecast_days=7")!
        let uv = try? await fetch(uvURL, UV.self, fresh: fresh)
        let h = r.hourly
        let sky = h.time.indices.map { effective(h.cloud_cover_low[safe: $0] ?? nil, h.cloud_cover_mid[safe: $0] ?? nil, h.cloud_cover_high[safe: $0] ?? nil) }
        let hours = h.time.indices.map { i in
            Hour(t: h.time[i].replacingOccurrences(of: "T", with: " "), code: skyCode(h.weather_code[i], sky[i]),
                 temp: h.temperature_2m[i], rainChance: h.precipitation_probability[i], rain: h.precipitation[i],
                 rh: h.relative_humidity_2m[i], wind: h.wind_speed_10m[i], gust: h.wind_gusts_10m[i],
                 windDir: h.wind_direction_10m[i], uv: uv?.hourly.uv_index[safe: i] ?? nil)
        }
        let d = r.daily
        let days = d.time.indices.map { i -> Day in
            // The daily code is the day's worst hour: use the daytime average cloud for plain skies, and
            // keep fog/rain only when it lasts 3+ daytime hours.
            let daytime = h.time.indices.filter { h.time[$0].hasPrefix(d.time[i]) && (7...17).contains(Int(h.time[$0].dropFirst(11).prefix(2)) ?? 0) }
            let clouds = daytime.compactMap { sky[$0] }
            let dayCloud = clouds.isEmpty ? nil : clouds.reduce(0, +) / Double(clouds.count)
            let weatherHours = daytime.filter { (h.weather_code[$0] ?? 0) > 3 }.count
            let code = d.weather_code[i]
            let dayCode = ((code ?? 0) > 3 && weatherHours < 3 && dayCloud != nil) ? skyCode(3, dayCloud) : skyCode(code, dayCloud)
            return Day(date: d.time[i], code: dayCode, min: d.temperature_2m_min[i], max: d.temperature_2m_max[i], uv: uv?.daily.uv_index_max[safe: i] ?? nil)
        }
        return Forecast(id: id, hourly: hours, daily: days, source: Forecast.sourceECMWF, now: r.current.map { nowHour($0, hours) })
    }

    /// ECMWF's current conditions at a point, for "now" at IMS places.
    static func current(lat: Double, lon: Double, hours: [Hour]) async throws -> Hour {
        let url = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=\(f4(lat))&longitude=\(f4(lon))&current=\(currentFields)&models=ecmwf_ifs&timezone=Asia%2FJerusalem")!
        return nowHour(try await fetch(url, CurrentOnly.self).current, hours)
    }

    private static func nowHour(_ c: Current, _ hours: [Hour]) -> Hour {
        let t = c.time.replacingOccurrences(of: "T", with: " ")
        let slot = hours.last { $0.t <= t }
        return Hour(t: t, code: skyCode(c.weather_code, effective(c.cloud_cover_low, c.cloud_cover_mid, c.cloud_cover_high)),
                    temp: c.temperature_2m, rainChance: slot?.rainChance, rain: slot?.rain, rh: c.relative_humidity_2m,
                    wind: c.wind_speed_10m, gust: c.wind_gusts_10m, windDir: c.wind_direction_10m, uv: slot?.uv)
    }

    /// Cloud as seen from the ground: the thickest layer, high (thin) cloud at half weight.
    private static func effective(_ low: Double?, _ mid: Double?, _ high: Double?) -> Double? {
        if low == nil && mid == nil && high == nil { return nil }
        return max(low ?? 0, mid ?? 0, (high ?? 0) / 2)
    }

    private static func skyCode(_ wmo: Int?, _ cloud: Double?) -> Int {
        guard let wmo, wmo <= 3, let cloud else { return imsCode(wmo) }
        return cloud < 25 ? 1250 : cloud < 60 ? 1220 : 1230
    }

    private static func imsCode(_ wmo: Int?) -> Int {
        switch wmo {
        case 0: return 1250
        case 1, 2: return 1220
        case 3: return 1230
        case 45, 48: return 1160
        case 51, 53, 55, 56, 57: return 1560
        case 61, 63, 65, 80, 81, 82: return 1140
        case 66, 67: return 1080
        case 71, 73, 77: return 1070
        case 75, 85, 86: return 1060
        case 95, 96, 97, 99: return 1020
        default: return 1230
        }
    }

    // Place search (GeoNames via Open-Meteo), in Israel, Hebrew and English names.
    private struct GeoResult: Decodable { let id: Int; let name: String; let latitude: Double; let longitude: Double; let admin1: String? }
    private struct GeoResponse: Decodable { let results: [GeoResult]? }

    struct GeoPlace: Identifiable, Hashable { let id: Int; let he: String; let en: String; let region: String?; let lat: Double; let lon: Double }

    private static func lookup(_ query: String, _ lang: String) async throws -> [GeoResult] {
        var c = URLComponents(string: "https://geocoding-api.open-meteo.com/v1/search")!
        c.queryItems = [.init(name: "name", value: query), .init(name: "count", value: "20"), .init(name: "language", value: lang), .init(name: "countryCode", value: "IL")]
        return try await fetch(c.url!, GeoResponse.self).results ?? []
    }

    static func search(_ query: String) async throws -> [GeoPlace] {
        async let he = lookup(query, "he")
        async let en = lookup(query, "en")
        let byID = Dictionary(try await en.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        var out: [GeoPlace] = []
        for r in try await he {
            var english = byID[r.id]?.name
            if english == nil {
                // A Hebrew query finds nothing in English: look the name up by id.
                english = try? await fetch(URL(string: "https://geocoding-api.open-meteo.com/v1/get?id=\(r.id)&language=en")!, GeoResult.self).name
            }
            out.append(GeoPlace(id: r.id, he: r.name, en: english ?? r.name, region: r.admin1, lat: r.latitude, lon: r.longitude))
        }
        return out
    }
}

/// Foreca with the user's own key (free Freemium plan: 2,000 requests a day).
enum Foreca {
    private struct Point: Decodable {
        let time: String?, date: String?, symbol: String?
        let temperature: Double?, maxTemp: Double?, minTemp: Double?, relHumidity: Double?
        let windSpeed: Double?, windGust: Double?, windDir: Double?, precipProb: Double?, precipAccum: Double?, uvIndex: Double?
    }
    private struct Current: Decodable { let current: Point }
    private struct Series: Decodable { let forecast: [Point] }

    private static func url(_ path: String, _ extra: String = "") -> URL {
        URL(string: "https://weatherapi.foreca.net/api/v1/\(path)?tempunit=C&windunit=KMH&tz=Asia/Jerusalem\(extra)")!
    }

    static func check(_ key: String) async -> Bool {
        (try? await fetch(url("current/34.78,32.08"), Current.self, bearer: key)) != nil
    }

    static func forecast(lat: Double, lon: Double, key: String, id: Int) async throws -> Forecast {
        let spot = String(format: "%.3f,%.3f", locale: Locale(identifier: "en_US_POSIX"), lon, lat)
        async let now = fetch(url("current/\(spot)"), Current.self, bearer: key)
        async let hours = fetch(url("forecast/hourly/\(spot)", "&periods=168&dataset=full"), Series.self, bearer: key)
        async let days = fetch(url("forecast/daily/\(spot)", "&periods=7&dataset=full"), Series.self, bearer: key)
        func hour(_ p: Point) -> Hour {
            Hour(t: String((p.time ?? "").prefix(16)).replacingOccurrences(of: "T", with: " "), code: imsCode(p.symbol), temp: p.temperature,
                 rainChance: p.precipProb, rain: p.precipAccum, rh: p.relHumidity, wind: p.windSpeed, gust: p.windGust, windDir: p.windDir, uv: p.uvIndex)
        }
        let n = try await now.current
        return Forecast(id: id,
                        hourly: try await hours.forecast.filter { $0.time != nil }.map(hour),
                        daily: try await days.forecast.compactMap { d in d.date.map { Day(date: $0, code: imsCode(d.symbol), min: d.minTemp, max: d.maxTemp, uv: d.uvIndex) } },
                        source: Forecast.sourceForeca, now: n.time != nil ? hour(n) : nil)
    }

    /// Foreca symbols ("d210": day/night, cloudiness 0–6, precipitation rate 0–4, type) → IMS codes.
    static func imsCode(_ symbol: String?) -> Int {
        guard let s = symbol, s.count == 4 else { return 1230 }
        let c = Array(s)
        guard let cloud = c[1].wholeNumberValue else { return 1230 }
        let rate = c[2].wholeNumberValue ?? 0, type = c[3].wholeNumberValue ?? 0
        switch true {
        case cloud == 6: return 1160
        case rate == 4: return 1020
        case rate > 0 && type == 2: return rate == 1 ? 1070 : 1060
        case rate > 0 && type == 1: return 1080
        case rate == 1: return 1560
        case rate == 2: return cloud <= 2 ? 1530 : 1540
        case rate == 3: return 1140
        case cloud <= 1: return 1250
        case cloud == 2 || cloud == 5: return 1220
        default: return 1230
        }
    }
}

extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}
