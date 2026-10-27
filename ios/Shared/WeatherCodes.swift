import Foundation

/// IMS weather codes (the app's common language, as on Android) → text and SF Symbol.
enum WeatherCodes {
    private struct Info { let he: String; let en: String; let day: String; let night: String }

    private static let codes: [Int: Info] = [
        1250: Info(he: "בהיר", en: "Clear", day: "sun.max.fill", night: "moon.stars.fill"),
        1220: Info(he: "מעונן חלקית", en: "Partly cloudy", day: "cloud.sun.fill", night: "cloud.moon.fill"),
        1230: Info(he: "מעונן", en: "Cloudy", day: "cloud.fill", night: "cloud.fill"),
        1570: Info(he: "אביך", en: "Dust", day: "sun.dust.fill", night: "moon.dust.fill"),
        1010: Info(he: "סופות חול", en: "Sandstorms", day: "sun.dust.fill", night: "moon.dust.fill"),
        1160: Info(he: "ערפל", en: "Fog", day: "cloud.fog.fill", night: "cloud.fog.fill"),
        1310: Info(he: "חם", en: "Hot", day: "sun.max.fill", night: "moon.stars.fill"),
        1580: Info(he: "חם מאוד", en: "Extremely hot", day: "sun.max.fill", night: "moon.stars.fill"),
        1270: Info(he: "הביל", en: "Muggy", day: "sun.haze.fill", night: "moon.haze.fill"),
        1320: Info(he: "קר", en: "Cold", day: "cloud.fill", night: "cloud.fill"),
        1590: Info(he: "קר מאוד", en: "Extremely cold", day: "cloud.fill", night: "cloud.fill"),
        1300: Info(he: "קרה", en: "Frost", day: "snowflake", night: "snowflake"),
        1530: Info(he: "מעונן חלקית, ייתכן גשם", en: "Partly cloudy, possible rain", day: "cloud.sun.rain.fill", night: "cloud.moon.rain.fill"),
        1540: Info(he: "מעונן, ייתכן גשם", en: "Cloudy, possible rain", day: "cloud.drizzle.fill", night: "cloud.drizzle.fill"),
        1560: Info(he: "מעונן, גשם קל", en: "Cloudy, light rain", day: "cloud.drizzle.fill", night: "cloud.drizzle.fill"),
        1140: Info(he: "גשום", en: "Rainy", day: "cloud.rain.fill", night: "cloud.rain.fill"),
        1020: Info(he: "סופות רעמים", en: "Thunderstorms", day: "cloud.bolt.rain.fill", night: "cloud.bolt.rain.fill"),
        1510: Info(he: "סוער", en: "Stormy", day: "cloud.bolt.fill", night: "cloud.bolt.fill"),
        1260: Info(he: "רוחות ערות", en: "Windy", day: "wind", night: "wind"),
        1080: Info(he: "שלג מעורב בגשם", en: "Sleet", day: "cloud.sleet.fill", night: "cloud.sleet.fill"),
        1070: Info(he: "שלג קל", en: "Light snow", day: "cloud.snow.fill", night: "cloud.snow.fill"),
        1060: Info(he: "שלג", en: "Snow", day: "cloud.snow.fill", night: "cloud.snow.fill"),
        1520: Info(he: "שלג כבד", en: "Heavy snow", day: "cloud.snow.fill", night: "cloud.snow.fill"),
    ]

    static func describe(_ code: Int, english: Bool = Fmt.english) -> String {
        guard let i = codes[code] else { return "" }
        return english ? i.en : i.he
    }

    static func symbol(_ code: Int, night: Bool = false) -> String {
        let i = codes[code] ?? codes[1230]!
        return night ? i.night : i.day
    }

    /// Whether an hour (or reading) falls mostly after sunset or before sunrise (central Israel).
    static func isNight(_ t: String) -> Bool {
        guard let d = Fmt.parse(t), let (rise, set) = Sun.times(day: String(t.prefix(10)), lat: 31.5, lon: 34.9) else { return false }
        let minute = Int(t.suffix(2)) ?? 0
        let mid = minute == 0 ? d.addingTimeInterval(1800) : d
        return mid < rise || mid >= set
    }

    /// Clear / partly cloudy / cloudy / rain…: the sky family, for picking widget backgrounds.
    static func isRainy(_ code: Int) -> Bool { [1530, 1540, 1560, 1140, 1020, 1510, 1080, 1070, 1060, 1520].contains(code) }
}
