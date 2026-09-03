from pathlib import Path

p = Path('.github/scripts/apply_v2003_axis_first_markers.py')
s = p.read_text(encoding='utf-8')

# Einige kurze UI-Fragmente kommen in der grossen Fragment-Datei mehrfach vor.
# Fuer den 003-Patch ist jeweils bewusst der erste Treffer im fachlich vorher
# festgelegten Bereich gemeint.
s = s.replace('    if count != 1:\n        raise SystemExit(f"{label}: erwartet genau 1 Treffer, gefunden {count}")\n',
              '    if count < 1:\n        raise SystemExit(f"{label}: kein Treffer gefunden")\n')

# Die Schriftgroesse 11sp wird auch bei manuellen KM-Ankern verwendet. Sie muss
# fuer G1/G2 nicht veraendert werden; nur der Radius wird auf 1,5x vergroessert.
marker = '    "Grenzmarker Textgroesse"\n)\n'
pos = s.find(marker)
if pos >= 0:
    start = s.rfind('gtext = replace_once(', 0, pos)
    if start >= 0:
        s = s[:start] + s[pos + len(marker):]

p.write_text(s, encoding='utf-8')
