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

    /// Formatters and parsed dates, kept: building a DateFormatter or parsing a string is costly, and
    /// the forecast views ask for the same few hundred dates on every redraw (each swipe frame).
    private static let lock = NSLock()
    private static var formatters: [String: DateFormatter] = [:]
    private static var parsed: [String: Date] = [:]

    private static func cachedFormatter(_ key: String, _ make: () -> DateFormatter) -> DateFormatter {
        lock.lock(); defer { lock.unlock() }
        if let f = formatters[key] { return f }
        let f = make()
        formatters[key] = f
        return f
    }

    private static let hourMinute = formatter("yyyy-MM-dd HH:mm")
    private static let dayOnly = formatter("yyyy-MM-dd")
    private static let clock = formatter("HH:mm")
    private static let hourOnly = formatter("HH")

    static func parse(_ s: String) -> Date? {
        lock.lock()
        if let d = parsed[s] { lock.unlock(); return d }
        lock.unlock()
        guard let d = hourMinute.date(from: s.replacingOccurrences(of: "T", with: " ")) else { return nil }
        lock.lock()
        if parsed.count > 4000 { parsed.removeAll() }
        parsed[s] = d
        lock.unlock()
        return d
    }
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
        let f = cachedFormatter("label-\(english)") {
            let f = DateFormatter()
            f.locale = Locale(identifier: english ? "en_US" : "he_IL")
            f.timeZone = israelZone
            f.dateFormat = english ? "EEE" : "EEEEE"
            return f
        }
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

    private static func localized(_ pattern: String) -> DateFormatter {
        cachedFormatter("\(english)-\(pattern)") {
            let f = DateFormatter()
            f.locale = Locale(identifier: english ? "en_US" : "he_IL")
            f.timeZone = israelZone
            f.dateFormat = pattern
            return f
        }
    }

    /// "Friday, 20 November" in the app's language.
    static func longDay(_ day: String) -> String {
        guard let d = parseDay(day) else { return day }
        return localized(english ? "EEEE, d MMMM" : "EEEE, d בMMMM").string(from: d)
    }

    /// A day's weekday initial and day of the month, for the day screen's date strip.
    static func dayParts(_ day: String) -> (String, String) {
        guard let d = parseDay(day) else { return ("", day) }
        return (localized("EEEEE").string(from: d), cachedFormatter("d") { formatter("d") }.string(from: d))
    }

    /// Where the wind comes from, in words ("northwest").
    static func compass(_ deg: Double) -> String {
        L("dir_\(Int(((deg.truncatingRemainder(dividingBy: 360) + 360 + 22.5) / 45)) % 8)")
    }
}

/// A localized string from Localizable.strings.
func L(_ key: String) -> String { NSLocalizedString(key, comment: "") }
