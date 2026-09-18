# TB-Helfer Android – Server-1.0-Baustelleneinladungen

Stand: 2026-09-18

Dieses Dokument beschreibt den Android-Übergangsvertrag für die bereits serverseitig eingeführten gezielten Baustelleneinladungen.

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
- `TeamManagementApi.redeemServerInvitation()`
  - ruft den Server-1.0-Endpunkt auf;
  - sendet `X-Client-Change-Id`;
  - behandelt gezielte 403-Einladungsfehler als Fachfehler und löscht deshalb nicht fälschlich den Geräte-Token.
- `AktivierungActivity`
  - Deep-Link-/Aktivierungsübergang.
- `BaustellenSyncFragment`
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

Zusätzlich prüft
`server/checks/48_verify_android_server_1_invitation_source.py`
den gemeinsamen Server-/Android-Vertrag statisch.

## Noch nicht Teil dieses Bausteins

Android erzeugt in diesem Schritt noch keine neue gezielte Server-1.0-Einladung. Das bestehende Legacy-Erzeugen bleibt während der Serverumstellung bestehen.

Die vollständige Android-Umstellung von Baustellen-, Team-, BTB-, Datei- und Sync-Endpunkten auf Server 1.0 erfolgt erst nach bestandenem Server-Staging-/E2E-Test.

Produktivserver/NAS werden durch diesen Android-Quellstand nicht verändert.
