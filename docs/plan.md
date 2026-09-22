# Rollercoaster Editor — Umsetzungsplan

Status: Phase 1-6 sind umgesetzt - Projektkern, Karten, Modelle, Sprite-Atlanten,
tile-gebundene Entities mit Schemas, Lichter, Übergänge, Dialoge, Events, Testmodus,
Event-/Dialogausführung, Vorab-Validierung, anklickbare Diagnosen, ein Example Game ohne
prozedurale Kartensonderbehandlung, ein gemessenes Lasttest-/Reaktionszeitbudget, eine verifizierte
native macOS-Distribution (Windows/Linux nur strukturell vorbereitet) und ein auf einem echten
Android-Emulator geprüftes Example Game (iOS-Simulatortest an dieser Maschine blockiert). Der
Editor ist ein eigenes Repository neben `rollercoaster`, `example-game` und `trackside`.
Trackside-Inhalte sind zunächst außerhalb des Arbeitsumfangs.

## Ziel und erster vollständiger Arbeitsablauf

Ein Projekt öffnen oder anlegen, einzelne Texturen importieren, daraus ein Tileset erstellen,
eine Karte bemalen, Höhen und Rampen bearbeiten, ein GLTF/GLB-Haus registrieren und auf einem
Plateau platzieren. Anschließend Startpunkt, NPC und eine Interaktion setzen, die Karte in der
Engine testen, speichern und für das Example Game exportieren. Für diesen Ablauf sollen keine
JSON-Dateien von Hand und keine spielespezifischen Java-Klassen geschrieben werden müssen.

Der Editor bearbeitet Karten, Asset-Registrierungen und Platzierungen. Die Modellierung eines
Hauses selbst erfolgt weiterhin in einem externen 3D-Werkzeug. Eigene Tools für Pixelgrafik,
Animationsmalerei, Skriptentwicklung und Mehrbenutzerbearbeitung sind kein Bestandteil der
ersten Version.

## Verantwortung der Repositories

| Repository | Verantwortung |
| --- | --- |
| `rollercoaster` | Runtime-Formate, Validierung, Asset-Lader, Terrain, Kollision, Rendering und Spielsystem-Schnittstellen |
| `rollercoaster-editor` | Projektverwaltung, Import, Bearbeitung, Undo/Redo, Vorschau und Export |
| `example-game` | Ausführbarer Verbraucher und Integrationstest der exportierten Daten |
| `trackside` | Spätere eigene Inhalte und Gameplay auf derselben Engine |

Die Engine hängt niemals vom Editor ab. Die Vorschau bindet die Engine als Bibliothek ein.
Lokal ist ein Composite Build mit `../rollercoaster` vorgesehen; für Releases wird eine
konkrete Bibliotheksversion festgelegt. Ein Export enthält die für das Spiel benötigten Assets
und Dokumente, jedoch keine Editor-Klassen.

Events, Dialoge und Beleuchtung gehören ebenfalls zur Kartenautorenschaft. Diese Daten werden als
stabile IDs und referenzierte Definitionen gespeichert, damit sie bei Umbenennungen und
Übersetzungen nicht an Bildschirmtext oder Dateipositionen hängen.

- **Events:** Auslöser wie Kartenstart, Interaktion, Betreten einer Fläche und Zeitwechsel;
  Bedingungen über Flags, Variablen und Tageszeit; Aktionen wie Dialog starten, NPC bewegen,
  Tür öffnen, Karte wechseln, Flag setzen und Licht schalten.
- **Dialoge:** Sprecher-ID, lokalisierbare Text-ID, Porträt, Antwortoptionen, Bedingungen und
  Sprünge. Die Verzweigung referenziert nur stabile Knoten-IDs; der Editor braucht eine Vorschau
  und eine Prüfung auf fehlende Sprachvarianten.
- **Beleuchtung:** globale Sonnen- und Umgebungsparameter, Tageszeit und Tagesverlauf sowie
  platzierbare Punkt- und Spotlichter mit ID, Position, Farbe, Intensität, Reichweite und
  Aktivierungszustand. Presets und Kurven gehören in die Editorquelle; der Export enthält die
  Runtime-Werte. Schattenoptionen werden separat aktiviert.

Events können Dialoge und Lichtquellen auslösen. Die Engine stellt dafür Runtime-Schnittstellen
bereit; konkrete Inhalte und Spielregeln bleiben im Spielprojekt. Der Editor bietet nur Felder an,
deren Runtime-Verhalten geprüft und exportierbar ist.

## Technischer Vorschlag

Die Editor-UI ist eine gewöhnliche Java-Desktop-Anwendung. Die 3D-Vorschau läuft als
**eigener Prozess** mit libGDX/LWJGL3 in einem externen Fenster und benutzt dieselbe
Renderpipeline und Beleuchtung wie das Spiel.

Diese Trennung ist keine Stilfrage, sondern erzwungen: LWJGL3 braucht auf macOS
`-XstartOnFirstThread`, und AWT/Swing braucht denselben Thread für seinen NSApplication-Runloop.
Beides im selben Prozess hängt sich auf — geprüft: eine JVM mit `-XstartOnFirstThread` bekommt
kein Swing-Fenster mehr. JavaFX hat über das Glass-Toolkit dieselbe Bindung, und libGDX liefert
seit dem LWJGL3-Backend keine AWT-Canvas-Klasse mehr, in die man den Viewport einbetten könnte.
Ein eingebetteter Viewport wäre also ein eigenes libGDX-Backend auf `lwjgl3-awt` — genau auf der
Zielplattform am unzuverlässigsten.

Folgen für den Entwurf:

- Die UI-Seite rendert kein 3D. Auswahl, Eigenschaften, Assetlisten, Diagnosen und die
  2D-Kartenansicht leben im UI-Prozess.
- Der Vorschauprozess bekommt das Dokument als Daten und meldet Ereignisse zurück: Picking-Treffer,
  Kameralage, Testlaufzustand. Der Kanal ist ein Implementierungsdetail (lokaler Socket genügt),
  aber das Protokoll gehört versioniert wie jedes andere Dokumentformat.
- Der Vorschauprozess ist zustandsarm und jederzeit neu startbar. Stürzt er ab, bleibt der Editor
  bedienbar; das ist ein Vorteil der Trennung, kein Notbehelf.
- Kein Editor-Zustand wird ausschließlich in der Vorschau gehalten. Die Wahrheit liegt im
  Dokumentmodell des UI-Prozesses.

Vorgesehene Gradle-Module:

```text
rollercoaster-editor/
  docs/                       Architektur, Datenverträge und Bedienung
  document/                   Editor-Dokumente, Commands, Historie und Serialisierung
  asset-pipeline/             Import, Atlas-Packing, Validierung und Export
  editor/                     Java-Desktop-UI, Werkzeuge und Projektverwaltung
  preview-protocol/           Nachrichtenformat zwischen UI und Vorschau
  preview/                    libGDX/LWJGL3-Vorschauprozess und Testmodus
  platforms/desktop/          Launcher und Distribution beider Prozesse
```

`document`, `preview-protocol` und die nichtgrafischen Teile der Asset-Pipeline müssen ohne
OpenGL testbar sein. Desktop-Code darf ein modernes JVM-Level verwenden; wiederverwendete
Engine-Klassen behalten das von Android/iOS benötigte Sprachlevel. Mobile Editor-Oberflächen
sind nicht geplant.

### Beleuchtung in der Vorschau

