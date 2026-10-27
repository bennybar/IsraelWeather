import AppIntents
import SwiftUI
import WidgetKit

// MARK: configuration (long-press → Edit Widget)

struct PlaceEntity: AppEntity {
    let id: Int
    let name: String
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Place"
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
    static var defaultQuery = PlaceQuery()
    static let myLocation = PlaceEntity(id: currentID, name: L("my_location"))
}

struct PlaceQuery: EntityQuery {
    func entities(for identifiers: [Int]) async throws -> [PlaceEntity] {
        try await suggestedEntities().filter { identifiers.contains($0.id) }
    }

    func suggestedEntities() async throws -> [PlaceEntity] {
        let s = AppSettings.shared
        var out = [PlaceEntity.myLocation]
        for id in s.places {
            if let p = await Repository.shared.place(for: id) { out.append(PlaceEntity(id: id, name: p.name(english: Fmt.english))) }
        }
        return out
    }

    func defaultResult() async -> PlaceEntity? { .myLocation }
}

struct SpectrumIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Spectrum"
    static var description = IntentDescription("Now, and the next days with temperature ranges and rain chances.")
    @Parameter(title: "Place") var place: PlaceEntity?
    @Parameter(title: "Color", default: .lavender) var color: SpectrumColor
}

// MARK: timeline

struct SpectrumProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SpectrumEntry {
        SpectrumEntry(date: Date(), place: "Yavne", forecast: SampleData.forecast, updated: Date(), color: .lavender)
    }

    func snapshot(for configuration: SpectrumIntent, in context: Context) async -> SpectrumEntry {
        if context.isPreview { return placeholder(in: context) }
        return await entry(configuration)
    }

    func timeline(for configuration: SpectrumIntent, in context: Context) async -> Timeline<SpectrumEntry> {
        let e = await entry(configuration)
        // Refresh in about half an hour (iOS decides the exact moment).
        return Timeline(entries: [e], policy: .after(Date().addingTimeInterval(30 * 60)))
    }

    private func entry(_ c: SpectrumIntent) async -> SpectrumEntry {
        let s = AppSettings.shared
        let chosen = c.place?.id ?? currentID
        let forecastID = chosen == currentID ? s.currentForecastID : s.forecastID(for: chosen)
        let name = await Repository.shared.place(for: chosen == currentID ? currentID : chosen)?.name(english: Fmt.english) ?? ""
        guard let fid = forecastID, let (f, at) = await Repository.shared.forecast(fid) else {
            return SpectrumEntry(date: Date(), place: name, forecast: nil, updated: nil, color: c.color)
        }
        return SpectrumEntry(date: Date(), place: name, forecast: f, updated: at, color: c.color)
    }
}

struct SpectrumWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Spectrum", intent: SpectrumIntent.self, provider: SpectrumProvider()) { entry in
            SpectrumFamilyView(entry: entry).widgetURL(URL(string: "israelweather://forecast"))
        }
        .configurationDisplayName("Spectrum")
        .description(L("widget_spectrum_desc"))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct IsraelWeatherWidgets: WidgetBundle {
    var body: some Widget { SpectrumWidget() }
}


/// Passes the widget's family to the shared view (which the app also renders, for previews).
struct SpectrumFamilyView: View {
    let entry: SpectrumEntry
    @Environment(\.widgetFamily) var family
    var body: some View { SpectrumWidgetView(entry: entry, small: family == .systemSmall) }
}
