import Foundation

enum Sun {
    /// Sunrise and sunset from NOAA's solar equations (as on Android); nil in polar day or night.
    static func times(day: String, lat: Double, lon: Double) -> (Date, Date)? {
        guard let date = Fmt.parseDay(day) else { return nil }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let comps = Calendar(identifier: .gregorian).dateComponents(in: israelZone, from: date)
        guard let y = comps.year, let m = comps.month, let d = comps.day,
              let utcMidnight = cal.date(from: DateComponents(year: y, month: m, day: d)) else { return nil }
        let doy = Double(Calendar(identifier: .gregorian).ordinality(of: .day, in: .year, for: date) ?? 1)
        let leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
        let g = 2 * Double.pi / (leap ? 366 : 365) * (doy - 1)
        let eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        let decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        let rad = Double.pi / 180
        let cosHa = cos(90.833 * rad) / (cos(lat * rad) * cos(decl)) - tan(lat * rad) * tan(decl)
        guard cosHa >= -1, cosHa <= 1 else { return nil }
        let ha = acos(cosHa) / rad
        let rise = utcMidnight.addingTimeInterval((720 - 4 * (lon + ha) - eq) * 60)
        let set = utcMidnight.addingTimeInterval((720 - 4 * (lon - ha) - eq) * 60)
        return (rise, set)
    }

    /// "Feels like" (°C): the Australian apparent temperature, as Open-Meteo and the Android app use.
    static func apparent(temp: Double?, rh: Double?, windKmh: Double?) -> Double? {
        guard let t = temp, let rh, let w = windKmh else { return nil }
        let vapour = rh / 100 * 6.105 * exp(17.27 * t / (237.7 + t))
        return t + 0.33 * vapour - 0.70 * (w / 3.6) - 4.0
    }
}
