# BlueJ light

English | [Italiano](README.it.md)

An independent Java IDE based on BlueJ 5.5.0, with an optimized editor,
code completion, searchable snippets, automatic class-diagram layout and
an integrated JDK selector. Current version: **5.5.3-light**.
This is not an official BlueJ release.

## What Changes In Light

Compared with BlueJ 5.5.0, light focuses on local Java editing and compilation.

| Area | Improvement |
| --- | --- |
| Editor | Incremental text updates, cached longest line and scope decorations limited to visible rows reduce unnecessary work, especially during distant scroll jumps. Search highlights remain visible after scrolling. |
| Completion | Keeps BlueJ's native Java engine; prioritizes local variables and parameters, avoids stale requests and loads selected-item documentation on demand. Java suggestions and snippets have distinct presentation. |
| Snippets | 17 configurable templates, searchable previews, context filters and linked fields navigated with `Tab`. A help catalogue lists the available templates. |
| Class diagram | ELK-based layout repositions classes and routes visible connections to reduce overlaps and crossings, with undo/redo. |
| Java runtime | Select any compatible installed JDK 21 x64 in Preferences, including a return to the bundled JDK; apply the choice at restart. |
| Portable use | Preferences, snippets and JDK selection stay in the application's `data` folder. |

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

## Exclusions And Scope

| Component | Scope and reason |
| --- | --- |
| Telemetry and usage statistics | Removed to avoid collecting or sending research events, source snapshots and startup usage data. |
| Team / integrated Git | Removed to focus on local Java projects and avoid the Git/SSH dependency stack. Existing project Git/SVN metadata is preserved; external Git tools remain usable. |
| Greenfoot | Excluded from the build: it is a separate teaching environment, not needed by this Java-focused application. |
| Submitter | The optional email/FTP project-submission extension is outside the editing workflow. It is omitted because this distribution does not provide its matching sources. |

**Stride is not fully removed:** shared internal support remains for compatibility,
but Stride-specific interface components are created only when a Stride editor opens.
Java compilation, debugging, the object bench and JUnit support remain available.
Official update checks and notices also remain: light is not an entirely network-free application.

## Limits

Completion does not use an external language server. Very large files
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
