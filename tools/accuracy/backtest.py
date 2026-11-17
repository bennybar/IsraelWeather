#!/usr/bin/python3
"""Grades weather models against Israeli airport observations.

Observations: METAR reports from the Iowa Environmental Mesonet archive (no key needed).
Forecasts: Open-Meteo's Previous Runs API, i.e. what each model forecast 1, 2 and 3 days
before each hour. Every comparison uses only the hours a station actually reported, so
airports that report part of the day are graded fairly.

    python3 tools/accuracy/backtest.py [--days 365] [--out results.json]

Also grades the sources recorded by logger.py (IMS, Foreca, ECMWF as the app gets it) when
--logs points at its folder.
"""
import argparse
import collections
import csv
import hashlib
import datetime as dt
import io
import json
import os
import statistics
import time
import urllib.parse
import urllib.request

STATIONS = {
    # ICAO: (name, lat, lon)
    "LLBG": ("Ben Gurion", 32.0114, 34.8867),
    "LLHA": ("Haifa", 32.8094, 35.0431),
    "LLIB": ("Rosh Pina", 32.981, 35.5719),
    "LLER": ("Ramon (Eilat)", 29.727, 34.998),
}

MODELS = {
    "ecmwf_ifs": "ECMWF IFS 9 km (the app's ECMWF)",
    "ecmwf_ifs025": "ECMWF IFS 25 km",
    "ecmwf_aifs025_single": "ECMWF AIFS (AI)",
    "icon_global": "DWD ICON",
    "gfs_global": "NOAA GFS",
    "meteofrance_arpege_world": "Météo-France ARPEGE",
    "ukmo_global_deterministic_10km": "UK Met Office",
    "jma_gsm": "JMA",
    "gem_global": "Canada GEM",
    "best_match": "Open-Meteo best match",
}

LEADS = (1, 2, 3)
RAIN_MM = 0.5  # forecast counts as "rain" from this total over the reported hours
RAIN_CODES = ("RA", "DZ", "SH", "TS")
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")


def get(url, retries=4):
    os.makedirs(CACHE, exist_ok=True)
    key = os.path.join(CACHE, hashlib.md5(url.encode()).hexdigest() + ".txt")
    if os.path.exists(key):
        return open(key, encoding="utf-8").read()
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "IsraelWeather-accuracy/1.0"})
            body = urllib.request.urlopen(req, timeout=120).read().decode("utf-8")
            open(key, "w", encoding="utf-8").write(body)
            return body
        except Exception:
            if attempt == retries - 1:
                raise
            time.sleep(5 * (attempt + 1))


def observations(icao, start, end):
    """{(day, hour): {"t": °C, "w": km/h, "rain": bool}} from the station's METARs (local time)."""
    q = urllib.parse.urlencode([
        ("station", icao), ("data", "tmpc"), ("data", "sknt"), ("data", "wxcodes"),
        ("year1", start.year), ("month1", start.month), ("day1", start.day),
        ("year2", end.year), ("month2", end.month), ("day2", end.day),
        ("tz", "Asia/Jerusalem"), ("format", "onlycomma"), ("missing", "M"),
        ("report_type", 3), ("report_type", 4),
    ])
    rows = csv.DictReader(io.StringIO(get("https://mesonet.agron.iastate.edu/cgi-bin/request/asos.py?" + q)))
    hours = {}
    for r in rows:
        day, hour = r["valid"][:10], int(r["valid"][11:13])
        o = hours.setdefault((day, hour), {"t": [], "w": [], "rain": False})
        if r["tmpc"] != "M":
            o["t"].append(float(r["tmpc"]))
        if r["sknt"] != "M":
            o["w"].append(float(r["sknt"]) * 1.852)
        if any(c in r["wxcodes"] for c in RAIN_CODES):
            o["rain"] = True
    return {k: {"t": statistics.mean(v["t"]) if v["t"] else None,
                "w": statistics.mean(v["w"]) if v["w"] else None,
                "rain": v["rain"]} for k, v in hours.items()}


