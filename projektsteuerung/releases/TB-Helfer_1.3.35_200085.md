TB-HELFER-RELEASE
PLATTFORM: ANDROID
VERSION: 1.3.35
BUILD: 200085
MINIMUM_BUILD: 200067
PFLICHTUPDATE: NEIN
DOWNLOAD_URL:

NEU:
- BTB-Verlauf: Verantwortlicher kann ein abgeschlossenes eigenes BTB nach Bestätigung zur Nachbearbeitung öffnen. Eine andere laufende Schicht bleibt aktiv; gleicher BTB und gleiche Eintrags-IDs bleiben erhalten. Benötigt passenden Serverstand BTB-INTEGRATION-031.
- Eindeutige gleiche Bildbeschreibung (z.B. Schwellenwechsel) innerhalb desselben BTB, Punkts, derselben Firma und Unterbaustelle kann die fehlende Bildverknüpfung ergänzen. Vorhandene Verknüpfungen und ausdrücklich gelöste Zuordnungen haben Vorrang. Mehrdeutige Ziele bleiben offen.

VERBESSERT:
- Android-Abschluss startet nach Klick einen gezielten Versuch mit dauerhaftem Hintergrund-Retry. Start, App-Rückkehr und wieder verfügbares Netz holen ausstehende Abschlüsse nach. Fortschritt zeigt echte Phasen; erneuter Klick zeigt den vollständigen Wartegrund. Kein zusätzlicher Portalabschluss als normaler Ablauf vorgesehen; praktische Abnahme noch offen.
- Firma und Unterbaustelle im normalen BTB-Eintrags- und Fotogruppen-Dialog korrigieren; Speicherübergabe des Eintrags vor Neuladen repariert. Neue Fotos nach Kurztext eindeutig im selben BTB/Punkt/Firma/Ort binden. Langtexteingabe fragt nach Kurztext; Android-BTB und Druck zeigen beide Texte. Originalbilder bleiben unverändert.
- Wetterantworten nach BTB-/Datums-/Schicht-/Eingabewechsel verwerfen; Standortmetadaten erst mit akzeptierter Antwort setzen. Konkrete Wetterabweichung am Gerät noch offen.
- PDF-Bildanhang mit drei Bildern pro Zeile und Nutzung der verfügbaren Seitenbreite, unabhängig von Hoch-/Querformat. Server-Portal ebenfalls drei Karten je Zeile; Server-PDF mit größerem Bildbereich.
- Strukturierte Firmen-/Unterbaustellenzuordnung erreicht Android-PDF und Word. Zugeordnete Bildnummern am manuellen Eintrag, keine zusätzliche Textzeile je verbundenem Foto.
- Abschluss überspringt den Download fremder Medien; eigene Uploads/Änderungen bleiben Voraussetzung. Worker wartet bei erlaubtem Mobilfunk nicht mehr pauschal auf ungetaktetes Netz; vorhandene Netzrichtlinien bleiben geprüft.
- Gesamtbilderliste verwendet bestätigte lokale Foto/Server-Bindung zur Erkennung von Original und Sync-Version; korrigierte Linkmetadaten statt allein Dateinamen. Fehlende Bilddateien/Katalogfehler melden einen unvollständigen Export.

BEHOBEN:
- Manuelles Löschen wird sofort gespeichert; Bilddateien werden beim Löschen eines Eintrags nicht gelöscht, sondern gelöst. Firmen-/Orts-/Punktwechsel behalten bestehende Eintrags-Fotoverknüpfung.
- Doppelpunkt im Text (z.B. 1stab 13:02) wird ohne bekannte Firma nicht als Firmenname interpretiert. Allgemeine Einträge dürfen ihre Unterbaustelle behalten.

TESTSTATUS:
- Runtimecommit5291d522a5cb7a8826d7d9190545ed3005d04b12, Android-CI41498293472: Androidjob125458915143 SUCCESS. Kotlin/KSP/Java-Kompilierung und testDebugUnitTest PASS;122 Tests ohne Fehler/Fehlschläge/übersprungene Fälle laut gelesenem JUnit-XML. Enthält die8 BtbIntegration031- und6 BtbPhotoDescription-Tests.
- lintDebug PASS:0 Error/0 Fatal,395 Warning laut gelesenem Lint-XML. KSP/AWT-NullPointer-Diagnose im Gradlelog vorhanden, Tasks/Job dennoch SUCCESS; nicht als vollständig fehlerfreies Tooling ausgeben.
- Gesamt-CI ROT: bekannte fünf Quellvertragsprüfungen13/19/83/85/86; unverändert, nicht abgeschwächt. Kein assemble/APK-/Installationsnachweis. Downloadfeld bleibt leer; diese MD keinem älteren APK zuordnen.
- Frühere Server-/Portal-/PDF-Funktionsnachweise für BTB-INTEGRATION-031 bleiben dokumentiert, in diesem Androidblock nicht erneut ausgeführt. Kein neuer PostgreSQL-/HTTP-/NAS-E2E.
- Keine Veröffentlichung, kein NAS-Deployment, keine Datenbereinigung. Abschlussgeschwindigkeit, WorkManager/Prozessneustart/Netzrückkehr und echter Handyabschluss ohne Portalaktion praktisch OFFEN.

