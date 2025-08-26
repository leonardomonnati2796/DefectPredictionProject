# Progetto di Defect Prediction

Questo progetto è un'applicazione Java progettata per analizzare repository software, generare un dataset di metriche a livello di metodo e identificare metodi potenzialmente difettosi (buggy) per suggerire interventi di refactoring. L'analisi viene eseguita su due progetti Apache: **BookKeeper** e **OpenJPA**.

## Funzionalità Principali

- **Integrazione con Git e Jira**: Clona i repository Git specificati e recupera le informazioni su release e bug ticket direttamente da Jira.
- **Generazione di Dataset**: Analizza la cronologia dei commit per calcolare una serie di metriche di processo (NR, NAuth, Churn) e metriche statiche (LOC, Complessità Ciclomatica) per ogni metodo in ogni release.
- **Etichettatura dei Bug**: Utilizza l'algoritmo *proportion* per determinare le versioni in cui un bug è stato introdotto (IV - Introduction Version) e per etichettare i metodi come "buggy" o "clean" in ogni release.
- **Preprocessing dei Dati**: Utilizza la libreria Weka per preparare i dati per l'analisi, includendo la gestione dei valori mancanti, la normalizzazione e la selezione delle feature più rilevanti tramite InfoGain.
- **Identificazione di Metodi "Actionable"**: Isola i metodi critici (AFMethod) che sono stati etichettati come "buggy" e presentano alti valori per le metriche che indicano una scarsa qualità del codice (es. alta complessità).
- **Supporto al Refactoring**: Estrae il codice sorgente del metodo "actionable" identificato e lo confronta con una versione (da modificare manualmente) per valutare l'impatto del refactoring sulle metriche di qualità.

## Tecnologie Utilizzate

- **Java 17**
- **Maven** per la gestione delle dipendenze e il build
- **SLF4J & Logback** per il logging
- **JGit** per l'interazione con i repository Git
- **Apache HTTP Client** & **JSON** per l'interazione con le API di Jira
- **Apache Commons CSV** per la gestione di file CSV
- **Weka** per il preprocessing dei dati
- **JavaParser** per l'analisi statica del codice sorgente Java

## Prerequisiti

- JDK 17 o superiore
- Apache Maven 3.6.0 o superiore
- Connessione a Internet per clonare i repository e contattare le API di Jira

## Installazione ed Esecuzione

1.  **Clonare il repository**:
    ```sh
    git clone <URL_DEL_REPOSITORY>
    cd <NOME_DELLA_CARTELLA>
    ```

2.  **Compilare il progetto**:
    Utilizzare Maven per compilare il progetto e creare il file JAR eseguibile.
    ```sh
    mvn clean package
    ```
    Questo comando creerà un file `defect-prediction-project-1.0-SNAPSHOT.jar` nella cartella `target`.

3.  **Eseguire l'applicazione**:
    Lanciare il JAR dalla root del progetto.
    ```sh
    java -jar target/defect-prediction-project-1.0-SNAPSHOT.jar
    ```

L'applicazione inizierà il processo di analisi per entrambi i progetti configurati.

## Struttura del Progetto

Il codice sorgente è organizzato nei seguenti package:

- `com.ispw2`: Contiene la classe `Main` di avvio e il gestore della configurazione.
- `com.ispw2.connectors`: Classi per la connessione e l'interazione con servizi esterni (Git, Jira).
- `com.ispw2.model`: Classi di dati (POJO e Record) che rappresentano le entità del dominio (es. `JiraTicket`, `ProjectRelease`).
- `com.ispw2.analysis`: Contiene la logica principale per l'analisi del codice, il calcolo delle metriche e l'identificazione dei metodi critici.
- `com.ispw2.preprocessing`: Classi dedicate alla preparazione e pulizia dei dati raccolti tramite Weka.
- `com.ispw2.classification`: (Opzionale) Classi per l'addestramento e la valutazione di modelli di classificazione.

## Output del Progetto

Al termine dell'esecuzione, verranno create due cartelle principali nella directory genitore del progetto:

1.  `github_projects/`: Conterrà i cloni locali dei repository Git analizzati.
2.  `datasets/`: Conterrà:
    - I file `.csv` e `.arff` generati per ogni progetto.
    - I file `_AFMethod.txt` con il codice sorgente dei metodi identificati come critici.
    - Una cartella `AFMethod_refactored` con i file vuoti pronti per il refactoring manuale.
