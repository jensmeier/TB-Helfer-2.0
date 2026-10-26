# AND-BIDIR-026 – Baustellen und Teams bidirektional prüfen
Datum: 26.10.2026
Status: QUELLPRÜFUNG ABGESCHLOSSEN / KORREKTUR OFFEN / GERÄTE-E2E OFFEN

## Geprüfter Stand
Android2368fbc681918c59d1e0111e67b994f9f2d149cd, neuer Auftrag b01c4d0c6c73bf3f61ae25a70305231fbe65098c.
Server923cf93709fe82dd93072ddfb5836cb3ca7fcd95 nur gelesen.
PR11 offen/nicht Draft und PR6 offen/Draft zuletzt im vorangegangenen Block geprüft.
Screenshot bestätigt installierte1.3.31/200081. Er bestätigt keine byteidentische APK zum GitHubstand oder NAS-Version.
Nutzer erklärt Handy und Portal als denselben Nutzer. Tatsächliche UUIDs/DB-Bindung ohne authentifizierten Laufzeitzugriff nicht verglichen.

## Soll
- Einmalige stabile Identität je Baustelle und Unterbaustelle, nicht automatische Zusammenführung nach Name.
- Anlegen/Bearbeiten auf Handy oder Portal wird dauerhaft gespeichert und automatisch zum Server sowie zu berechtigten Gegenstellen abgeglichen.
- Alleinnutzung durch verantwortlichen TB benötigt weder Helfer noch vorheriges Öffnen einer Einladung.
- Helfereinladung/-beitritt/-entfernung/-Teamwechsel aktualisieren Rechte und Baustellen-/Teamkontext. Einladungsanlage ist nach bestehendem Vertrag noch kein angenommener Beitritt.
- Offline bleiben lokale Eingaben möglich und persistent; Nachholung bei Verbindung. Konflikt/Recovery/Originalschutz erhalten.
- Haupt-TB sieht PDF Entwurf und BTB Abschluss, auch vor erster Serverbestätigung. Helferrechte separat bewahren.
- Erfolgsanzeige darf nicht allein einen abgeschlossenen Worker als Vollständigkeit aller lokalen Aufträge darstellen.

## Quellbefunde
| Weg | Beleg | Ergebnis |
|---|---|---|
| Handy-Neuanlage → Server | BaustelleNeuFragment.kt; BaustellenBaumRepository.kt:74; BaustellenTeamDirekt.kt:48,105; WorksiteProvisioningApi.kt:29 | Lokale Anlage und explizites Server-Provisioning existieren getrennt. Im untersuchten Anlage-/selektiven Workerpfad fehlt Provisioning. Expliziter Aufruf sitzt in Nutzer-hinzufügen/Einladen. |
| Ungebundene Baustelle → selektiver Sync | SelectiveSyncWorker.kt:roots/scopes-Schleifen | Kein Binding: retryNeeded, später Result.retry; exponentieller Backoff ab30s, kein Create/Bind. Lange Wartezeit kann entstehen, ohne diese Voraussetzung je selbst herzustellen. Kein Nachweis eines konkreten HTTP-Timeouts beim Nutzer. |
| Lokaler Eintrag an Unterbaustelle | BautagebuchFragment.kt:2584,7287; WorksiteStructureSync.kt:97 | stableNodeKey erfordert bestehenden Serverkontext. Fehlender Key blockiert Hinzufügen. Uhrzeit/KM dürfen leer sein. Snackbar am Fragment kann vom offenen Dialog verdeckt sein. |
| PDF-Vorschau lokal | BtbModernLifecycleInstaller.kt:454–500 | serverState==null und ENTWURF -> Vorschau GONE, Abschluss sichtbar. Kein Beweis einer falsch erkannten Helferrolle. |
| Portal-Neuanlage → Server | v1_worksites.html:235; worksites.py:207–291 | Gemeinsames POST /api/v1/worksites; erzeugt Baustelle, verantwortliche Mitgliedschaft, Team und WORKSITE-Event transaktional/idempotent. |
| Portal-Neuanlage → Android | Server1DeltaApi.kt:160; Server1DeltaSync.kt:129–150 | Schneller Feedfilter enthält BTB/BTB_ENTRY/BTB_PARTICIPANT/FILE/WORKSITE_NODE, kein WORKSITE/WORKSITE_MEMBERSHIP/WORKSITE_INVITATION. Apply beschränkt sich auf bereits lokal gebundene Kontexte; nur Filter erweitern reicht nicht. |
| Kontext-Erstaufbau | TeamManagementApi.kt:194; Server1AndroidSyncCoordinator.kt:52; WorksiteBindingRepository.kt:52 | Vollabgleich liest device/me, sichtbare Baustellen und Mitgliedschaften; kann fehlende lokale Roots erzeugen. Gleichnamige vorhandene lokale Baustelle wird bewusst nicht automatisch verbunden. Identitätsschutz erhalten; pending Provisioning muss über stabile ID erkannt werden. |
| Unterbaustelle ändern | ServiceLocator.kt:91; WorksiteStructureSync; WorksiteDeltaPolicy | Persistent markierte Struktur + selektiver Worker; WORKSITE_NODE-Abruf für gebundene Baustellen vorhanden. Nicht pauschal fehlender Sync; Dirty/Pending/409-Schutz erhalten. Geräte-E2E nicht durch Quellprüfung ersetzt. |
| Hauptbaustelle umbenennen | BaustellenBaumRepository.kt:262–283; worksites.py-Routen | Ändert lokale Root/Kinder und Altname, ohne onStructureChanged/Syncauftrag. Im untersuchten kanonischen API-Modul kein PATCH für Baustellenkopf. Zusatz zum Serververtrag nötig, keine Nebenbei-Serveränderung. |
| Helfer von Portal/Handy | TeamManagementApi createServerInvitation/redeemServerInvitation; v1_worksite.html:487; worksites.py:1121,1397,1592 | Gemeinsame serverseitige Einladungs-/Beitrittswege vorhanden; Beitritt emittiert WORKSITE_MEMBERSHIP. Hintergrund-Aktualisierung Android fehlt für diese Eventarten; manuelles Teamöffnen/Vollsync ist kein automatischer Nachweis. |
| Geöffnete Portal-Baustelle | v1_worksite.html:911–959 | SSE /sync/events vorhanden; WORKSITE/WORKSITE_NODE/MEMBERSHIP/INVITATION und Fachdaten lösen gezielte Refreshes aus. Bestehende bestätigte Funktion schützen. |
| Portal-Baustellenliste | v1_worksites.html vollständig | loadWorksites bei Laden/eigener Anlage und manuell, kein EventSource-/Wait-Listener in dieser Vorlage. Offene Liste zieht fremde Neuanlagen nicht über denselben Livepfad nach. |
| Erfolgsanzeige | SyncKatalogFragment.kt:163–208; SyncWorker; Server1AndroidSyncCoordinator | Neu laden startet nach Katalog auch vollständigen Fachworker. SUCCEEDED wird zu „Synchronisation vollständig“. Coordinator beginnt mit Serverkontexten; ungebundene lokale Baustelle ist darin nicht vollständig erfasst. Frühere Vermutung „nur Katalogstatus“ ausdrücklich korrigiert. |
| Gemeinsame Identität | client_auth.py; portal.py _load_session; TeamManagementApi device/me | Portal-Cookie und Geräte-Bearer werden auf Servernutzer abgebildet. Gleicher Anzeigename ist kein technischer Identitätsnachweis; aktuelle UUIDs fehlen. Kein zweites unabhängiges Nutzermodell erfinden. |

