# Progetto di Defect Prediction

Questo progetto è un'applicazione Java progettata per analizzare repository software, generare un dataset di metriche a livello di metodo e identificare metodi potenzialmente difettosi (buggy). L'analisi viene eseguita su due progetti Apache: **BookKeeper** e **OpenJPA**.

L'obiettivo finale è simulare l'impatto del refactoring su un metodo critico e quantificare la potenziale riduzione dei difetti attraverso metriche personalizzate.

## Funzionalità Principali

- **Integrazione con Git e Jira**: Clona i repository Git e recupera le informazioni su release e bug ticket direttamente dalle API di Jira.
- **Generazione di Dataset**: Analizza la cronologia dei commit per calcolare una serie di metriche di processo (es. NR, NAuth, Churn) e metriche statiche (es. LOC, Complessità Ciclomatica) per ogni metodo in ogni release.
- **Etichettatura dei Bug**: Utilizza l'algoritmo *proportion* per stimare la versione di introduzione di un bug (IV) e per etichettare i metodi come "buggy" o "clean".
- **Preprocessing dei Dati**: Utilizza la libreria Weka per preparare i dati, includendo la gestione dei valori mancanti, la normalizzazione e la selezione delle feature più rilevanti tramite InfoGain.
- **Training e Valutazione Modelli**: Addestra e valuta tre diversi classificatori (Random Forest, Naive Bayes, IBk), seleziona il migliore (`BClassifier`) e lo salva su file per riutilizzarlo in esecuzioni future.
- **Simulazione "What-If"**: Identifica un metodo critico (`AFMethod`) e simula l'impatto del refactoring su di esso, calcolando due metriche finali (`drop` e `reduction`) che misurano il beneficio potenziale.
- **Supporto al Refactoring**: Estrae il codice sorgente del metodo critico e lo confronta con una versione refattorizzata manualmente, mostrando l'impatto sulle metriche di qualità del codice.
- **Configurabilità e Logging Avanzato**: Permette una facile configurazione tramite file esterni e produce report dettagliati e separati per ogni progetto analizzato.

## Tecnologie Utilizzate

- **Java 17**
- **Maven** per la gestione delle dipendenze e il build
- **SLF4J & Logback** per il sistema di logging
- **JGit** per l'interazione con i repository Git
- **Apache HTTP Client** & **JSON** per l'interazione con le API di Jira
- **Apache Commons CSV** per la gestione di file CSV
- **Weka** per il preprocessing dei dati e il machine learning
- **JavaParser** per l'analisi statica del codice sorgente Java

## Prerequisiti

- JDK 17 o superiore
- Apache Maven 3.6.0 o superiore
- Connessione a Internet

## Installazione ed Esecuzione

#### 1. Clonare il Repository
```sh
git clone <URL_DEL_REPOSITORY>
cd <NOME_DELLA_CARTELLA>
```

#### 2. Configurare il Progetto
Prima di eseguire, è possibile personalizzare alcuni parametri nel file `src/main/resources/config.properties`, come ad esempio la percentuale di release da analizzare.

#### 3. Compilare il Progetto
Utilizzare Maven per compilare il progetto e creare il file JAR eseguibile.
```sh
mvn clean package
```
Questo comando creerà un file `defect-prediction-project-1.0-SNAPSHOT.jar` nella cartella `target`.

#### 4. Eseguire l'Applicazione
Lanciare il JAR dalla **cartella radice del progetto** (la stessa che contiene la cartella `target`).
```sh
java -jar target/defect-prediction-project-1.0-SNAPSHOT.jar
```
L'applicazione inizierà il processo di analisi per entrambi i progetti configurati.

## Configurazione del Logging

Il sistema di logging è gestito tramite il file `src/main/resources/logback.xml` e offre due funzionalità principali:

1.  **Doppio Output**: I log vengono stampati sia sulla **console** (per un feedback immediato) sia su **file di report**.
2.  **Report Separati per Progetto**: Vengono creati file di report distinti per ogni progetto (es. `BOOKKEEPER-report.txt` e `OPENJPA-report.txt`) nella directory principale del progetto. Questi file contengono solo i log di livello `INFO` e superiori.

Per un'analisi più approfondita, è possibile cambiare il livello di log generale da `DEBUG` a `TRACE` nel file `logback.xml` per visualizzare ogni singolo dettaglio dell'esecuzione.

## Struttura del Progetto

-   `pom.xml`: Definisce le dipendenze e la configurazione di build.
-   `src/main/java/com/ispw2/`:
    -   `Main`: Classe di avvio che orchestra l'intera pipeline.
    -   `ConfigurationManager`: Carica e fornisce i parametri di configurazione.
    -   `connectors/`: Classi per l'interazione con Git e Jira.
    -   `model/`: Classi di dati (POJO e Record) che rappresentano le entità del dominio.
    -   `analysis/`: Contiene la logica principale per il calcolo delle metriche, l'analisi e la simulazione.
    -   `preprocessing/`: Classi dedicate alla preparazione e pulizia dei dati tramite Weka.
    -   `classification/`: Contiene il `ClassifierRunner` per l'addestramento e la valutazione dei modelli.
-   `src/main/resources/`:
    -   `config.properties`: File di configurazione per i parametri della pipeline.
    -   `logback.xml`: File di configurazione per il sistema di logging.

## Output del Progetto

Al termine dell'esecuzione, verranno create le seguenti cartelle e file **nella directory genitore** della cartella del progetto:

-   `github_projects/`: Contiene i cloni locali dei repository Git analizzati.
-   `datasets/`: Contiene:
    -   I file `.csv` e `.arff` generati per ogni progetto.
    -   I file `_best.model` con il modello di classificazione addestrato.
    -   I file `_AFMethod.txt` con il codice sorgente dei metodi critici identificati.
-   `AFMethod_refactored/`: Contiene i file di testo (`_AFMethod_refactored.txt`) in cui inserire il codice refattorizzato per la comparazione.

Inoltre, nella **directory radice del progetto**, verranno creati i file di report:
-   `BOOKKEEPER-report.txt`
-   `OPENJPA-report.txt`