Die Vorschau nutzt die Engine-Beleuchtung unverändert: `LightingEnvironment` (Umgebungslicht,
Sonnenrichtung/-farbe/-intensität), `PointLightSource` (Position, Farbe, Intensität, Reichweite,
Schaltzustand) und `DayNightCycle` (Tageszeit, Sekunden pro Tag, Pause). Zwei harte Grenzen
gehören in die Editorvalidierung, nicht erst in den Export:

- `LightingEnvironment.MAX_POINT_LIGHTS` ist 8, und Punkt- **und** Spotlichter teilen sich dieses
  Budget. Mehr Lichter pro Karte muss der Editor abweisen oder sichtbar nach Relevanz beschneiden,
  statt sie still zu verlieren.
- Ein Spotlicht ist dieselbe `PointLightSource` mit `setSpot(richtung, innenWinkel, außenWinkel)`;
  die Winkel sind volle Kegelwinkel in Grad, `außen > innen` und höchstens 180. Der Editor bietet
  Position, Richtung, beide Winkel, Farbe, Intensität, Reichweite und Schaltzustand an und
  schaltet mit `setPoint()` zurück.
- Schatten sind in der Engine noch in Arbeit. Das Editorfeld dafür bleibt aus, bis die Runtime
  eine stabile Schnittstelle hat — ein Schalter ohne Wirkung ist schlimmer als keiner.

Der Editor setzt Tageszeit und Schaltzustände für reproduzierbare Tests direkt; der laufende
Tagesverlauf ist dabei pausierbar.

## Gemeinsame Datenverträge vor der Implementierung

Die vorhandenen `MapLoader`, `ModelManifest`, `TilesetManifest`, `TextureTileset` und
`WorldSceneLoader` sind Integrationspunkte, keine bereits vollständige Editor-API. Insbesondere
sind Tilesets bislang auf Atlasregionen und flache Prototypen beschränkt. Schreibpfade,
Projektverwaltung und editorfähige Auflösung externer Assets müssen erst entstehen.

- Karten, Tiles, Modelle, Sprites, Entities und Trigger erhalten stabile IDs. Anzeigenamen,
  Dateinamen und Atlaspositionen dürfen sich ändern, ohne Platzierungen umzudeuten.
- Quelldokumente enthalten editierbare Daten statt GPU-Objekten. Die Engine erzeugt daraus
  ihre Runtime-Daten. Speichern und erneutes Öffnen erhalten alle unterstützten Eigenschaften.
- Engine-Dokumente und Editor-Projektdatei haben explizite Versionen. Unbekannte Versionen
  werden mit einer verständlichen Meldung abgewiesen. Migrationen erfolgen gezielt; ungelesene
  Felder dürfen beim Speichern nicht still verloren gehen.
- Assetpfade sind relativ zum Projekt beziehungsweise Exportpaket. GLTF-Buffer und Texturen
  werden beim Import mitgenommen und referenziert. Ein verschobener Projektordner bleibt nutzbar.
- Ein austauschbarer Asset-Resolver erschließt Editor-Dateisystem und verpackte Spielressourcen.
  Die heutigen Classpath-Pfade dürfen nicht zur zweiten, widersprüchlichen Ladepipeline führen.
- Raster, Weltkoordinaten, Texturursprung, Drehung, Maßstab und Pivot werden dokumentiert und
  getestet. Welt-Y ist die Höhe, X/Z die Kartenebene. Der bestehende Tile-Mittelpunkt-Versatz
  wird zentral aus der Engine übernommen. Die bisherige JSON-Bezeichnung `y` für Raster-Z
  benötigt einen eindeutigen Adapter oder eine versionierte Migration.
- Die Map-Tileset-Referenz wird tatsächlich aufgelöst und validiert. Ob eine Karte mehrere
  Tilesets direkt referenziert oder der Export sie zusammenführt, wird vor dem Formatabschluss
  entschieden; Tile-IDs dürfen dabei nicht kollidieren.
- Modellmaße werden aus dem Import abgeleitet und ihre Beziehung zu Maßstab, Höhe, Anker und
  Kollisionsform explizit gemacht. Kollision darf kein zweites unabhängig gepflegtes Abbild
  derselben Modellplatzierung werden.
- Das `source`-Feld des Modellmanifests kodiert bereits das **Format** (`gltf:`/`glb:`) und wird
  gegen die Dateiendung geprüft. Der Asset-Resolver braucht für den **Ort** einen eigenen Kanal;
  heute landet jeder Pfad hart auf `Gdx.files.classpath`.
- Ein Sprite-Atlas ist wie ein Tileset auf **eine** Seite beschränkt: `BillboardRenderer`
  verlangt, dass alle Regionen zur selben `Texture` gehören. Der Editor muss das beim Packen
  erzwingen, nicht erst beim Laden scheitern.
- Die Texturorientierung ist festgelegt: die Oberkante eines Billboards bildet auf `region.getV()`
  ab, also auf die **obere** Bildkante. Ein Atlas wird normal orientiert gepackt.
- Picking gegen die Spielkamera muss `PixelCamera.snapToPixelGrid` berücksichtigen. Das Snapping
  verändert `projection`/`combined` nach `camera.update()`, während `invProjectionView` — die
  Grundlage von `unproject` — und das Frustum nur *in* `update()` neu berechnet werden. Ohne
  Korrektur liegt der Cursor systematisch bis zu ein Low-Res-Pixel daneben, bei Faktor 4 also
  vier Bildschirmpixel. Entweder die Vorschau pickt gegen die freie Editorkamera ohne Snap, oder
  die Engine bekommt einen Aufruf, der die invertierte Matrix nach dem Snap nachzieht.

### Terrain-Vertrag der Engine

Der Vertrag steht in `rollercoaster` und wird vom Editor aufgerufen, nicht nachgebaut.

- **`TileShape`** — `FLAT` und `RAMP_NORTH/EAST/SOUTH/WEST`. Eine Rampe überbrückt genau ein
  Level über die Tile-Tiefe. Die gespeicherte Höhe eines Tiles ist seine Oberflächenhöhe **in der
  Tile-Mitte**; bei einer Rampe also der Mittelwert, nicht Ober- oder Unterkante. Form und
  Begehbarkeit hängen am Tile-Typ (`TilePrototype`), damit Geometrie und Regel nicht
  auseinanderlaufen können.
- **Drei Terrainarten** wie gefordert: begehbare Rampe (`RAMP_*`, `walkable`), nicht begehbare
  Rampe (`RAMP_*`, nicht `walkable` — sie steigt sichtbar an, ist aber Kulisse) und Klippe, die
  sich aus dem Höhenunterschied zwischen Nachbarn ergibt und keine eigene Kachelart braucht.
- **`TerrainSurface`** — `heightAt(worldX, worldZ)` liefert die Oberflächenhöhe an beliebiger
  Weltposition, innerhalb einer Rampe interpoliert; dazu `heightAtCenter` und `heightAtEdge`.
  Positionen außerhalb der Karte klemmen auf das Randtile, damit ein Editor-Cursor jenseits der
  Kante weiterhin einen brauchbaren Wert liefert. Tile `(x, z)` deckt Welt-X `[x-1, x]` und
  Welt-Z `[z-1, z]` ab, seine Mitte liegt also bei `(x-0.5, z-0.5)`.
