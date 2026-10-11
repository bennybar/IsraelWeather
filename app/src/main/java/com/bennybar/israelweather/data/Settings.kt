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
) {
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
            .apply()
    }

    fun setTempUnit(v: TempUnit) = update { it.copy(tempUnit = v) }
    fun setWindUnit(v: WindUnit) = update { it.copy(windUnit = v) }
    fun setTheme(v: ThemeMode) = update { it.copy(theme = v) }
    fun setAccent(v: Int) = update { it.copy(accent = v) }
    fun setRefreshMinutes(v: Int) = update { it.copy(refreshMinutes = v) }
    fun setUseLocation(v: Boolean) = update { it.copy(useLocation = v) }
    fun setCurrentPlace(id: Int?) = update { it.copy(currentPlace = id) }

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

    /** Per-widget "refresh now" button (shown by default). */
    fun widgetShowRefresh(appWidgetId: Int) = prefs.getBoolean("widget_refresh_$appWidgetId", true)
    fun setWidgetShowRefresh(appWidgetId: Int, show: Boolean) = prefs.edit().putBoolean("widget_refresh_$appWidgetId", show).apply()

    private fun <T> List<T>.moved(item: T, delta: Int): List<T> {
        val i = indexOf(item)
        val j = (i + delta).coerceIn(0, size - 1)
        if (i < 0 || i == j) return this
        return toMutableList().apply { add(j, removeAt(i)) }
    }
}
