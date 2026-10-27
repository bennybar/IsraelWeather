import Foundation

/// Dates and numbers, always in Israel time and with Latin digits (like Android's Locale.ROOT).
enum Fmt {
    private static func formatter(_ pattern: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = israelZone
        f.dateFormat = pattern
        return f
    }

    private static let hourMinute = formatter("yyyy-MM-dd HH:mm")
    private static let dayOnly = formatter("yyyy-MM-dd")
    private static let clock = formatter("HH:mm")
    private static let hourOnly = formatter("HH")

    static func parse(_ s: String) -> Date? { hourMinute.date(from: s.replacingOccurrences(of: "T", with: " ")) }
    static func parseDay(_ s: String) -> Date? { dayOnly.date(from: s) }
    static func dayString(_ d: Date) -> String { dayOnly.string(from: d) }
    static func time(_ d: Date) -> String { clock.string(from: d) }
    static func hour(_ d: Date) -> String { hourOnly.string(from: d) }

    /// The "yyyy-MM-dd HH:00" slot covering [d].
    static func slot(_ d: Date) -> String { String(hourMinute.string(from: d).prefix(13)) + ":00" }

    static var english: Bool { Locale.preferredLanguages.first?.hasPrefix("he") != true }

    /// "Today", or a short weekday in the app's language.
    static func dayLabel(_ day: String, english: Bool = Fmt.english) -> String {
        if day == dayString(Date()) { return L("today") }
        guard let d = parseDay(day) else { return day }
        let f = DateFormatter()
        f.locale = Locale(identifier: english ? "en_US" : "he_IL")
        f.timeZone = israelZone
        f.dateFormat = english ? "EEE" : "EEEEE"
        let s = f.string(from: d)
        return english ? s : s.replacingOccurrences(of: "יום ", with: "")
    }

    static func temp(_ c: Double?, _ s: AppSettings = .shared) -> String {
        guard let c else { return "–" }
        let v = s.fahrenheit ? c * 9 / 5 + 32 : c
        return "\(Int(v.rounded()))°"
    }

    static func wind(_ kmh: Double?, _ s: AppSettings = .shared) -> String {
        guard let kmh else { return "–" }
        return s.windMS ? "\(Int((kmh / 3.6).rounded()))" : "\(Int(kmh.rounded()))"
    }

    static func windUnit(_ s: AppSettings = .shared) -> String { L(s.windMS ? "unit_ms" : "unit_kmh") }

    static func pct(_ v: Int?) -> String { v.map { "\($0)%" } ?? "–" }
}

/// A localized string from Localizable.strings.
func L(_ key: String) -> String { NSLocalizedString(key, comment: "") }