- **`TerrainRules`** — eine einzige Bewegungsregel für Runtime und Editor. `step(fromX, fromZ,
  dx, dz)` liefert `ALLOWED`, `OUTSIDE_MAP`, `BLOCKED` oder `TOO_STEEP`; `canStep` ist die
  boolesche Kurzform, die `GridActor` benutzt. Geprüft wird **an der gemeinsamen Kante**, nicht
  zwischen Tile-Mitten: nahtlos anschließende Rampen unterscheiden sich dort um 0, eine Klippe um
  ein volles Level. `maxStepHeight` (Standard 0,5) ist damit eine Aussage über Klettern, nicht
  über Kachelabstand. Der Editor nutzt denselben Aufruf für Begehbarkeits-Overlays und
  Diagnosetexte.
- **Props** stehen auf der interpolierten Oberfläche unter ihrer Ankerkachel, nicht auf deren
  Stufenhöhe. `ModelDefinition.alignToSlope` kippt ein Prop optional in die Hangneigung;
  ohne das Flag bleibt es aufrecht.
- **Kollision hängt am Modell.** Der Fußabdruck aus dem Modellmanifest wird beim Platzieren in
  die Kollision der Karte geschrieben, statt gegen sie geprüft zu werden. Kartendokumente müssen
  Prop-Kacheln nicht mehr von Hand sperren. Rotationen drehen den Fußabdruck mit und nehmen seine
  achsparallelen Grenzen — bei 90-Grad-Schritten exakt, dazwischen konservativ. Der manuelle
  Kollisionslayer bleibt daneben bestehen und ist additiv.
- **Chunk-Aktualisierung** ist weiterhin ein vollständiger Neuaufbau.

- **Form ist Kartendatum, Aussehen ist Kacheltyp.** Die optionale Kartenebene `shape` trägt pro
  Zelle `flat` oder `ramp_north/east/south/west`. Der Editor dreht eine Rampe also, ohne die
  Kachel zu tauschen, und dieselbe Form lässt sich mit einem begehbaren und einem nicht begehbaren
  Kacheltyp benutzen.
- **`ChunkMesher` erzeugt die Geometrie** aus Höhen und Formen, statt Prototyp-Meshes zu kopieren.
  Deckflächen laufen durch die vier Eckhöhen, Seitenflächen entstehen nur an Kanten, an denen der
  Nachbar tiefer liegt. Damit stellt ein exportiertes Tileset erstmals Klippen und Rampen dar,
  Innenflächen entfallen ersatzlos, und der Kartenrand bekommt einen Skirt der Tiefe
  `borderDepth`. Ein Kacheltyp (`TileSurface`) beschreibt nur noch Farbe beziehungsweise
  Atlasregion für oben und für die Seiten sowie die Begehbarkeit; fehlt die Seitenregion, gilt die
  Oberseite.

Damit ist der frühere Blocker für die Abnahme von Phase 3 aufgelöst. Der Editor muss dafür
liefern: eine Formebene im Kartendokument, Ober- **und** Seitenregion je Kachel im Tileset-Export
(`side` ist optional) sowie `walkable` je Kacheltyp.

Der Editor zeigt diese Regeln an und ruft sie für Vorschau und Testlauf auf. Er dupliziert
weder Rampengeometrie noch Bewegungskollision. Ein Haus auf ungeeignetem Untergrund erhält eine
sichtbare Diagnose gemäß Engine-Regel; es wird nicht heimlich versetzt.

## Bearbeitbare Projektdateien und Export

Vorgeschlagene Struktur eines Inhaltsprojekts, unabhängig vom Editor-Quellcode:

```text
my-world/
  project.json                Projektversion, Asset-Wurzeln und Exportziel
  maps/                       Bearbeitbare Karten mit stabilen Referenzen
  catalogs/                   Modelle, Tilesets, Sprites und Entity-Typen
  sources/textures/           Einzeltexturen als dauerhafte Quellen
  sources/models/             GLTF/GLB mit zugehörigen Dateien
  sources/sprites/            Sprite-Quellen und Animationsdefinitionen
  .editor/                    Lokaler Zustand, Wiederherstellung und Vorschau-Cache
  export/                     Erzeugte Runtime-Dateien; nicht von Hand bearbeiten
```

Quellen und Kataloge gehören in Versionskontrolle; Cache und reproduzierbare Exporte können
ignoriert werden. Atlas-Packing verwendet stabile Reihenfolge und feste Einstellungen für
Padding, Extrusion, Filter, Rotation und Seitengröße. Bis die Engine mehrere Seiten unterstützt,
meldet ein zu großer Atlas einen Fehler. IDs bleiben beim erneuten Packen erhalten.

Gespeichert wird atomar über temporäre Dateien. Autosave ergänzt die explizite Speicherung und
bietet Wiederherstellung nach Absturz. Export wird vollständig in einem temporären Ziel erzeugt,
mit den Runtime-Loadern geprüft und erst danach als gültiges Paket bereitgestellt. Fehler dürfen
weder Originalquellen überschreiben noch den letzten gültigen Export beschädigen.

## Umsetzung in Reihenfolge

### Phase 1 — Projektkern und Desktop-Prototyp

- [x] Gradle-Module (`preview-protocol`, `preview`, `editor`, `platforms/desktop`) und Launcher
  angelegt; `platforms/desktop:run` startet den Editor, der die Vorschau als Subprozess startet.
- [x] Vorschauprozess verbinden und Protokollversion prüfen: `Hello`/`HelloAck`-Handshake über
  einen lokalen Socket, newline-getrenntes JSON mit Klassen-Tags (`preview-protocol`).
- [x] Vorschau nutzt dieselbe Renderpipeline und Beleuchtung wie das Spiel
  (`ModelBatch`/`WorldShaderProvider`/`LightingEnvironment`) über einer platzhalterhaften,
  beleuchteten Szene; freie Kamera per `CameraInputController`.
- [x] Überwachen und neu starten: Diagnosen-Panel protokolliert Status, Menü "Vorschau ▸ Neu
  verbinden" startet den Subprozess neu.
- [x] Dokumentmodell und Commands für Änderungen (`document`-Modul): `ProjectDocument` mutiert
  nur über `Command.execute/undo`, `CommandHistory` verfolgt Undo/Redo und Dirty-Status über die
  Stack-Tiefe (bleibt korrekt dirty, wenn eine neue Änderung den gespeicherten Redo-Zweig verwirft).
- [x] Projekt anlegen/öffnen, Speichern unter und zuletzt geöffnete Projekte (`ProjectController`,
  `RecentProjects`); `project.json` wird atomar über eine Sibling-Temp-Datei geschrieben und weist
  unbekannte Formatversionen mit klarer Meldung ab.
- [x] Autosave alle 30s nach `<projekt>/.editor/`, getrennt vom letzten expliziten Speicherstand;
  beim Öffnen wird eine neuere automatische Sicherung erkannt und zur Wiederherstellung angeboten.
- [x] Relative Assetpfade: Texturen, Modelle samt Abhängigkeiten und Sprites liegen unterhalb des
  Projektordners; ihre Exporte werden vom jeweiligen Katalog aus relativ aufgelöst.
- [x] Picking-Treffer als Nachricht zurück an die UI: `PickResult` (Y=0-Bodenebene, echtes
  Terrain-Picking ist Phase 3). `ClickPicker` teilt sich den Input-Multiplexer mit
  `CameraInputController` und feuert nur bei einem echten Klick ohne Kamera-Drag.
- [x] Vorschau zeigt eine generische Szene, bis im Editor ein Projekt offen ist; `ShowGenericScene`/
  `ShowSampleLevel` schaltet um. Solange es keine Dokument-Karte gibt, steht `SampleScene`
  (das aus `example-game` kopierte Testfeld) für "ein Projekt ist offen".
