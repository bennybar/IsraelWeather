#!/usr/bin/python3
"""Records today's forecasts from the sources that keep no history (IMS, Foreca) and ECMWF as the
app gets it, for the airports backtest.py grades. Run once a day (launchd: see README.md).

Each record is one JSON file: {source, station, issued, hours: [{t, temp, rain, wind}]}, with
rain in mm; IMS gives only a chance, so its hours carry 1 mm at 50% or more, else 0.
The Foreca key is read from ~/.config/israelweather/foreca_key (never from the repo).
"""
import datetime as dt
import json
import math
import os
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from backtest import STATIONS  # noqa: E402

OUT = os.path.expanduser("~/Library/Application Support/IsraelWeather/accuracy")
PROXY = "https://israel-weather-proxy.israel-weather-proxy.workers.dev"
KEY_FILE = os.path.expanduser("~/.config/israelweather/foreca_key")


def fetch(url, headers=None):
    req = urllib.request.Request(url, headers={"User-Agent": "IsraelWeather-accuracy/1.0", **(headers or {})})
    return json.loads(urllib.request.urlopen(req, timeout=60).read().decode("utf-8"))


def km(lat1, lon1, lat2, lon2):
    k = math.cos(math.radians(lat1))
    return math.hypot(lat2 - lat1, (lon2 - lon1) * k) * 111


def ims(lat, lon, places):
    p = min(places, key=lambda p: km(lat, lon, p["lat"], p["lon"]))
    f = fetch(f"{PROXY}/v1/forecast/{p['id']}")
    hours = [{"t": h["t"], "temp": h.get("temp"), "wind": h.get("wind"),
              "rain": h["rain"] if h.get("rain") is not None else (1.0 if (h.get("rainChance") or 0) >= 50 else 0.0)}
             for h in f.get("hourly", [])]
    return hours, {"ims_point": p["en"], "km": round(km(lat, lon, p["lat"], p["lon"]), 1)}


def foreca(lat, lon, key):
    q = urllib.parse.urlencode({"tempunit": "C", "windunit": "KMH", "tz": "Asia/Jerusalem", "periods": 168, "dataset": "full"})
    f = fetch(f"https://weatherapi.foreca.net/api/v1/forecast/hourly/{lon:.3f},{lat:.3f}?{q}", {"Authorization": f"Bearer {key}"})
    return [{"t": h["time"][:16].replace("T", " "), "temp": h.get("temperature"), "wind": h.get("windSpeed"),
             "rain": h.get("precipAccum")} for h in f.get("forecast", [])], {}


def combined(lat, lon):
    """Best Combined as the app gets it: temperature best match + AIFS, rain GFS + ARPEGE + ICON,
    wind GFS + ARPEGE, each averaged over the models that cover the hour."""
    q = urllib.parse.urlencode({"latitude": lat, "longitude": lon, "hourly": "temperature_2m,precipitation,wind_speed_10m",
                                "models": "best_match,ecmwf_aifs025_single,gfs_global,meteofrance_arpege_world,icon_global",
                                "timezone": "Asia/Jerusalem", "forecast_days": 5})
    h = fetch("https://api.open-meteo.com/v1/forecast?" + q)["hourly"]

    def mean(field, models, i):
        v = [h[f"{field}_{m}"][i] for m in models if h.get(f"{field}_{m}") and h[f"{field}_{m}"][i] is not None]
        return sum(v) / len(v) if v else h[f"{field}_best_match"][i]
    return [{"t": t.replace("T", " "),
             "temp": mean("temperature_2m", ["best_match", "ecmwf_aifs025_single"], i),
             "rain": mean("precipitation", ["gfs_global", "meteofrance_arpege_world", "icon_global"], i),
             "wind": mean("wind_speed_10m", ["gfs_global", "meteofrance_arpege_world"], i)}
            for i, t in enumerate(h["time"])], {}


def open_meteo(lat, lon, model):
    """An Open-Meteo model as the app gets it: ECMWF (ecmwf_ifs) or best match (best_match)."""
    q = urllib.parse.urlencode({"latitude": lat, "longitude": lon, "hourly": "temperature_2m,precipitation,wind_speed_10m",
                                "models": model, "timezone": "Asia/Jerusalem", "forecast_days": 5})
    h = fetch("https://api.open-meteo.com/v1/forecast?" + q)["hourly"]
    return [{"t": t.replace("T", " "), "temp": h["temperature_2m"][i], "wind": h["wind_speed_10m"][i], "rain": h["precipitation"][i]}
            for i, t in enumerate(h["time"])], {}


def main():
    os.makedirs(OUT, exist_ok=True)
    today = dt.date.today().isoformat()
    places = fetch(f"{PROXY}/v1/locations")["locations"]
    key = open(KEY_FILE).read().strip() if os.path.exists(KEY_FILE) else None
    for icao, (_, lat, lon) in STATIONS.items():
        jobs = {"ims": lambda: ims(lat, lon, places), "app_ecmwf": lambda: open_meteo(lat, lon, "ecmwf_ifs"),
                "app_best": lambda: open_meteo(lat, lon, "best_match"),
                "app_combined": lambda: combined(lat, lon)}
        if key:
            jobs["foreca"] = lambda: foreca(lat, lon, key)
        for source, job in jobs.items():
            path = os.path.join(OUT, f"{today}_{source}_{icao}.json")
            if os.path.exists(path):
                continue
            try:
                try:
                    hours, extra = job()
                except Exception:
                    time.sleep(30)  # timeouts happen: one more try before giving up until the next run
                    hours, extra = job()
                json.dump({"source": source, "station": icao, "issued": dt.datetime.now().isoformat(timespec="minutes"),
                           "hours": hours, **extra}, open(path, "w", encoding="utf-8"))
                print("saved", os.path.basename(path), len(hours), "hours")
            except Exception as e:  # one failed source must not stop the others
                print("failed", source, icao, e, file=sys.stderr)


if __name__ == "__main__":
    main()
