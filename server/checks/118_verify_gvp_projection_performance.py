"""Executable geometry regressions; no API/DB packages or live data required.

Loads the actual pure helpers from survey_catalog.py via AST, so running this
check neither imports the server/router graph nor opens a database connection.
The unchanged exhaustive projector is the reference oracle. This is a local
algorithm test, not an authenticated HTTP or Synology E2E test.

Run: python server/checks/118_verify_gvp_projection_performance.py
Optional measured comparison: append --benchmark.
"""
from __future__ import annotations

import ast
import copy
import math
from pathlib import Path
import random
import statistics
import sys
from time import perf_counter
from typing import Any
import unittest
from unittest.mock import patch


SOURCE = Path(__file__).resolve().parents[1] / "tbserver" / "survey_catalog.py"
NAMES = {
    "_num", "_project_xy_station", "_GvpProjectionIndex", "_segment_km_quality",
    "_MANUAL_KINDS", "GVP_MAX_AXIS_DISTANCE_M", "GVP_OFFSET_OUTLIER_M",
    "GVP_MIN_ROBUST_MATCHES",
}
tree = ast.parse(SOURCE.read_text(encoding="utf-8"))
selected = []
found = set()
for node in tree.body:
    names = {node.name} if isinstance(node, (ast.FunctionDef, ast.ClassDef)) else set()
    if isinstance(node, ast.Assign):
        names = {target.id for target in node.targets if isinstance(target, ast.Name)}
    if names & NAMES:
        selected.append(node)
        found.update(names & NAMES)
assert found == NAMES, ("missing production helper", NAMES - found)
scope = {"math": math, "statistics": statistics, "Any": Any}
exec(compile(ast.Module(body=selected, type_ignores=[]), str(SOURCE), "exec"), scope)
Index = scope["_GvpProjectionIndex"]
full_scan = scope["_project_xy_station"]
quality = scope["_segment_km_quality"]
RADIUS = scope["GVP_MAX_AXIS_DISTANCE_M"]


def point(x, y, station):
    return {"x_dbref": x, "y_dbref": y, "station_m": station}


def reference(x, y, samples):
    result = full_scan(x, y, samples)
    return result if result is not None and result["distance_m"] <= RADIUS else None


class ReferenceIndex:
    def __init__(self, samples):
        self.samples = samples

    def project(self, x, y):
        return reference(x, y, self.samples)


def no_geometry(*args):
    raise AssertionError("Unexpected geometry load for a non-GVP result")


scope["_segment_points"] = no_geometry


class ProjectionTests(unittest.TestCase):
    def assert_projections(self, samples, queries):
        index = Index(samples)
        before = copy.deepcopy(samples)
        for x, y in queries:
            # Same arithmetic and first-hit tie-break: require exact equality.
            self.assertEqual(index.project(x, y), reference(x, y, samples), (x, y))
        self.assertEqual(samples, before)

    def test_empty_invalid_degenerate_and_gaps(self):
        for samples in (
            [], [point(0, 0, 0)], [point(0, 0, 0), point(0, 0, 100)],
            [point(0, 0, 0), point(None, 0, 10), point(400, 0, 400)],
            [point(0, 0, 0), point(float("nan"), 0, 10), point(400, 0, 400)],
            [point(0, 0, 0), point(10, 0, None), point(20, 0, 20)],
            [point(False, 0, 0), point(20, 0, 20)],
            [point("0", "0", "10"), point("100", "0", "110")],
        ):
            self.assert_projections(samples, [(0, 0), (200, 0), (-120, 0)])

    def test_radius_and_cell_boundaries(self):
        for origin in (0.0, -480.0, 4_500_000.0):
            samples = [point(origin, 0, 100), point(origin + 240, 0, -140)]
            queries = [(origin + x, y) for x in (-120, 0, 120, 240, 360)
                       for y in (-120.000001, -120, 0, 120, 120.000001)]
            self.assert_projections(samples, queries)
            self.assertIsNotNone(Index(samples).project(origin + 120, 120))
            self.assertIsNone(Index(samples).project(origin + 120, 120.000001))

    def test_overlap_crossing_and_first_hit(self):
        samples = [point(-200, 0, 0), point(200, 0, 400),
                   point(0, -200, 1000), point(0, 200, 1400)]
        self.assert_projections(samples, [(0, 0), (1, 1), (100, 10)])
        self.assertEqual(Index(samples).project(0, 0)["station_m"], 200)
        self.assert_projections(list(reversed(samples)), [(0, 0), (1, 1)])

    def test_long_diagonal_segment_is_not_lost(self):
        samples = [point(-1_000_000, -1_000_000, 900),
                   point(1_000_000, 1_000_000, -900)]
        index = Index(samples)
        self.assertEqual(len(index._wide_segments), 1)
        self.assertEqual(len(index._cells), 0)
        self.assert_projections(samples, [(0, 0), (0, 100), (0, 200), (999999, 999999)])

    def test_station_jump_and_decreasing_station(self):
        samples = [point(0, 0, 1000), point(120, 0, 880),
                   point(120, 0, 5000), point(240, 0, 4900)]
        self.assert_projections(samples, [(60, 10), (120, 0), (180, 20)])

    def test_seeded_curved_geometries(self):
        rng = random.Random(20261002)
        for run in range(24):
            ox, oy = (4_450_000, 5_350_000) if run % 2 else (-1000, -1000)
            samples = [point(ox + i * 5, oy + 250 * math.sin(i / 35),
                             8000 + (i * 5 if run % 3 else -i * 5))
                       for i in range(301)]
            queries = [(ox + rng.uniform(-150, 1650), oy + rng.uniform(-400, 400))
                       for _ in range(50)]
            self.assert_projections(samples, queries)