- [x] Vorschau mit Spielkamera, zusätzlich zur freien Kamera: Menü "Vorschau" schaltet per
  Funksignal (`SetCameraMode`) zwischen beiden um, beide bleiben verfügbar. Die Spielkamera nutzt
  dieselbe `PixelCamera`/`LowResTarget`-Pipeline wie `example-game` selbst - fester Anstellwinkel,
  Bildausschnitt anhand einer Kartenebene, Pixelraster-Snapping, echtes Downsampling auf die
  niedrig aufgelöste Zielauflösung und Hochskalieren. Sie blickt auf die Entity vom Typ "player"
  (Konvention wie in `example-game`), ersatzweise auf denselben Punkt wie die freie Kamera. Da die
  Vorschau keine Spielersteuerung hat, ist die Position statisch - Bewegung ist Sache eines
  künftigen Testmodus. Picking bleibt an die freie Kamera gebunden und pausiert in der Spielkamera,
  da deren Bildausgabe über einen separaten Renderpfad läuft.
- [x] UI mit Assetliste und Kartenansicht: Texturen, Tilesets, Modelle und Sprite-Sheets lassen
  sich bearbeiten; Kartenwerkzeuge, Overlays, Props und Entities arbeiten direkt auf dem Dokument.

Abnahme: Ein kleines Dokument lässt sich ändern, rückgängig machen, speichern, verschieben
und erneut öffnen, ohne Datenverlust oder kaputte Referenzen.

### Phase 2 — Texturen, Tilesets und Modelle

- [x] Atlas erzeugen und Tileset-Manifest exportieren (`asset-pipeline`-Modul): `TilePacker` packt
  gleich große Tiles als stabiles Grid (kein Bin-Packer/Rotation nötig, da das Manifest ohnehin
  eine gemeinsame `tileWidth`/`tileHeight` je Tileset annimmt), weist zu große Tilesets
  (>2048px-Seite), abweichende Tile-Größen und doppelte IDs mit klarer Meldung ab. `TilesetExport`
  schreibt PNG plus Manifest im exakten `TilesetManifest`-Schema - gegen den echten Engine-Parser
  geprüft, nicht nur gegen die eigene Ausgabe.
- [x] Einzeltexturen importieren und Tiles/Tilesets daraus anlegen (Assets-Panel, Tab "Texturen"
  und "Tilesets"): kopiert in `sources/textures/`, Katalog lebt im Dokumentmodell
  (`TextureAsset`/`TilesetAsset`/`TileEntry`) mit Commands für Undo/Redo. Textur-, Tileset-,
  Tile- und Modelllisten werden nach ID sortiert; die Texturliste zeigt skalierte Vorschauen ihrer
  Quellen. Die Kartenfläche zeichnet die importierten Quellen direkt und nutzt ID-Farben nur als
  Fallback für fehlende oder unlesbare Dateien.
- [x] Oberflächen-/Seitenzuordnung: `TileEntry.sideTextureId` optional, `TilePacker` packt Ober-
  und Seitenbild als getrennte Atlas-Regionen (beide müssen dieselbe Größe wie die übrigen
  Tiles/Seiten im Tileset haben - dieselbe Grid-Vereinfachung wie beim generellen Packing).
  Ohne Seitentextur fällt der Export weiterhin auf die Oberseite zurück.
- [x] Terrainformen gemäß dem abgeschlossenen Engine-Vertrag: `MapAsset` (`document`-Modul) trägt
  Kachel-, Höhen-, Form- und manuelle Kollisionsebene und spiegelt `TileMap`s eigene
  Grid-Höhen-Prüfung (flache Kacheln auf ganzen, Rampen auf halben Leveln), damit ein Zustand nie
  entstehen kann, den die Engine ablehnen würde. `MapExport` schreibt `maps/<id>.json` exakt im
  `MapLoader`-Schema - gegen den echten Parser geprüft (eine `Tileset` aus reinen `TileSurface`-
  Objekten braucht dafür keinen GL-Kontext). Noch offen: die eigentliche Kartenbearbeitung in der
  UI ist Phase 3.
- [x] GLTF/GLB samt Abhängigkeiten importieren und Modell registrieren (Assets-Panel, Tab
  "Modelle"): Hauptdatei plus Abhängigkeiten (z. B. `.bin`) werden zusammen nach
  `sources/models/` kopiert; die Bounds kommen aus einem echten `Model`/`Mesh`, das nur die
  Vorschau (mit GL-Kontext) bauen kann - `ComputeModelBounds`/`ModelBoundsResult` holen sie per
  Roundtrip, `ModelBoundsService` spiegelt `GltfModelFactory`s Ladepfad exakt. Export schreibt
  `catalogs/models/` plus ein gemeinsames `models.json`; primäre Dateien und registrierte
  Abhängigkeiten werden zusammen exportiert und über den echten `ModelManifest`-Parser geprüft.
  Die Karten-Vorschau löst die relativen Quellen gegen dieses Manifest auf und rendert damit das
  reale GLTF/GLB. Dateinamen sind auf einzelne Projektdateien beschränkt, damit ein gespeichertes
  Projekt nicht beim Export aus seinem Asset-Ordner ausbrechen kann.
- [x] Bounds anzeigen, Maßstab/Pivot/Höhe einstellen und Kollisions-Fußabdruck bearbeiten:
  "Eigenschaften…" im Modelle-Tab zeigt die importierten Bounds und die abgeleitete Höhe und
  bearbeitet Anker, Skalierung, Fußabdruck, Hangausrichtung und begehbare Laufhöhe als eine
  Undo/Redo-Änderung.
- [x] Reimport und Umbenennung von Modellquellen anbieten; betroffene Karten vor einer Löschung
  weiterhin mit allen Prop-Instanz-IDs auflisten. Der Reimport behält ID und Platzierungsdaten,
  aktualisiert aber Quellen und Bounds; alte Quelldateien bleiben für Undo/Redo erhalten.

Abnahme: Tileset und Haus entstehen ausschließlich über die UI und laden im Example Game.
Ein zweiter Export derselben Quellen erzeugt dieselben Inhalte und erhält alle Referenzen.

### Phase 3 — Karte, Gelände und Kollision

- [x] Karten anlegen und Tilesets zuordnen, auf Dokumentebene: `MapAsset` (`document`-Modul) trägt
  Kachel-, Höhen-, Form- und Kollisionsebene; `CreateMapCommand`/`RemoveMapCommand` und
  `PaintTilesCommand`/`PaintTerrainCommand`/`PaintCollisionCommand` decken Anlegen und Bemalen ab
  - je Aufruf eine Liste von Zell-Änderungen, also bereits so geschnitten, dass ein ganzer
  Pinselstrich eine einzige Undo-Aktion wird. `ProjectController.exportMap` schreibt
  `maps/<id>.json` im echten `MapLoader`-Format. "Größe ändern…" erhält beim Vergrößern die
  gemeinsamen nordwestlichen Zellen und initialisiert neue Zellen leer und flach; beim Verkleinern
  verhindert es Datenverlust, falls ein Prop-Anker außerhalb der neuen Karte läge. Der gesamte
  Vorgang ist eine Undo/Redo-Aktion.
