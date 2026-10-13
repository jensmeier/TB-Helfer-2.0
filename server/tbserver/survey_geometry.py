from __future__ import annotations

import math
from typing import Any

SAMPLE_DISTANCE_M = 2.0
TRANSITION_STEP_M = 0.5

# Android DbRefKoordinatenwandler parity constants.
A_BESSEL = 6_377_397.155
INV_F_BESSEL = 299.1528128
A_GRS80 = 6_378_137.0
INV_F_GRS80 = 298.257222101
FALSE_NORTHING = 0.0
SCALE = 1.0
TX = 584.9636
TY = 107.7175
TZ = 413.8067
RX_ARCSEC = -1.1155
RY_ARCSEC = -0.2824
RZ_ARCSEC = 3.1384
SCALE_PPM = 7.9922


def _finite_number(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) else None


def _curvature(radius: Any) -> float:
    value = _finite_number(radius)
    if value is None or abs(value) < 0.0001:
        return 0.0
    return 1.0 / value


def _radius(node: dict[str, Any]) -> float | None:
    for key in ("radius_to_m", "radius_from_m"):
        value = _finite_number(node.get(key))
        if value is not None and abs(value) > 0.0001 and abs(value) < 1_000_000.0:
            return value
    return None


def _node_sample(node: dict[str, Any]) -> dict[str, float] | None:
    station = _finite_number(node.get("station_m"))
    x = _finite_number(node.get("x_dbref"))
    y = _finite_number(node.get("y_dbref"))
    direction = _finite_number(node.get("direction_deg"))
    if None in (station, x, y, direction):
        return None
    assert station is not None and x is not None and y is not None and direction is not None
    return {"station_m": station, "x_dbref": x, "y_dbref": y, "direction_deg": direction % 360.0}


def _sample_straight(start: dict[str, Any], distance: float) -> tuple[float, float, float]:
    theta = math.radians(float(start["direction_deg"]))
    x = float(start["x_dbref"]) + distance * math.sin(theta)
    y = float(start["y_dbref"]) + distance * math.cos(theta)
    return x, y, theta


def _sample_arc(start: dict[str, Any], distance: float) -> tuple[float, float, float]:
    radius = _radius(start)
    if radius is None or abs(radius) < 0.001:
        return _sample_straight(start, distance)
    theta0 = math.radians(float(start["direction_deg"]))
    theta = theta0 + distance / radius
    x = float(start["x_dbref"]) + radius * (math.cos(theta0) - math.cos(theta))
    y = float(start["y_dbref"]) + radius * (math.sin(theta) - math.sin(theta0))
    return x, y, theta


def _sample_transition(start: dict[str, Any], distance: float, length: float) -> tuple[float, float, float]:
    k0 = _curvature(start.get("radius_from_m"))
    k1 = _curvature(start.get("radius_to_m"))
    if abs(k0) < 1e-9 and abs(k1) < 1e-9:
        return _sample_straight(start, distance)

    x = float(start["x_dbref"])
    y = float(start["y_dbref"])
    theta = math.radians(float(start["direction_deg"]))
    walked = 0.0
    while walked < distance - 0.000001:
        ds = min(TRANSITION_STEP_M, distance - walked)
        middle = walked + ds * 0.5
        t = max(0.0, min(1.0, middle / length))
        k = k0 + (k1 - k0) * t
        theta_middle = theta + k * ds * 0.5
        x += ds * math.sin(theta_middle)
        y += ds * math.cos(theta_middle)
        theta += k * ds
        walked += ds
    return x, y, theta


def _sample_linear(start: dict[str, Any], end: dict[str, Any], t_raw: float) -> tuple[float, float, float]:
    t = max(0.0, min(1.0, t_raw))
    x = float(start["x_dbref"]) + (float(end["x_dbref"]) - float(start["x_dbref"])) * t
    y = float(start["y_dbref"]) + (float(end["y_dbref"]) - float(start["y_dbref"])) * t
    theta0 = math.radians(float(start["direction_deg"]))
    theta1 = math.radians(float(end["direction_deg"]))
    return x, y, theta0 + (theta1 - theta0) * t


