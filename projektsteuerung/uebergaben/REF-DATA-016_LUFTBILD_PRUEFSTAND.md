# Luftbildanbieter – Prüfstand 04.10.2026

Die Auswahl bleibt freiwillig. Keine API-Schlüssel oder kostenpflichtigen Konten werden eingerichtet. Quellenangaben erscheinen am Kartenrand. Bildabruf erfolgt direkt vom Browser zum Landesdienst; Originalachsen werden dadurch nicht verschoben.

Direkte Capabilities-/Bildabrufe aus der Arbeitsumgebung endeten für BB/HE/TH/NW/RP mit HTTP403, NI mit Timeout. Dies belegt keine Störung auf der Synology. Anbieterangaben/Layer wurden soweit unten angegeben anhand offizieller Produkt-/Metadatenseiten geprüft. Ein sichtbarer Bayern-Hintergrund wurde vom Benutzer nachgewiesen. Weitere eingebundene Anbieter benötigen einen tatsächlichen Browser-Nachtest.

| Land | Stand |
|---|---|
| Bayern | REF015 eingebunden; Bild beim Benutzer sichtbar |
| Sachsen | REF015 eingebunden; Live-Bildnachtest offen |
| Baden-Württemberg | REF015 eingebunden; Live-Bildnachtest offen |
| Berlin | Mit Brandenburg über bebb_dop20c ergänzt; Metadaten geprüft |
| Brandenburg | bebb_dop20c ergänzt; WMS1.3, EPSG4326, korrekte Lat/Lon-BBOX |
| Hessen | he_dop20_rgb ergänzt; aktuelle Metadaten statt älterem he_dop_rgb-PDF |
| Nordrhein-Westfalen | nw_dop_rgb ergänzt; offizielle Produktseite/amtlicher TIM-Ausdruck |
| Thüringen | th_dop ergänzt; offizielles Produktblatt nennt URL, Layer und Lizenz |
| Bremen | Landesangebot verlinkt; konkreter aktueller Dienst/Layer und Bedingungen noch offen |
| Hamburg | Landesangebot verlinkt; alter Datensatz ausdrücklich zur Löschung vorgesehen, Nachfolger DOP-Zeitreihe prüfen |
| Mecklenburg-Vorpommern | Landesdienstübersicht verlinkt; konkrete Einbettung noch offen |
| Niedersachsen | OpenData-DOP-WMS bekannt, offizielles Portal verlinkt; konkreter aktueller Layer/Bildtest offen |
| Rheinland-Pfalz | DOP20-Metadaten und neuer Dienst bekannt, Portal verlinkt; konkrete Einbettung offen |
| Saarland | Landesangebot verlinkt; aktuellen Jahrgang/Layer/Bedingungen noch verifizieren |
| Sachsen-Anhalt | OpenData-WMS bekannt, Portal verlinkt; konkreter Layer/Bildtest offen |
| Schleswig-Holstein | Kostenfreier DOP20-Dienst offiziell beschrieben, Portal verlinkt; konkrete Einbettung offen |

Die acht zuletzt aufgeführten Länder sind über „Weitere Bundesländer …“ mit sichtbarem Prüfstatus erreichbar, **noch nicht als eingebetteter Hintergrund freigegeben**. Keine Behauptung einer fertigen bundesweiten Luftbildabdeckung.

## Offizielle Quellen

- NRW: https://www.bezreg-koeln.nrw.de/geobasis-nrw/produkte-und-dienste/luftbild-und-satellitenbildinformationen/aktuelle-luftbild-und-0 — kostenfreie DOP, WMS-Adresse, dl-de/zero-2-0. Layerbeleg amtlicher TIM-Ausdruck: https://www1.muelheim-ruhr.de/system/files/ausschreibungen/240118_Luftbild_1000_nicht_amtlich_2.pdf
- BB/BE: https://geobroker.geobasis-bb.de/gbss.php?MODE=GetProductPreview&PRODUCTID=7a503f0f-db46-4772-80e3-b27733fd7acd — WMS1.3.0, bebb_dop20c, EPSG4326, Attribution für beide Länder. Lizenzangaben verschiedener BB-Seiten zu Berlin unterscheiden sich; Einbettung übernimmt den konkreten verwendeten Dienstnachweis (dl-de/by-2-0 für beide).
- Hessen: https://opendata.hessen.de/dataset/atkis-dop-20/resource/af746f3f-2470-47f3-9ccd-a9adc1e779a9/view/69be3238-ea5a-440b-9d6b-907874b9eba1 und https://www.geoportal.hessen.de/mapbender/php/mod_showMetadata.php?id=54936&layout=tabs&redirectToMetadataUrl=1&resource=layer — he_dop20_rgb/EPSG4326; https://opendata.hessen.de/en/dataset/?groups=orthofoto&license_id=dl-zero-de%2F2.0&res_format=WMS — OpenData-Lizenz. Altes PDF2022 mit he_dop_rgb nicht als aktuellen Layer übernehmen.
- Thüringen: https://geoportal.geoportal-th.de/gaialight-th/_apps/dladownload/dl-lbop-info.html — DOP20-URL, th_dop, © GDI-Th / dl-de/by-2-0.
- Niedersachsen: https://opengeodata.lgln.niedersachsen.de/ und https://ni-lgln-opengeodata.hub.arcgis.com/pages/newsfeed — https://opendata.lgln.niedersachsen.de/doorman/noauth/dop_wms
- Sachsen-Anhalt: https://www.geodatenportal.sachsen-anhalt.de/gfds/de/gdp-open-data.html — ST_LVermGeo_DOP_WMS_OpenData/guest.
- Schleswig-Holstein: https://www.schleswig-holstein.de/DE/landesregierung/ministerien-behoerden/LVERMGEOSH/Service/serviceGeobasisdaten/serviceWms/serviceGeobasisdatenWmsDop20 — kostenfreier Dienst; Metadatenlink führte hier nur auf Suchanwendung.
- MV: https://www.geoportal-mv.de/portal/Geowebdienste/Fachthemen/Luftbilder_und_Satellitenbilder
- Hamburg: https://suche.transparenz.hamburg.de/dataset/digitale-orthophotos-hamburg9 — Ablösung durch DOP-Zeitreihen, keinen alten Jahreslayer blind einbauen.
- Bremen: https://www.geo.bremen.de/produkte/luftbildprodukte-11703
- Rheinland-Pfalz: https://www.geoportal.rlp.de/mapbender/php/mod_showMetadata.php?id=3398&languageCode=de&layout=tabs&resource=wms und https://lvermgeo.rlp.de/service/aktuelles/detail/vorabinformation
- Saarland: https://www.shop.lvgl.saarland.de/index.php?Itemid=179&option=com_virtuemart&view=category&virtuemart_category_id=1022

WMS1.3.0 mit EPSG4326 verlangt Lat/Lon-BBOX; vorhandene 1.1.1-Anbieter behalten Lon/Lat. Neuer Node-Test deckt diese Trennung ab. Grobe Landes-Boundingbox dient nur als Anbietervorschlag, nicht als amtliche Landesgrenze. In Grenzbereichen Anbieter ausdrücklich wählen.
