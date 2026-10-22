TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.27
BUILD: 200077
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- Gemeinsame Bestandsübersicht bei Strecke/Bahnhof mit Suche, Objektarten und einzelnen KM-Abschnitten.
- Gezielte Objekt- und KM-Übernahme in Baustellenformulare; optionale Bahnhofsuche auf dem Server.
- Stabile Foto-Eintragszuordnung für Kamera und Galerie sowie bestehende serverseitige Fotolinks.
- Getrennte Zeitbindung REPORT/MANUAL für Personal und Maschinen.

VERBESSERT:
- Beginn und Ende von BTB-Einträgen einschließlich Nachtschichten und explizit gelöschter Endzeit.
- Effektive GPS-/Aufnahmezeit-Metadaten für Fotos, PDF und Export; unbekannte Werte bleiben unbekannt.
- Weichenverbindungen regulär auswählbar; Gleis/Weiche/WVB mit kompatibler Objektart im Sync.
- Freie Strecke übernimmt keinen Bahnhof; Kontextwechsel und wiederhergestellte Formulare sind geschützt.

BEHOBEN:
- Syncbilder bleiben unter der maximalen Uploadgröße von 2.000.000 Bytes.
- Gelöschte GPS-Werte werden nicht aus alten Dateinamen wiederhergestellt.
- WVB-Präfixe hinterlassen keinen VB-Rest in der Objektnummer.
- Reine äquivalente Darstellungs-/Metadatenübergänge sollen keine künstlichen Sync- oder PDF-Änderungen auslösen.

SERVERVERTRAG:
- Unveränderter Serverstand 0f82b42da7412a138bf99b6f80b93031e3b5373e.
- Bestehende Geräte-Bearer-, Katalog-, WorksiteNode-, BTB- und Foto-PATCH-Verträge.
- Keine server/**-Datei geändert, keine automatische Veröffentlichung.

TESTSTATUS:
- Tatsächlicher CI-Lauf39716736906 für Code36bebf0a24078f4a81313fe41d5b5f90ba48e9fb: Unit-Tests + Debug PASS; Release PASS; Lint Debug PASS; APK-Upload PASS.
- Separater Quellvertragsjob:8PASS/5FAIL (13,19,83,85,86). Gesamt-CI ROT, keine Releasefreigabe.
- Debug-APK mit Android-Debug-Zertifikat; Updatefähigkeit über vorhandene Installation ungeprüft.
- Release-APK UNSIGNIERT, nicht als installierbares Release bereitgestellt.
- Beide APK-Manifeste: de.tbhelfer, Version1.3.27, Build200077.
- Installation und Pixel/Poco-/NAS-/Portal-E2E offen. Kein Deinstallieren oder Datenlöschen zum Erzwingen einer Installation.
- BTB-AUTO-016 weiterhin nur dokumentiert, nicht implementiert und nicht Bestandteil dieses Builds.

NÄCHSTER TEST:
- Debug-APK erst nach Signaturabgleich mit der vorhandenen Installation testen.
- Foto-Uploadgröße/Zuordnung/GPS-Löschen, Eintrags-Endzeit, Firmenzeitbindung, Bestandsübernahme und freie Strecke auf Pixel/Poco und im Portal prüfen.
- Offene Quellvertragsprüfungen mit ihrem zuständigen Bereich abgleichen; keine server/**-Änderung in diesem Android-Arbeitsblock.

BUILDNACHWEIS:
- https://github.com/jensmeier/TB-Helfer-2.0/actions/runs/39716736906
- Debug-APK SHA256:205ccbb826464a20ec7c3b72999c49546e24c4ca19c3d42d5506b216569169c4
- Originales CI-Artefakt:12400166655 (enthält die damalige vorbereitende Release-MD; diese Fassung aktualisiert ihren Teststatus).
