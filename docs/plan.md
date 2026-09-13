# Rollercoaster Editor — Umsetzungsplan

Status: Projektkern, Karten, Modelle, Sprite-Atlanten und tile-gebundene Entities sind umgesetzt.
Der Editor ist ein eigenes Repository neben `rollercoaster`, `example-game` und `trackside`.
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
- [ ] Vorschau mit Spielkamera (statt freier Kamera) - noch offen, sinnvoll erst mit echtem
  Karteninhalt aus dem Dokument statt der `SampleScene`.
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
- Startpunkte, NPCs, Triggerflächen und Übergänge mit stabilen Instanz-IDs platzieren.
- Dialoge mit Sprecher-, Text-, Porträt- und Antwortknoten anlegen; Verzweigungen, Bedingungen
  und Übersetzungs-IDs prüfen.
- Events mit Auslösern, Bedingungen und Aktionen verknüpfen; Dialoge, Kartenwechsel, Flags,
  NPCs, Türen und Lichtquellen als Ziele unterstützen.
- Sonnenverlauf, Umgebungslicht sowie Punkt- und Spotlichter pro Karte bearbeiten; Position,
  Höhe, Farbe, Intensität, Reichweite und Schaltzustand in der Vorschau zeigen.
- Eigenschaften aus registrierten Entity-/Interaktionsschemas anzeigen; Referenzen auf
  Zielkarte, Zielpunkt, Dialogknoten und Licht-ID prüfen.
- Spielregeln bleiben in der Engine beziehungsweise im Spiel. Fehlende Runtime-Unterstützung
  für Trigger/NPCs ist eine explizite Abhängigkeit, kein scheinbar funktionsfähiges Editorfeld.

Abnahme: Haus auf dem Plateau, Startpunkt und NPC lassen sich speichern und wieder laden.
Ein einfacher Übergang funktioniert in Vorschau und Export, sobald seine Runtime vorhanden ist.

### Phase 5 — Testmodus und belastbarer Export

- Testmodus mit derselben World-, Bewegungs-, Kollisions- und Renderlogik wie im Example Game.
- Testlauf arbeitet auf einer Kopie; Bewegung und Gameplay verändern das Quelldokument nicht.
- Event- und Dialogabläufe einzeln auslösen, Flags zurücksetzen und Tageszeit sowie Lichtzustände
  für reproduzierbare Tests vorgeben.
- Diagnosen mit anklickbarer Map-Position beziehungsweise Asset-ID.
- Fehlende Assets, doppelte IDs, ungültige Regionen, unbekannte
  Versionen und unauflösbare Verweise werden vor dem Export erkannt.
- Example Game lädt das exportierte Paket ohne prozedurale `ExampleMap`-Sonderbehandlung.

Abnahme: Der vollständige Zielablauf vom Einzelbild bis zur begehbaren Welt funktioniert.
Speichern/Öffnen und Export/Laden erhalten Geometrie, Platzierung, Höhe und Kollision.

### Phase 6 — Größere Projekte und Distribution

- Nur betroffene Chunks und Vorschauen erneuern, Ressourcen beim Reimport sauber freigeben.
- Culling-Grenzen nach Änderungen aktualisieren; Assetlisten bei Bedarf virtualisieren.
- Lasttests mit größeren Karten, vielen Props und wiederholtem Projektwechsel.
- Desktop-Distributionen für macOS, Windows und Linux erstellen und prüfen.
- Exportiertes Paket auch über Android und iOS des Example Game testen; tatsächliche
  Geräte-/Simulatorergebnisse getrennt von erfolgreichen Kompilierungen dokumentieren.

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
