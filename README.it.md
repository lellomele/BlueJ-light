# BlueJ light

[English](README.md) | Italiano

Ambiente Java indipendente basato su BlueJ 5.5.0, con editor ottimizzato,
completamento automatico, snippet ricercabili, riordino del diagramma delle
classi, selettore JDK integrato e strumenti didattici. Versione attuale: **5.7.1-light**.
Non è una release ufficiale di BlueJ.

## Cosa Cambia In Light

Rispetto a BlueJ 5.5.0, light privilegia editing e compilazione Java su progetti locali.

| Area | Miglioramento |
| --- | --- |
| Editor | Aggiornamenti incrementali del testo, cache delle larghezze e riquadri aggiornati in gruppo durante apertura ed editing riducono il lavoro superfluo. Sono decorate solo le righe visibili; la ricerca rimane evidenziata dopo lo scroll. |
| Completamento | Mantiene il motore Java nativo BlueJ; dà priorità a variabili locali e parametri, riutilizza la finestra dei suggerimenti e conserva le firme testuali senza memorizzare i membri risolti. Scarta richieste obsolete e carica la documentazione quando serve. |
| Snippet | 17 modelli configurabili, ricerca con anteprima, filtri per contesto e campi collegati navigabili con `Tab`. Un catalogo di aiuto elenca i modelli disponibili. |
| Diagramma delle classi | Il riordino basato su ELK riposiziona le classi e instrada i collegamenti visibili per ridurre sovrapposizioni e incroci, con annullamento e ripristino. |
| Ambiente Java | Scelta di un JDK 21 x64 compatibile dalle preferenze, anche tornando a quello incluso; la modifica si applica al riavvio. |
| Uso portable | Preferenze, snippet e scelta del JDK restano nella cartella `data` dell'applicazione. |
| Navigazione e recupero | Elenco metodi ricercabile, posizioni seguite durante le modifiche, cronologia dei salvataggi e recupero delle bozze. |
| Didattica | Esercizi ZIP bilingui, suggerimenti progressivi, spiegazioni degli errori e risultati JUnit in processo separato, con valori attesi e ottenuti. |

## Installazione

