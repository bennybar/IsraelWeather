package com.bennybar.israelweather.ui.forecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bennybar.israelweather.R
import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.Observation
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.data.Settings
import com.bennybar.israelweather.data.Warning
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.WindUnit
import com.bennybar.israelweather.data.israelNow
import com.bennybar.israelweather.ui.appLocale
import com.bennybar.israelweather.ui.compassIndex
import com.bennybar.israelweather.ui.dayName
import com.bennybar.israelweather.ui.forLevel
import com.bennybar.israelweather.ui.hh
import com.bennybar.israelweather.ui.isEnglish
import com.bennybar.israelweather.ui.radar.RadarPreview
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.theme.LocalWeatherColors
import com.bennybar.israelweather.ui.warningLabel
import com.bennybar.israelweather.ui.warningRange
import com.bennybar.israelweather.ui.windValue
import com.bennybar.israelweather.widget.mm
import com.bennybar.israelweather.widget.pct
import kotlin.math.roundToInt

private val cardShape = RoundedCornerShape(32.dp)

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(cardShape).background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
private fun windUnitLabel(s: Settings) = stringResource(if (s.windUnit == WindUnit.MS) R.string.unit_ms else R.string.unit_kmh)

@Composable
private fun compass(deg: Double?): String {
    if (deg == null) return ""
    val names = listOf(R.string.dir_n, R.string.dir_ne, R.string.dir_e, R.string.dir_se, R.string.dir_s, R.string.dir_sw, R.string.dir_w, R.string.dir_nw)
    return stringResource(names[compassIndex(deg)])
}

// ---- Hero: the big accent card with the current temperature ----

@Composable
fun HeroCard(f: Forecast, obs: Observation?, s: Settings) {
    val cs = MaterialTheme.colorScheme
    val now = israelNow()
    val h = f.currentHour(now) ?: return
    val today = f.daily.firstOrNull { it.localDate == now.toLocalDate() }
    val english = isEnglish()
    Row(Modifier.padding(horizontal = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1f).heightIn(min = 210.dp).clip(RoundedCornerShape(40.dp)).background(cs.primary).padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (obs?.temp != null) stringResource(R.string.measured_now) else stringResource(R.string.forecast_now),
                    Modifier.weight(1f), color = cs.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Image(painterResource(WeatherCodes.drawable(h.code, WeatherCodes.isNight(h.time))), null, Modifier.size(48.dp))
            }
            Text(
                temp(obs?.temp ?: h.temp, s), color = cs.onPrimary,
                fontSize = 100.sp, lineHeight = 96.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-4).sp,
            )
            Text(
                buildString {
                    append(WeatherCodes.describe(h.code, english))
                    if (today != null) append(" · ${temp(today.max, s)}/${temp(today.min, s)}")
                },
                color = cs.onPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            )
            if (obs != null) Text(obs.station.name, color = cs.onPrimary, fontSize = 13.sp)
        }
        Column(Modifier.width(112.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SideTile(stringResource(R.string.humidity), "${(obs?.rh ?: h.rh)?.roundToInt() ?: "–"}%", null, Modifier.weight(1f))
            SideTile(stringResource(R.string.wind), windValue(obs?.wind ?: h.wind, s), "${windUnitLabel(s)} ${compass(obs?.windDir ?: h.windDir)}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SideTile(label: String, value: String, sub: String?, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

// ---- Alerts: active IMS warnings for this place ----

@Composable
fun AlertsCard(warnings: List<Warning>, onOpen: () -> Unit) {
    val wc = LocalWeatherColors.current
    val english = isEnglish()
    val locale = appLocale()
    val shown = warnings.filter { !it.isFlash }.take(3)
    if (shown.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { w ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(wc.warningBg).clickable(onClick = onOpen)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(10.dp).height(38.dp).clip(RoundedCornerShape(5.dp)).background(wc.forLevel(w.level)))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(w.typeName.get(english), warningLabel(w)).joinToString(" · "),
                        color = wc.onWarningBg, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(warningRange(w, locale), color = wc.onWarningBg, fontSize = 13.sp)
                }
            }
        }
    }
}

// ---- 24 hours: temperature line + rain-chance bars ----

