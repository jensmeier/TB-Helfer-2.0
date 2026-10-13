# TB-Helfer Server 0.7.0
# Der geprüfte 0.7-Quellstand ist verlustfrei in top-level Python-Teile zerlegt.
# Alle Teile laufen in derselben Reihenfolge und im selben Modul-Namespace wie der Monolith.
from pathlib import Path as _TBPath

_TB_PARTS = _TBPath(__file__).with_name("app_parts")
for _tb_part in sorted(_TB_PARTS.glob("[0-9][0-9]_part.py")):
    exec(compile(_tb_part.read_bytes(), str(_tb_part), "exec"), globals(), globals())
del _tb_part, _TB_PARTS, _TBPath
