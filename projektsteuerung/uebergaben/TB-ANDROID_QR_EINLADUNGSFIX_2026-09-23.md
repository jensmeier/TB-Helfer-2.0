# TB-Helfer 2.0 – Android-QR-Einladungsfix

Stand: 2026-09-23  
SERVER-Branch: `feature/tb-server-1.0`  
ANDROID-Branch geprüft: `feature/tb-android-2.0` @ `3ea67aa1542a47f4057dca38014a5e73f1e6717f`

## Reproduzierter Integrationsfehler

Der Server erzeugt für Server-1.0-Baustelleneinladungen den modernen, case-sensitiven Deep-Link:

```
tbhelfer://worksite-invite?token=<server-token>
```

Der gemeinsame Android-Parser `WorksiteInvitationLink.parse(...)` kann diesen Link bereits korrekt verarbeiten.  
`TeamManagementApi.redeemServerInvitation(...)` existiert ebenfalls bereits und verwendet:

```
POST /api/v1/worksites/invitations/redeem
```

Der **direkte QR-Scanner in der Baustelle** umgeht diesen vorhandenen Vertrag jedoch noch.

Betroffene Datei:

```
app/src/main/java/de/tbhelfer/ui/baustelle/BaustellenTeamDirekt.kt
```

Aktuell:

- `qrScannen()` ruft `extractInvitationCode(...)` auf;
- `extractInvitationCode(...)` akzeptiert nur Legacy `TBI-...` bzw. `tbhelfer://invite?code=TBI-...`;
- `redeemInvitationCode(...)` ruft ausschließlich `teamApi.redeemInvitation(...)` auf;
- damit wird der neue Server-1.0-QR im direkten Baustellen-Scanner verworfen.

## Verbindlicher Android-Fix

1. In `BaustellenTeamDirekt.kt` importieren:

```kotlin
import de.tbhelfer.ui.sync.WorksiteInvitationLink
```

2. In `qrScannen()` nicht mehr `extractInvitationCode(...)`, sondern den vorhandenen gemeinsamen Parser verwenden:

```kotlin
val invitation = WorksiteInvitationLink.parse(barcode.rawValue.orEmpty())
if (invitation == null) {
    Toast.makeText(
        fragment.requireContext(),
        "Kein gültiger TB-Helfer-Baustellen-QR.",
        Toast.LENGTH_LONG
    ).show()
} else {
    redeemInvitationCredential(fragment, invitation)
}
```

3. Den parallelen privaten Legacy-Parser `extractInvitationCode(...)` vollständig entfernen.

4. `redeemInvitationCode(fragment, code)` durch einen Credential-basierten Pfad ersetzen:

```kotlin
private fun redeemInvitationCredential(
    fragment: Fragment,
    invitation: WorksiteInvitationLink.Credential
) {
    val tokenStore = SecureTokenStore(fragment.requireContext())
    val token = tokenStore.load()
    if (token.isNullOrBlank()) {
        Toast.makeText(
            fragment.requireContext(),
            "Gerät ist noch nicht für den Server aktiviert.",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    fragment.viewLifecycleOwner.lifecycleScope.launch {
        Toast.makeText(
            fragment.requireContext(),
            "Baustelle wird übernommen …",
            Toast.LENGTH_SHORT
        ).show()

        val redeem = withContext(Dispatchers.IO) {
            when (invitation) {
                is WorksiteInvitationLink.Credential.ServerToken ->
                    teamApi.redeemServerInvitation(token, invitation.value)

                is WorksiteInvitationLink.Credential.LegacyCode ->
                    teamApi.redeemInvitation(token, invitation.value)
            }
        }

        // vorhandene RedeemResult-/Sync-/Binding-/Öffnen-Logik unverändert weiterverwenden
        ...
    }
}
```

Wichtig: Die vorhandene Behandlung von `TeamManagementApi.RedeemResult` und anschließend `syncJoinedWorksite(...)`, `showOpenJoinedWorksiteChoice(...)` bzw. `showBindingChoice(...)` **nicht neu bauen**. Nur den Credential-Einstieg vereinheitlichen.

## Schutzregeln

- Moderne Server-Tokens sind **case-sensitive** und dürfen niemals `uppercase()` durchlaufen.
- Legacy-`TBI-...` bleibt während der Übergangszeit unterstützt.
- Keine Änderung am Server-Endpoint nötig.
- Kein zweiter QR-/Redeem-Vertrag.
- Bestehende Geräteaktivierung `tbhelfer://activate?... ` bleibt unverändert.
- Bestehende `WorksiteInvitationLinkTest`-Tests müssen weiterhin grün bleiben.

## Integrationscheck

Der SERVER-Source-Check

```
server/checks/48_verify_android_server_1_invitation_source.py
```

wurde erweitert. Er erwartet künftig auch im direkten Baustellen-Scanner:

- `WorksiteInvitationLink.parse(barcode.rawValue.orEmpty())`
- `Credential.ServerToken` -> `redeemServerInvitation(...)`
- `Credential.LegacyCode` -> `redeemInvitation(...)`
- **kein** `extractInvitationCode(...)` mehr.

## Praktischer E2E-Test nach Android-Build

1. Pixel als verantwortlicher TB: frische Baustelleneinladung erzeugen.
2. Zweites aktiviertes Gerät: in der Baustelle **QR scannen**.
3. Neuer QR `tbhelfer://worksite-invite?token=...` muss akzeptiert werden.
4. API-Log muss `POST /api/v1/worksites/invitations/redeem` erfolgreich zeigen.
5. Eingeladene Baustelle synchronisiert sich lokal.
6. Rolle prüfen: LEAD bzw. HELPER entsprechend Einladung.
7. Bestehende aktuelle Baustelle darf nicht ungefragt ersetzt werden.
8. Legacy-`TBI-`-QR regressiv prüfen, sofern noch benötigt.
9. Token-Case mit gemischter Groß-/Kleinschreibung darf nicht verändert werden.

## Status

**URSACHE BESTÄTIGT / ANDROID-FIX EXAKT DEFINIERT / UMSETZUNG + BUILD + INSTALLATION + E2E IM ANDROID-PROJEKT OFFEN**

Der SERVER-Branch ändert gemäß Projekttrennung kein `app/**`.
