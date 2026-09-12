# Rollercoaster Editor — Umsetzungsplan

Status: Planung. Der Editor wird ein eigenes Repository neben `rollercoaster`,
`example-game` und `trackside`. Dieser Plan implementiert weder den Editor noch Änderungen
an der Engine. Trackside-Inhalte sind zunächst außerhalb des Arbeitsumfangs.

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

Desktop-Anwendung mit libGDX/LWJGL3, Scene2D UI und derselben Renderpipeline wie das Spiel.
Dadurch können Picking, Terrainvorschau und Testmodus ohne zweite Renderimplementierung
aufgebaut werden. Die UI-Eignung für Dateiauswahl, Docking und große Assetlisten wird im ersten
Prototyp geprüft, bevor zusätzliche UI-Bibliotheken festgelegt werden.

Vorgesehene Gradle-Module:

```text
rollercoaster-editor/
  docs/                       Architektur, Datenverträge und Bedienung
  document/                   Editor-Dokumente, Commands, Historie und Serialisierung
  asset-pipeline/             Import, Atlas-Packing, Validierung und Export
  editor/                     UI, Werkzeuge, Engine-Vorschau und Testmodus
  platforms/desktop/          Desktop-Launcher und Distribution
```

`document` und die nichtgrafischen Teile der Asset-Pipeline müssen ohne OpenGL testbar sein.
Desktop-Code darf ein modernes JVM-Level verwenden; wiederverwendete Engine-Klassen behalten
das von Android/iOS benötigte Sprachlevel. Mobile Editor-Oberflächen sind zunächst nicht geplant.

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

### Abstimmung mit den laufenden Terrain-Arbeiten

Rampen und Terrain-Kollision werden aktuell in `rollercoaster` bearbeitet. Vor Phase 3 wird
deren fertiger Vertrag übernommen. Dieser Plan legt keine konkurrierenden Rampenfelder fest.
Benötigt werden Antworten beziehungsweise APIs für:

1. Terrainform, Orientierung, Höhenrepräsentation und Verbindung benachbarter Tiles.
2. Oberflächenhöhe an einer Weltposition, auch innerhalb einer Rampe.
3. Bewegungsprüfung von einer Position zur nächsten: Richtung, Höhenwechsel, gesperrte Kanten
   und Terrainform statt ausschließlich eines booleschen Zielfeldes.
4. Platzierung von Props auf flachen und geneigten Flächen: Bezugshöhe, zusätzlicher Höhenversatz,
   Stellfläche und Anforderungen an einen ebenen Untergrund.
5. Zusammenspiel abgeleiteter Terrain-Kollision, Modell-Kollision und manueller Map-Sperren.
6. Aktualisierung betroffener Chunks, Nachbarchunks, Kollisionsdaten und Culling-Grenzen nach
   einer Änderung. Ein vollständiger Neuaufbau ist als erste korrekte Variante zulässig.

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

- Gradle-Module, Launcher und Engine-Anbindung anlegen.
- Projekt anlegen/öffnen, Speichern unter, relative Pfade und zuletzt geöffnete Projekte.
- Dokumentmodell und Commands für Änderungen; Undo/Redo, Dirty-Status und Autosave.
- UI mit Assetliste, Kartenansicht, Eigenschaften und Diagnosen.
- Freie Editor-Kamera sowie Vorschau mit Spielkamera; korrekte Maus-/Viewport-Koordinaten.

Abnahme: Ein kleines Dokument lässt sich ändern, rückgängig machen, speichern, verschieben
und erneut öffnen, ohne Datenverlust oder kaputte Referenzen.

### Phase 2 — Texturen, Tilesets und Modelle

- Einzeltexturen importieren, benennen, sortieren und Vorschauen anzeigen.
- Tiles aus Texturen definieren, Atlas erzeugen und Tileset-Manifest exportieren.
- Oberflächen-/Seitenzuordnung und Terrainformen gemäß dem abgeschlossenen Engine-Vertrag.
- GLTF/GLB samt Abhängigkeiten importieren, Modell registrieren und dreidimensional anzeigen.
- Bounds anzeigen, Maßstab/Pivot/Höhe einstellen und Kollisions-Fußabdruck bearbeiten.
- Abhängigkeiten bei Umbenennung, Löschung und Reimport prüfen; betroffene Karten auflisten.

Abnahme: Tileset und Haus entstehen ausschließlich über die UI und laden im Example Game.
Ein zweiter Export derselben Quellen erzeugt dieselben Inhalte und erhält alle Referenzen.

### Phase 3 — Karte, Gelände und Kollision

- Karten anlegen, Größen ändern und Tilesets zuordnen.
- Malen, Löschen, Pipette, Füllen, Rechteckauswahl, Kopieren und Einfügen.
- Ein Pinselstrich entspricht einer Undo-Aktion; Vorschau aktualisiert sich während der Arbeit.
- Höhen ändern, Plateaus und Rampen setzen und ihre Orientierung bearbeiten.
- Terrain, Gitter, Begehbarkeit, Kanten und manuelle Sperren getrennt ein-/ausblenden.
- Picking auf der tatsächlichen Terrainoberfläche; Tile-Mitte und Cursor stimmen auch bei
  geneigter Kamera, erhöhten Tiles und Rampen überein.

Abnahme: Eine Ebene führt über eine Rampe auf ein Plateau. Der Testspieler erreicht das Plateau;
Steilkanten und gesperrte Flächen verhalten sich genauso wie im exportierten Spiel.

### Phase 4 — Props, Entities, Sprites und Interaktionen

- Modelle mit Vorschau platzieren, wählen, verschieben, drehen, duplizieren und löschen.
- Terrainbezug, Höhenversatz und Kollisions-Fußabdruck sichtbar bearbeiten.
- Sprite-Atlanten registrieren; Richtungen, Idle-/Laufsequenzen, Frame-Dauer und Fußpunkt zuordnen.
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
- Nichtquadratische Karten, fehlende Assets, doppelte IDs, ungültige Regionen, unbekannte
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

- Vor Phase 1: UI-Prototyp bewerten und Editor-/Engine-Abhängigkeitsrichtung festlegen.
- Vor Phase 2: Asset-Resolver, schreibbare Dokumente, IDs, Atlas- und Versionsvertrag festlegen.
- Vor Phase 3: abgeschlossene Rampen-/Terrain-Kollisions-API übernehmen und gemeinsam prüfen.
- Vor Phase 4: Entity-, Sprite- und Interaktionsschemas mit der Runtime abstimmen.
- Vor Phase 6: realistische Referenzkartengröße und messbares Reaktionszeitbudget festlegen.

Einzelne Ebenen, mehrere Tilesets, unregelmäßige Kollisionsformen und Autotiling werden im
Datenmodell berücksichtigt, aber nur mit definiertem Runtime-Verhalten freigeschaltet.
Wasser, Brücken, übereinanderliegende begehbare Flächen und Skripting folgen als eigene,
versionierte Erweiterungen statt impliziter Sonderfälle im ersten Terrainformat.