ABNAHME NACH BUILD UND SERVER-ÜBERNAHME:
1. Als Update ohne Deinstallation installieren; zwei Testnutzer (Verantwortlicher/Helfer), freigegebene Testbaustelle.
2. Sieben eindeutige Fotos erfassen/synchronisieren, zweimal exportieren: sieben Dateien, sieben Tages-/Gesamtzeilen, alle Links öffnen.
3. Schwellenwechsel eingeben: passende Fotos derselben Firma/Unterbaustelle/Punkt einmal zugeordnet; fremde Firma/Ort und doppelte Zieltexte bleiben getrennt.
4. Eintrag ändern, andere Firma wählen, Ort/Punkt wechseln und löschen; Neustart, Server sowie PDF/Word vergleichen. Stabile IDs, keine verloren gegangenen Bilder.
5. Helfer arbeitet, beendet nur Teilnahme; danach schreibgeschützt. Verantwortlicher beendet BTB; Offline zeigt ausstehend, Serverabschluss erst nach Bestätigung. Fehler/Retry ohne doppelten Abschluss.
6. Neue Schicht starten, altes BTB nachbearbeiten und erneut abschließen: neue Schicht bleibt aktiv; aktuelle PDF/Word erneuert, Ursprungsstand erhalten.
7. Android-/Server-PDF und Portal: drei Bilder pro Zeile, keine abgeschnittenen Beschriftungen; Wetter vor/nach Serverbestätigung separat prüfen.


APK-PRUEFUNG DIESES BUILDS:
VARIANTE: DEBUG
BAU_COMMIT: 510779453c85a25cefa2c1c0d886844ba1a41c4d
APK_SHA256: 8749a52bcda74af47e19998774c434c7e038dc471f0886b109606887a49fe627
APK_BYTES: 26784582
MANIFEST: de.tbhelfer / 1.3.35 / 200085
UPDATEKOMPATIBILITAET: Zertifikat der installierten App nicht verglichen; bei Signaturabweichung nicht deinstallieren.
SIGNATURPRUEFUNG:
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v3.2 scheme (APK Signature Scheme v3.2): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
V2 Signer: certificate DN: C=US, O=Android, CN=Android Debug
V2 Signer: certificate SHA-256 digest: cac77b36f13253f39b4feecd74ebcc8442361467f3eaefaef8f41a734b5a7b76
V2 Signer: certificate SHA-1 digest: 3b38ab33933a3de321cce101ea7d8e18cfa1c841
V2 Signer: certificate MD5 digest: 5cd06d7b49db19a618d614b1c5f6a203
V2 Signer: key algorithm: RSA
V2 Signer: key size (bits): 2048
V2 Signer: public key SHA-256 digest: c12a44d391f5de782dcb4807663b9a611c7a79424c6f7d7e520d34a2a44f8882
V2 Signer: public key SHA-1 digest: 3efe3ca4d94c6be140bf6029df6c2d3e708f11ee
V2 Signer: public key MD5 digest: 4b0759bcf2fd0e75519e3041ca21e723
WARNING: META-INF/com/android/build/gradle/app-metadata.properties not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.activity_activity-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.activity_activity.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.annotation_annotation-experimental.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.appcompat_appcompat-resources.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.appcompat_appcompat.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.arch.core_core-runtime.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.camera.featurecombinationquery_featurecombinationquery.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.camera_camera-camera2.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.camera_camera-core.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.camera_camera-extensions.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.camera_camera-lifecycle.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.camera_camera-video.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.camera_camera-view.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.cardview_cardview.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.coordinatorlayout_coordinatorlayout.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.core_core-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.core_core-viewtree.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.core_core.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.cursoradapter_cursoradapter.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.customview_customview-poolingcontainer.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.customview_customview.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.datastore_datastore-core.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.datastore_datastore-preferences-core.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.datastore_datastore-preferences.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.datastore_datastore.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.drawerlayout_drawerlayout.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.dynamicanimation_dynamicanimation.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.emoji2_emoji2-views-helper.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.emoji2_emoji2.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.exifinterface_exifinterface.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.fragment_fragment-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.fragment_fragment.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.graphics_graphics-shapes.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.interpolator_interpolator.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-livedata-core-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-livedata-core.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-livedata.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-process.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-runtime-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-runtime.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-service.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-viewmodel-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-viewmodel-savedstate.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.lifecycle_lifecycle-viewmodel.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.loader_loader.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.navigation_navigation-common-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.navigation_navigation-common.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.navigation_navigation-fragment-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.navigation_navigation-fragment.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.navigation_navigation-runtime-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.navigation_navigation-runtime.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.navigation_navigation-ui-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.navigation_navigation-ui.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.profileinstaller_profileinstaller.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.recyclerview_recyclerview.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.room_room-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.room_room-runtime.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.savedstate_savedstate-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.savedstate_savedstate.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.slidingpanelayout_slidingpanelayout.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.sqlite_sqlite-framework.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.sqlite_sqlite.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.startup_startup-runtime.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.tracing_tracing-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.tracing_tracing.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.transition_transition.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.vectordrawable_vectordrawable-animated.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.vectordrawable_vectordrawable.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.versionedparcelable_versionedparcelable.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.viewpager2_viewpager2.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.viewpager_viewpager.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.window_window.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.work_work-runtime-ktx.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx.work_work-runtime.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx/annotation/annotation/LICENSE.txt not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx/collection/collection-ktx/LICENSE.txt not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx/collection/collection/LICENSE.txt not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx/datastore/datastore-core-okio/LICENSE.txt not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx/datastore/datastore-preferences-external-protobuf/LICENSE.txt not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx/datastore/datastore-preferences-proto/LICENSE.txt not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx/lifecycle/lifecycle-common/LICENSE.txt not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/androidx/room/room-common/LICENSE.txt not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/com.google.android.material_material.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/kotlinx_coroutines_android.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/kotlinx_coroutines_core.version not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
WARNING: META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory not protected by signature. Unauthorized modifications to this JAR entry will not be detected. Delete or move the entry outside of META-INF/.