## Reihenfolge einer gezielten Korrektur
1. Lokale Anlage mit persistenter Provisioning-/Outboxkennung und Wiederaufnahme verbinden; vorhandenes idempotentes API verwenden. Keine Dubletten bei POST-Erfolg und verlorenem Antwort-/Bindschritt.
2. Unterbaustellen-Identität vor Serverbindung lokal dauerhaft ermöglichen und beim Binden unverändert übernehmen; keine neue Identität aus Name/GPS. PDF-Vorschau für lokalen Haupt-TB wieder sichtbar.
3. WORKSITE-/Mitgliedschafts-/Teamereignisse als Kontextaktualisierung verarbeiten, neue berechtigte Baustellen initialisieren, Rechteentzug/Teamwechsel beachten; Cursor erst nach erfolgreicher notwendiger Verarbeitung. Keine Vollsynchronisation sämtlicher Fotos bei jedem Event.
4. Hauptbaustellenkopf-Vertrag für Umbenennen/Metadaten gezielt im SERVER-Projekt ergänzen; Android danach an dokumentierten Vertrag anpassen.
5. Portal-Listen-Livepfad im SERVER-Projekt; Statusanzeige zeigt lokale ungebundene/pending/konfliktbehaftete Aufträge ehrlich.

## Praktische Prüfmatrix – vollständig OFFEN
A. Haupt-Handy online neue Baustelle ohne Helfer; Portal und zweites berechtigtes Gerät sehen dieselbe Server-ID, Eintrag/PDF bereits lokal möglich.
B. Offline Neuanlage, Unterbaustelle, Eintrag, Neustart; später online genau eine Serverbaustelle. POST erfolgreich/Antwort verloren erzeugt keine Dublette.
C. Portal neue Baustelle für denselben Nutzer, App bereits offen: automatischer Kontextaufbau ohne Neuaktivierung und ohne Namensraten.
D. Name/Kopf und Unterbaustelle jeweils Handy→Portal und Portal→Handy; gleichzeitig geöffnete Liste/Detailansicht nachziehen.
E. Einladung vom Handy sowie vom Portal, Annahme am Helfer-Handy; vor Annahme keine erfundene Mitgliedschaft, danach richtiger TB/Team/BTB-Bereich.
F. Helfer ändern/entfernen/Team wechseln, Geräte online und offline; Rechte werden wirksam und lokale ausstehende Daten nicht fremdem Team zugeordnet.
G. BTB speichern, Bild, Schichtende, Haupt-TB-Abschluss; nur berechtigte Gegenstellen, unveränderte Originale/IDs. Paralleländerung409 bleibt sichtbar.
H. Erfolgsmeldung bei ungebundener Baustelle, Pending, Konflikt und Rückstand; kein pauschales vollständig.

## Nachweisgrenzen und Übergabe
Keine Runtimekorrektur, APK, Versionsänderung, Actions, NAS-/Produktivdatenänderung oder Deployment.
Quellpfade gelesen, keine HTTP-/Postman-/Emulator-/Pixel-/Poco-E2Es ausgeführt. postman und adb lokal nicht verfügbar. API-Testing-/Android-Emulator-QA-Anleitungen konsultiert; dies ist kein Testnachweis.
Keine NAS-Übernahme für diesen Dokumentationsblock. Serververtrag/Portal-Korrekturen als gesonderte SERVER-Aufgabe, Androidkorrektur noch offen. Nutzer muss Baustelle nicht löschen/neu aktivieren oder im Portal doppelt anlegen.
