# Web-Editor

Ziel: den Editor als Web-App (auch auf dem iPad) betreiben. Projekte liegen in einem Git-Repo auf
einer eigenen Gitea-Instanz; der Editor liest und schreibt sie über die Gitea-API.

## Vorschau

`web-preview` übersetzt die Engine mit gdx-teavm nach JavaScript und rendert über WebGL 2 mit
denselben Loadern, Shadern und derselben Beleuchtung wie das Spiel. Die Prozesstrennung der
Desktop-Vorschau (LWJGL3 und Swing auf macOS) entfällt im Browser.

Die Vorschau läuft in einem iframe und bekommt Projektdateien per `postMessage`, nicht über das
Dateisystem. `MemoryFileHandle` stellt sie der Engine als gewöhnliche `FileHandle` bereit, sodass
relative Verweise (Atlas, GLTF-Buffer) wie auf der Platte aufgelöst werden.

```sh
./gradlew :web-preview:run
```

### Nachrichten

An die Vorschau:

| `type` | Felder |
| --- | --- |
| `showMap` | `files` (Pfad → `Uint8Array`), `map`, `tileset`, optional `models`, `sprites` |
| `setTimeOfDay` | `hours` |

Von der Vorschau (an `window.parent`, immer mit `source: 'rollercoaster-preview'`):

| `type` | Bedeutung |
| --- | --- |
| `ready` | Vorschau ist gestartet und nimmt Nachrichten an |
| `showMapResult` | `error` ist `null` oder die Fehlermeldung; bei Fehler bleibt die alte Karte sichtbar |

## Offene Formatfrage

Modell- und Sprite-Manifeste lösen ihre Quellen im Example Game relativ zur Classpath-Wurzel auf
(`models/house.gltf`), im Editor-Export und in beiden Vorschauen relativ zum Manifest. Vor dem
Web-Editor muss das Format sich auf eine Regel festlegen.
