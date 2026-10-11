package com.bennybar.israelweather.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TempUnit { C, F }
enum class WindUnit { KMH, MS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The forecast page's sections, in their default order. */
enum class Card { HERO, ALERTS, HOURLY, WEEK, DETAILS, RADAR, SUMMARY }

data class Settings(
    val tempUnit: TempUnit = TempUnit.C,
    val windUnit: WindUnit = WindUnit.KMH,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Int = 0,
    /** Background refresh interval in minutes; 0 = off. */
    val refreshMinutes: Int = 60,
    val cards: List<Card> = Card.entries,
    val hiddenCards: Set<Card> = emptySet(),
    /** Saved IMS location ids, in the user's order. */
    val places: List<Int> = emptyList(),
    val useLocation: Boolean = true,
    /** The IMS location nearest the device, from the last successful fix. */
    val currentPlace: Int? = null,
    /** The device's last fix (rounded), and its distance to [currentPlace]. */
    val currentLat: Double? = null,
    val currentLon: Double? = null,
    val currentKm: Int? = null,
    /** The town at the device's location (reverse geocoded), named when Open-Meteo is used. */
    val currentTown: Localized? = null,
) {
    /**
     * The forecast to show for "my location": the nearest IMS point's when it is within
     * [IMS_RADIUS_KM] (or the distance is unknown), else Open-Meteo for the exact spot.
     */
    val currentForecastId: Int?
        get() = when {
            currentPlace == null -> null
            currentLat == null || currentKm == null || currentKm <= IMS_RADIUS_KM -> currentPlace
            else -> CURRENT_ID
        }

    val visibleCards get() = cards.filter { it !in hiddenCards }
}

/** SharedPreferences-backed settings exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value get() = _state.value

    private fun load(): Settings {
        val d = Settings()
        val cards = prefs.getString("cards", null)?.split(',')?.mapNotNull { n -> Card.entries.firstOrNull { it.name == n } }
        return Settings(
            tempUnit = enumOr(prefs.getString("tempUnit", null), d.tempUnit),
            windUnit = enumOr(prefs.getString("windUnit", null), d.windUnit),
            theme = enumOr(prefs.getString("theme", null), d.theme),
            accent = prefs.getInt("accent", d.accent),
            refreshMinutes = prefs.getInt("refreshMinutes", d.refreshMinutes),
            // Cards added in later versions are appended so they still show up.
            cards = cards?.let { it + Card.entries.filter { c -> c !in it } } ?: d.cards,
            hiddenCards = prefs.getString("hiddenCards", "")!!.split(',').mapNotNull { n -> Card.entries.firstOrNull { it.name == n } }.toSet(),
            places = prefs.getString("places", "")!!.split(',').mapNotNull { it.toIntOrNull() },
            useLocation = prefs.getBoolean("useLocation", d.useLocation),
            currentPlace = prefs.getInt("currentPlace", -1).takeIf { it >= 0 },
            currentLat = prefs.getString("currentLat", null)?.toDoubleOrNull(),
            currentLon = prefs.getString("currentLon", null)?.toDoubleOrNull(),
            currentKm = prefs.getInt("currentKm", -1).takeIf { it >= 0 },
            currentTown = prefs.getString("currentTownHe", null)?.let { Localized(it, prefs.getString("currentTownEn", null)) },
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private fun update(f: (Settings) -> Settings) {
        val s = f(_state.value)
        _state.value = s
        prefs.edit()
            .putString("tempUnit", s.tempUnit.name)
            .putString("windUnit", s.windUnit.name)
            .putString("theme", s.theme.name)
            .putInt("accent", s.accent)
            .putInt("refreshMinutes", s.refreshMinutes)
            .putString("cards", s.cards.joinToString(",") { it.name })
            .putString("hiddenCards", s.hiddenCards.joinToString(",") { it.name })
            .putString("places", s.places.joinToString(","))
            .putBoolean("useLocation", s.useLocation)
            .putInt("currentPlace", s.currentPlace ?: -1)
            .putString("currentLat", s.currentLat?.toString())
            .putString("currentLon", s.currentLon?.toString())
            .putInt("currentKm", s.currentKm ?: -1)
            .putString("currentTownHe", s.currentTown?.he)
            .putString("currentTownEn", s.currentTown?.en)
            .apply()
    }

    fun setTempUnit(v: TempUnit) = update { it.copy(tempUnit = v) }
    fun setWindUnit(v: WindUnit) = update { it.copy(windUnit = v) }
    fun setTheme(v: ThemeMode) = update { it.copy(theme = v) }
    fun setAccent(v: Int) = update { it.copy(accent = v) }
    fun setRefreshMinutes(v: Int) = update { it.copy(refreshMinutes = v) }
    fun setUseLocation(v: Boolean) = update { it.copy(useLocation = v) }
    fun setCurrentPlace(id: Int?) = update { it.copy(currentPlace = id) }

    fun setCurrentLocation(id: Int, lat: Double, lon: Double, km: Int, town: Localized?) =
        update { it.copy(currentPlace = id, currentLat = lat, currentLon = lon, currentKm = km, currentTown = town) }

    fun setCardVisible(card: Card, visible: Boolean) =
        update { it.copy(hiddenCards = if (visible) it.hiddenCards - card else it.hiddenCards + card) }

    fun moveCard(card: Card, delta: Int) = update { it.copy(cards = it.cards.moved(card, delta)) }
    fun resetCards() = update { it.copy(cards = Card.entries, hiddenCards = emptySet()) }

    fun addPlace(id: Int) = update { if (id in it.places) it else it.copy(places = it.places + id) }
    fun removePlace(id: Int) = update { it.copy(places = it.places - id) }
    fun movePlace(id: Int, delta: Int) = update { it.copy(places = it.places.moved(id, delta)) }

    // ---- per-widget place: "current" or an IMS location id ----
    fun widgetPlace(appWidgetId: Int): String? = prefs.getString("widget_$appWidgetId", null)
    fun setWidgetPlace(appWidgetId: Int, place: String) = prefs.edit().putString("widget_$appWidgetId", place).apply()

    /** Whether we already explained and asked for "Allow all the time" (asked once; Settings can ask again). */
    fun backgroundAsked() = prefs.getBoolean("backgroundAsked", false)
    fun setBackgroundAsked() = prefs.edit().putBoolean("backgroundAsked", true).apply()

    /** Per-widget "refresh now" button (shown by default). */
    fun widgetShowRefresh(appWidgetId: Int) = prefs.getBoolean("widget_refresh_$appWidgetId", true)
    fun setWidgetShowRefresh(appWidgetId: Int, show: Boolean) = prefs.edit().putBoolean("widget_refresh_$appWidgetId", show).apply()

    /** Color of the Quiet week / Ranges widgets: "lavender" (default) or "orange". */
    fun widgetColor(appWidgetId: Int): String = prefs.getString("widget_color_$appWidgetId", null) ?: "lavender"
    fun setWidgetColor(appWidgetId: Int, color: String) = prefs.edit().putString("widget_color_$appWidgetId", color).apply()

    private fun <T> List<T>.moved(item: T, delta: Int): List<T> {
        val i = indexOf(item)
        val j = (i + delta).coerceIn(0, size - 1)
        if (i < 0 || i == j) return this
        return toMutableList().apply { add(j, removeAt(i)) }
    }
}
