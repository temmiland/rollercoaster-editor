# Web-Editor

Ziel: den Editor als Web-App (auch auf dem iPad) betreiben. Projekte liegen in einem Git-Repo auf
einer eigenen Gitea-Instanz; der Editor liest und schreibt sie über die Gitea-API.

## Starten

```sh
./gradlew :web-preview:gdx_teavm_web_js_build   # Engine-Vorschau nach JavaScript übersetzen
cd web
npm install
npm run dev -- --host                           # --host macht ihn im WLAN erreichbar, z. B. fürs iPad
```

Der Dev-Server liefert unter `/preview` die übersetzte Vorschau und unter `/demo-project` das
Example Game aus dem Nachbar-Repo als Demo-Projekt (anderer Ordner über
`ROLLERCOASTER_DEMO_PROJECT`). `npm run build` legt die Vorschau mit in `web/dist`.

Oberfläche und Komponentenaufbau: `docs/web-editor-ux.md`.

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

## Dateiverweise

Jede Manifest-Datei (Tileset, Modelle, Sprites) verweist auf ihre Dateien relativ zu ihrem eigenen
Verzeichnis. Die Engine kennt keinen zweiten Auflösungsweg über die Classpath-Wurzel mehr; ein
Katalog bleibt so zusammen mit seinen Dateien verschiebbar, egal ob er aus dem Classpath, von der
Platte oder aus dem Speicher der Web-Vorschau kommt.

## Projekt-Repo

Der Editor bekommt ein (leeres oder bestehendes) Git-Repo und legt dort die gesamte Struktur an, die
ein Spiel auf der Engine braucht: Quelldateien, Kataloge, Karten und Build-Infrastruktur. Ziel ist,
dass ein vom Editor angelegtes Repo ohne Handarbeit baut und spielbar ist.
