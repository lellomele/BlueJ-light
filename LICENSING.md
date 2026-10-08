# Licenze E Sorgenti

BlueJ light 5.5.2 e' una versione modificata indipendente di BlueJ 5.5.0.
Il codice BlueJ e le modifiche BlueJ light mantengono la GNU GPL versione 2
con Classpath Exception, riportata integralmente in [LICENSE.txt](LICENSE.txt).
I copyright originali restano validi. Le modifiche nei sorgenti sono identificate
con avvisi datati; gli avvisi dell'icona originale sono conservati in
[bluej/icons/license.txt](bluej/icons/license.txt) e
[bluej/icons/CREDITS.txt](bluej/icons/CREDITS.txt).

La Classpath Exception consente il collegamento con moduli indipendenti sotto
le rispettive licenze; non elimina gli obblighi sui sorgenti BlueJ modificati.
Le librerie di terze parti restano separate nei loro JAR, senza modifiche.

## Sorgenti Corrispondenti

La [release 5.5.2](https://github.com/lellomele/BlueJ-light/releases/tag/v5.5.2-light)
distribuisce i pacchetti Windows insieme a:

- `BlueJ-light-5.5.2-source.zip`: sorgenti dell'applicazione, launcher e installer.
- `OpenJDK21U-jdk-sources_21.0.6_7.tar.gz`: sorgenti ufficiali Temurin/OpenJDK
  21.0.6+7, compresi VM, librerie e strumenti; per portable e installer completo.
- `OpenJFX-23.0.2-sources.zip`: sorgenti OpenJFX 23.0.2-ga, inclusi i componenti nativi.
- `BlueJ-light-5.5.2-dependency-sources.zip`: sorgenti delle librerie incluse,
  con inventario delle versioni, provenienza e impronte SHA-256.

I binari Inno Setup conservano i propri avvisi e indirizzi originali; la licenza
del motore installer e' inclusa in `bluej/doc/thirdpartylicenses/Inno-Setup.txt`.
Il launcher nativo senza JDK viene compilato con GCC/MinGW e usa la GCC Runtime
Library Exception per i componenti di runtime collegati staticamente.

## Componenti Principali

| Componente | Versione | Licenza |
| --- | --- | --- |
| BlueJ e icona originale | Base 5.5.0 | GPLv2 con Classpath Exception |
| Temurin/OpenJDK | 21.0.6+7 | GPLv2 con Classpath Exception e avvisi in `runtime/legal` |
| OpenJFX | 23.0.2 | GPLv2 con Classpath Exception; ulteriori avvisi dei componenti nativi |
| Eclipse Layout Kernel | 0.11.0 | EPL 2.0 |
| Eclipse Modeling Framework | 2.12.0 | EPL 1.0 |
| Eclipse Xbase | 2.36.0 | EPL 2.0 |
| XOM | 1.3.9 | LGPL 2.1 |
| JNA | 5.7.0 | Apache 2.0, nell'alternativa di licenza prevista dal progetto |
| Inno Setup | 7.1.0 | Inno Setup License |

I testi integrali delle licenze e gli avvisi sono in
`bluej/doc/thirdpartylicenses`, `bluej/doc/THIRDPARTYLICENSE.txt`,
`bluej/doc/LIGHT-THIRDPARTY.txt` e nei JAR originali. Gli avvisi upstream
storici possono citare versioni precedenti: l'inventario distribuito con i
sorgenti delle dipendenze identifica le versioni effettivamente incluse.
I termini completi dei singoli componenti prevalgono su questo riepilogo.

Riferimenti originali: [licenza BlueJ](https://www.bluej.org/about/license.html),
[EPL 1.0](https://www.eclipse.org/legal/epl/epl-v10.html),
[EPL 2.0](https://www.eclipse.org/legal/epl/epl-v20.html),
[sorgenti Temurin](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.6%2B7),
[sorgenti OpenJFX](https://github.com/openjdk/jfx23u/tree/23.0.2-ga).
