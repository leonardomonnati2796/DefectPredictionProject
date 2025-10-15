# Progetto di Predizione dei Difetti Software

## Descrizione

Questo progetto implementa un sistema completo di predizione dei difetti software che analizza progetti open-source, identifica metodi problematici e simula l'impatto di operazioni di refactoring. Il sistema utilizza tecniche di machine learning per predire la presenza di difetti e valuta l'efficacia di miglioramenti del codice attraverso simulazioni "what-if".

## Caratteristiche Principali

- **Analisi Automatica**: Estrazione di metriche da repository Git
- **Machine Learning**: Training e ottimizzazione di classificatori (RandomForest, NaiveBayes, IBk)
- **Simulazione What-If**: Valutazione dell'impatto del refactoring
- **Confronto Feature**: Analisi completa delle metriche prima e dopo il refactoring
- **Calibrazione Dinamica**: Ottimizzazione automatica delle soglie di decisione

## Tecnologie Utilizzate

- **Java 11+**: Linguaggio di programmazione principale
- **Weka**: Framework per machine learning e data mining
- **JavaParser**: Parsing e analisi del codice Java
- **Maven**: Gestione delle dipendenze e build
- **SLF4J + Logback**: Sistema di logging
- **JGit**: Interfaccia per repository Git
- **Jira REST API**: Integrazione con sistemi di issue tracking

## Struttura del Progetto

```
DefectPredictionProject/
├── src/main/java/com/ispw2/
│   ├── DefectPredictionPipeline.java          # Orchestratore principale
│   ├── dataset/
│   │   ├── DatasetGenerator.java              # Generazione dataset
│   │   └── DatasetPreprocessor.java           # Preprocessing dati
│   ├── classification/
│   │   └── MachineLearningModelTrainer.java   # Training modelli ML
│   ├── analysis/
│   │   ├── RefactoringImpactAnalyzer.java     # Simulazione refactoring
│   │   └── MethodFeatureComparator.java       # Confronto feature
│   └── preprocessing/
│       └── DatasetUtilities.java              # Utility dataset
├── datasets/                                  # Dataset generati
├── report.tex                                 # Report LaTeX completo
└── pom.xml                                    # Configurazione Maven
```

## Risultati Sperimentali

### Progetto BOOKKEEPER

- **Release Analizzate**: 11 release (da 4.0.0 a 4.2.1)
- **Metodi Analizzati**: 13,836 istanze
- **Classificatore Migliore**: IBk (AUC: 0.935)
- **Riduzione Difetti**: 80.5% (da 2,791 a 543 difetti predetti)

### Confronto Feature

| Feature | Originale | Refactored | Miglioramento |
|---------|-----------|------------|---------------|
| CyclomaticComplexity | 20.00 | 2.00 | +18.00 |
| CodeSmells | 2.00 | 1.00 | +1.00 |
| ParameterCount | 1.00 | 1.00 | 0.00 |
| NestingDepth | 2.00 | 3.00 | -1.00 |

## Come Eseguire il Progetto

### Prerequisiti

- Java 11 o superiore
- Maven 3.6 o superiore
- Accesso a internet (per download dipendenze)

### Esecuzione

```bash
# Clona il repository
git clone <repository-url>
cd DefectPredictionProjectFinal/DefectPredictionProject

# Compila il progetto
mvn clean compile

# Esegui il pipeline completo
mvn exec:java -Dexec.mainClass="com.ispw2.DefectPredictionPipeline"
```

### Configurazione

Il file `src/main/resources/config.properties` contiene le configurazioni:

```properties
# Percorsi di output
datasets.output.path=../datasets
github.projects.path=../github_projects

# Configurazioni Jira
jira.base.url=https://issues.apache.org/jira
jira.timeout=30000
```

## Milestone Implementati

### Milestone 1: Generazione e Preprocessing
- ✅ Estrazione metriche da repository Git
- ✅ Integrazione con Jira per mapping difetti
- ✅ Preprocessing e bilanciamento dataset
- ✅ Selezione feature con InfoGain

### Milestone 2: Analisi e Simulazione
- ✅ Valutazione classificatori base
- ✅ Ottimizzazione iperparametri
- ✅ Identificazione metodo target
- ✅ Simulazione what-if
- ✅ Calibrazione soglia dinamica
- ✅ Confronto feature completo

## Problemi Risolti

1. **Formattazione Probabilità**: Risolto problema placeholder `{:.3f}`
2. **Predizioni Zero**: Implementata calibrazione dinamica soglia
3. **Retraining Forzato**: Rimosso retraining automatico
4. **Confronto Limitato**: Implementato accesso completo alle feature

## Autori

- **Studente**: [Nome Studente]
- **Corso**: Ingegneria del Software II
- **Anno Accademico**: 2024/2025

## Licenza

Questo progetto è rilasciato sotto licenza MIT. Vedi il file LICENSE per i dettagli.

## Contributi

I contributi sono benvenuti! Per favore:

1. Fork del repository
2. Crea un branch per la tua feature (`git checkout -b feature/AmazingFeature`)
3. Commit delle modifiche (`git commit -m 'Add some AmazingFeature'`)
4. Push al branch (`git push origin feature/AmazingFeature`)
5. Apri una Pull Request

## Contatti

Per domande o supporto, contatta [email@example.com]
