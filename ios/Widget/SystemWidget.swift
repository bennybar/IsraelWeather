import AppIntents
import SwiftUI
import WidgetKit

extension SystemStyle: AppEnum {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Style"
    static var caseDisplayRepresentations: [SystemStyle: DisplayRepresentation] = [
        .upNext: "Up next", .hourly: "Next hours", .threeDays: "Three days",
    ]
}

extension SystemAppearance: AppEnum {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Appearance"
    static var caseDisplayRepresentations: [SystemAppearance: DisplayRepresentation] = [
        .automatic: "Automatic", .light: "Light", .dark: "Dark",
    ]
}

struct SystemIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Israel Weather"
    static var description = IntentDescription("The weather in Apple's widget style.")
    @Parameter(title: "Place") var place: PlaceEntity?
    @Parameter(title: "Style", default: .upNext) var style: SystemStyle
    @Parameter(title: "Appearance", default: .automatic) var appearance: SystemAppearance
}

struct SystemProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SystemEntry {
        SystemEntry(date: Date(), place: "Yavne", forecast: SampleData.forecast)
    }

    func snapshot(for configuration: SystemIntent, in context: Context) async -> SystemEntry {
        if context.isPreview {
            return SystemEntry(date: Date(), place: "Yavne", forecast: SampleData.forecast, style: configuration.style, appearance: configuration.appearance)
        }
        return await entry(configuration)
    }

    func timeline(for configuration: SystemIntent, in context: Context) async -> Timeline<SystemEntry> {
        Timeline(entries: [await entry(configuration)], policy: .after(Date().addingTimeInterval(30 * 60)))
    }

    private func entry(_ c: SystemIntent) async -> SystemEntry {
        let s = AppSettings.shared
        let chosen = c.place?.id ?? currentID
        let forecastID = chosen == currentID ? s.currentForecastID : s.forecastID(for: chosen)
        let name = await Repository.shared.place(for: chosen)?.name(english: Fmt.english) ?? ""
        let f = await forecastID.asyncMap { await Repository.shared.forecast($0) }??.0
        return SystemEntry(date: Date(), place: name, forecast: f, style: c.style, appearance: c.appearance)
    }
}

extension Optional {
    func asyncMap<T>(_ f: (Wrapped) async -> T) async -> T? {
        guard let v = self else { return nil }
        return await f(v)
    }
}

struct SystemWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "IsraelWeatherSystem", intent: SystemIntent.self, provider: SystemProvider()) { entry in
            SystemWidgetView(entry: entry).widgetURL(URL(string: "israelweather://forecast"))
        }
        .configurationDisplayName("Israel Weather")
        .description(L("widget_system_desc"))
        .supportedFamilies([.systemSmall])
    }
}
