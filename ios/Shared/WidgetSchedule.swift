import Foundation

/// When a widget's timeline shows what, and when it next asks for data.
///
/// One download gives several entries: now, and the start of each of the next hours, so the widget
/// moves on to the next hour by itself, with no wake-up and no network. New data is asked for once
/// an hour (every two in Low Power Mode); a widget with nothing to show retries sooner.
enum WidgetSchedule {
    /// Now, then the next three hour starts (same day only: "Today" must stay today).
    static func dates(from now: Date = Date()) -> [Date] {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = israelZone
        var out = [now]
        var t = now
        for _ in 0..<3 {
            guard let next = cal.nextDate(after: t, matching: DateComponents(minute: 0, second: 0), matchingPolicy: .nextTime),
                  Fmt.dayString(next) == Fmt.dayString(now) else { break }
            out.append(next)
            t = next
        }
        return out
    }

    static func nextRefresh(hasData: Bool, from now: Date = Date()) -> Date {
        if !hasData { return now.addingTimeInterval(15 * 60) }
        return now.addingTimeInterval((ProcessInfo.processInfo.isLowPowerModeEnabled ? 120 : 60) * 60)
    }
}
