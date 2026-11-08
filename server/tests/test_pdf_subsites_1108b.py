"""Existing server rendering must keep distinct switches under identical short names."""
from pathlib import Path
import fitz
from tbserver.btb_pdf_android_style import _node_label, render_android_style_btb_pdf


def test_same_short_name_distinct_switches_rendered(tmp_path: Path):
    nodes = [dict(id=f"id{i}", external_key=f"n{i}", node_kind="BAHNHOF", name="MBG",
                  payload=dict(node_name="MBG", station_name="MBG", track_or_switch=f"W 99W{i}")) for i in (1, 4)]
    for node in nodes:
        label = _node_label(node)
        assert "MBG" in label
        assert node["payload"]["track_or_switch"] in label
    assert _node_label(None) == "Hauptbaustelle"
    target = tmp_path / "Unterbaustellen.pdf"
    snapshot = dict(btb=dict(work_date="2026-11-04", started_at="2026-11-04T07:00:00+01:00", ended_at="2026-11-04T19:00:00+01:00", header={}),
                    entries=[dict(id=f"e{i}", section="ARBEIT", payload=dict(text="Schwellenwechsel", worksiteNodeKey=f"n{i}")) for i in (1, 4)], photos=[])
    render_android_style_btb_pdf(target, snapshot=snapshot, original_snapshot=None,
                                context=dict(worksite_name="Berchtesgaden", lead_name="Test", worksite_nodes=nodes),
                                photo_files=[], renderer_version="subsite-test")
    with fitz.open(target) as doc:
        text = "\n".join(p.get_text() for p in doc)
        assert "W 99W1" in text
        assert "W 99W4" in text
