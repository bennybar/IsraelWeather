// Israel Weather proxy (Cloudflare Worker).
//
// Fetches IMS (Israel Meteorological Service) data, trims it to what the app
// needs and caches it at the edge, so phones and widgets download a few KB
// instead of IMS's full payloads. The Envista token (live station readings)
// lives here as a secret and never ships in the APK.

const IMS = 'https://ims.gov.il';
const ENVISTA = 'https://api.ims.gov.il/v1/envista';
const UA = 'IsraelWeatherProxy/1.0 (+https://github.com/bennybar/IsraelWeather)';

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const parts = url.pathname.split('/').filter(Boolean); // ["v1", route, arg]
    if (request.method !== 'GET' || parts[0] !== 'v1') return json({ error: 'not_found' }, 404);

    // Whole responses are cached at the edge for `ttl` seconds.
    const cache = caches.default;
    const hit = await cache.match(request);
    if (hit) return hit;

    let body, ttl;
    try {
      switch (parts[1]) {
        case 'locations': [body, ttl] = [await locations(), 86400]; break;
        case 'forecast': [body, ttl] = [await forecast(parts[2]), 1800]; break;
        case 'warnings': [body, ttl] = [await warnings(), 300]; break;
        case 'radar': [body, ttl] = [await radar(), 120]; break;
        case 'now': [body, ttl] = [await now(env, parts[2]), 300]; break;
        default: return json({ error: 'not_found' }, 404);
      }
    } catch (e) {
      const status = e.status || 502;
      return json({ error: e.code || 'upstream', message: String(e.message || e) }, status);
    }
    const res = json(body, 200, ttl);
    ctx.waitUntil(cache.put(request, res.clone()));
    return res;
  },
};

function json(body, status = 200, ttl = 0) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': ttl ? `public, max-age=${ttl}` : 'no-store',
    },
  });
}

function fail(code, status, message) {
  return Object.assign(new Error(message || code), { code, status });
}

async function imsJson(path, ttl) {
  const res = await fetch(IMS + path, { headers: { 'user-agent': UA }, cf: { cacheTtl: ttl, cacheEverything: true } });
  if (!res.ok) throw fail('upstream', 502, `IMS ${path} → ${res.status}`);
  return (await res.json()).data;
}

const num = (v) => (v === null || v === undefined || v === '' ? null : Number(v));

// ---- locations: the 175 IMS forecast points, Hebrew + English names ----
async function locations() {
  const [he, en] = await Promise.all([imsJson('/he/locations_info', 86400), imsJson('/en/locations_info', 86400)]);
  return {
    locations: Object.values(he).map((l) => ({
      id: Number(l.lid),
      he: l.name.trim(),
      en: (en[l.lid]?.name || l.name).trim(),
      lat: num(l.lat),
      lon: num(l.lon),
      rid: Number(l.rid),
    })),
  };
}

// ---- forecast: hourly + daily for one location ----
async function forecast(id) {
  if (!/^\d+$/.test(id || '')) throw fail('bad_id', 400);
  const [he, en] = await Promise.all([imsJson(`/he/full_forecast_data/${id}`, 1800), imsJson(`/en/full_forecast_data/${id}`, 1800)]);
  const days = Object.keys(he).sort();
  if (!days.length) throw fail('unknown_location', 404);
  const hourly = [];
  const daily = [];
  for (const day of days) {
    const d = he[day];
    for (const h of Object.values(d.hourly || {})) {
      hourly.push({
        t: h.forecast_time.slice(0, 16), // local Israel time, "YYYY-MM-DD HH:mm"
        code: Number(h.weather_code),
        temp: num(h.precise_temperature) ?? num(h.temperature),
        rainChance: num(h.rain_chance),
        rain: num(h.rain),
        rh: num(h.relative_humidity),
        wind: num(h.wind_speed),
        gust: num(h.gust_speed),
        // IMS direction ids are 16 compass points, 1 = north, clockwise.
        windDir: h.wind_direction_id ? (Number(h.wind_direction_id) - 1) * 22.5 : null,
        uv: num(h.u_v_index),
        heat: num(h.heat_stress_level),
        pm10: num(h.pm10),
      });
    }
    if (d.daily) {
      daily.push({
        date: day,
        code: Number(d.daily.weather_code),
        min: num(d.daily.minimum_temperature),
        max: num(d.daily.maximum_temperature),
        uv: num(d.daily.maximum_uvi),
      });
    }
  }
  hourly.sort((a, b) => (a.t < b.t ? -1 : 1));
  const today = he[days[0]].country;
  return {
    id: Number(id),
    issued: today?.created || null,
    country: { he: today?.description?.trim() || null, en: en[days[0]]?.country?.description?.trim() || null },
    hourly,
    daily,
  };
}