def model_forecasts(model, lat, lon, start, end):
    """{lead: {(day, hour): (temp, precip, wind)}} for leads 1-3."""
    fields = []
    for lead in LEADS:
        fields += [f"temperature_2m_previous_day{lead}", f"precipitation_previous_day{lead}", f"wind_speed_10m_previous_day{lead}"]
    out = {lead: {} for lead in LEADS}
    # The API serves up to a few months per call comfortably: go quarter by quarter.
    a = start
    while a <= end:
        b = min(a + dt.timedelta(days=90), end)
        q = urllib.parse.urlencode({
            "latitude": lat, "longitude": lon, "hourly": ",".join(fields), "models": model,
            "start_date": a.isoformat(), "end_date": b.isoformat(), "timezone": "Asia/Jerusalem",
        })
        d = json.loads(get("https://previous-runs-api.open-meteo.com/v1/forecast?" + q))
        if "hourly" in d:
            h = d["hourly"]
            for i, t in enumerate(h["time"]):
                key = (t[:10], int(t[11:13]))
                for lead in LEADS:
                    out[lead][key] = (h[f"temperature_2m_previous_day{lead}"][i],
                                      h[f"precipitation_previous_day{lead}"][i],
                                      h[f"wind_speed_10m_previous_day{lead}"][i])
        a = b + dt.timedelta(days=1)
    return out


def score(obs, fc):
    """Errors of one source at one station, given {(day, hour): (temp, precip, wind)}."""
    by_day = collections.defaultdict(list)
    for (day, hour), o in obs.items():
        if (day, hour) in fc:
            by_day[day].append((hour, o, fc[(day, hour)]))
    hi, lo, wind = [], [], []
    hits = misses = false_alarms = dry_ok = 0
    for day, rows in by_day.items():
        pairs = [(o["t"], f[0]) for _, o, f in rows if o["t"] is not None and f[0] is not None]
        # A day counts when it has most of its daytime hours (the high) and some early hours (the low).
        day_hours = {h for h, o, f in rows if o["t"] is not None}
        if len(day_hours & set(range(10, 17))) >= 5 and pairs:
            hi.append(abs(max(p[1] for p in pairs) - max(p[0] for p in pairs)))
        if len(day_hours & set(range(3, 8))) >= 3 and pairs:
            early = [(o["t"], f[0]) for h, o, f in rows if h <= 9 and o["t"] is not None and f[0] is not None]
            if early:
                lo.append(abs(min(p[1] for p in early) - min(p[0] for p in early)))
        wind += [abs(f[2] - o["w"]) for _, o, f in rows if o["w"] is not None and f[2] is not None]
        if len(rows) >= 10:
            observed = any(o["rain"] for _, o, f in rows)
            forecast = sum(f[1] or 0 for _, o, f in rows) >= RAIN_MM
            hits += observed and forecast
            misses += observed and not forecast
            false_alarms += forecast and not observed
            dry_ok += not observed and not forecast
    return {"hi": hi, "lo": lo, "wind": wind, "rain": (hits, misses, false_alarms, dry_ok)}


def combine(parts):
    """Pools the station scores of one source and lead."""
    hi = sum((p["hi"] for p in parts), [])
    lo = sum((p["lo"] for p in parts), [])
    wind = sum((p["wind"] for p in parts), [])
    h, m, fa, ok = (sum(p["rain"][i] for p in parts) for i in range(4))
    return {
        "temp_mae": statistics.mean(hi + lo) if hi + lo else None,
        "high_mae": statistics.mean(hi) if hi else None,
        "low_mae": statistics.mean(lo) if lo else None,
        "wind_mae": statistics.mean(wind) if wind else None,
        "rain_csi": h / (h + m + fa) if h + m + fa else None,
        "rain_pod": h / (h + m) if h + m else None,
        "rain_far": fa / (h + fa) if h + fa else None,
        "rain_days": h + m, "days": len(hi), "hours": len(wind),
    }


# Grades: points 4 (A) to 0 (F), interpolated between the thresholds below.
SCALES = {
    "temp_mae": [1.0, 1.4, 1.8, 2.3, 3.0],   # °C: A at or under 1.0 … F at 3.0
    "wind_mae": [4.0, 5.5, 7.0, 9.0, 12.0],  # km/h
    "rain_csi": [0.65, 0.55, 0.45, 0.35, 0.2],  # higher is better
}
WEIGHTS = {"temp_mae": 0.5, "rain_csi": 0.3, "wind_mae": 0.2}


