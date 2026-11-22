import SwiftUI

@main
struct IsraelWeatherApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .onAppear {
                    #if DEBUG
                    if ProcessInfo.processInfo.arguments.contains("-renderWidgets") { Task { await WidgetSnapshots.render() } }
                    #endif
                }
                .onChange(of: phase, initial: true) { _, p in if p == .active { model.start() } }
        }
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        TabView(selection: $model.tab) {
            Tab(L("tab_forecast"), systemImage: "cloud.sun.fill", value: 0) { ForecastView() }
            Tab(L("tab_alerts"), systemImage: "exclamationmark.triangle.fill", value: 1) { AlertsView() }
            Tab(L("tab_places"), systemImage: "list.bullet", value: 2) { PlacesView(tab: $model.tab) }
        }
        // The whole app sits on the sky: light text, icons and bars everywhere (Settings too).
        .preferredColorScheme(.dark)
    }
}

/// The sky behind a page: day blue, night navy, grey when it rains.
struct SkyBackground: View {
    let code: Int
    let night: Bool

    var body: some View {
        LinearGradient(colors: Self.colors(code: code, night: night), startPoint: .top, endPoint: .bottom).ignoresSafeArea()
    }

    /// The sky's top and bottom colors. Settings → Background can keep the day or the night sky.
    static func colors(code: Int, night: Bool) -> [Color] {
        let night = switch AppSettings.shared.skyMode {
        case .automatic: night
        case .day: false
        case .night: true
        }
        return WeatherCodes.isRainy(code) ? [Color(hex: 0x6A7A92), Color(hex: 0x3E4B62)]
            : night ? [Color(hex: 0x1F2A4A), Color(hex: 0x2B2552)]
            : [Color(hex: 0x4A86DE), Color(hex: 0x23479A)]
    }
}

