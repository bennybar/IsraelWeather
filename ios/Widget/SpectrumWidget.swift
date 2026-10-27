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

extension SpectrumLayout: AppEnum {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Layout"
    static var caseDisplayRepresentations: [SpectrumLayout: DisplayRepresentation] = [
        .sky: "Sky Glass", .capsules: "Glass Capsules", .classic: "Classic", .refined: "Refined",
        .tiles: "Tiles", .aurora: "Aurora", .bold: "Bold", .boldLines: "Bold lines",
    ]
}

extension SpectrumColor: AppEnum {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Color"
    static var caseDisplayRepresentations: [SpectrumColor: DisplayRepresentation] = [
        .lavender: "Lavender", .orange: "Orange", .twilight: "Twilight", .sky: "Sky",
        .mint: "Mint", .rose: "Rose", .glass: "Liquid glass", .aurora: "Aurora",
    ]
}

struct SpectrumIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Spectrum"
    static var description = IntentDescription("Now, and the next days with temperature ranges and rain chances.")
    @Parameter(title: "Place") var place: PlaceEntity?
    @Parameter(title: "Layout", default: .sky) var layout: SpectrumLayout
    @Parameter(title: "Color", default: .lavender) var color: SpectrumColor
    @Parameter(title: "Days", default: 4, inclusiveRange: (2, 5)) var days: Int
    @Parameter(title: "Feels, humidity and wind", default: true) var stats: Bool
    @Parameter(title: "Wind each day", default: false) var wind: Bool
    @Parameter(title: "Wind as arrow pills", default: false) var windCapsules: Bool
    @Parameter(title: "Same-size rain pills", default: false) var equalPills: Bool

    // Only the settings the chosen layout uses.
    static var parameterSummary: some ParameterSummary {
        Switch(\.$layout) {
            Case(.classic) {
                Summary { \.$place; \.$layout; \.$color; \.$days; \.$stats; \.$wind; \.$windCapsules; \.$equalPills }
            }
            Case(.refined) {
                Summary { \.$place; \.$layout; \.$color; \.$stats }
            }
            Case(.tiles) {
                Summary { \.$place; \.$layout; \.$color; \.$days; \.$stats }
            }
            Case(.aurora) {
                Summary { \.$place; \.$layout; \.$color; \.$days }
            }
            Case(.bold) {
                Summary { \.$place; \.$layout; \.$color; \.$days }
            }
            Case(.boldLines) {
                Summary { \.$place; \.$layout; \.$color; \.$days }
            }
            DefaultCase {
                Summary { \.$place; \.$layout; \.$days }
            }
        }
    }

    var options: WidgetOptions {
        WidgetOptions(layout: layout, color: color, days: days, stats: stats, wind: wind, windCapsules: windCapsules, equalPills: equalPills)
    }
}

// MARK: timeline

struct SpectrumProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SpectrumEntry {
        SpectrumEntry(date: Date(), place: "Yavne", forecast: SampleData.forecast, updated: Date())
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
            return SpectrumEntry(date: Date(), place: name, forecast: nil, updated: nil, options: c.options)
        }
        return SpectrumEntry(date: Date(), place: name, forecast: f, updated: at, options: c.options)
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
