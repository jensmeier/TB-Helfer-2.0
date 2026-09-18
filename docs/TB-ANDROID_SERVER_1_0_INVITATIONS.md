# TB-Helfer Android – Server-1.0-Baustelleneinladungen

Stand: 2026-09-18

Dieses Dokument beschreibt den Android-Übergangsvertrag für die bereits serverseitig eingeführten gezielten Baustelleneinladungen – Einlösen und Erzeugen.

## Ziel

Android soll die stabile Server-1.0-Einladungs-URI direkt öffnen oder per QR-Scanner einlesen können, ohne die noch produktiv verwendete Legacy-0.7-Einladung sofort abzuschneiden.

Der Client unterstützt deshalb während der Umstellung beide Formate.

## Server 1.0

Stabile App-URI:

`tbhelfer://worksite-invite?token=<einmal-token>`

Der Token ist **case-sensitive**. Android darf ihn weder normalisieren noch in Großbuchstaben umwandeln.

Zusätzlich kann Android den Portal-Link verarbeiten:

`https://<portal>/portal/v1/invitations/redeem#token=<einmal-token>`

sowie den rohen Token beim manuellen Einfügen.

Einlösung:

`POST /api/v1/worksites/invitations/redeem`

Body:

`{"token":"<einmal-token>"}`

Authentisierung erfolgt mit dem vorhandenen Geräte-Bearer. Der Server benötigt für Geräte-Bearer keinen Browser-CSRF-Header. Android sendet zusätzlich für diese Mutation eine eigene `X-Client-Change-Id`.

## Teamdarstellung auf Server 1.0

Damit die neuen Einladungsfunktionen nicht mehr vom alten `/api/v1/collab/.../team`-Endpunkt abhängen, lädt `TeamManagementApi.loadTeam()` jetzt zuerst den Server-1.0-Fachvertrag:

- `GET /api/v1/worksites/{worksite_id}`;
- `GET /api/v1/device/me`;
- `GET /api/v1/worksites/{worksite_id}/members`;
- `GET /api/v1/worksites/{worksite_id}/teams`.

Aus diesen Antworten wird die bestehende Android-`TeamOverview` aufgebaut:

- aktueller Gerätebenutzer über `device/me`;
- aktuelle Baustellenrolle/Area-Scope aus der Membership;
- eigenes Team über `team_id`;
- aktive TB-Teams mit ihren Helfern aus Member-/Teamlisten;
- `RESPONSIBLE_TB` und `TB` werden für die vorhandene UI als `LEAD` dargestellt.

Nur wenn der moderne Baustellenvertrag nicht vorhanden ist (404 bzw. fehlende Server-1.0-Antwortstruktur), fällt `loadTeam()` auf den bisherigen Legacy-Collab-Teamendpunkt zurück.

Damit kann die Personenwahl für neue Server-1.0-Einladungen auf einer reinen Server-1.0-Instanz tatsächlich angezeigt werden.

## Erzeugen auf Android

Android versucht beim Erzeugen einer Baustelleneinladung jetzt zuerst den gezielten Server-1.0-Vertrag.

Kandidaten:

`GET /api/v1/worksites/{worksite_id}/invite-candidates`

Der Server liefert nur aktive Benutzer derselben Firma, die noch kein aktives Mitglied dieser Baustelle sind. Android zeigt deshalb zuerst eine konkrete Personenauswahl. Eine ungerichtete „jeder mit Code darf hinein“-Einladung wird auf Server 1.0 nicht erzeugt.

Erzeugen:

`POST /api/v1/worksites/{worksite_id}/invitations`

Android sendet:

- `invited_user_id`;
- `worksite_role`;
- `permission_profile`;
- `permissions_override={}`;
- `area_scope`;
- `expires_days`;
- eine eigene `X-Client-Change-Id`.

Rollenabbildung im aktuellen Android-Dialog:

- Helfer -> `worksite_role=HELPER`, Profil `HELPER_STANDARD`;
- weiterer verantwortlicher TB -> `worksite_role=RESPONSIBLE_TB`, Profil `RESPONSIBLE_TB`.

Für Helfer bleibt der bereits vorhandene Bereichsdialog erhalten und wird als `area_scope` an Server 1.0 weitergegeben.

Die erste erfolgreiche Create-Antwort liefert den Einmal-Token sowie App-/Browser-Link. Android erzeugt den sichtbaren QR lokal aus der vom Server gelieferten App-URI und teilt bevorzugt den Browser-Link. Der geheime Token wird nicht lokal dauerhaft gespeichert.

