import Foundation

/// Id of the forecast for the device's exact spot (Open-Meteo / Foreca), as on Android.
let currentID = -1

/// IMS forecasts within this distance of one of its points; farther, a model forecast for the spot.
let imsRadiusKm = 3.0

let israelZone = TimeZone(identifier: "Asia/Jerusalem")!

/// One of IMS's forecast points (or a searched place, with a negative id).
struct Place: Codable, Hashable, Identifiable {
    let id: Int
    let he: String
    let en: String
    let lat: Double
    let lon: Double
    let rid: Int

    func name(english: Bool) -> String { english ? en : he }
}

struct PlacesResponse: Codable { let locations: [Place] }

/// A place found with search: its own coordinates, and the IMS point within 3 km (if any).
struct CustomPlace: Codable, Hashable {
    let id: Int
    let he: String
    let en: String
    let lat: Double
    let lon: Double
    let ims: Int?
    let nearest: Int?
}

struct Hour: Codable, Hashable {
    /// "yyyy-MM-dd HH:mm", Israel time.
    let t: String
    let code: Int
    var temp: Double? = nil
    var rainChance: Double? = nil
    var rain: Double? = nil
    var rh: Double? = nil
    var wind: Double? = nil
    var gust: Double? = nil
    var windDir: Double? = nil
    var uv: Double? = nil

    var date: Date { Fmt.parse(t) ?? .distantPast }
    var day: String { String(t.prefix(10)) }
}

struct Day: Codable, Hashable {
    /// "yyyy-MM-dd".
    let date: String
    let code: Int
    var min: Double? = nil
    var max: Double? = nil
    var uv: Double? = nil
}

struct Forecast: Codable {
    let id: Int
    var hourly: [Hour] = []
    var daily: [Day] = []
    var source: String = Forecast.sourceIMS
    /// Current conditions where the source has them (ECMWF every 15 minutes, Foreca).
    var now: Hour? = nil

    static let sourceIMS = "ims"
    static let sourceECMWF = "open-meteo"
    static let sourceForeca = "foreca"

    enum CodingKeys: String, CodingKey { case id, hourly, daily, source, now }

    init(id: Int, hourly: [Hour] = [], daily: [Day] = [], source: String = Forecast.sourceIMS, now: Hour? = nil) {
        self.id = id
        self.hourly = hourly
        self.daily = daily
        self.source = source
        self.now = now
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        hourly = try c.decodeIfPresent([Hour].self, forKey: .hourly) ?? []
        daily = try c.decodeIfPresent([Day].self, forKey: .daily) ?? []
        source = try c.decodeIfPresent(String.self, forKey: .source) ?? Forecast.sourceIMS
        now = try c.decodeIfPresent(Hour.self, forKey: .now)
    }

    /// "Now": the source's current conditions while under an hour old, else the hour slot covering now.
    func currentHour(_ at: Date = Date()) -> Hour? {
        if let n = now, n.date <= at, n.date > at.addingTimeInterval(-3600) { return n }
        let slot = Fmt.slot(at)
        return hourly.first { $0.t >= slot }
    }

    func upcoming(_ count: Int, _ at: Date = Date()) -> [Hour] {
        let slot = Fmt.slot(at)
        return Array(hourly.filter { $0.t >= slot }.prefix(count))
    }

    func hours(on day: String) -> [Hour] { hourly.filter { $0.day == day } }

    /// The next [n] days starting today.
    func days(_ n: Int, _ at: Date = Date()) -> [Day] {
        let today = Fmt.dayString(at)
        return Array(daily.filter { $0.date >= today }.prefix(n))
    }

    /// A day's rain chance: its highest hourly chance (%).
    func rainChance(_ d: Day) -> Int? {
        hours(on: d.date).compactMap(\.rainChance).max().map { Int($0.rounded()) }
    }

    /// A day's strongest wind and that hour's direction.
    func wind(_ d: Day) -> (Double?, Double?) {
        let h = hours(on: d.date).filter { $0.wind != nil }.max { ($0.wind ?? 0) < ($1.wind ?? 0) }
        return (h?.wind, h?.windDir)
    }
}

struct Localized: Codable, Hashable {
    var he: String? = nil
    var en: String? = nil
    func get(english: Bool) -> String? { (english ? en : he) ?? he ?? en }
}

struct Warning: Codable, Hashable, Identifiable {
    let id: Int
    let severity: Int
    let level: String
    var early: Bool? = false
    var typeName: Localized? = nil
    var from: String? = nil
    var to: String? = nil
    var he: String = ""
    var en: String = ""
    var regions: [Int] = []

    func text(english: Bool) -> String {
        let t = english ? en : he
        return t.isEmpty ? (he.isEmpty ? en : he) : t
    }

    var isActive: Bool {
        guard let to, let end = Fmt.parse(String(to.prefix(16))) else { return true }
        return end > Date()
    }
}

struct WarningsResponse: Codable { let warnings: [Warning] }

enum HTTPError: Error { case status(Int) }
