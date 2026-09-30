from __future__ import annotations

import ast
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def _write_text(data: bytearray, offset: int, length: int, value: str) -> None:
    encoded = value.encode("cp1252")[:length]
    data[offset : offset + len(encoded)] = encoded


def _raw_kf(
    *,
    version: str = "VERSION 7.0",
    kf_number: float,
    x: float,
    y: float,
    height: float,
    station: float,
    location: str,
    part: str,
    route: str,
    rikz: str,
) -> bytes:
    data = bytearray(204)
    _write_text(data, 40, 34, version)
    _write_text(data, 97, 5, "00000")
    base = 102
    struct.pack_into("<d", data, base, kf_number)
    struct.pack_into("<d", data, base + 8, x)
    struct.pack_into("<d", data, base + 16, y)
    struct.pack_into("<d", data, base + 24, height)
    struct.pack_into("<d", data, base + 32, station)
    _write_text(data, base + 40, 16, location)
    _write_text(data, base + 70, 4, part)
    _write_text(data, base + 74, 1, "0")
    _write_text(data, base + 83, 14, "TEST")
    _write_text(data, base + 97, 4, route)
    _write_text(data, base + 101, 1, rikz)
    return bytes(data)


def verify_raw_kf_parity() -> None:
    sys.path.insert(0, str(ROOT / "server"))
    from tbserver.survey_parser import parse_survey_original

    def parse(name: str, data: bytes):
        family, detected, payload, warnings = parse_survey_original("KF", name, data, "a" * 64)
        assert family == "GVP_FESTPUNKT", family
        assert detected == "KF_7_BINARY", detected
        assert len(payload["points"]) == 1, payload
        return payload["points"][0], warnings

    point, warnings = parse(
        "5362_km70,6-81,5_PS1-PS3_20260619_V2.kf",
        _raw_kf(
            kf_number=40.00300,
            x=3_597_538.6261,
            y=5_280_599.5563,
            height=721.8565,
            station=70_698.1786,
            location="1070698",
            part="PS1",
            route="5362",
            rikz="5",
        ),
    )
    assert point["point_name"] == "1070698", point
    assert point["type"] == "PS1", point
    assert point["route"] == "5362", point
    assert abs(point["km_meter"] - 70_698.1786) < 0.00001, point
    assert abs(point["x_dbref"] - 3_597_538.6261) < 0.00001, point
    assert abs(point["y_dbref"] - 5_280_599.5563) < 0.00001, point
    assert abs(point["height_m"] - 721.8565) < 0.00001, point
    assert point["coordinate_system"] == "DB_REF_GK_3", point
    assert any("Roh-KF 7.0 direkt importiert" in warning for warning in warnings), warnings

    point, _ = parse(
        "Festpkt_5300_054-059_DHDN.kf",
        _raw_kf(
            version="VERSION 7.0  R",
            kf_number=5.40600,
            x=4_403_282.57605,
            y=5_407_261.69976,
            height=416.4690,
            station=54_319.9350,
            location="54/6",
            part="GVPV",
            route="5300",
            rikz="5",
        ),
    )
    assert point["point_name"] == "54/6", point
    assert point["type"] == "GVPV", point
    assert point["route"] == "5300", point
    assert abs(point["km_meter"] - 54_319.9350) < 0.00001, point
    assert abs(point["height_m"] - 416.4690) < 0.00001, point
    assert point["coordinate_system"] == "DB_REF2016_4", point

    point, warnings = parse(
        "5300 km 51-53 GE+WE Harburg_DBREF2016.kf",
        _raw_kf(
            kf_number=77.00051,
            x=4_405_010.5870,
            y=5_403_770.3040,
            height=412.5310,
            station=0.0,
            location="50/51",
            part="GVPV",
            route="5300",
            rikz="1",
        ),
    )
    assert point["route"] == "5300", point
    assert point["km_meter"] is None, point
    assert any("ohne plausible Station" in warning for warning in warnings), warnings

    point, warnings = parse(
        "Bbf_Pasing_FPF_Gesamt_mit_Gl30-40_neu_DBREF.kf",
        _raw_kf(
            kf_number=7.00011,
            x=4_459_039.0800,
            y=5_335_216.9630,
            height=1e40,
            station=1e40,
            location="30005",
            part="",
            route="0000",
            rikz="0",
        ),
    )
    assert point["route"] is None, point
    assert point["km_meter"] is None, point
    assert point["height_m"] is None, point
    assert any("ohne plausible Strecke" in warning for warning in warnings), warnings


