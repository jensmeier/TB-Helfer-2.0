"""Read-only Open-Meteo suggestions. Saving stays in versioned BTB header API."""
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo
import json
import math
import os
from threading import Lock
from time import monotonic
from urllib.parse import urlencode
from urllib.request import urlopen

_CACHE = {}
_LOCK = Lock()
_BERLIN = ZoneInfo('Europe/Berlin')


def _get(service, params):
    key = os.environ.get('TB_WEATHER_OPEN_METEO_API_KEY', '').strip()
    hosts = {'locations': 'geocoding-api', 'forecast': 'api', 'archive': 'archive-api'}
    host = ('customer-' if key else '') + hosts[service] + '.open-meteo.com'
    path = '/v1/search' if service == 'locations' else '/v1/' + service
    query = dict(params)
    if key:
        query['apikey'] = key
    url = 'https://' + host + path + '?' + urlencode(query)
    with _LOCK:
        cached = _CACHE.get(url)
        if cached and cached[0] > monotonic():
            return cached[1]
    with urlopen(url, timeout=8) as response:
        raw = response.read(1_000_001)
    if len(raw) > 1_000_000:
        raise ValueError('Wetterantwort ist zu groß.')
    data = json.loads(raw)
    if not isinstance(data, dict) or data.get('error'):
        raise ValueError('Wetterdienst konnte die Anfrage nicht beantworten.')
    with _LOCK:
        if len(_CACHE) >= 128:
            _CACHE.clear()
        _CACHE[url] = (monotonic() + 900, data)
    return data


def locations(query):
    data = _get('locations', {'name': query, 'count': 5, 'language': 'de', 'format': 'json'})
    return [{'label': ', '.join(str(r[k]) for k in ('name', 'admin1', 'country') if r.get(k)),
             'lat': r['latitude'], 'lon': r['longitude']}
            for r in data.get('results', []) if 'latitude' in r and 'longitude' in r]


def local_datetime(value):
    if isinstance(value, str):
        value = datetime.fromisoformat(value.replace('Z', '+00:00'))
    if value.tzinfo is None:
        return value.replace(tzinfo=_BERLIN)
    return value.astimezone(_BERLIN)


def sample_times(start, end):
    start, end = local_datetime(start), local_datetime(end)
    if end <= start or end - start > timedelta(days=2):
        raise ValueError('Bitte eine Berichtszeit mit Ende nach Beginn und höchstens 48 Stunden speichern.')
    span = (end - start).total_seconds()
    result = []
    for fraction in (1/6, 1/2, 5/6):
        point = start + timedelta(seconds=span * fraction)
        minute = point.hour * 60 + point.minute + point.second / 60
        rounded = math.ceil(minute / 15) * 15
        point = point.replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(minutes=rounded)
        result.append(point)
    return result


def cloud_label(percent):
    if percent is None or not math.isfinite(percent) or not 0 <= percent <= 100:
        return None
    for limit, label in ((5, 'sonnig / wolkenlos / klar'), (25, 'heiter'), (50, 'leicht bewölkt'),
                         (75, 'wolkig'), (90, 'stark bewölkt')):
        if percent <= limit:
            return label
    return 'bedeckt'


def rain_label(code, amount):
    if amount is not None and (not math.isfinite(amount) or amount < 0):
        amount = None
    if amount == 0:
        return 'trocken'
    labels = {51: 'Nieselregen', 53: 'Nieselregen', 55: 'Nieselregen',
              56: 'gefrierender Regen', 57: 'gefrierender Regen', 66: 'gefrierender Regen', 67: 'gefrierender Regen',
              61: 'Regen (leicht)', 63: 'Regen (mäßig)', 65: 'Regen (stark)',
              80: 'Schauer', 81: 'Schauer', 82: 'Schauer', 68: 'Schneeregen', 69: 'Schneeregen',
              77: 'Graupel', 71: 'Schneefall (leicht)', 73: 'Schneefall (mäßig)', 75: 'Schneefall (stark)',
              85: 'Schneefall (leicht)', 86: 'Schneefall (stark)', 95: 'Gewitter', 96: 'Hagel', 99: 'Hagel'}
    if code in labels:
        return labels[code]
    if amount is None:
        return None
    return 'Nieselregen' if amount < 0.3 else 'Regen (leicht)' if amount < 1.5 else 'Regen (mäßig)' if amount < 4 else 'Regen (stark)'


def weather_suggestion(lat, lon, start, end):
    points = sample_times(start, end)
    today = datetime.now(_BERLIN).date()
    first, last = min(p.date() for p in points), max(p.date() for p in points)
    if first < today - timedelta(days=5):
        service = 'archive'
    else:
        service = 'forecast'
    params = {'latitude': lat, 'longitude': lon, 'hourly': 'temperature_2m,cloud_cover,precipitation,weather_code',
              'start_date': first.isoformat(), 'end_date': last.isoformat(), 'timezone': 'Europe/Berlin', 'timeformat': 'unixtime'}
    data = _get(service, params)
    hourly = data.get('hourly', {})
    times = hourly.get('time') or []
    if not times:
        raise ValueError('Für diesen Zeitraum sind keine Wetterdaten verfügbar.')
    slots = []
    for point in points:
        index = min(range(len(times)), key=lambda i: abs(float(times[i]) - point.timestamp()))
        if abs(float(times[index]) - point.timestamp()) > 3600:
            raise ValueError('Für einen Zeitpunkt der Schicht fehlen Wetterdaten.')
        def field(name):
            values = hourly.get(name) or []
            return values[index] if index < len(values) else None
        temp, cloud, mm, code = field('temperature_2m'), field('cloud_cover'), field('precipitation'), field('weather_code')
        if temp is None:
            raise ValueError('Für einen Zeitpunkt fehlt die Temperatur; bitte manuell ergänzen.')
        slot = {'uhrzeit': point.strftime('%H:%M'), 'tempC': round(temp), 'bewoelkung': cloud_label(cloud)}
        if code is not None or mm is not None:
            slot['niederschlag'] = rain_label(code, mm)
        if mm is not None:
            slot['niederschlagMm'] = mm
        slots.append(slot)
    return {'source': 'AUTO', 'standEpochMs': int(datetime.now(timezone.utc).timestamp()*1000),
            'lat': lat, 'lon': lon, 'slots': slots, 'provider': 'Open-Meteo',
            'providerUrl': 'https://open-meteo.com/', 'dataset': service}