Wenn der Server den neuen Kandidaten-Endpunkt noch nicht kennt (404), fällt Android während der Umstellung auf den bisherigen Legacy-Einladungspfad zurück. Ein fachliches 403 oder ein anderer Server-1.0-Fehler wird **nicht** als Anlass genommen, heimlich eine ungezielte Legacy-Einladung zu erzeugen.

## Legacy-Fallback

Bis zur vollständigen Android-Umstellung bleiben lesbar:

- manueller `TBI-...`-Code;
- `tbhelfer://invite?code=TBI-...`;
- alter Einlösepfad `/api/v1/collab/invitations/redeem`.

Nur Legacy-`TBI`-Codes werden in Großbuchstaben normalisiert.

Aktivierungslinks `tbhelfer://activate?... ` werden vom Baustelleneinladungsparser ausdrücklich nicht als Einladung akzeptiert.

## Android-Einstiegspunkte

### Deep Link

`AktivierungActivity` ist für `tbhelfer://worksite-invite` registriert.

Ist das Gerät bereits aktiviert, wird die Einladung direkt mit dem vorhandenen Geräte-Bearer eingelöst.

Ist noch keine Geräteaktivierung vorhanden, bleibt die Einladung im laufenden Aktivierungsdialog erhalten. Nach erfolgreicher Geräteaktivierung wird dieselbe Einladung automatisch eingelöst.

### QR-Scanner

`BaustellenSyncFragment` verwendet den bereits vorhandenen Google-Code-Scanner.

Akzeptiert werden:

- Server-1.0-App-URI;
- Server-1.0-Portal-Link;
- roher Server-1.0-Token;
- Legacy-`TBI`-Code/-URI.

### Manuelle Eingabe

Der vorhandene Dialog akzeptiert denselben Parser wie QR und Deep Link. Dadurch existiert keine zweite Tokeninterpretation.

## Implementierung

- `app/src/main/java/de/tbhelfer/ui/sync/WorksiteInvitationLink.kt`
  - zentraler Parser;
  - trennt `ServerToken` und `LegacyCode`;
  - erhält die Groß-/Kleinschreibung moderner Tokens.
- `TeamManagementApi.loadServerInvitationCandidates()` / `createServerInvitation()` / `redeemServerInvitation()`
  - laden konkrete Zielbenutzer;
  - erzeugen gezielte Server-1.0-Einladungen;
  - senden bei Mutationen `X-Client-Change-Id`;
  - behandeln fachliche 403-Einladungsfehler als Fachfehler und löschen deshalb nicht fälschlich den Geräte-Token.
- `AktivierungActivity`
  - Deep-Link-/Aktivierungsübergang.
- `BaustellenSyncFragment`
  - konkrete Personenauswahl und Server-1.0-Einladungserzeugung;
  - lokaler QR/Teilen-Dialog;
  - QR-/Copy-Paste-Einlösung.
- `AndroidManifest.xml`
  - registriert Host `worksite-invite`.

## Tests

`WorksiteInvitationLinkTest` prüft mindestens:

- moderne App-URI;
- Portal-Fragmentlink;
- rohen Server-Token;
- Legacy-`TBI`;
- Erhalt der Token-Groß-/Kleinschreibung;
- Ablehnung von Aktivierungs-/falschen Hosts.

Android CI führt künftig `:app:testDebugUnitTest` vor Debug-/Release-Build aus.

Zusätzlich prüfen
`server/checks/48_verify_android_server_1_invitation_source.py`
den gemeinsamen Server-/Android-Einlösevertrag und
`server/checks/50_verify_android_server_1_invitation_create_source.py`
die gezielte Kandidaten-/Create-Anbindung mit Legacy-Fallback und
`server/checks/51_verify_android_server_1_team_source.py`
den modernen Worksite-/Member-/Team-Aufbau mit begrenztem Legacy-Fallback statisch.

## Noch nicht Teil dieses Bausteins

Das Erzeugen und Einlösen gezielter Server-1.0-Einladungen sowie die Teamdarstellung aus den Server-1.0-Worksite-/Member-/Team-Endpunkten sind jetzt im Android-Quellstand angebunden. Noch nicht umgestellt sind die übrigen Baustellen-, BTB-, Datei- und Sync-Endpunkte; diese folgen erst nach bestandenem Server-Staging-/E2E-Test capability-gesteuert.

Produktivserver/NAS werden durch diesen Android-Quellstand nicht verändert.
