# BlueJ light

[English](README.md) | Italiano

Ambiente Java indipendente basato su BlueJ 5.5.0, con editor ottimizzato,
completamento automatico, snippet ricercabili, riordino del diagramma delle
classi e selettore JDK integrato. Versione attuale: **5.5.3-light**.
Non è una release ufficiale di BlueJ.

## Cosa Cambia In Light

Rispetto a BlueJ 5.5.0, light privilegia editing e compilazione Java su progetti locali.

| Area | Miglioramento |
| --- | --- |
| Editor | Aggiornamenti incrementali del testo, cache della riga più lunga e riquadri limitati alle righe visibili riducono il lavoro superfluo, soprattutto nei salti di scorrimento. L'evidenziazione della ricerca rimane visibile dopo lo scroll. |
| Completamento | Mantiene il motore Java nativo BlueJ; dà priorità a variabili locali e parametri, evita richieste obsolete e carica la documentazione della voce selezionata quando serve. Suggerimenti Java e snippet hanno una presentazione distinta. |
| Snippet | 17 modelli configurabili, ricerca con anteprima, filtri per contesto e campi collegati navigabili con `Tab`. Un catalogo di aiuto elenca i modelli disponibili. |
| Diagramma delle classi | Il riordino basato su ELK riposiziona le classi e instrada i collegamenti visibili per ridurre sovrapposizioni e incroci, con annullamento e ripristino. |
| Ambiente Java | Scelta di un JDK 21 x64 compatibile dalle preferenze, anche tornando a quello incluso; la modifica si applica al riavvio. |
| Uso portable | Preferenze, snippet e scelta del JDK restano nella cartella `data` dell'applicazione. |

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

## Esclusioni E Ambito

| Componente | Ambito e motivazione |
| --- | --- |
| Telemetria e statistiche | Rimosse per evitare raccolta e invio di eventi di ricerca, copie dei sorgenti e dati di utilizzo all'avvio. |
| Team / Git integrato | Rimosso per concentrarsi sui progetti Java locali ed evitare le dipendenze Git/SSH. I metadati Git/SVN già presenti nei progetti sono conservati; si possono usare strumenti Git esterni. |
| Greenfoot | Escluso dalla compilazione: è un ambiente didattico distinto, non necessario a questa applicazione orientata a Java. |
| Submitter | L'estensione opzionale per inviare progetti via email/FTP è estranea all'editing. È esclusa perché questa distribuzione non fornisce i suoi sorgenti corrispondenti. |

**Stride non è completamente rimosso:** il supporto interno condiviso rimane
per compatibilità, ma i componenti grafici specifici vengono creati solo aprendo
un editor Stride. Restano compilazione Java, debugger, banco degli oggetti e JUnit.
Restano anche gli aggiornamenti e gli avvisi ufficiali: light non è un'applicazione
completamente priva di comunicazioni di rete.

## Limiti

Il completamento non usa un language server esterno. File molto grandi possono
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