def densify_tra_payload(payload: dict[str, Any], sample_distance_m: float = SAMPLE_DISTANCE_M) -> list[dict[str, float]]:
    """Port of Android TraDateiParser 2-m densification using parsed TRA nodes."""
    if not math.isfinite(sample_distance_m) or sample_distance_m <= 0.05:
        raise ValueError("INVALID_SAMPLE_DISTANCE")
    raw_nodes = payload.get("nodes") if isinstance(payload, dict) else None
    if not isinstance(raw_nodes, list) or not raw_nodes:
        return []

    nodes: list[dict[str, Any]] = []
    for raw in raw_nodes:
        sample = _node_sample(raw) if isinstance(raw, dict) else None
        if sample is None:
            return []
        nodes.append({**raw, **sample})
    if len(nodes) == 1:
        return [_node_sample(nodes[0])]  # type: ignore[list-item]

    samples: list[dict[str, float]] = []
    first = _node_sample(nodes[0])
    if first is None:
        return []
    samples.append(first)

    for start, end in zip(nodes, nodes[1:]):
        station_length = float(end["station_m"]) - float(start["station_m"])
        source_length = _finite_number(start.get("length_m")) or 0.0
        length = station_length if station_length > 0.001 else source_length if source_length > 0.001 else 0.0
        if length <= 0.001:
            endpoint = _node_sample(end)
            if endpoint is not None:
                samples.append(endpoint)
            continue

        distance = sample_distance_m
        while distance < length - 0.10:
            raw_type = int(start.get("raw_type") or 0)
            if raw_type == 0:
                x, y, theta = _sample_straight(start, distance)
            elif raw_type == 1:
                x, y, theta = _sample_arc(start, distance)
            elif raw_type == 2:
                x, y, theta = _sample_transition(start, distance, length)
            else:
                x, y, theta = _sample_linear(start, end, distance / length)
            samples.append(
                {
                    "station_m": float(start["station_m"]) + distance,
                    "x_dbref": x,
                    "y_dbref": y,
                    "direction_deg": math.degrees(theta) % 360.0,
                }
            )
            distance += sample_distance_m
        endpoint = _node_sample(end)
        if endpoint is not None:
            samples.append(endpoint)

    unique: list[dict[str, float]] = []
    seen_station_mm: set[int] = set()
    for sample in samples:
        key = round(sample["station_m"] * 1000.0)
        if key in seen_station_mm:
            continue
        seen_station_mm.add(key)
        unique.append(sample)
    return unique


def _inverse_transverse_mercator(easting: float, northing: float, false_easting: float, lon0_rad: float) -> tuple[float, float]:
    f = 1.0 / INV_F_BESSEL
    e2 = 2.0 * f - f * f
    e4 = e2 * e2
    e6 = e4 * e2
    ep2 = e2 / (1.0 - e2)
    meridional_arc = (northing - FALSE_NORTHING) / SCALE
    mu = meridional_arc / (A_BESSEL * (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0))
    e1 = (1.0 - math.sqrt(1.0 - e2)) / (1.0 + math.sqrt(1.0 - e2))
    e1_2 = e1 * e1
    e1_3 = e1_2 * e1
    e1_4 = e1_2 * e1_2
    phi1 = (
        mu
        + (3.0 * e1 / 2.0 - 27.0 * e1_3 / 32.0) * math.sin(2.0 * mu)
        + (21.0 * e1_2 / 16.0 - 55.0 * e1_4 / 32.0) * math.sin(4.0 * mu)
        + (151.0 * e1_3 / 96.0) * math.sin(6.0 * mu)
        + (1097.0 * e1_4 / 512.0) * math.sin(8.0 * mu)
    )
    sin_phi1 = math.sin(phi1)
    cos_phi1 = math.cos(phi1)
    tan_phi1 = math.tan(phi1)
    n1 = A_BESSEL / math.sqrt(1.0 - e2 * sin_phi1 * sin_phi1)
    r1 = A_BESSEL * (1.0 - e2) / (1.0 - e2 * sin_phi1 * sin_phi1) ** 1.5
    t1 = tan_phi1 * tan_phi1
    c1 = ep2 * cos_phi1 * cos_phi1
    d = (easting - false_easting) / (n1 * SCALE)
    lat_rad = phi1 - (n1 * tan_phi1 / r1) * (
        d * d / 2.0
        - (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ep2) * d**4 / 24.0
        + (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ep2 - 3.0 * c1 * c1) * d**6 / 720.0
    )
    lon_rad = lon0_rad + (
        d
        - (1.0 + 2.0 * t1 + c1) * d**3 / 6.0
        + (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ep2 + 24.0 * t1 * t1) * d**5 / 120.0
    ) / cos_phi1
    return lat_rad, lon_rad


def _geodetic_to_xyz(lat_rad: float, lon_rad: float, h: float, a: float, inverse_flattening: float) -> tuple[float, float, float]:
    f = 1.0 / inverse_flattening
    e2 = 2.0 * f - f * f
    sin_lat = math.sin(lat_rad)
    cos_lat = math.cos(lat_rad)
    n = a / math.sqrt(1.0 - e2 * sin_lat * sin_lat)
    return (
        (n + h) * cos_lat * math.cos(lon_rad),
        (n + h) * cos_lat * math.sin(lon_rad),
        (n * (1.0 - e2) + h) * sin_lat,
    )


def _helmert_dbref_to_etrs89(source: tuple[float, float, float]) -> tuple[float, float, float]:
    rx = math.radians(RX_ARCSEC / 3600.0)
    ry = math.radians(RY_ARCSEC / 3600.0)
    rz = math.radians(RZ_ARCSEC / 3600.0)
    scale = 1.0 + SCALE_PPM * 1e-6
    x, y, z = source
    return (
        TX + scale * (x + rz * y - ry * z),
        TY + scale * (-rz * x + y + rx * z),
        TZ + scale * (ry * x - rx * y + z),
    )


