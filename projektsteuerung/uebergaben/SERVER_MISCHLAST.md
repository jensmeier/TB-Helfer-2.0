# SERVER-MISCHLAST – BTB, Foto und zweiter Sync-Client

Ausgang Server 9d9b388b, Android 47a8fd58. Auftrag fe96a3a6. Testwerkzeug `server/tools/btb_mixed_loadtest.py`.

Der Lauf schließt einen kleinen BTB ab und prüft dessen PDF. Anschließend laufen ein fotoreicher BTB-Abschluss und Android-artige Foto-Uploads parallel. Ein zweites Gerät im Test wird per eigenem Bearer-Token simuliert. Sein Delta-Lauf startet am aktuellen Cursor und muss das konkrete FINALIZE-Ereignis sowie alle erzeugten FILE-CREATE-Ereignisse sehen. Beide PDFs werden per Größe und SHA-256 geprüft. Ausgabe als JSON mit Abschluss- und Uploadzeiten, Client-Überlappung und Zeit vom jeweiligen Request-Start bis zum Erkennen der Änderung. Die Überlappung beschreibt die Clientfenster, keine nachgewiesene gleichzeitige Ausführung im Server. Zwei unterschiedlich große BTBs eignen sich zum Vergleich von Arbeitsabläufen. Für eine kausale Vorher-Nachher-Messung denselben Fixture-Stand, dieselben Fotos und dieselbe Parallelität auf beiden Serverständen wiederholen.

## Testinstanz vorbereiten

- Isolierte Datenbank und Storage, lokale API mit eigener Portnummer. Das Skript akzeptiert ausschließlich Loopback-IP mit explizitem Port und erfordert `--confirm-disposable-test`. Keine Weiterleitungen und keine Umgebungs-Proxys. Den Port vorab der Testinstanz zuordnen. Ein Loopback-Port kann auch auf eine produktive Instanz zeigen.
- Drei verschiedene aktive Test-BTBs mit passenden Teamrechten. Klein mit höchstens einem Foto, fotoreich mit mindestens sechs Fotos, dritter BTB als Uploadziel. Der schreibende Nutzer muss in allen drei BTBs Haupt-TB sein, der lesende Nutzer muss den fotoreichen und den Upload-BTB sehen können. Die Helferfreigaben für beide Abschlüsse müssen vorliegen. Der Test setzt `allow_unfinished=false`.
- Zwei unterschiedliche Testgeräte-Tokens ausschließlich als Umgebungsvariablen `TB_MIXED_WRITER_TOKEN` und `TB_MIXED_READER_TOKEN` setzen. Ein synthetisches JPEG bis 2.000.000 Bytes bereithalten. Keine produktiven Daten oder Zugangsdaten verwenden.

```bash
python server/tools/btb_mixed_loadtest.py \
  --base-url http://127.0.0.1:8081 --confirm-disposable-test \
  --small-btb "$SMALL_TEST_BTB" --rich-btb "$RICH_TEST_BTB" \
  --upload-btb "$UPLOAD_TEST_BTB" --jpeg testfoto.jpg --uploads 3
```

Der Lauf schreibt Testdaten dauerhaft und schließt zwei Testberichte ab. Auch bei Fehler können Teilergebnisse bestehen. Für Wiederholungen frische Fixtures oder die zurückgesetzte Testinstanz verwenden. Standardmäßig drei, maximal zehn Uploads. Keine Ausgabe von Tokens, Nutzdaten oder Serverfehlertexten.

Zusätzlich die beiden `btb_finalize_timing`-Zeilen im API-Log und RAM, CPU, DB-Last und Neustarts der Container erfassen. Die JSON-Zeiten enthalten Client- und Netzwerkanteile. Drei lokale Tests gegen einen synthetischen HTTP-Server bestanden (Parallelablauf mit beiden Delta-Ereignissen, falsche PDF-Prüfsumme, Abweisung eines externen Ziels). Syntax und CLI-Hilfe geprüft. Echte PostgreSQL-/PostGIS-/NAS-Mischlast, Postman, Android-Geräte und neue GitHub-CI bleiben offen. Keine Kapazitäts- oder Latenzzusage aus dem lokalen Test.

**TESTWERKZEUG FERTIG / LOKALE HTTP-PRÜFUNG BESTANDEN / MESSUNG AN ECHTER TESTINSTANZ OFFEN.**
