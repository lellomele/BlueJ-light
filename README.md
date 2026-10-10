# BlueJ light

English | [Italiano](README.it.md)

An independent Java IDE based on BlueJ 5.5.0, with an optimized editor,
code completion, searchable snippets, automatic class-diagram layout and
an integrated JDK selector and teaching tools. Current version: **5.6.0-light**.
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
| Navigation and recovery | Searchable method outline, tracked back/forward locations, local saved versions and recovery of unsaved drafts. |
| Teaching | Bilingual exercise ZIPs, progressive hints, explained compiler diagnostics and separate-process JUnit feedback with expected/actual values. |

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
| Format Java code | Edit > Format Java Code; `Ctrl+Shift+I` |
| Snippet catalogue | Help > Java Snippets |
| Snippet fields | `Tab` / `Shift+Tab`; `Esc` to finish |
| Arrange class diagram | Arrange Diagram; `Ctrl+Shift+L` |
| Change JDK | Tools > Preferences > Java / JDK; restart BlueJ light |
| Method outline | Editor Tools > Methods; `Ctrl+Shift+O` |
| Navigate back / forward | Editor Tools > Previous / Next Location; `Alt+Left` / `Alt+Right` |
| Saved versions | Editor Tools > Local History; restore into the buffer, then save or undo |
| Explained errors | Compile, then Editor Tools > Explain Compiler Errors |
| Diagram filters and locks | Diagram context menu: arrange new classes, lock selected positions, selected-class connections |
| Exercises | Project Tools > Exercises: import, instructions/test feedback, create/export |

The JDK selector does not change `JAVA_HOME`, `PATH` or other applications.
The Windows formatter is bundled: AStyle expands one-line methods/blocks and aligns
braces with four-space indentation. It runs locally in the background, preserves
text-block content and verifies that Java tokens are unchanged. Undo reverses the
whole operation. It does not run on every keystroke or overwrite project files directly.
If the code changes while formatting, the result is discarded. Maximum input: 5 MiB.
On other platforms, without a bundled formatter, the original indentation remains available.
Customize snippets using `snippets.properties` in the preferences folder
shown in About; the file shipped in `app` provides examples.

Exercise archives contain project sources/resources and `exercise.properties`, not compiled
classes, Git metadata or bundled libraries. Create a pack from an open project; enter fully
qualified JUnit 4/5 test class names and up to 20 hints per language. Import into a new folder.
Feedback can be exported as CSV. Only run trusted exercises: the child JVM is **not a sandbox**.
External project libraries must be installed separately. A sample is in `examples/Sum`
in the application folder (`tools/Exercises/Sum` in the sources).

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
With locked positions, box collisions are avoided but connections use BlueJ's normal routing
and may cross. New-only layout leaves loaded or manually positioned classes unchanged.
Local history keeps up to 50 revisions or 20 MiB per file in the preferences folder, and drafts
up to 5 MiB after 1.5 seconds of inactivity. It is not a backup service; copied/moved files have
separate histories. Preview text is limited to 200,000 characters; restoration uses the full text.
History stores source text locally and never uploads it. Normal BlueJ saves/compilation still apply after restoration.
Exercise tests and compilation each have a 30-second limit and a 256 MiB heap. Reports are not tamper-proof
grading records; assertion details depend on the JUnit assertion type.
Error explanations also appear in the code tooltip. The detailed window does not
disable the editor and refreshes on compilation. A prolonged UI stall is recorded
locally in `bluej-ui-stall.log` in the preferences folder shown in About, without source text.

## Build

With JDK 21, run `./gradlew :bluej:assemble` or `gradlew.bat :bluej:assemble`.
The first build downloads Gradle and dependencies.
Windows packaging uses `tools/package-windows.ps1` and
`tools/package-installers.ps1`, requiring MinGW-w64 and Inno Setup 7.
SVG, PNG and ICO icon sources are included; regeneration uses Node.js and `sharp`.
The formatter sources and MIT licence are in `tools/thirdparty/astyle-3.6.19`;
`tools/build-formatter.ps1` rebuilds the Windows tool with MinGW-w64.

## License And Copyright

GPLv2 with Classpath Exception; see [LICENSE.txt](LICENSE.txt) and
[licences and corresponding sources](LICENSING.md).
BlueJ light modifications: © 2026 - Prof. Ing. Raffaele Mele.
Original BlueJ credits are retained; the bird icon is copyright Michael Kolling.