@Composable
fun HourlyCard(f: Forecast, s: Settings) {
    val hours = f.upcoming(24)
    if (hours.size < 6) return
    val cs = MaterialTheme.colorScheme
    val wc = LocalWeatherColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val groups = hours.size / 3
    // Labels sit at the middle hour of each 3-hour group, so they line up with the chart points.
    val labelled = (0 until groups).map { g -> hours[g * 3 + 1] }
    CardBox {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.next_24h), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Legend(cs.primary, stringResource(R.string.legend_temp))
                Spacer(Modifier.width(12.dp))
                Legend(wc.rain, stringResource(R.string.legend_rain))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                labelled.forEach { h ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Image(painterResource(WeatherCodes.drawable(h.code, WeatherCodes.isNight(h.time))), null, Modifier.size(24.dp))
                    }
                }
            }
            val n = groups * 3
            val temps = hours.take(n).map { it.temp ?: 0.0 }
            val lo = temps.min()
            val hi = temps.max().coerceAtLeast(lo + 1)
            Canvas(Modifier.fillMaxWidth().height(96.dp).padding(vertical = 6.dp)) {
                val step = size.width / n
                fun x(i: Int) = if (rtl) size.width - (i + 0.5f) * step else (i + 0.5f) * step
                val top = 8.dp.toPx()
                val chartH = size.height * 0.62f
                hours.take(n).forEachIndexed { i, h ->
                    val chance = (h.rainChance ?: 0.0).toFloat() / 100f
                    if (chance > 0f) {
                        val bh = size.height * 0.45f * chance
                        drawRoundRect(wc.rain, Offset(x(i) - step * 0.3f, size.height - bh), Size(step * 0.6f, bh), CornerRadius(3.dp.toPx()))
                    }
                }
                val path = Path()
                temps.forEachIndexed { i, t ->
                    val y = top + chartH * (1f - ((t - lo) / (hi - lo)).toFloat())
                    if (i == 0) path.moveTo(x(i), y) else path.lineTo(x(i), y)
                }
                drawPath(path, cs.primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                (0 until groups).forEach { g ->
                    val i = g * 3 + 1
                    val y = top + chartH * (1f - ((temps[i] - lo) / (hi - lo)).toFloat())
                    drawCircle(cs.surfaceContainer, 5.dp.toPx(), Offset(x(i), y))
                    drawCircle(cs.primary, 3.5.dp.toPx(), Offset(x(i), y))
                }
            }
            val unit = stringResource(R.string.unit_mm)
            Row(Modifier.fillMaxWidth()) {
                labelled.forEachIndexed { g, h ->
                    // Rain for the whole 3-hour slot: its highest chance and total amount.
                    val slot = hours.subList(g * 3, g * 3 + 3)
                    val chance = slot.mapNotNull { it.rainChance }.maxOrNull()?.roundToInt()
                    val amount = slot.sumOf { it.rain ?: 0.0 }
                    val color = if ((chance ?: 0) > 0 || amount > 0) wc.rain else cs.onSurfaceVariant
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(temp(h.temp, s), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(hh(h.time), fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text(pct(chance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                        Text("${mm(amount)} $unit", fontSize = 10.sp, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- 7 days ----

@Composable
fun WeekCard(f: Forecast, s: Settings) {
    if (f.daily.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val wc = LocalWeatherColors.current
    val locale = appLocale()
    val today = israelNow().toLocalDate()
    val days = f.daily.filter { !it.localDate.isBefore(today) }.take(7)
    val lo = days.mapNotNull { it.min }.minOrNull() ?: 0.0
    val hi = (days.mapNotNull { it.max }.maxOrNull() ?: 1.0).coerceAtLeast(lo + 1)
    CardBox {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            days.forEach { d ->
                val dayHours = f.hoursOn(d.localDate)
                // IMS gives hourly amounts further ahead than chances: no chance shows "–".
                val rain = dayHours.mapNotNull { it.rainChance }.maxOrNull()?.roundToInt()
                val amount = dayHours.sumOf { it.rain ?: 0.0 }
                val rainColor = if ((rain ?: 0) > 0 || amount > 0) wc.rain else cs.onSurfaceVariant
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // English full names ("Wednesday") don't fit the column; Hebrew ones do.
                        if (d.localDate == today) stringResource(R.string.today) else dayName(d.localDate, locale, short = locale.language == "en"),
                        Modifier.width(68.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                    Image(painterResource(WeatherCodes.drawable(d.code)), null, Modifier.size(28.dp))
                    // Chance (the day's highest hourly) over its total amount.
                    Column(Modifier.width(60.dp).padding(start = 6.dp)) {
                        Text(pct(rain), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rainColor, maxLines = 1)
                        Text("${mm(amount)} ${stringResource(R.string.unit_mm)}", fontSize = 11.sp, color = rainColor, maxLines = 1)
                    }
                    Text(temp(d.min, s), Modifier.width(36.dp), fontSize = 15.sp, color = cs.onSurfaceVariant)
                    BoxWithConstraints(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(wc.track)) {
                        val start = (((d.min ?: lo) - lo) / (hi - lo)).toFloat()
                        val end = (((d.max ?: hi) - lo) / (hi - lo)).toFloat()
                        Box(
                            Modifier.padding(start = maxWidth * start).width(maxWidth * (end - start).coerceAtLeast(0.04f))
                                .fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(wc.heat),
                        )
                    }
                    Text(temp(d.max, s), Modifier.width(40.dp).padding(start = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ---- Details grid ----

@Composable
fun DetailsCard(f: Forecast, s: Settings) {
    val now = israelNow()
    val h = f.currentHour(now) ?: return
    val next24 = f.upcoming(24, now)
    val today = f.daily.firstOrNull { it.localDate == now.toLocalDate() }
    val wc = LocalWeatherColors.current
    val uv = today?.uv ?: f.hoursOn(now.toLocalDate()).mapNotNull { it.uv }.maxOrNull()
    val rainToday = f.hoursOn(now.toLocalDate()).filter { !it.time.isBefore(now.withMinute(0)) }.sumOf { it.rain ?: 0.0 }
    val gust = next24.mapNotNull { it.gust }.maxOrNull()
    val rainChance = next24.mapNotNull { it.rainChance }.maxOrNull()
    val tiles = listOf(
        Triple(stringResource(R.string.gusts), windValue(gust, s), windUnitLabel(s)) to null,
        Triple(stringResource(R.string.uv), uv?.roundToInt()?.toString() ?: "–", uvLabel(uv)) to wc.heat,
        Triple(stringResource(R.string.rain_chance), "${rainChance?.roundToInt() ?: 0}%", stringResource(R.string.next_24h_short)) to wc.rain,
        Triple(stringResource(R.string.rain_today), "%.1f".format(rainToday), stringResource(R.string.unit_mm)) to wc.rain,
        Triple(stringResource(R.string.heat_stress), heatLabel(h.heat), "") to null,
        Triple(stringResource(R.string.pm10), h.pm10?.roundToInt()?.toString() ?: "–", stringResource(R.string.unit_ugm3)) to null,
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (t, color) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                    ) {
                        Text(t.first, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(t.second, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color ?: Color.Unspecified, maxLines = 1)
                        Text(t.third, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun uvLabel(uv: Double?): String = stringResource(
    when {
        uv == null || uv < 3 -> R.string.uv_low
        uv < 6 -> R.string.uv_moderate
        uv < 8 -> R.string.uv_high
        uv < 11 -> R.string.uv_very_high
        else -> R.string.uv_extreme
    },
)

@Composable
private fun heatLabel(level: Int?): String = stringResource(
    when (level ?: 0) {
        0 -> R.string.heat_none
        1 -> R.string.heat_light
        2 -> R.string.heat_moderate
        3 -> R.string.heat_heavy
        else -> R.string.heat_extreme
    },
)

// ---- Radar teaser ----

@Composable
fun RadarCard(place: Place?, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    CardBox(Modifier.height(220.dp).clickable(onClick = onOpen)) {
        RadarPreview(place, Modifier.fillMaxWidth().height(220.dp))
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp)
                .clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainer.copy(alpha = 0.92f)).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.ims_radar), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.clip(RoundedCornerShape(22.dp)).background(cs.primary).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.play), color = cs.onPrimary, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// ---- IMS's national text forecast ----

@Composable
fun SummaryCard(f: Forecast) {
    val text = f.country.get(isEnglish()) ?: return
    CardBox {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.country_forecast), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