Scaricare dalla sezione [Releases](https://github.com/lellomele/BlueJ-light/releases/latest).
Richiede Windows 10/11 x64 e un **JDK 21 x64**; tutti i pacchetti includono JavaFX.
I nomi dei file usano l'inglese: `with-jdk`, `without-jdk` e `portable`.

- **Installer completo:** include il JDK.
- **Installer senza JDK:** usa un JDK già presente; il solo JRE non basta.
- **Portable:** include il JDK. Estrarre l'intera cartella e avviare `BlueJ light.exe`.
  Conservare `app`, `runtime` e `portable.flag`; le preferenze sono in `data`.

Gli eseguibili non sono firmati; Windows potrebbe mostrare un avviso.
Per i pacchetti con JDK incluso, usare un percorso di installazione/estrazione con
caratteri ASCII: questo runtime non si è avviato in un percorso provato con una lettera greca.
Gli installer possono registrare i progetti `.bluej` in Apri con. Scegliere BlueJ light
come app predefinita in Windows; le preferenze esistenti non vengono sostituite.
La portable non registra tipi di file.

## Uso

Aprire un progetto BlueJ e fare doppio clic su una classe per modificarla.

| Operazione | Comando o scorciatoia |
| --- | --- |
| Completamento automatico | `Ctrl+Spazio` |
| Ricerca snippet | Strumenti > Sfoglia snippet Java; `Ctrl+Shift+Spazio` |
| Formattazione Java | Modifica > Formatta codice Java; `Ctrl+Shift+I` |
| Catalogo snippet | Aiuto > Snippet Java |
| Campi degli snippet | `Tab` / `Shift+Tab`; `Esc` per terminare |
| Riordino del diagramma | Riordina diagramma; `Ctrl+Shift+L` |
| Nuova classe / elimina elementi selezionati | Focus nel diagramma: `Ins` / `Delete` o `Backspace`; eliminazione con conferma |
| Cambio JDK | Strumenti > Preferenze > Java / JDK; riavviare BlueJ light |
| Elenco metodi | Strumenti dell'editor > Elenco metodi; `Ctrl+Shift+O` |
| Navigazione indietro / avanti | Strumenti dell'editor > Posizione precedente / successiva; `Alt+Sinistra` / `Alt+Destra` |
| Versioni salvate | Strumenti dell'editor > Cronologia locale; ripristinare nel testo, poi salvare o annullare |
| Spiegazione errori | Compilare, poi Strumenti dell'editor > Spiega errori di compilazione |
| Filtri e blocchi diagramma | Menu contestuale del diagramma: classi nuove, blocco posizioni, collegamenti selezionati |
| Esercizi | Strumenti del progetto > Esercizi: importazione, consegna/risultati, creazione/esportazione |

Il selettore JDK non modifica `JAVA_HOME`, il `PATH` o altre applicazioni.
Il formatter Windows e incluso: AStyle espande metodi e blocchi su una sola riga,
allinea le graffe e usa quattro spazi. Lavora localmente in background, preserva
il contenuto dei text block e verifica che i token Java siano invariati. Undo annulla
l'intera operazione. Non interviene a ogni tasto e non sovrascrive direttamente i file.
Se il codice cambia nel frattempo, il risultato viene scartato. Limite: 5 MiB.
Sugli altri sistemi, senza formatter incluso, rimane disponibile il rientro originale.
Gli snippet si personalizzano con `snippets.properties` nella cartella delle
preferenze indicata in Info; il file distribuito in `app` contiene gli esempi.

I pacchetti esercizio contengono sorgenti/risorse del progetto e `exercise.properties`,
non classi compilate, metadati Git o librerie. Creare un pacchetto dal progetto aperto,
indicando i nomi completi delle classi JUnit 4/5 e fino a 20 suggerimenti per lingua.
Importare in una cartella nuova. I risultati si esportano in CSV. Eseguire solo esercizi
attendibili: la JVM separata **non è una sandbox**. Installare separatamente le librerie
esterne del progetto. Esempio: `examples/Sum` nella cartella dell'applicazione
(`tools/Exercises/Sum` nei sorgenti).

## Esclusioni E Ambito

| Componente | Ambito e motivazione |
| --- | --- |
| Telemetria e statistiche | Rimosse per evitare raccolta e invio di eventi di ricerca, copie dei sorgenti e dati di utilizzo all'avvio. |
| Team / Git integrato | Rimosso per concentrarsi sui progetti Java locali ed evitare le dipendenze Git/SSH. I metadati Git/SVN già presenti nei progetti sono conservati; si possono usare strumenti Git esterni. |
| Greenfoot | Escluso dalla compilazione: è un ambiente didattico distinto, non necessario a questa applicazione orientata a Java. |
| Submitter | L'estensione opzionale per inviare progetti via email/FTP è estranea all'editing. È esclusa perché questa distribuzione non fornisce i suoi sorgenti corrispondenti. |

**Solo sorgenti Java:** il linguaggio a blocchi, il relativo editor e gli strumenti
di conversione non sono inclusi. Converti le classi Stride in Java con BlueJ
originale prima di aprire il progetto in BlueJ light. Rimuovi gli import del runtime
del linguaggio e sostituisci le eventuali chiamate specifiche con equivalenti Java.
I sorgenti non riconosciuti non vengono cancellati. Restano compilazione Java,
debugger, banco degli oggetti e JUnit.
Restano anche gli aggiornamenti e gli avvisi ufficiali: light non è un'applicazione
completamente priva di comunicazioni di rete.

## Limiti

Il completamento non usa un language server esterno. Le keyword Java sono disponibili
fuori dagli accessi ai membri; i membri dichiarati nella classe precedono variabili
locali, membri ereditati, tipi e snippet. File molto grandi possono richiedere tempo
all'apertura; diagrammi complessi possono conservare incroci.
Il completamento propone anche tipi del file corrente e classi del progetto prima
della compilazione. I buffer aperti prevalgono sui vecchi file class; con codice
incompleto possono essere disponibili solo le dichiarazioni già riconosciute.
I tipi di altri package mostrano il package e, quando necessario, sono inseriti
con il nome qualificato, senza aggiungere import. L'indice leggero legge le intestazioni
in background e riusa i dati dei file invariati. Sono mostrati al massimo 256 tipi per
richiesta; un prefisso restringe l'elenco nei progetti grandi. Il completamento non
forza salvataggi o compilazioni; restano attivi i normali controlli automatici.
Le modifiche manuali invalidano i percorsi automatici e l'annullamento del riordino.
Con posizioni bloccate si evitano collisioni tra box, ma i collegamenti usano i percorsi
standard BlueJ e possono incrociarsi. Il riordino delle classi nuove conserva quelle
caricate dal progetto o posizionate manualmente.
La cronologia conserva fino a 50 versioni o 20 MiB per file nelle preferenze, e bozze
fino a 5 MiB. Il checkpoint viene richiesto 1,5 secondi dopo l'inizio delle modifiche,
anche durante la digitazione continua; un crash prima della scrittura può perdere le ultime modifiche.
Non sostituisce un backup; file copiati o spostati hanno cronologie distinte.
Le anteprime sono limitate a 200.000 caratteri,
ma il ripristino usa il testo completo. I sorgenti rimangono solo sul computer,
senza invii; dopo il ripristino restano attivi i normali salvataggi BlueJ.
Se un salvataggio fallisce durante la chiusura, puoi continuare a modificare oppure
salvare una copia UTF-8 verificata in `recovered-sources`, nella cartella delle
preferenze, e chiudere. Il percorso viene mostrato. La copia non sostituisce il file
originale; per compilare occorre comunque salvare il sorgente originale.
Dopo una chiusura anomala, progetti e classi Java aperte vengono riaperti se il relativo automatismo
è attivo nelle Preferenze. Il registro locale conserva fino a 200 package e 1000 classi per package.
I file mancanti non vengono ricreati. Il recupero richiede preferenze scrivibili e non riprende lo stato
di un programma Java in esecuzione: oggetti ed esecuzione vanno ricreati dopo il riavvio della JVM.
Compilazione e test degli esercizi hanno ciascuno un limite
di 30 secondi e 256 MiB di memoria. I risultati non sono certificazioni di valutazione;
i dettagli atteso/ottenuto dipendono dall'asserzione JUnit usata.
Le spiegazioni degli errori sono visibili anche nel messaggio sul codice. La finestra
di approfondimento resta aperta senza bloccare l'editor e si aggiorna alla compilazione.
Un eventuale blocco prolungato viene registrato solo localmente in `bluej-ui-stall.log`,
nella cartella delle preferenze indicata in Info; il registro non contiene i sorgenti.

## Compilazione

Con JDK 21, eseguire `./gradlew :bluej:assemble` o `gradlew.bat :bluej:assemble`.
La prima compilazione scarica Gradle e le dipendenze.
I pacchetti Windows si generano con `tools/package-windows.ps1` e
`tools/package-installers.ps1`; servono MinGW-w64 e Inno Setup 7.
Le icone SVG, PNG e ICO sono incluse; per rigenerarle servono Node.js e `sharp`.
Sorgenti e licenza MIT del formatter: `tools/thirdparty/astyle-3.6.19`.
`tools/build-formatter.ps1` ricompila il tool Windows con MinGW-w64.

## Licenza E Copyright

GPLv2 con Classpath Exception; consultare [LICENSE.txt](LICENSE.txt) e
[licenze e sorgenti corrispondenti](LICENSING.md).
Modifiche BlueJ light: © 2026 - Prof. Ing. Raffaele Mele.
I crediti BlueJ sono conservati; l'icona dell'uccello è di Michael Kolling.