- [x] 2D-Kartenansicht mit Mausbedienung (`MapPanel`/`MapCanvas`): Kartenliste anlegen/löschen/
  exportieren, Kacheln malen (Palette aus dem zugeordneten Tileset) und Sperren malen; ein
  Pinselstrich - auch über mehrere Zellen gezogen - sammelt sich lokal und wird erst beim
  Loslassen als eine `PaintTilesCommand`/`PaintCollisionCommand` und damit eine Undo-Aktion
  übergeben. Rampenrichtung wird als kleines Dreieck angezeigt. Löschen, Pipette, Flutfüllung,
  Rechteck, Kopieren und Einfügen arbeiten für die Kachelebene direkt auf der Karte und erzeugen
  jeweils nur eine Undo-Aktion; der interne Kachel-Zwischenspeicher wird beim Projektwechsel nicht
  persistiert. Die Karte zeichnet importierte Oberflächentexturen direkt in der Tile-Ansicht;
  bei einer fehlenden oder unlesbaren Quelle bleibt die eindeutige ID-Farbe als Fallback erhalten.
- [x] Höhen ändern, Plateaus und Rampen setzen und ihre Orientierung über die Formebene bearbeiten:
  Werkzeug "Terrain formen" nimmt Level (das Plateau, zu dem eine Rampe hinaufführt) und Form
  entgegen statt einer rohen Höhe - jede Kombination, die es erzeugen kann, ist dadurch bereits
  gültig (ganze Level für flach, halbe für eine Rampe), sodass `PaintTerrainCommand` sie nie
  ablehnt. Begehbare und nicht begehbare Rampen unterscheiden sich weiterhin nur im Kacheltyp.
- [x] Bewegte Charaktere folgen der Rampenoberfläche entlang ihrer tatsächlichen Weltposition:
  `GridActor` fragt beim Schritt `TerrainSurface.heightAt` ab, statt die Höhen der Tile-Mitten
  linear zu verbinden. Der Regressionstest `RampMovementTest` deckt Einfahrt, Mitte und Ausfahrt
  einer begehbaren Rampe bis zum Plateau ab.
- [x] Bemalte Karte dreidimensional und live anzeigen: `Live-Vorschau` bündelt kurz aufeinander
  folgende Pinselstriche, exportiert danach Karte, Tileset, Modelle und bei Bedarf Sprites und
  schickt ihre Katalogpfade per `ShowMap` an den Vorschauprozess. Dieser baut mit
  `WorldSceneLoader`/`ChunkMesher` dieselbe Szene wie das Spiel, einschließlich echter
  Tile-Atlasregionen, GLTF/GLB-Props und statischer Entity-Sprites. Der letzte Kartenstand wird
  nach einer Neuverbindung der Vorschau erneut geladen; damit ist sie keine Farb- oder
  Platzhalterannäherung mehr.
- [x] Terrain, Gitter, Begehbarkeit, Kanten und manuelle Sperren getrennt ein-/ausblenden:
  Die Kartenleiste steuert jeden Layer einzeln. Terrain zeigt Höhe und Rampenrichtung,
  Begehbarkeit hebt nicht begehbare Tiletypen hervor, Kanten markieren Höhenunterschiede an
  Tilegrenzen und manuelle Sperren bleiben von der modellbasierten Laufbarkeit getrennt.
- [x] Picking auf der tatsächlichen Terrainoberfläche: `ClickPicker` marschiert den Pick-Strahl
  gegen `TerrainSurface.heightAt` und verfeinert den Treffer per Bisektion, statt immer die
  Y=0-Ebene zu schneiden - ohne echtes Terrain (generische Platzhalterszene) bleibt die Ebene der
  Ersatz. Da die Vorschau die freie Kamera nutzt, nicht `PixelCamera`, entfällt die im Vertrag
  beschriebene Snap-Korrektur; das war die einzige vor Phase 3 offene Entscheidung dazu.

Abnahme: Eine Ebene führt über eine Rampe auf ein Plateau. Der Testspieler erreicht das Plateau;
Steilkanten und gesperrte Flächen verhalten sich genauso wie im exportierten Spiel.

### Phase 4 — Props, Entities, Sprites und Interaktionen

- [x] Registrierte Modelle als Props platzieren, wählen, verschieben, drehen, duplizieren und
  löschen: Die Modellpalette und das Prop-Werkzeug arbeiten auf Rasterkacheln; eine Instanzliste
  mit Transformdialog verwaltet Höhenversatz und Drehung. Die 2D-Ansicht markiert Props farbig,
  die Vorschau lädt die exportierten GLTF/GLB-Dateien mit ihren Abhängigkeiten. Ungültige oder aus
  der Karte verschobene Anker werden abgewiesen, bevor ein Command das Dokument verändert.
- [x] Startpunkte und NPCs als tile-gebundene Entities mit stabilen Instanz-IDs platzieren,
  bearbeiten und löschen: Die Kartenansicht markiert sie separat, und `MapExport` übergibt ID,
  Typ, optionalen Sprite und Position an den rückwärtskompatibel erweiterten `MapLoader`.
- [x] Terrainbezug, Höhenversatz und Kollisions-Fußabdruck sichtbar bearbeiten: Höhenversatz und
  Drehung stehen bereits im Transformdialog; die Kartenansicht umreißt zusätzlich den mitgedrehten
  Kollisions-Fußabdruck des ausgewählten Props. Bei Zwischenwinkeln zeigt das die tatsächliche
  gedrehte Form, nicht die konservative achsparallele Näherung der Engine - genau genug, um eine
  Platzierung zu beurteilen, kein Byte-für-Byte-Abbild des exportierten Kollisionsrasters.
- [x] Sprite-Atlanten registrieren; Richtungen, Idle-/Laufsequenzen, Frame-Dauer und Fußpunkt
  zuordnen: Ein importiertes PNG-Sheet wird über Spalten/Zeilen und Frame-Indizes eingerichtet,
  deterministisch als einzelne Atlas-Seite gepackt und als echtes `SpriteManifest` exportiert.
  Entities können nur registrierte Sprite-IDs referenzieren.
- [x] Übergänge (Kartenwechsel) mit stabilen Instanz-IDs platzieren, bearbeiten und löschen:
  `MapTransition` (Engine) und `MapLoader` lesen ein optionales `transitions`-Array, spiegelbildlich
  zu Props/Entities/Lights - reine Daten, keine Laufzeitlogik. Der Editor prüft beim Platzieren,
  dass die Zielkarte existiert, und verweigert das Löschen einer Karte, solange ein Übergang noch
  auf sie zeigt. Die Kartenansicht markiert Übergänge als eigenes Symbol, eine Instanzliste erlaubt
  Bearbeiten von Position, Zielkarte und Zielposition. Das tatsächliche Auslösen beim Betreten der
  Kachel ist Sache des Spiels beziehungsweise eines künftigen Testmodus, nicht des Editors.
- [x] Dialoge mit Sprecher-, Text-, Porträt- und Antwortknoten anlegen; Verzweigungen und
  Bedingungen prüfen: `Dialogue`/`DialogueNode`/`DialogueResponse` (Engine, neues `dialogue`-Paket)
  sind reine Daten, projektweit über eine `DialogueManifest`-Datei registriert - wie Modelle und
  Sprites, nicht kartengebunden. Sprünge zwischen Knoten werden schon beim Anlegen gegen die
  Knotenmenge desselben Dialogs geprüft, da anders als bei Kartenübergängen keine Ladereihenfolge
  im Spiel ist. Der Editor bekommt dafür einen eigenen "Dialoge"-Reiter mit verschachteltem
  Knoten-/Antworten-/Bedingungseditor. Text-, Sprecher- und Porträt-Felder sind stabile IDs, die
  das Spiel auflöst - ein Übersetzungskatalog mit Prüfung auf fehlende Sprachvarianten existiert im
  Projekt noch nicht und ist damit bewusst nicht Teil dieser Prüfung.