def verify_filename_metadata() -> None:
    sys.path.insert(0, str(ROOT / "server"))
    from tbserver.survey_parser import _axis_meta_from_name, _route_from_name

    assert _route_from_name("2025-04-25_MHRG_Gleis_10_O00.GRA") is None
    assert _route_from_name("2026-03-25_5300_050-053_R_O00.GRA") == "5300"
    assert _route_from_name("2025-04-25_MHRG_Gleis_10_O00_km5300.GRA") == "5300"

    meta = _axis_meta_from_name("2025-04-25_MHRG_Gleis_10_O00.GRA")
    assert meta["route_from_name"] is None, meta
    assert meta["track_from_name"] == "10", meta
    assert meta["axis_type_from_name"] == "GLEIS", meta

    meta = _axis_meta_from_name("2026-03-25_5300_050-053_R_O00.GRA")
    assert meta["route_from_name"] == "5300", meta
    assert meta["direction_from_name"] == "2", meta
    assert meta["source_direction_from_name"] == "R", meta
    assert meta["axis_type_from_name"] == "STRECKE", meta

    meta = _axis_meta_from_name("250919_Gl1_MGHN_O00.GRA")
    assert meta["route_from_name"] is None, meta
    assert meta["track_from_name"] == "1", meta

    meta = _axis_meta_from_name("250919_5302R_ER0.TRA")
    assert meta["route_from_name"] == "5302", meta
    assert meta["track_from_name"] == "303", meta
    assert meta["direction_from_name"] == "2", meta

    meta = _axis_meta_from_name("WVB_14-22_ER0_GGP2026_02_16.TRA")
    assert meta["axis_type_from_name"] == "WEICHENVERBINDUNG", meta
    assert meta["object_key_from_name"] == "WVB:14-22", meta
    assert meta["switch_a_from_name"] == "14", meta
    assert meta["switch_b_from_name"] == "22", meta

    meta = _axis_meta_from_name("W_27_ER0_GGP2026_02_16.TRA")
    assert meta["axis_type_from_name"] == "WEICHE", meta
    assert meta["object_key_from_name"] == "W:27", meta


def main() -> None:
    parser = read("server/tbserver/survey_parser.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    migration = read("server/v1_migrations/003_survey_parse_catalog.sql")

    ast.parse(parser, filename="server/tbserver/survey_parser.py")

    assertions = [
        ("CREATE TABLE tb.survey_parse_catalog", migration),
        ("REFERENCES tb.survey_originals(id) ON DELETE RESTRICT", migration),
        ("PARSER_VERSION = \"tb-survey-parser-4\"", parser),
        ("TRA_ER0_BINARY", parser),
        ("GRA_BINARY", parser),
        ("KF_7_BINARY", parser),
        ("KF_TEXT", parser),
        ("KV_TEXT", parser),
        ("GVP_TEXT", parser),
        ("KOR_RAW_NO_TEXT_PARSER", parser),
        ("SURVEY_ORIGINAL_INTEGRITY_MISMATCH", parser),
        ("_hash_file(path)", parser),
        ("ON CONFLICT (original_id) DO UPDATE", parser),
        ("def reparse_original_for_catalog(", parser),
        ("_DATE_TOKEN_RE", parser),
        ("track_from_name", parser),
        ("direction_from_name", parser),
        ("survey_parser_router", main_source),
        ('"survey_parser_catalog_v1": True', caps),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Vermessungsparser-Vertragsmarker: " + ", ".join(missing))

    forbidden = [
        "UPDATE tb.survey_originals",
        "DELETE FROM tb.survey_originals",
        "INSERT INTO tb.survey_originals",
        "os.replace",
    ]
    present = [needle for needle in forbidden if needle in parser]
    if present:
        raise SystemExit("Parser darf Vermessungsoriginale nicht mutieren: " + ", ".join(present))

    if 'kind in {"GVP", "KF", "KV", "KF.TXT", "FESTPUNKT"}' not in parser:
        raise SystemExit("KF/KV muessen Teil der gemeinsamen GVP/Festpunkt-Parserfamilie bleiben")

    verify_raw_kf_parity()
    verify_filename_metadata()
    print("OK: TB-Server 1.0 survey parser v4 + Raw-KF parity + object identity + date-safe filename metadata")


if __name__ == "__main__":
    main()
