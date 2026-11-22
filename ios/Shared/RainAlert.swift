import Foundation
import UserNotifications

/// "Rain from 15:00 in Yavne", about an hour before rain at my location. No background work of its
/// own: whenever my location's forecast is downloaded (by the app or a widget) the one pending
/// notification is set again from it, or removed when the rain is gone from the forecast.
enum RainAlert {
    private static let id = "rain-soon"
    private static let lead: TimeInterval = 3600

    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    static func cancel() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
    }

    /// When the next rain starts and when to say so, or nil for no alert: rain must follow a dry
    /// hour within 12 hours, the alert an hour ahead must fall 07–22 and not be due within a minute,
    /// and one alert covers a spell (none for rain starting within 4 hours of the last one sent).
    static func next(_ f: Forecast, now: Date, lastFire: Date?) -> (start: Date, fire: Date)? {
        let hours = f.upcoming(12, now)
        guard let first = hours.firstIndex(where: \.wet), first > 0 else { return nil }
        let start = hours[first].date
        let fire = start.addingTimeInterval(-lead)
        let hour = Calendar(identifier: .gregorian).dateComponents(in: israelZone, from: fire).hour ?? 12
        guard fire > now.addingTimeInterval(60), (7..<22).contains(hour) else { return nil }
        if let last = lastFire, last < now, start < last.addingTimeInterval(lead + 4 * 3600) { return nil }
        return (start, fire)
    }

    static func update(_ f: Forecast, place: String?) {
        let settings = AppSettings.shared
        guard settings.rainAlerts else { return }
        let now = Date()
        guard let (start, fire) = next(f, now: now, lastFire: settings.rainAlertFire) else {
            cancel() // rain gone from the forecast (or already sent): drop a pending alert
            return
        }
        let content = UNMutableNotificationContent()
        content.title = L("rain_alert_title")
        content.body = place.map { String(format: L("rain_alert_body"), Fmt.time(start), $0) }
            ?? String(format: L("sum_rain_from"), Fmt.time(start))
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: fire.timeIntervalSince(now), repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
        settings.rainAlertFire = fire
    }
}