def _xyz_to_geodetic(x: float, y: float, z: float, a: float, inverse_flattening: float) -> tuple[float, float]:
    f = 1.0 / inverse_flattening
    e2 = 2.0 * f - f * f
    b = a * (1.0 - f)
    ep2 = (a * a - b * b) / (b * b)
    p = math.sqrt(x * x + y * y)
    theta = math.atan2(z * a, p * b)
    lon = math.atan2(y, x)
    lat = math.atan2(
        z + ep2 * b * math.sin(theta) ** 3,
        p - e2 * a * math.cos(theta) ** 3,
    )
    for _ in range(3):
        n = a / math.sqrt(1.0 - e2 * math.sin(lat) ** 2)
        h = p / math.cos(lat) - n
        lat = math.atan2(z, p * (1.0 - e2 * n / (n + h)))
    return lat, lon


def dbref_to_wgs84(x_dbref: Any, y_dbref: Any) -> tuple[float, float] | None:
    """Port of Android DbRefKoordinatenwandler for GK zones 3..5."""
    x = _finite_number(x_dbref)
    y = _finite_number(y_dbref)
    if x is None or y is None or not 4_000_000.0 <= y <= 6_500_000.0:
        return None
    zone = math.floor(x / 1_000_000.0)
    if zone not in (3, 4, 5):
        return None
    false_easting = zone * 1_000_000.0 + 500_000.0
    delta_e = x - false_easting
    if abs(delta_e) > 500_000.0:
        return None
    lat_db, lon_db = _inverse_transverse_mercator(x, y, false_easting, math.radians(zone * 3.0))
    xyz = _geodetic_to_xyz(lat_db, lon_db, 0.0, A_BESSEL, INV_F_BESSEL)
    ex, ey, ez = _helmert_dbref_to_etrs89(xyz)
    lat, lon = _xyz_to_geodetic(ex, ey, ez, A_GRS80, INV_F_GRS80)
    lat_deg, lon_deg = math.degrees(lat), math.degrees(lon)
    if not (math.isfinite(lat_deg) and math.isfinite(lon_deg)):
        return None
    if not (45.0 <= lat_deg <= 56.0 and 4.0 <= lon_deg <= 17.0):
        return None
    return lat_deg, lon_deg


def samples_to_wgs84(samples: list[dict[str, float]]) -> list[dict[str, float]]:
    result: list[dict[str, float]] = []
    for sample in samples:
        gps = dbref_to_wgs84(sample.get("x_dbref"), sample.get("y_dbref"))
        if gps is None:
            continue
        result.append({**sample, "lat": gps[0], "lon": gps[1]})
    return result


def projection_quality(distance_m: float) -> str:
    if distance_m <= 2.0:
        return "GRUEN"
    if distance_m <= 5.0:
        return "GELB"
    if distance_m <= 15.0:
        return "ORANGE"
    return "ROT"


def project_wgs84_to_samples(lat: float, lon: float, samples: list[dict[str, float]]) -> dict[str, float | str] | None:
    if not (math.isfinite(lat) and math.isfinite(lon)) or len(samples) < 2:
        return None
    m_per_deg_lat = 111_320.0
    m_per_deg_lon = max(1.0, 111_320.0 * math.cos(math.radians(lat)))
    best: dict[str, float | str] | None = None
    for a, b in zip(samples, samples[1:]):
        alat, alon = _finite_number(a.get("lat")), _finite_number(a.get("lon"))
        blat, blon = _finite_number(b.get("lat")), _finite_number(b.get("lon"))
        sa, sb = _finite_number(a.get("station_m")), _finite_number(b.get("station_m"))
        if None in (alat, alon, blat, blon, sa, sb):
            continue
        assert alat is not None and alon is not None and blat is not None and blon is not None
        assert sa is not None and sb is not None
        ax = (alon - lon) * m_per_deg_lon
        ay = (alat - lat) * m_per_deg_lat
        bx = (blon - lon) * m_per_deg_lon
        by = (blat - lat) * m_per_deg_lat
        vx, vy = bx - ax, by - ay
        length2 = vx * vx + vy * vy
        if length2 <= 0.000001:
            continue
        raw_t = (-(ax * vx + ay * vy)) / length2
        t = max(0.0, min(1.0, raw_t))
        px, py = ax + t * vx, ay + t * vy
        distance = math.hypot(px, py)
        if best is not None and distance >= float(best["distance_m"]):
            continue
        station = sa + (sb - sa) * t
        proj_lat = lat + py / m_per_deg_lat
        proj_lon = lon + px / m_per_deg_lon
        best = {
            "station_m": station,
            "distance_m": distance,
            "quality": projection_quality(distance),
            "projected_lat": proj_lat,
            "projected_lon": proj_lon,
            "segment_t": t,
        }
    return best


def project_dbref_point_to_tra(x_dbref: Any, y_dbref: Any, tra_payload: dict[str, Any]) -> dict[str, float | str] | None:
    gps = dbref_to_wgs84(x_dbref, y_dbref)
    if gps is None:
        return None
    dense = densify_tra_payload(tra_payload)
    samples = samples_to_wgs84(dense)
    return project_wgs84_to_samples(gps[0], gps[1], samples)