def points(metric, v):
    if v is None:
        return None
    s = SCALES[metric]
    better_low = s[0] < s[-1]
    for i in range(len(s) - 1):
        a, b = s[i], s[i + 1]
        inside = a <= v <= b if better_low else b <= v <= a
        if (v <= a if better_low else v >= a):
            return 4.0
        if inside:
            return 4 - i - (v - a) / (b - a)
    return 0.0


def letter(p):
    return "A" if p >= 3.5 else "A-" if p >= 3.2 else "B+" if p >= 2.9 else "B" if p >= 2.5 else "B-" if p >= 2.2 else \
        "C+" if p >= 1.9 else "C" if p >= 1.5 else "C-" if p >= 1.2 else "D" if p >= 0.6 else "F"


def grade(r):
    pts = {m: points(m, r[m]) for m in WEIGHTS}
    have = {m: p for m, p in pts.items() if p is not None}
    total = sum(have[m] * WEIGHTS[m] for m in have) / sum(WEIGHTS[m] for m in have)
    return round(total, 2), letter(total), {m: (letter(p) if p is not None else None) for m, p in pts.items()}


def logged_forecasts(folder, source, icao):
    """{lead: {(day, hour): (temp, precip, wind)}} from logger.py's files for one source and station."""
    out = {lead: {} for lead in LEADS}
    if not folder or not os.path.isdir(folder):
        return out
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".json"):
            continue
        rec = json.load(open(os.path.join(folder, name), encoding="utf-8"))
        if rec.get("source") != source or rec.get("station") != icao:
            continue
        issued = dt.date.fromisoformat(rec["issued"][:10])
        for h in rec["hours"]:
            day = dt.date.fromisoformat(h["t"][:10])
            lead = (day - issued).days
            if lead in out:
                out[lead][(h["t"][:10], int(h["t"][11:13]))] = (h.get("temp"), h.get("rain"), h.get("wind"))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--days", type=int, default=365)
    ap.add_argument("--out", default="results.json")
    ap.add_argument("--logs", default=os.path.expanduser("~/Library/Application Support/IsraelWeather/accuracy"))
    args = ap.parse_args()
    end = dt.date.today() - dt.timedelta(days=2)
    start = end - dt.timedelta(days=args.days)

    obs = {s: observations(s, start, end) for s in STATIONS}
    sources = {m: ("model", MODELS[m]) for m in MODELS}
    for src, name in (("ims", "IMS (logged)"), ("foreca", "Foreca (logged)"), ("app_ecmwf", "ECMWF as the app gets it (logged)")):
        sources[src] = ("log", name)

    results = {}
    for src, (kind, name) in sources.items():
        per_lead = {lead: [] for lead in LEADS}
        per_station = {}
        for icao, (_, lat, lon) in STATIONS.items():
            fc = model_forecasts(src, lat, lon, start, end) if kind == "model" else logged_forecasts(args.logs, src, icao)
            for lead in LEADS:
                if fc[lead]:
                    sc = score(obs[icao], fc[lead])
                    per_lead[lead].append(sc)
                    if lead == 1:
                        per_station[icao] = combine([sc])
        leads = {lead: combine(parts) for lead, parts in per_lead.items() if parts}
        if not leads or all(r["days"] == 0 for r in leads.values()):
            continue
        overall = combine(sum(per_lead.values(), []))
        results[src] = {"name": name, "kind": kind, "overall": overall, "grade": grade(overall),
                        "leads": {l: {**r, "grade": grade(r)} for l, r in leads.items()}, "stations": per_station}
        print(f"{name:40s} {results[src]['grade'][1]:3s} temp {overall['temp_mae']:.2f}°C  wind {overall['wind_mae'] or 0:.1f} km/h  rain CSI {overall['rain_csi'] or 0:.2f}")

    json.dump({"start": start.isoformat(), "end": end.isoformat(), "stations": STATIONS, "results": results},
              open(args.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
