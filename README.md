# BlueJ light

English | [Italiano](README.it.md)

An independent Java IDE based on BlueJ 5.5.0, with an optimized editor,
code completion, searchable snippets, automatic class-diagram layout and
an integrated JDK selector. Current version: **5.5.3-light**.
This is not an official BlueJ release.

## Installation

Download from [Releases](https://github.com/lellomele/BlueJ-light/releases/latest).
Requires Windows 10/11 x64 and a **JDK 21 x64**; all packages include JavaFX.

- **Complete installer:** includes the JDK.
- **Without-JDK installer:** uses an existing JDK; a JRE is not sufficient.
- **Portable:** includes the JDK. Extract the entire folder and run `BlueJ light.exe`.
  Keep `app`, `runtime` and `portable.flag`; preferences are stored in `data`.

The executables are unsigned; Windows may display a warning.

## Usage

Open a BlueJ project and double-click a class to edit it.

| Action | Location or shortcut |
| --- | --- |
| Code completion | `Ctrl+Space` |
| Search snippets | Tools > Browse Java Snippets; `Ctrl+Shift+Space` |
| Snippet catalogue | Help > Java Snippets |
| Snippet fields | `Tab` / `Shift+Tab`; `Esc` to finish |
| Arrange class diagram | Arrange Diagram; `Ctrl+Shift+L` |
| Change JDK | Tools > Preferences > Java / JDK; restart BlueJ light |

The JDK selector does not change `JAVA_HOME`, `PATH` or other applications.
Customize snippets using `snippets.properties` in the preferences folder
shown in About; the file shipped in `app` provides examples.

## Limits

Telemetry, usage statistics, Team/Git and Submitter are excluded. Official
update checks and notices remain. Greenfoot is not built; internal Stride
support remains. Completion uses BlueJ's native engine. Very large files
may still take time to open; complex diagrams can retain crossing connections.
Manual diagram changes invalidate automatic routes and layout undo.

## Build

With JDK 21, run `./gradlew :bluej:assemble` or `gradlew.bat :bluej:assemble`.
The first build downloads Gradle and dependencies.
Windows packaging uses `tools/package-windows.ps1` and
`tools/package-installers.ps1`, requiring MinGW-w64 and Inno Setup 7.
SVG, PNG and ICO icon sources are included; regeneration uses Node.js and `sharp`.

## License And Copyright

GPLv2 with Classpath Exception; see [LICENSE.txt](LICENSE.txt) and
[licences and corresponding sources](LICENSING.md).
BlueJ light modifications: © 2026 - Prof. Ing. Raffaele Mele.
Original BlueJ credits are retained; the bird icon is copyright Michael Kolling.