class QualityTests(unittest.TestCase):
    samples = [point(i * 2, 0, i * 2) for i in range(301)]
    segment = {"source_kind": "ORIGINAL_TRA"}

    def context(self, offsets):
        return {"explicit_gra": [], "active_segment_count": 1,
                "gvp_points": [{"x_dbref": 30 + i * 70, "y_dbref": 10,
                                "km_meter": 30 + i * 70 + offset}
                               for i, offset in enumerate(offsets)]}

    def compare_quality(self, context):
        actual = quality(None, {}, self.segment, context, self.samples)
        with patch.dict(scope, {"_GvpProjectionIndex": ReferenceIndex}):
            expected = quality(None, {}, self.segment, context, self.samples)
        self.assertEqual(actual, expected)
        return actual

    def test_gvp_robust_offsets_and_full_result(self):
        result = self.compare_quality(self.context([10000, 10000, 10000, 10000, 11000]))
        self.assertEqual(result["status"], "AUTOMATISCH")
        self.assertEqual(result["matched_gvp"], 4)
        self.assertEqual((result["km_from_m"], result["km_to_m"]), (10000, 10600))
        self.assertEqual(result["color"], "GRUEN")

    def test_no_robust_cluster_remains_grey(self):
        result = self.compare_quality(self.context([100, 500, 1000, 1500, 2000]))
        self.assertEqual(result["status"], "UNPLAUSIBEL")
        self.assertEqual(result["color"], "GRAU")

    def test_radius_filter_applies_before_robust_count(self):
        context = self.context([10000] * 4)
        context["gvp_points"][3]["y_dbref"] = 120.000001
        self.assertEqual(self.compare_quality(context)["status"], "UNPLAUSIBEL")
        context["gvp_points"][3]["y_dbref"] = 120
        self.assertEqual(self.compare_quality(context)["matched_gvp"], 4)

    def test_no_or_too_few_references_do_not_load_geometry(self):
        for count, status in ((0, "FEHLT"), (1, "UNPLAUSIBEL"), (3, "UNPLAUSIBEL")):
            result = quality(None, {}, self.segment, self.context([10000] * count))
            self.assertEqual(result["status"], status)

    def test_manual_and_explicit_gra_keep_priority(self):
        context = self.context([10000] * 5)
        context["explicit_gra"] = [{"km_from_m": 1234, "km_to_m": 1834}]
        result = quality(None, {}, self.segment, context)
        self.assertEqual((result["source"], result["km_from_m"]), ("GRA_KM", 1234))
        for kind in ("MANUAL_TRA_SECTION", "MANUAL_GPS"):
            manual = {"source_kind": kind, "km_from_m": 900, "km_to_m": 400}
            result = quality(None, {}, manual, context)
            self.assertEqual((result["status"], result["color"]), ("MANUELL", "ORANGE"))
            self.assertEqual((result["km_from_m"], result["km_to_m"]), (400, 900))


def benchmark():
    # 40 km at 2 m sampling, 200 reference points. Synthetic, never sent to NAS.
    samples = [point(4_450_000 + i * 2, 5_350_000 + 50 * math.sin(i / 300), i * 2)
               for i in range(20_001)]
    queries = [(4_450_000 + i * 200 + 40, 5_350_000 + 15) for i in range(200)]
    start = perf_counter()
    expected = [reference(x, y, samples) for x, y in queries]
    baseline_seconds = perf_counter() - start
    start = perf_counter()
    index = Index(samples)
    actual = [index.project(x, y) for x, y in queries]
    indexed_seconds = perf_counter() - start
    assert actual == expected, "Benchmark results differ from exhaustive projection"
    print(f"BENCHMARK samples={len(samples)} gvp={len(queries)} "
          f"full_scan_s={baseline_seconds:.6f} index_build_and_query_s={indexed_seconds:.6f} "
          f"speedup={baseline_seconds / max(indexed_seconds, 1e-9):.1f}x "
          "exact_results=PASS (local synthetic; not Synology E2E)")


if __name__ == "__main__":
    measure = "--benchmark" in sys.argv
    result = unittest.main(argv=[sys.argv[0]], exit=False)
    if not result.result.wasSuccessful():
        raise SystemExit(1)
    if measure:
        benchmark()
