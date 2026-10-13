#!/usr/bin/env python3
from __future__ import annotations
import re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTROL = ROOT / "projektsteuerung"
REQUIRED = ["00_START_HIER.md","01_MASTERSTATUS.md","02_AKTIVER_AUFTRAG.md","03_ANDROID_STATUS.md","04_SERVER_STATUS.md","05_ANDROID_SERVER_VERTRAG.md","06_E2E_TESTSTATUS.md","07_OFFENE_PUNKTE.md","08_CHAT_CHECKPOINT.md","TB-HELFER_STATUS.xlsx"]
LEGACY = ["projektstatus","releases","uebergaben"]
errors=[]
for name in REQUIRED:
    if not (CONTROL/name).exists(): errors.append(f"FEHLT: projektsteuerung/{name}")
for name in LEGACY:
    if (ROOT/name).exists(): errors.append(f"ALTORDNER NOCH VORHANDEN: {name}/")
def git(*args):
    try: return subprocess.check_output(["git",*args],cwd=ROOT,text=True).strip()
    except Exception: return "nicht verfügbar"
print("TB-HELFER PROJEKTSTEUERUNG")
print("Branch:",git("branch","--show-current"))
print("HEAD:  ",git("rev-parse","HEAD"))
p=ROOT/"app"/"build.gradle.kts"
if p.exists():
    t=p.read_text(encoding="utf-8",errors="replace")
    vn=re.search(r'versionName\s*=\s*"([^"]+)"',t); vc=re.search(r'versionCode\s*=\s*(\d+)',t)
    print("Android:",vn.group(1) if vn else "?","/",vc.group(1) if vc else "?")
p=ROOT/"server"/"tbserver"/"__init__.py"
if p.exists():
    t=p.read_text(encoding="utf-8",errors="replace"); sv=re.search(r'SERVER_VERSION\s*=\s*"([^"]+)"',t)
    print("Server:",sv.group(1) if sv else "?")
md=[CONTROL/x for x in REQUIRED if x.endswith(".md")]
b=sum(x.stat().st_size for x in md if x.exists()); l=sum(len(x.read_text(encoding="utf-8",errors="replace").splitlines()) for x in md if x.exists())
print(f"Steuerungs-MDs: {l} Zeilen / {b} Bytes")
print("Nur Dokumentgröße – KEIN ChatGPT-Kontext-Prozentwert.")
if errors:
    print("\nFEHLER:"); [print("-",e) for e in errors]; raise SystemExit(1)
print("\nOK: zentrale Projektsteuerung vorhanden; alte Top-Level-Steuerungsordner fehlen.")