// ---- warnings: every active IMS warning once, with the regions it covers ----
const LEVEL = { 0: 'green', 2: 'yellow', 3: 'yellow', 4: 'orange', 5: 'orange', 6: 'red', 7: 'red', 8: 'gray' };
const RANK = { red: 4, orange: 3, yellow: 2, gray: 1, green: 0 };

async function warnings() {
  const [data, metaHe, metaEn] = await Promise.all([
    imsJson('/he/warnings', 300),
    imsJson('/he/warnings_metadata', 86400),
    imsJson('/en/warnings_metadata', 86400),
  ]);
  const seen = new Map();
  for (const regions of Object.values(data.full_warnings_data || {})) {
    for (const byId of Object.values(regions)) {
      for (const w of Object.values(byId)) {
        if (seen.has(w.wid)) continue;
        const type = w.warning_type_id;
        seen.set(w.wid, {
          id: Number(w.wid),
          severity: Number(w.severity_id),
          level: LEVEL[w.severity_id] || 'gray',
          early: w.severity_id === '2' || w.severity_id === '5' || w.severity_id === '6',
          type: Number(type),
          typeName: { he: metaHe.ims_warning_type?.[type]?.name || null, en: metaEn.ims_warning_type?.[type]?.name || null },
          sent: w.sent?.slice(0, 16) || null,
          from: w.valid_from?.slice(0, 16) || null,
          to: w.valid_to?.slice(0, 16) || null,
          he: (w.full_he || '').replace(/\s+/g, ' ').trim(),
          en: (w.full_en || '').replace(/\s+/g, ' ').trim(),
          regions: (w.regions || []).map(Number),
        });
      }
    }
  }
  return { warnings: [...seen.values()].sort((a, b) => RANK[b.level] - RANK[a.level] || (a.from < b.from ? -1 : 1)) };
}

// ---- radar: the last ~2h of IMS radar frames (transparent PNG overlays) ----
async function radar() {
  const d = await imsJson('/he/radar_satellite', 120);
  const frames = (d.types?.IMSRadar || []).map((f) => ({ t: f.forecast_time.slice(0, 16), url: IMS + f.file_name }));
  // Corners IMS's own Leaflet map uses for these images.
  return { bounds: { south: 29.4468656950938161, west: 31.7662931774178858, north: 34.5318966003582659, east: 37.8648132475530090 }, frames };
}

// ---- now: latest reading from the IMS station nearest a location ----
async function now(env, id) {
  if (!env.IMS_TOKEN) throw fail('no_token', 503, 'IMS_TOKEN secret is not set');
  if (!/^\d+$/.test(id || '')) throw fail('bad_id', 400);
  const loc = (await locations()).locations.find((l) => l.id === Number(id));
  if (!loc) throw fail('unknown_location', 404);
  const headers = { authorization: `ApiToken ${env.IMS_TOKEN}`, 'user-agent': UA };
  const stationsRes = await fetch(`${ENVISTA}/stations`, { headers, cf: { cacheTtl: 86400, cacheEverything: true } });
  if (!stationsRes.ok) throw fail('upstream', 502, `Envista stations → ${stationsRes.status}`);
  const stations = (await stationsRes.json()).filter((s) => s.active && s.location?.latitude);
  const dist = (s) => (s.location.latitude - loc.lat) ** 2 + ((s.location.longitude - loc.lon) * Math.cos((loc.lat * Math.PI) / 180)) ** 2;
  const station = stations.sort((a, b) => dist(a) - dist(b))[0];
  if (!station) throw fail('no_station', 404);
  const dataRes = await fetch(`${ENVISTA}/stations/${station.stationId}/data/latest`, { headers });
  if (!dataRes.ok) throw fail('upstream', 502, `Envista data → ${dataRes.status}`);
  const row = (await dataRes.json()).data?.[0];
  const ch = Object.fromEntries((row?.channels || []).filter((c) => c.valid).map((c) => [c.name, c.value]));
  return {
    station: { id: station.stationId, name: station.name },
    t: row?.datetime || null,
    temp: ch.TD ?? null,
    rh: ch.RH ?? null,
    wind: ch.WS != null ? ch.WS * 3.6 : null, // m/s → km/h
    gust: ch.WSmax != null ? ch.WSmax * 3.6 : null,
    windDir: ch.WD ?? null,
    rain: ch.Rain ?? null,
  };
}
