"""Integration-1108 regressions; local renderer/policy tests, no production E2E."""
import unittest
from tbserver.btb_pdf_android_style import _photo_description, _photo_section_lines, _weather_rows
from tbserver.btb_weather import cloud_label, rain_label

class Integration1108(unittest.TestCase):
    def test_short_and_long_text_survive(self):
        self.assertEqual("Schwellenwechsel – Gleis 2", _photo_description(dict(kurztext="Schwellenwechsel", notiz="Gleis 2")))
        self.assertEqual("TEST", _photo_description(dict(kurztext="Test", notiz="TEST")))

    def test_same_description_groups_across_time_gap_without_crossing_company_or_node(self):
        photos=[]
        for i,(company,node,hour) in enumerate([("A","n1",8),("A","n1",14),("B","n1",9),("A","n2",10)]):
            photos.append(dict(id=str(i),captured_at=f"2026-11-08T{hour:02d}:00:00+01:00",photo_metadata=dict(firma=company,worksiteNodeKey=node,kurztext="Schwellenwechsel",notiz="Detail",km=0)))
        rows=_photo_section_lines(dict(photos=photos),{}, {},set())["ARBEIT"]
        self.assertEqual(3,len(rows))
        self.assertIn("08:00-14:00",rows[0].text)
        self.assertIn("Schwellenwechsel – Detail",rows[0].text)

    def test_weather_zero_and_unknown_are_distinct(self):
        self.assertEqual("trocken",rain_label(61,0))
        self.assertIsNone(rain_label(None,None))
        self.assertIsNone(rain_label(None,float("nan")))
        self.assertIsNone(cloud_label(101))
        _,rows=_weather_rows(dict(weather=dict(slots=[dict(uhrzeit="07:00",tempC=0,niederschlag="trocken",niederschlagMm=0)])))
        self.assertIn("0 mm",rows[0][3])
        self.assertEqual("0°C",rows[0][1])

if __name__ == "__main__": unittest.main()
