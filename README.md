# BlueJ light 5.5.2

BlueJ light e' un ambiente Java basato su BlueJ 5.5.0, con editor incrementale,
completamento automatico, snippet personalizzabili e riordino del diagramma delle classi.
La versione mostrata nell'applicazione e' **5.5.2-light**.
E' un progetto indipendente, non una release ufficiale del progetto BlueJ.

I pacchetti Windows e i sorgenti corrispondenti sono nella
[release 5.5.2](https://github.com/lellomele/BlueJ-light/releases/tag/v5.5.2-light).

## Installazione

Sono disponibili due installer Windows x64: **completo**, con JDK 21 incluso,
e **senza JDK**, per chi possiede gia' un JDK 21 Windows x64. Entrambi includono
JavaFX, il collegamento nel menu Start e la disinstallazione. La versione senza
JDK cerca Java automaticamente e consente di selezionarne la cartella se necessario.
Il solo JRE non basta. Le due varianti installano la stessa applicazione.

Per Windows, estrarre l'intero pacchetto portabile e aprire **BlueJ light.exe**.
Il pacchetto include il JDK 21 e JavaFX; non richiede un'installazione separata di Java.
Non spostare l'eseguibile da solo: le cartelle `app` e `runtime` sono necessarie.
Nella variante portable le preferenze e gli snippet personali restano nella cartella
`data`, accanto all'eseguibile. Estrarre in una cartella scrivibile e mantenere
il file `portable.flag`; i progetti possono essere salvati dove si preferisce.

## Uso

Aprire o creare un progetto BlueJ. Un doppio clic su una classe apre l'editor Java.
Il completamento si apre dopo il punto; `Ctrl+Spazio` lo richiama manualmente.
Le preferenze dell'editor consentono di attivare anche il completamento durante
la scrittura degli identificatori.

Gli snippet, fra cui `sout`, `fori`, `foreach`, `main` e `trycatch`, compaiono nel
completamento nei contesti appropriati. `Tab` e `Shift+Tab` passano fra i campi;
`Esc` termina la modifica dei campi. I segnaposto ripetuti rimangono sincronizzati.
Per personalizzarli, usare `snippets.properties` nella cartella delle preferenze
indicata nella finestra Info; il file distribuito in `app` contiene gli esempi.
I test JUnit richiedono la corrispondente libreria nel progetto.

**Aiuto > Snippet Java** mostra l'elenco completo, l'anteprima del codice e l'uso
dei campi. Nell'editor, **Strumenti > Sfoglia snippet Java** o `Ctrl+Shift+Spazio`
apre il catalogo ricercabile: frecce e PagSu/PagGiu scorrono l'elenco, Invio inserisce
lo snippet. I filtri distinguono corpo del metodo e membri della classe; quelli
compatibili con la posizione del cursore vengono mostrati per primi.

Premere **Riordina diagramma** nella barra a sinistra, scegliere lo stesso comando
nel menu **Modifica**, oppure usare
`Ctrl+Shift+L`. Sono disponibili annullamento, ripristino e interruzione del riordino.
Il comando considera i collegamenti attualmente visibili. Il calcolo dispone i box
e riduce gli incroci delle frecce; alcuni grafi richiedono comunque incroci.
Le classi di test associate vengono mantenute accanto alla classe principale.
Un movimento manuale invalida i percorsi automatici: riordinare nuovamente il diagramma
per ricalcolarli. L'annullamento del riordino e' disponibile fino alla successiva
modifica manuale della geometria del diagramma.

## Requisiti E Limiti

Il pacchetto Windows richiede Windows 10/11 a 64 bit.
La telemetria Blackbox e le statistiche di utilizzo all'avvio sono rimosse: non
vengono raccolti o inviati eventi, sorgenti, contatori o identificativi di ricerca.
Team e il supporto Git integrato sono rimossi. I progetti vengono aperti come
progetti locali; i metadati Git/SVN gia' presenti non vengono cancellati.
La verifica degli aggiornamenti e gli avvisi ufficiali restano disponibili.
L'estensione Submitter per l'invio dei progetti non e' inclusa.
Su file Java molto grandi, l'apertura e il parsing iniziale possono richiedere tempo.
Il completamento usa il motore nativo BlueJ; non include un language server esterno.
Greenfoot e' escluso dalla compilazione. Il supporto interno a Stride resta incluso,
ma i suoi componenti grafici vengono creati soltanto quando si apre un editor Stride.

## Compilazione

Installare un JDK 21. Il wrapper scarica Gradle e le dipendenze al primo utilizzo.

```text
./gradlew :bluej:assemble
./gradlew :bluej:runBlueJ
```

Su Windows usare `gradlew.bat`. Per generare il pacchetto Windows portabile:

```powershell
./tools/package-windows.ps1 -JdkPath "C:/percorso/jdk-21" -OutputDirectory "C:/percorso/output"
```

Le icone SVG, PNG e ICO sono incluse nei sorgenti. Per rigenerarle servono Node.js
e il pacchetto `sharp`; eseguire `node tools/generate-icons.cjs`.

## Copyright

Modifiche BlueJ light: © 2026 - Prof. Ing. Raffaele Mele.
BlueJ mantiene i copyright e i crediti dei suoi autori originali.
L'uccello nell'icona e' l'icona originale BlueJ di Michael Kolling;
crediti e licenza sono conservati in `bluej/icons` nei sorgenti.
Consultare `LICENSE.txt` per GPL con Classpath exception e
`bluej/doc/THIRDPARTYLICENSE.txt` e `bluej/doc/LIGHT-THIRDPARTY.txt` per le librerie.
La Classpath Exception viene mantenuta nelle modifiche BlueJ light.
Le [informazioni di licenza](LICENSING.md) indicano i sorgenti delle dipendenze.