- [x] Events mit Auslösern, Bedingungen und Aktionen verknüpfen; Dialoge, Kartenwechsel, Flags,
  NPCs, Türen und Lichtquellen als Ziele unterstützen: `GameEvent`/`EventTrigger`/`Condition`/`Action`
  (Engine, neues `event`-Paket) sind reine Daten pro Karte, spiegelbildlich zu Props/Entities/
  Lights/Transitions. Auslöser: Kartenstart, Interaktion (mit einer Entity), Fläche betreten,
  Zeitwechsel. Bedingungen: Flag, Variable, Tageszeit, je mit Vergleichsoperator. Aktionen: Dialog
  starten, NPC bewegen, Tür öffnen (als Entity-Ziel), Karte wechseln, Flag setzen, Licht schalten.
  Der Editor prüft beim Platzieren und Bearbeiten jede Referenz - Entity, Licht, Dialog oder
  Zielkarte - gegen die tatsächlich vorhandenen Assets, mit derselben Ladereihenfolge-Rücksicht wie
  bei Übergängen (Zielkarten werden nur interaktiv geprüft, nicht beim Laden). Die Kartenansicht
  markiert nur Flächen-Trigger, da Kartenstart/Interaktion/Zeitwechsel keine Kachelposition haben;
  alle Events einer Karte erscheinen zusätzlich in einer eigenen Liste mit Formular-Editor. Flags
  sind freie Strings ohne eigene Registrierung - ein Flag-Katalog wäre eine eigene, größere
  Änderung. Das tatsächliche Auswerten von Bedingungen und Ausführen von Aktionen ist Sache des
  Spiels beziehungsweise eines künftigen Testmodus, nicht des Editors.
- [x] Punkt- und Spotlichter pro Karte bearbeiten: `MapLight` (Engine) und `MapLoader` lesen ein
  optionales `lights`-Array, spiegelbildlich zu Props/Entities - der Aufrufer entscheidet, was
  daraus wird, genau wie bei Entities. Der Editor prüft das gemeinsame 8-Licht-Budget der Engine
  bereits beim Platzieren, nicht erst beim Export. Die Vorschau wendet Position, Farbe, Intensität,
  Reichweite und Schaltzustand über `LightingEnvironment`/`PointLightSource` an; ein zurückgewiesenes
  `ShowMap` lässt die zuvor sichtbare Beleuchtung unangetastet. **Sonnenverlauf und Umgebungslicht
  bleiben unautorisierbar** - die Engine wählt sie weiterhin aus einem festen `LightingSituation`-
  Tagesverlauf, nicht aus Kartendaten; das wäre eine eigene, größere Änderung.
- [x] Eigenschaften aus registrierten Entity-/Interaktionsschemas anzeigen; Referenzen auf
  Zielkarte, Dialogknoten, Licht- und andere Entity-IDs prüfen: `MapEntity` (Engine) trägt jetzt
  ein freies `properties`-Feld (Schlüssel/Wert, beides Strings) - reine Daten, keine Laufzeitlogik,
  genau wie bei Events. Die Registrierung eines Typs ist optional: Ein `type`, der zu keinem
  registrierten `EntityTypeAsset` passt, verhält sich exakt wie vorher (Freitext, keine
  Eigenschaften) - erst ein registrierter Typ verwandelt das Textfeld in ein geprüftes Formular.
  Eigenschaftstypen: Text, Zahl, Wahrheitswert sowie Referenzen auf Karte, Dialog, Licht (auf
  derselben Karte) und andere Entities (auf derselben Karte); Pflichtfelder und Referenzen werden
  beim Platzieren und Bearbeiten geprüft, mit derselben Ladereihenfolge-Rücksicht wie bei Events.
  Der Editor bekommt dafür einen "Entity-Typen"-Reiter zum Anlegen der Schemas; der
  Entity-Platzierungsdialog zeigt bei einem passenden Typ automatisch das passende Formular.
  "Zielpunkt" ist kein eigener Eigenschaftstyp - ein Schema-Autor bildet ihn bei Bedarf über zwei
  Zahl-Eigenschaften ab, wie es Kartenwechsel-Aktionen intern auch tun.
- Spielregeln bleiben in der Engine beziehungsweise im Spiel. Fehlende Runtime-Unterstützung
  für Trigger/NPCs ist eine explizite Abhängigkeit, kein scheinbar funktionsfähiges Editorfeld.

Abnahme: Haus auf dem Plateau, Startpunkt und NPC lassen sich speichern und wieder laden.
Ein einfacher Übergang funktioniert in Vorschau und Export, sobald seine Runtime vorhanden ist.

### Phase 5 — Testmodus und belastbarer Export

- [x] Testmodus mit derselben World-, Bewegungs-, Kollisions- und Renderlogik wie im Example Game:
  Ein neuer `SetTestMode`-Schalter (Menü "Vorschau ▸ Testmodus") lässt die "player"-Entity der
  aktuell gezeigten Dokumentkarte per `GridActor`/`TerrainRules`/`DirectionalSpriteAnimation`
  bewegen - denselben Klassen, die auch `example-game` verwendet, mit identischer Geschwindigkeit
  und Kollisionsprüfung. Orthogonal zur Kameraauswahl: Testmodus funktioniert mit freier Kamera
  genauso wie mit der Spielkamera, die dann live folgt statt einer statischen Position. Mit echtem
  GL-Kontext verifiziert. Jede Aktivierung setzt die Figur auf den authored Startpunkt zurück, für
  reproduzierbare Testläufe.
- [x] Testlauf arbeitet auf einer Kopie; Bewegung und Gameplay verändern das Quelldokument nicht:
  Die Vorschau liest ausschließlich bereits exportierte Dateien und schreibt nie in `project.json`
  - das gilt unverändert auch für den Testmodus, der rein auf der geladenen `WorldScene` operiert.
- [x] Event- und Dialogabläufe einzeln auslösen, Flags zurücksetzen und Tageszeit sowie
  Lichtzustände für reproduzierbare Tests vorgeben: `GameState`/`ConditionEvaluator`/
  `EventDispatcher`/`EventActionHandler` (Engine, neues Laufzeitstück im `event`-Paket) prüfen
  Bedingungen und reichen Aktionen an einen Handler weiter - reine Mechanik, die Interpretation
  bleibt beim Aufrufer, genau wie beim Datenmodell selbst. Der "Auslösen"-Knopf in der
  Ereignisliste sendet die gewählte Instanz-ID unabhängig vom authored Trigger; die Vorschau
  wertet SET_FLAG in ihrem eigenen `GameState` aus, verschiebt bei MOVE_NPC den passenden
  Sprite, schaltet bei TOGGLE_LIGHT das zugehörige `PointLightSource` (per Lichter-ID verfolgt,
  da die Engine-Klasse selbst keine trägt), und lässt bei START_DIALOGUE `DialoguePlayback` den
  Dialogbaum automatisch entlang der ersten zutreffenden Antwort abgehen - ein Ersatz für einen
  echten Dialogdialog, den es ohne Übersetzungskatalog ohnehin nicht sinnvoll geben kann.
  OPEN_DOOR und CHANGE_MAP werden nur protokolliert; ein echter Kartenwechsel im Testmodus ist
  der nächste offene Punkt. "Flags zurücksetzen" leert den Testzustand, ein Tageszeit-Feld setzt
  Uhrzeit und damit Beleuchtung direkt - Lichter selbst sind bereits über das gewöhnliche
  Bearbeiten-und-Live-Vorschau-Dokumentfeld reproduzierbar steuerbar. Jede Auswirkung meldet sich
  als `EventLogEntry` im Diagnosen-Panel zurück, da der Editor sonst keine Sicht auf
  vorschau-interne Effekte hätte. Mit echtem GL-Kontext verifiziert.
