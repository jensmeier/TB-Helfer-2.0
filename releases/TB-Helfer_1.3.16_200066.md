TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.16
BUILD: 200066
MINIMUM_BUILD:
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

BEHOBEN:
- Der direkte QR-Scanner in der Baustelle akzeptiert jetzt Server-1.0-Baustelleneinladungen vom Typ tbhelfer://worksite-invite?token=...
- Der direkte Baustellen-Scanner verwendet jetzt denselben gemeinsamen WorksiteInvitationLink-Parser wie die übrigen Einladungswege
- Moderne case-sensitive Server-Tokens werden unverändert an POST /api/v1/worksites/invitations/redeem übergeben
- Legacy-TBI-Einladungen bleiben während der Übergangszeit unterstützt
- Der parallele private Legacy-Parser im direkten Baustellen-Scanner wurde entfernt

UNVERÄNDERT:
- Die vorhandene RedeemResult-/Sync-/Binding-/Öffnen-Logik wird nach erfolgreichem Einlösen weiterverwendet
- Geräteaktivierung tbhelfer://activate?... bleibt unverändert
- Bestätigter BTB-Lifecycle-Fix aus 1.3.13 / 200063 bleibt geschützt
- BTB-Bildnachtrag, horizontale Bildreihe und Verschieben-Funktionen aus 200064/200065 bleiben enthalten

SERVERVERTRAG:
- Server-1.0-Link: tbhelfer://worksite-invite?token=<case-sensitives-token>
- Server-1.0-Redeem: POST /api/v1/worksites/invitations/redeem
- Legacy-Fallback: TBI-* bzw. tbhelfer://invite?code=TBI-*
- Keine Serveränderung für diesen Android-Fix erforderlich

TESTSTATUS:
- Verbindliche Übergabe TB-ANDROID_QR_EINLADUNGSFIX_2026-09-23.md aus dem Serverbranch gelesen
- Source-Checks für Parser, Credential-Zweige, Redeem-Endpunkte und Legacy-Fallback bestanden
- Bestehende WorksiteInvitationLinkTest-Testfälle bleiben unverändert vorhanden
- Android-Build 200066 noch offen
- Installation und praktischer QR-E2E gegen den laufenden Server noch offen
