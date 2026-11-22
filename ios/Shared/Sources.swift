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

private func fetchData(_ url: URL, fresh: Bool = false) async throws -> Data {
    var req = URLRequest(url: url)
    if fresh { req.cachePolicy = .reloadIgnoringLocalCacheData }
    let (data, res) = try await session.data(for: req)
    if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) { throw HTTPError.status(http.statusCode) }
    return data
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
        let uv_index: [Double?]?
    }
    private struct Daily: Decodable {
        let time: [String]
        let weather_code: [Int?]
        let temperature_2m_max: [Double?]
        let temperature_2m_min: [Double?]
        let uv_index_max: [Double?]?
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

    /// "Best Combined", from one multi-model response (fields suffixed with each model's name): each
    /// measure averaged over the models that forecast it best 1–3 days ahead against Israeli airports
    /// (tools/accuracy). Temperature: best match and ECMWF's AI model. Rain: GFS, Météo-France and ICON
    /// (chance: GFS and ICON, the others give none). Wind speed and gusts: GFS and Météo-France. The
    /// sky, humidity, wind direction and UV: best match, kept consistent with the blend (a wet blend
    /// gets a rain code, gusts never below the speed, the day's high and low from the hours, "now"
    /// shifted to the blend of its hour). An hour no chosen model covers falls back to best match; a
    /// missing column is all nils.
    private static func combine(_ data: Data) throws -> Response {
        guard let o = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let h = o["hourly"] as? [String: Any], let d = o["daily"] as? [String: Any],
              let hTimes = h["time"] as? [String], let dTimes = d["time"] as? [String] else { throw HTTPError.status(0) }
        func nums(_ obj: [String: Any], _ key: String, _ n: Int) -> [Double?] {
            let a = obj[key] as? [Any] ?? []
            return (0..<n).map { i in i < a.count ? (a[i] as? NSNumber)?.doubleValue : nil }
        }
        func best(_ obj: [String: Any], _ field: String, _ n: Int) -> [Double?] { nums(obj, "\(field)_best_match", n) }
        func mean(_ obj: [String: Any], _ field: String, _ n: Int, _ models: [String]) -> [Double?] {
            let cols = models.map { nums(obj, "\(field)_\($0)", n) }
            let fallback = best(obj, field, n)
            return (0..<n).map { i in
                let v = cols.compactMap { $0[i] }
                return v.isEmpty ? fallback[i] : v.reduce(0, +) / Double(v.count)
            }
        }
        // The hour's code from its blended rain, for dry, foggy, missing and rain codes alike (0–3,
        // 45, 48, 51–67, 80–82): light rain from 0.2 mm, rain from 1 mm, else a plain sky code where
        // best match said rain (the cloud decides). Freezing rain keeps its code when wet; storms and
        // snow stay.
        func wetCode(_ code: Int?, _ mm: Double?) -> Int? {
            guard let mm else { return code }
            if let code, freezing.contains(code) { return mm >= wetHourMM - eps ? code : 3 }
            let rainCode = code.map { (51...67).contains($0) || (80...82).contains($0) } ?? false
            guard code.map({ $0 <= 3 || $0 == 45 || $0 == 48 }) ?? true || rainCode else { return code }
            if mm >= 1 - eps { return 61 }
            if mm >= wetHourMM - eps { return 51 }
            return rainCode ? 3 : code
        }
        let n = hTimes.count, nd = dTimes.count
        let bestTemp = best(h, "temperature_2m", n)
        let temp = mean(h, "temperature_2m", n, ["best_match", "ecmwf_aifs025_single"])
        // To the 0.1 mm the app shows, so the icons' thresholds match the amounts on screen.
        let rain = mean(h, "precipitation", n, ["gfs_global", "meteofrance_arpege_world", "icon_global"]).map { $0.map { ($0 * 10).rounded() / 10 } }
        let wind = mean(h, "wind_speed_10m", n, ["gfs_global", "meteofrance_arpege_world"])
        let rawGusts = mean(h, "wind_gusts_10m", n, ["gfs_global", "meteofrance_arpege_world"])
        let gusts: [Double?] = (0..<n).map { i -> Double? in
            guard let g = rawGusts[i], let w = wind[i] else { return rawGusts[i] }
            return max(g, w)
        }
        let hourCodes = best(h, "weather_code", n)
        let hourly = Hourly(
            time: hTimes,
            temperature_2m: temp,
            precipitation_probability: mean(h, "precipitation_probability", n, ["gfs_global", "icon_global"]),
            precipitation: rain,
            weather_code: (0..<n).map { i -> Int? in wetCode(hourCodes[i].map { Int($0) }, rain[i]) },
            relative_humidity_2m: best(h, "relative_humidity_2m", n),
            wind_speed_10m: wind,
            wind_direction_10m: best(h, "wind_direction_10m", n),
            wind_gusts_10m: gusts,
            cloud_cover_low: best(h, "cloud_cover_low", n),
            cloud_cover_mid: best(h, "cloud_cover_mid", n),
            cloud_cover_high: best(h, "cloud_cover_high", n),
            uv_index: best(h, "uv_index", n))
        let dayHours: [[Int]] = dTimes.map { day in hTimes.indices.filter { hTimes[$0].hasPrefix(day) } }
        let dayCodes = best(d, "weather_code", nd), bestMax = best(d, "temperature_2m_max", nd), bestMin = best(d, "temperature_2m_min", nd)
        let daily = Daily(
            time: dTimes,
            // best match's; forecast() sets rainy days from the blended daytime rain (wetDayMM).
            weather_code: dayCodes.map { $0.map { Int($0) } },
            // The day's high and low from the blended hours, so they agree with the hourly row.
            temperature_2m_max: (0..<nd).map { i -> Double? in dayHours[i].compactMap { temp[$0] }.max() ?? bestMax[i] },
            temperature_2m_min: (0..<nd).map { i -> Double? in dayHours[i].compactMap { temp[$0] }.min() ?? bestMin[i] },
            uv_index_max: best(d, "uv_index_max", nd))
        // "Now" is best match's (15-minutely), moved onto the blend of the hour it falls in: its
        // temperature shifted by the blend's difference, the blended wind and gusts, a rain code if wet.
        var current = try (o["current"]).map { try JSONDecoder().decode(Current.self, from: JSONSerialization.data(withJSONObject: $0)) }
        if let c = current, let slot = hTimes.lastIndex(where: { $0 <= c.time }) {
            let shift = temp[slot].flatMap { t in bestTemp[slot].map { t - $0 } } ?? 0
            let w = wind[slot] ?? c.wind_speed_10m
            current = Current(time: c.time, temperature_2m: c.temperature_2m.map { $0 + shift },
                              relative_humidity_2m: c.relative_humidity_2m,
                              weather_code: wetCode(c.weather_code, rain[slot]),
                              wind_speed_10m: w, wind_direction_10m: c.wind_direction_10m,
                              wind_gusts_10m: gusts[slot] ?? c.wind_gusts_10m.map { g in w.map { max(g, $0) } ?? g },
                              cloud_cover_low: c.cloud_cover_low, cloud_cover_mid: c.cloud_cover_mid, cloud_cover_high: c.cloud_cover_high)
        }
        return Response(hourly: hourly, daily: daily, current: current)
    }

    private static let currentFields = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover_low,cloud_cover_mid,cloud_cover_high"

    static let ecmwfModel = "ecmwf_ifs"
    /// NOAA's GFS: the best-graded model against Israeli airports (tools/accuracy).
    static let gfsModel = "gfs_global"
    /// "Best Combined": several models in one request, each measure from the ones best at it.
    static let bestModel = "combined"
    // best_match must stay first: Open-Meteo answers `current` for the first model listed.
    /// Best Combined: daytime (07–17) blended rain that makes a day rainy, whatever its hour codes.
    private static let wetDayMM = 0.5
    /// Best Combined: blended rain in an hour that makes it light rain (1 mm: rain).
    private static let wetHourMM = 0.2
    /// Slack for thresholds: a mean of tenths can land a hair under them (0.6 / 3 = 0.19999…).
    private static let eps = 1e-6
    /// Freezing drizzle and rain (WMO 56, 57, 66, 67): keep their sleet icon when wet.
    private static let freezing: Set<Int> = [56, 57, 66, 67]
    private static let combinedModels = "best_match,ecmwf_aifs025_single,gfs_global,meteofrance_arpege_world,icon_global"

    static func forecast(lat: Double, lon: Double, id: Int, fresh: Bool, model: String = ecmwfModel) async throws -> Forecast {
        let url = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=\(f4(lat))&longitude=\(f4(lon))" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,relative_humidity_2m," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover_low,cloud_cover_mid,cloud_cover_high,uv_index" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max&current=\(currentFields)" +
            "&models=\(model == bestModel ? combinedModels : model)&timezone=Asia%2FJerusalem&forecast_days=7")!
        // ECMWF has no UV index: take it from Open-Meteo's default blend, at the same time (one
        // radio wake-up, not two in a row). GFS carries its own, so it needs no second request.
        let uvURL = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=\(f4(lat))&longitude=\(f4(lon))&hourly=uv_index&daily=uv_index_max&timezone=Asia%2FJerusalem&forecast_days=7")!
        async let uvTask: UV? = model != ecmwfModel ? nil : try? await fetch(uvURL, UV.self, fresh: fresh)
        let r = model == bestModel ? try combine(try await fetchData(url, fresh: fresh)) : try await fetch(url, Response.self, fresh: fresh)
        let uv = await uvTask
        let h = r.hourly
        let sky = h.time.indices.map { effective(h.cloud_cover_low[safe: $0] ?? nil, h.cloud_cover_mid[safe: $0] ?? nil, h.cloud_cover_high[safe: $0] ?? nil) }
        let hours = h.time.indices.map { i in
            Hour(t: h.time[i].replacingOccurrences(of: "T", with: " "), code: skyCode(h.weather_code[i], sky[i]),
                 temp: h.temperature_2m[i], rainChance: h.precipitation_probability[i], rain: h.precipitation[i],
                 rh: h.relative_humidity_2m[i], wind: h.wind_speed_10m[i], gust: h.wind_gusts_10m[i],
                 windDir: h.wind_direction_10m[i], uv: (uv?.hourly.uv_index ?? h.uv_index)?[safe: i] ?? nil)
        }
        let d = r.daily
        let days = d.time.indices.map { i -> Day in
            // The daily code is the day's worst hour: use the daytime average cloud for plain skies, and
            // keep fog/rain only when it lasts 3+ daytime hours.
            let daytime = h.time.indices.filter { h.time[$0].hasPrefix(d.time[i]) && (7...17).contains(Int(h.time[$0].dropFirst(11).prefix(2)) ?? 0) }
            let clouds = daytime.compactMap { sky[$0] }
            let dayCloud = clouds.isEmpty ? nil : clouds.reduce(0, +) / Double(clouds.count)
            let weatherHours = daytime.filter { (h.weather_code[$0] ?? 0) > 3 }.count
            // Best Combined: real daytime rain (0.5 mm+, at least one wet hour) makes the day light rain
            // (rain from 2 mm) however few hours it took; best match's daily code, often set by a night
            // shower, doesn't decide, and without such rain its rain code doesn't stand.
            let daytimeRain = daytime.reduce(0.0) { $0 + (h.precipitation[$1] ?? 0) }
            let wetDay = model == bestModel && daytimeRain >= wetDayMM - eps
                && daytime.contains { (h.precipitation[$0] ?? 0) >= wetHourMM - eps }
            let code = d.weather_code[i]
            // Storms, snow and freezing rain count only in the daytime itself (a night thunderstorm
            // doesn't make the day one).
            func isStormOrSnow(_ c: Int?) -> Bool { c.map { $0 >= 95 || (71...77).contains($0) || $0 == 85 || $0 == 86 || freezing.contains($0) } ?? false }
            let stormOrSnow = isStormOrSnow(code) && daytime.contains { isStormOrSnow(h.weather_code[$0]) }
            let codedRain = code.map { (51...67).contains($0) || (80...82).contains($0) } ?? false
            let dayCode: Int
            if wetDay {
                // Storms and snow keep their code; otherwise the amount decides (light rain or rain).
                dayCode = skyCode(stormOrSnow ? code : daytimeRain >= 2 - eps ? 61 : 51, dayCloud)
            } else if model == bestModel && (codedRain || (isStormOrSnow(code) && !stormOrSnow)) {
                dayCode = skyCode(3, dayCloud) // best match's rain or night storm without daytime weather in the blend
            } else if (code ?? 0) > 3 && weatherHours < 3 && dayCloud != nil {
                dayCode = skyCode(3, dayCloud)
            } else {
                dayCode = skyCode(code, dayCloud)
            }
            return Day(date: d.time[i], code: dayCode, min: d.temperature_2m_min[i], max: d.temperature_2m_max[i], uv: (uv?.daily.uv_index_max ?? d.uv_index_max)?[safe: i] ?? nil)
        }
        return Forecast(id: id, hourly: hours, daily: days, source: model == gfsModel ? Forecast.sourceGFS : model == bestModel ? Forecast.sourceBest : Forecast.sourceECMWF, now: r.current.map { nowHour($0, hours) })
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