- [x] Diagnosen mit anklickbarer Asset-ID: Jede Validierungs- und Fehlermeldung in diesem
  Projekt nennt die betroffene ID bereits in einfachen Anführungszeichen - ein Klick auf eine
  Diagnosen-Zeile extrahiert das erste gequotete Token und versucht es erst über
  `MapPanel.trySelectPlacement` (die Karte selbst oder eine Platzierung auf der aktuell
  gewählten Karte) und dann über `AssetsPanel.trySelect` (die projektweiten Kataloge, samt
  Tab-Wechsel) aufzulösen - keine nachrichtenspezifische Sonderbehandlung nötig. Platzierungen
  werden bewusst nur auf der aktuell gewählten Karte gesucht, da Instanz-IDs kartenübergreifend
  nicht eindeutig sind und eine Diagnosezeile selbst keine Karten-ID mitführt.
- [x] Anklickbare Map-Position: Ein Klick auf einen `Pick`-Diagnoseeintrag markiert die getroffene
  Stelle auf der 2D-Kartenansicht der aktuell gewählten Karte. Die Umrechnung ist die Umkehrung
  der bereits im Code etablierten Welt-Mittelpunkt-Konvention (gespeicherte Rastermitte liegt bei
  `Kachelindex - 0,5`); das Ergebnis wird auf die Kartengrenzen geklemmt, statt außerhalb des
  Canvas zu zeichnen. Mit einem echten Screenshot gegen die exakt erwartete Zelle verifiziert.
- [x] Fehlende Assets, doppelte IDs, ungültige Regionen, unbekannte Versionen und unauflösbare
  Verweise werden vor dem Export erkannt: Doppelte IDs, Regionsgrenzen und Formatversionen waren
  bereits an der richtigen Stelle abgedeckt - beim Registrieren im Dokument, beim Packen von
  Tileset/Sprite-Atlas beziehungsweise durch die echten Engine-Parser, gegen die die
  Asset-Pipeline-Tests laufen. Die eigentliche Lücke war, dass Kartenübergreifende Verweise
  (Übergangs-Zielkarte, Event-Aktion `CHANGE_MAP`) beim Laden bewusst *nicht* geprüft werden -
  Karten können sich unabhängig von ihrer Dateireihenfolge referenzieren -, und danach nichts
  mehr nachprüft, ob eine von Hand bearbeitete oder beschädigte `project.json` einen toten Verweis
  enthält. `ProjectValidation` (Dokumentmodul) wiederholt dieselben Prüfungen, die die
  Platzieren-/Bearbeiten-Commands ohnehin schon einzeln durchsetzen, einmal gesammelt über das
  ganze geladene Projekt. `ProjectController.validateProject()` ergänzt das um eine
  Dateiexistenzprüfung für registrierte Textur-, Modell- und Sprite-Quellen, da nur der Controller
  den tatsächlichen Projektpfad kennt. Menüpunkt "Datei ▸ Projekt validieren…" zeigt alle
  gefundenen Probleme gesammelt an, statt beim ersten Fehler abzubrechen.
- [x] Example Game lädt das exportierte Paket ohne prozedurale `ExampleMap`-Sonderbehandlung:
  `ExampleMap.createScene()` (`example-game`) baute sein Tileset bisher aus hartcodierten
  Java-Farben, obwohl `testfield.json` bereits über den echten `MapLoader` geladen wurde - nur die
  Kachel-**Optik** kam nicht aus einem echten Export. Jetzt lädt `TextureTileset` ein echtes,
  gepacktes `overworld.json`/`overworld.png` (dieselbe Klasse, die auch die Editor-Vorschau nutzt),
  mit denselben sechs Kachel-IDs und derselben Begehbarkeit wie zuvor. Mit `compileJava`, der
  vollständigen Testsuite und allen drei Render-Smoke-Tests (`renderSmokeTest`,
  `lightingSmokeTest`, `worldSmokeTest`) sowie einem echten Screenshot verifiziert. `example-game`
  hatte zum Zeitpunkt dieser Änderung ein umfangreiches, nicht committetes Paket-Rename im
  Gange; die Änderung wurde bewusst nicht selbst committet, sondern zur Durchsicht zusammen mit
  diesem Rename belassen.

Abnahme: Der vollständige Zielablauf vom Einzelbild bis zur begehbaren Welt funktioniert.
Speichern/Öffnen und Export/Laden erhalten Geometrie, Platzierung, Höhe und Kollision.

### Phase 6 — Größere Projekte und Distribution

- [x] Nur betroffene Chunks und Vorschauen erneuern, Ressourcen beim Reimport sauber freigeben:
  Die vor Phase 6 offene Entscheidung - realistische Referenzkartengröße und ein messbares
  Reaktionszeitbudget - wurde durch einen neuen Lasttest beantwortet, statt geschätzt:
  `LoadTestSmokeTest` (`editor`-Modul) baut eine Referenzkarte von 128x128 Kacheln (8x8
  `ChunkMesher.CHUNK_SIZE`-Chunks) mit 256 verteilten Props über dieselbe echte `ProjectController`/
  `PreviewProcess`-Pipeline wie der Editor und misst den vollständigen Export-/Neulade-Zyklus, den
  jeder einzelne Pinselstrich heute auslöst. Ergebnis: ein Kaltstart kostet 111ms, ein einzelner
  Pinselstrich im Mittel 30ms (16-62ms über 20 Wiederholungen) - deutlich innerhalb des ohnehin
  bestehenden 180ms-Debounce-Fensters der Live-Vorschau (`MapPanel.livePreviewTimer`). Ein
  inkrementeller Neuaufbau nur der betroffenen Chunks - `ChunkMesher`, `WorldSceneLoader` und
  `WorldScene` bieten dafür aktuell keine API, jede Änderung baut die komplette Szene neu auf - ist
  bei dieser Referenzgröße durch Messung nicht gerechtfertigt und bleibt zurückgestellt, bis ein
  reales Projekt sie überschreitet; das wäre sonst Komplexität ohne belegten Bedarf. Ressourcenfreigabe
  beim Reimport ist bereits korrekt: `PreviewApplication.disposeDocumentAssets()` verwirft die alte
  Szene erst, nachdem die neue erfolgreich aufgebaut wurde, und `ModelBoundsService.compute()` gibt
  seinen temporären Scene-Import in einem `finally` frei. Der Lasttest prüft das zusätzlich mit acht
  aufeinanderfolgenden Wechseln zwischen der großen Karte und einer winzigen zweiten Karte über
  dieselbe Verbindung, ohne Fehlschlag oder Verbindungsabbruch.
- [x] Culling-Grenzen nach Änderungen aktualisieren; Assetlisten bei Bedarf virtualisieren:
  `WorldScene`s Bounds-Array wird ausschließlich beim vollständigen Szenenaufbau (Konstruktor)
  beziehungsweise bei `applyTransform` neu berechnet - das ist bereits lückenlos, weil jede Änderung
  heute ohnehin die ganze Szene neu aufbaut (siehe oben); eine gesonderte Invalidierung wäre erst
  mit einer inkrementellen Mutation nötig, die bewusst zurückgestellt ist. `AssetsPanel` benutzt
  bereits gewöhnliche `JList`/`DefaultListModel` (zeichnet nur sichtbare Zeilen) mit einem eigenen
  Thumbnail-Cache; ein Messlauf mit 1500 importierten Texturen zeigt `refresh()`-Kosten von 0-2ms
  pro Aufruf. Eine eigene Virtualisierung wäre bei den hier realistischen Katalogumfängen
  unbegründeter Mehraufwand und bleibt aus demselben Grund wie oben zurückgestellt.
