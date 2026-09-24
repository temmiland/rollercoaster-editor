# Web-Editor: UX-Konzept

Der Editor läuft auf Desktop, Tablet und Telefon. Er richtet sich nicht nach dem Gerät, sondern
nach der verfügbaren Breite und der Eingabeart. Jede Funktion ist ohne Hover, ohne rechte
Maustaste und ohne Tastatur erreichbar; Maus und Tastatur sind Beschleuniger, keine Voraussetzung.

## Aufgaben

1. Projekt öffnen (Demo, später Gitea-Repo) und einen Überblick bekommen.
2. Assets durchsehen: Karten, Tilesets, Modelle, Sprites, Dialoge.
3. Eine Karte in der 3D-Vorschau ansehen und prüfen.
4. Später: Karten und Assets bearbeiten, Änderungen als Commit zurückschreiben.

## Informationsarchitektur

```text
Projekt
├── Karten        Liste → Karte (Vorschau + Eigenschaften)
├── Tilesets      Liste → Tileset
├── Modelle       Liste → Modell
├── Sprites       Liste → Sprite
└── Dialoge       Liste → Dialog
```

Jeder Bereich folgt demselben Muster **Liste → Detail**. Das Detail hat einen Arbeitsbereich
(Vorschau oder Editor) und optional einen Inspektor mit Eigenschaften. Die aktuelle Auswahl
steht in der URL, damit Zurück-Geste, Neuladen und Lesezeichen funktionieren.

## Layouts nach Breite

| Klasse | Breite | Typisch | Navigation | Liste/Detail | Inspektor |
| --- | --- | --- | --- | --- | --- |
| kompakt | < 640px | Telefon | Leiste unten | eine Ebene sichtbar, Detail schiebt sich darüber | Blatt von unten |
| mittel | 640–1023px | Tablet hochkant | Schiene links | Liste und Detail nebeneinander | Blatt von unten |
| weit | ≥ 1024px | Tablet quer, Desktop | Schiene links | Liste und Detail nebeneinander | feste Spalte rechts |

```text
kompakt                 mittel                          weit
┌──────────────┐        ┌──┬────────┬──────────────┐    ┌──┬───────┬───────────────┬────────┐
│ Kopf     ⋯   │        │  │ Liste  │ Detail       │    │  │ Liste │ Detail        │ Inspek-│
├──────────────┤        │N │        │              │    │N │       │               │ tor    │
│              │        │a │        │              │    │a │       │               │        │
│ Liste        │        │v │        │              │    │v │       │               │        │
│  oder Detail │        │  │        ├──────────────┤    │  │       │               │        │
│              │        │  │        │ Inspektor ▴  │    │  │       │               │        │
├──────────────┤        └──┴────────┴──────────────┘    └──┴───────┴───────────────┴────────┘
│ ▣  ▣  ▣  ▣  ▣│
└──────────────┘
```

Die Vorschau bleibt beim Wechsel zwischen Liste und Detail geladen (React `Activity`), damit die
Engine nicht bei jedem Zurück neu startet.

## Eingabe

- Mindestgröße für Bedienelemente 44 × 44px bei Touch (`pointer: coarse`), 32px bei Maus.
- Pointer Events für Maus, Touch und Stift gemeinsam; Gesten in der Vorschau übernimmt die Engine.
- Tastatur: Pfeiltasten in Listen, `Esc` schließt Blätter, `/` fokussiert die Suche.
- Sichere Bereiche (Notch, Home-Indikator) über `env(safe-area-inset-*)`.

## Gestaltung

- Werkzeug-Oberfläche: ruhig, dunkel als Standard, helles Thema über `prefers-color-scheme`.
- Alle Farben, Abstände, Radien und Größen sind Design-Tokens (CSS Custom Properties).
- Status und Fehler sind immer Text plus Farbe, nie nur Farbe.

## Komponenten (Atomic Design)

| Ebene | Komponenten |
| --- | --- |
| Atome | `Icon`, `Button`, `IconButton`, `Heading`, `Text`, `Badge`, `Spinner`, `Input` |
| Moleküle | `SearchField`, `ListItem`, `NavItem`, `EmptyState`, `PropertyList`, `Toolbar` |
| Organismen | `AppBar`, `Navigation`, `AssetList`, `PreviewViewport`, `Inspector`, `Sheet` |
| Templates | `WorkspaceTemplate` (Navigation, Liste, Detail, Inspektor als Slots) |
| Seiten | `WelcomePage`, `WorkspacePage` |

Atome und Moleküle kennen weder Projekt noch Engine; sie bekommen alles über Props. Erst
Organismen verbinden Daten, Seiten verbinden Routen. So lassen sich die unteren Ebenen später
ohne Umbau in Storybook erfassen.
