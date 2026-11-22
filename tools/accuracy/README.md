# Forecast accuracy

Grades forecast sources against what Israeli airports actually measured.

- `backtest.py` grades every model Open-Meteo archives (what each forecast 1, 2 and 3 days
  ahead) over the past year, plus whatever `logger.py` has recorded. Writes `results.json`.
- `logger.py` records IMS, Foreca, and ECMWF and best match (as the app gets them) three times a day (each source once per day), since IMS and
  Foreca keep no forecast history.

Run them with the system Python (`/usr/bin/python3`): Python.org's build can't verify HTTPS
certificates on this Mac.

## Data

- **Observations**: METAR reports from the Iowa Environmental Mesonet
  (Ben Gurion, Haifa, Rosh Pina, Ramon). Only Ben Gurion reports around the clock; every
  comparison uses just the hours a station reported.
- **Rain**: Israeli METARs carry no amounts, so a day is wet when any report shows rain,
  drizzle, showers or thunder. A forecast says wet at 0.5 mm or more over the same hours.
- **Grades**: temperature (daily high and low error) 50%, rain (critical success index)
  30%, wind (hourly speed error) 20%. Scales are in `backtest.py` (`SCALES`).

## Daily logging

A launchd job runs `logger.py` at 06:00 (or on wake) and saves to
`~/Library/Application Support/IsraelWeather/accuracy/`:

    ~/Library/LaunchAgents/com.bennybar.israelweather.accuracy.plist

The Foreca key lives in `~/.config/israelweather/foreca_key` (mode 600), never in the repo.
Stop logging with `launchctl unload` on the plist.
