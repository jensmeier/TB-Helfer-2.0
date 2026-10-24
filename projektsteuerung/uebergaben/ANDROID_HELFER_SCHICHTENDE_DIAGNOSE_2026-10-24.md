# AND-HELPER-END-024 – Helfer-Schichtende, Diagnose

Stand24.10.2026. Android b315305846c95dfaacfbec0ad7baeba3ff376667 (Runtime unverändert gegenüber1.3.30/200080), Server25b40892affe3c9ea637c53029d3c508ded2d891. PR11 und Draft-PR6 gelesen. Nutzer meldet: Helfer kann BTB nicht schließen beziehungsweise Status kommt am Server nicht an.

## Fachlich verbindlich

Helfer beendet nur seine eigene Schicht über shift-finish. Der gemeinsame BTB bleibt für Haupt-TB/andere Teilnehmer offen. Nur der Haupt-TB finalisiert. Direktes WhatsApp-Teilen ist ausdrücklich NICHT gewünscht und kein offener Punkt; Galerie & Dateien bleibt ausreichend.

## Geprüfter Aufrufweg

1. HauptActivity installiert BtbModernLifecycleInstaller (Zeile72).
2. btnPdfFinal ruft closeCurrentBtb auf. Bei bestätigter Helferrolle zeigt der Watcher Schicht Ende bzw. Schicht fortsetzen.
3. BtbUiSaveBridge.speichernUndNeuLaden liest den kanonischen aktiven BTB; UI-Abschluss lädt zunächst per prepareAccess den aktuellen Serverstatus.
4. Ist participantStatus FINISHED, wählt die UI shiftResume, andernfalls shiftFinish.
5. BtbLifecycleCoordinator.shiftFinish ruft prepareAccess(uploadContent=true): bestehende lokale Einträge exportieren und hochladen, exakt zugehörige Fotos übertragen, aktuellen Serverstand erneut lesen. Jeder erkannte Fehler beendet den Ablauf VOR dem Schichtende-Aufruf.
6. Server1BtbApi.shiftFinish sendet POST /api/v1/btbs/{serverBtbId}/shift-finish. Bei HTTP-Erfolg wird danach loadBtbAccess aufgerufen. Erst dessen Erfolg führt zur lokalen Serverzustandsübernahme.
7. Server finish_own_shift prüft Rechte/Team/aktiven BTB und schreibt set_own_status(FINISHED) in tb.btb_participant_states. BTB_PARTICIPANT- und BTB-Events werden ausgesendet. Finale BTB-Freigabe prüft unfinished_participants mit Status WORKING.

## Belegte Schwachstellen des Android-Pfads

### A – Abschlusswunsch nicht dauerhaft vorgemerkt

Im geprüften Helfer-Lifecyclepfad wird die Schicht-Ende-Aktion direkt aus einer viewLifecycleOwner-Coroutine ausgeführt. Es gibt hier keinen persistenten Finish-Intent mit Wiederholungsworker. Fach-Outbox/SelectiveSync übertragen Inhalte/Fotos; ein später erfolgreicher Fachsync führt nicht automatisch shift-finish aus (Server1AndroidSyncCoordinator dokumentiert Lifecycle ausdrücklich nur als explizite Aktion).

Folge: Offline, Netzfehler, fehlende Capability, fehlende Baustellenbindung, Uploadkonflikt oder Fotofehler können verhindern, dass der Finish-POST überhaupt gesendet wird. Nach bloßem späteren Speichern/Synchronisieren kann der Teilnehmer deshalb weiter WORKING bleiben. Kein belegter Fehler des Status-UPDATE im Server allein daraus.

### B – Mehrfachklick und umkehrbare Aktion

closeCurrentBtb startet pro Klick eine neue Coroutine. In diesem Pfad gibt es keinen busy-/inFlight-/Mutex-Guard und kein temporäres Deaktivieren des Abschlussbuttons. Die gewählte Aktion wird erst nach dem asynchronen prepareAccess aus dem dann aktuellen Serverstatus bestimmt.

Möglicher Ablauf: erster Klick beendet die Schicht; ein weiterer gestarteter Klick liest inzwischen FINISHED und wählt shiftResume. Dann ist der Helfer wieder WORKING. Das ist eine aus dem Code abgeleitete Race-Möglichkeit, keine auf Pixel/Poco reproduzierte Ursache dieses Nutzerfalls.

### C – POST und anschließendes GET werden als ein Ergebnis behandelt

lifecycleMutation wertet einen erfolgreichen POST erst zusammen mit anschließendem loadBtbAccess als Success. Scheitert das GET, bleibt eine Fehleranzeige möglich, obwohl der Server FINISHED schon committed hat. Ein fehlender lokaler Erfolgshinweis beweist deshalb nicht, dass der Server das Schichtende nicht erhielt.

## Was nicht belegt ist

- Tatsächlich installierte APK-Version und eingesetzter NAS-Commit.
- Welche Meldung beim betroffenen Klick erscheint; ob danach Schicht Ende oder Schicht fortsetzen angezeigt wird.
- Ob Finish-POST gar nicht gesendet, erfolgreich committed oder anschließend durch Resume rückgängig gemacht wurde.
- Konkrete Rechte-/Netz-/Upload-/Fotofehlermeldung dieses Vorgangs.
- Kein adb/Pixel-/Poco-/NAS- oder HTTP-E2E-Zugriff in dieser Diagnose. Keine Produktionsdaten oder Tokens angefordert/geändert.

## Nächste gezielte Eingrenzung

Nutzerbeobachtung: genaue Meldung nach EINEM Klick auf Schicht Ende und danach angezeigter Buttontext. Serverdiagnose beim selben Benutzer, serverBtbId und Zeitpunkt: Teilnehmerstatus/finished_at sowie Audit BTB_SHIFT_FINISH und BTB_SHIFT_RESUME. Gleiche BTB-/Benutzer-UUID vergleichen, nicht allein Anzeigenamen.

Sinnvolle Folgekorrektur nach Diagnoseauftrag: explizites Ziel FINISHED beim Klick festhalten; während laufender Aktion sperren; echten Schichtende-Wunsch dauerhaft/outboxfähig speichern; erst nach bestätigtem Upload abschließen; nur FINISHED vom Server als beendet zeigen; erfolgreiche Mutation mit fehlgeschlagener Nachabfrage getrennt behandeln. Kein automatisches Resume, keine automatische Team-Finalisierung, keine lokalen Helferdaten verwerfen.

Dieser Block ändert nur Diagnose/Anforderungen. Keine Runtimekorrektur, APK, Versionsanhebung, CI oder NAS-Übernahme/Deployment. Bestehende98er Tests sind kein Nachweis dieses Fehlerfalls.