- [x] Lasttests mit größeren Karten, vielen Props und wiederholtem Projektwechsel:
  `LoadTestSmokeTest` (siehe oben, `:editor:loadTestSmokeTest`) ist der dauerhafte Lasttest - er
  bleibt im Projekt, um dieselbe Messung nach künftigen Änderungen zu wiederholen, statt eine
  einmalige Ad-hoc-Prüfung zu sein.
- [x] Desktop-Distributionen für macOS, Windows und Linux erstellen und prüfen: Neue `jpackage`-
  Tasks in `platforms/desktop/build.gradle` (`jpackageAppImage`, `jpackageDistribution`) bauen ein
  natives Installationspaket für das jeweils aktuelle Betriebssystem - `jpackage` kompiliert nicht
  quer, jedes Zielsystem muss dort gebaut werden. Zwei Probleme kamen erst beim tatsächlichen Start
  des gebauten Pakets zum Vorschein, nicht beim Bauen selbst: `jpackage` legt jeden Jar irgendwo
  unter `--input` rekursiv auf den Hauptklassenpfad, ein reines Unterverzeichnis für die Vorschau
  hätte also `gdx-backend-lwjgl3` und alle LWJGL-Natives doch auf den Editor-Prozess gezogen - genau
  das, was die Prozesstrennung verhindern soll. Die Vorschau-Jars werden deshalb über
  `--app-content` außerhalb des Klassenpfads platziert und über `$APPDIR/../jpackage-preview-libs/*`
  in `-Dtrackside.editor.previewClasspath` aufgelöst. Zweitens entfernen `jpackage`s
  Standard-`--jlink-options` das `java`-Kommandozeilenprogramm aus dem gebündelten Laufzeitabbild,
  da ein natives Paket es normalerweise nie erneut braucht - diese Architektur aber schon, weil
  `PreviewProcess` die Vorschau als echten zweiten `java`-Prozess startet; ein eigener
  `--jlink-options`-Wert ohne `--strip-native-commands` behält es. Auf macOS (dieser Maschine) mit
  einem echten `.app`/`.dmg` geprüft: gestartet, dabei den Vorschau-Subprozess selbst gestartet,
  über das Protokoll verbunden ("Vorschau: verbunden") und eine echte Szene mit GL-Kontext
  gerendert - sowohl aus dem gebauten App-Bundle als auch aus dem gemounteten `.dmg` heraus. Windows-
  und Linux-Pakete sind mit demselben Task nur strukturell vorbereitet (Typ und `%APPDIR%`/`$APPDIR`
  richten sich nach dem erkannten Host-Betriebssystem) und bewusst **nicht** gebaut oder geprüft -
  diese Maschine ist ein Mac; ein Windows- oder Linux-Build/-Test braucht einen entsprechenden
  Build-Rechner oder eine CI-Matrix, die es in diesem Projekt noch nicht gibt.
- [x] Exportiertes Paket auch über Android und iOS des Example Game testen; tatsächliche
  Geräte-/Simulatorergebnisse getrennt von erfolgreichen Kompilierungen dokumentieren: Android auf
  einem echten Emulator (`Medium_Phone_API_36.1`, API 36) geprüft - und dabei ein echter, bis dahin
  unbemerkter Fehler gefunden, den eine erfolgreiche Kompilierung nicht zeigt: `platforms/android/
  build.gradle` deklarierte nie `gdx-platform`-Android-Natives (nur Desktop hatte das), also fehlte
  `libgdx.so` im APK vollständig und die App stürzte bei jedem Start sofort mit
  `UnsatisfiedLinkError` ab - kompilierte, installierte und startete augenscheinlich, lief aber nie.
  Behoben nach dem etablierten libGDX-Gradle-Rezept: eine `natives`-Konfiguration mit den vier
  Android-ABI-Klassifizierern plus ein `copyAndroidNatives`-Task, der ihre `.so`-Dateien nach
  `libs/<abi>/` entpackt, das über `jniLibs.srcDirs` ins APK einfließt. Mit demselben Emulator erneut
  geprüft: App bleibt am Leben, `ExampleGame` protokolliert echte Renderframes über einen echten
  GLES-3.1-Kontext, und ein Screenshot zeigt exakt dieselbe Szene wie die Desktop-Renderprüfungen -
  Wegkreuz, Haus, Straßenlaterne, Spielersprite. iOS ließ sich in dieser Umgebung nicht auf dieselbe
  Art abschließend prüfen: `xcrun simctl` hängt hier unabhängig vom Projekt dauerhaft, auch nach
  einem Neustart des CoreSimulator-Diensts - ein Umgebungsproblem dieser Maschine, kein Befund am
  Code. `platforms/ios` selbst kompiliert (siehe `trackside`s bereits verifizierter signierter
  Geräte-Build auf demselben RoboVM-Weg); ein tatsächlicher Start auf Simulator oder Gerät bleibt
  offen, bis diese Maschine einen funktionierenden Simulator hat oder ein echtes Gerät angeschlossen
  wird - bewusst nicht als geprüft ausgegeben, obwohl der Build durchläuft, genau die Unterscheidung,
  die dieser Punkt verlangt.

## Prüfstrategie und Abschlusskriterien

Dokument- und Exporttests prüfen Roundtrips, Undo/Redo, Migration, deterministisches Packing
und Fehlerfälle ohne GL-Kontext. Render-Integrationstests prüfen Texturorientierung,
Atlasränder, Picking, transformierte Bounds, Rampen, Höhenplatzierung und Ressourcenwechsel.
Ein gemeinsames Referenzprojekt enthält flaches Terrain, eine Rampe, ein Plateau, ein Haus,
eine blockierte Kante, einen Startpunkt und einen NPC.

Die erste nutzbare Editor-Version ist erst erreicht, wenn dieses Projekt vollständig in der UI
erstellt, wieder geöffnet und unverändert im Example Game gespielt werden kann. Ein erfolgreicher
Build allein weist weder Editorbedienung noch Terrain-/Kollisionskorrektheit nach.

## Entscheidungen vor den jeweiligen Phasen

- Vor Phase 1: Java-UI-Toolkit wählen und das Nachrichtenprotokoll zur Vorschau festlegen.
  Die Prozesstrennung selbst ist entschieden und nicht mehr offen.
- Vor Phase 2: Asset-Resolver, schreibbare Dokumente, IDs, Atlas- und Versionsvertrag festlegen.
- Vor Phase 3: Terrain-Vertrag ist vollständig übernommen; offen bleibt nur die Abstimmung mit
  dem Schattensystem, sobald dessen Runtime steht.
- Vor Phase 4: Entity-, Sprite- und Interaktionsschemas mit der Runtime abstimmen.
- Vor Phase 6: realistische Referenzkartengröße und messbares Reaktionszeitbudget festlegen.

Einzelne Ebenen, mehrere Tilesets, unregelmäßige Kollisionsformen und Autotiling werden im
Datenmodell berücksichtigt, aber nur mit definiertem Runtime-Verhalten freigeschaltet.
Wasser, Brücken, übereinanderliegende begehbare Flächen und Skripting folgen als eigene,
versionierte Erweiterungen statt impliziter Sonderfälle im ersten Terrainformat.
