# Israel Weather

An Android weather app for Israel, built on data from the **Israel Meteorological Service (IMS)**.
Kotlin + Jetpack Compose (Material 3 Expressive), Hebrew-first (RTL) with English.

- Forecast for your location (snapped to the nearest of IMS's 175 forecast points) and saved places
- Hourly chart, 7 days, details (UV, gusts, rain, heat stress, PM10), IMS national forecast
- IMS warnings for your places, animated IMS rain radar over OpenStreetMap
- Editable forecast screen (reorder / hide cards), units, theme, accent, language
- Home-screen widgets: hourly 4×2, daily 4×2, daily strip 4×1, alerts & rain 4×1 — each with
  its own place and an optional refresh button (long-press → widget settings)
- Background refresh: 15 min / 30 min / 1 h / 3 h / off

## Layout

- `app/` — the Android app (single module; packages `data`, `location`, `ui`, `widget`)
- `proxy/` — a Cloudflare Worker that fetches IMS data, trims it to compact JSON and caches it
  at the edge. It also holds the IMS Envista token (live station readings) as a secret.

## Proxy

```
cd proxy
npm install
npx wrangler dev          # local, http://localhost:8787
npx wrangler deploy
npx wrangler secret put IMS_TOKEN   # optional: enables /v1/now (live station readings)
```

Endpoints: `/v1/locations`, `/v1/forecast/{id}`, `/v1/warnings`, `/v1/radar`, `/v1/now/{id}`.

The release build uses `proxyUrl` from `gradle.properties`; debug builds can override it in
`local.properties` (e.g. `proxyUrl=http://10.0.2.2:8787` for the emulator).

## Build

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease   # needs key.properties + the keystore at the repo root (gitignored)
```

### Widget picker previews

Launchers that support Android 15's generated previews (Pixel) show each widget's live
layout; others (e.g. Samsung) show the static images in `res/drawable-nodpi` (Hebrew) and
`res/drawable-en-nodpi` (English). After changing a widget layout, regenerate them with the
debug build: launch the app (it publishes the generated previews), then
`adb shell am start -n com.bennybar.israelweather/.PreviewExportActivity` and pull
`files/previews/*.webp` with `adb exec-out run-as com.bennybar.israelweather cat …`.

Data: Israel Meteorological Service. Map tiles: © OpenStreetMap contributors.
