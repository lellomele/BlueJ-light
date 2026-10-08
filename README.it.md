# BlueJ light

[English](README.md) | Italiano

Ambiente Java indipendente basato su BlueJ 5.5.0, con editor ottimizzato,
completamento automatico, snippet ricercabili, riordino del diagramma delle
classi e selettore JDK integrato. Versione attuale: **5.5.3-light**.
Non è una release ufficiale di BlueJ.

## Installazione

Scaricare dalla sezione [Releases](https://github.com/lellomele/BlueJ-light/releases/latest).
Richiede Windows 10/11 x64 e un **JDK 21 x64**; tutti i pacchetti includono JavaFX.

- **Installer completo:** include il JDK.
- **Installer senza JDK:** usa un JDK già presente; il solo JRE non basta.
- **Portable:** include il JDK. Estrarre l'intera cartella e avviare `BlueJ light.exe`.
  Conservare `app`, `runtime` e `portable.flag`; le preferenze sono in `data`.

Gli eseguibili non sono firmati; Windows potrebbe mostrare un avviso.

## Uso

Aprire un progetto BlueJ e fare doppio clic su una classe per modificarla.

| Operazione | Comando o scorciatoia |
| --- | --- |
| Completamento automatico | `Ctrl+Spazio` |
| Ricerca snippet | Strumenti > Sfoglia snippet Java; `Ctrl+Shift+Spazio` |
| Catalogo snippet | Aiuto > Snippet Java |
| Campi degli snippet | `Tab` / `Shift+Tab`; `Esc` per terminare |
| Riordino del diagramma | Riordina diagramma; `Ctrl+Shift+L` |
| Cambio JDK | Strumenti > Preferenze > Java / JDK; riavviare BlueJ light |

Il selettore JDK non modifica `JAVA_HOME`, il `PATH` o altre applicazioni.
Gli snippet si personalizzano con `snippets.properties` nella cartella delle
preferenze indicata in Info; il file distribuito in `app` contiene gli esempi.

## Limiti

Telemetria, statistiche di utilizzo, Team/Git e Submitter sono esclusi.
Restano la verifica degli aggiornamenti e gli avvisi ufficiali. Greenfoot
non viene compilato; il supporto interno a Stride rimane incluso.
Il completamento usa il motore nativo BlueJ. File molto grandi possono
richiedere tempo all'apertura; diagrammi complessi possono conservare incroci.
Le modifiche manuali invalidano i percorsi automatici e l'annullamento del riordino.

## Compilazione

Con JDK 21, eseguire `./gradlew :bluej:assemble` o `gradlew.bat :bluej:assemble`.
La prima compilazione scarica Gradle e le dipendenze.
I pacchetti Windows si generano con `tools/package-windows.ps1` e
`tools/package-installers.ps1`; servono MinGW-w64 e Inno Setup 7.
Le icone SVG, PNG e ICO sono incluse; per rigenerarle servono Node.js e `sharp`.

## Licenza E Copyright

GPLv2 con Classpath Exception; consultare [LICENSE.txt](LICENSE.txt) e
[licenze e sorgenti corrispondenti](LICENSING.md).
Modifiche BlueJ light: © 2026 - Prof. Ing. Raffaele Mele.
I crediti BlueJ sono conservati; l'icona dell'uccello è di Michael Kolling.
