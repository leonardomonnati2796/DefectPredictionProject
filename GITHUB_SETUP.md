# Istruzioni per l'Upload su GitHub

## Passaggi per caricare il progetto su GitHub

### 1. Crea un nuovo repository su GitHub

1. Vai su [GitHub.com](https://github.com)
2. Clicca su "New repository" (o il pulsante "+" in alto a destra)
3. Inserisci i dettagli:
   - **Repository name**: `DefectPredictionProject`
   - **Description**: `Progetto di Predizione dei Difetti Software - Analisi e Simulazione dell'Impatto del Refactoring`
   - **Visibility**: Public o Private (a tua scelta)
   - **NON** inizializzare con README, .gitignore o licenza (già presenti)

### 2. Collega il repository locale a GitHub

Esegui questi comandi nel terminale (nella directory del progetto):

```bash
# Aggiungi il remote origin (sostituisci USERNAME con il tuo username GitHub)
git remote add origin https://github.com/USERNAME/DefectPredictionProject.git

# Rinomina il branch principale in 'main' (se necessario)
git branch -M main

# Push del codice su GitHub
git push -u origin main
```

### 3. Verifica l'upload

1. Vai sul tuo repository su GitHub
2. Verifica che tutti i file siano presenti:
   - `README.md` - Documentazione del progetto
   - `DefectPredictionProject/report.tex` - Report LaTeX completo
   - `DefectPredictionProject/` - Codice sorgente Java
   - `datasets/` - Dataset generati
   - `.gitignore` - File di esclusione Git

### 4. Configurazione aggiuntiva (opzionale)

#### Aggiungi una licenza
1. Vai su "Add file" > "Create new file"
2. Nome file: `LICENSE`
3. Scegli una licenza appropriata (es. MIT License)

#### Configura GitHub Pages (per il report)
1. Vai su Settings > Pages
2. Source: Deploy from a branch
3. Branch: main / (root)
4. Salva

## Struttura del Repository

```
DefectPredictionProject/
├── README.md                           # Documentazione principale
├── .gitignore                          # Esclusioni Git
├── GITHUB_SETUP.md                     # Questo file
├── DefectPredictionProject/
│   ├── report.tex                      # Report LaTeX completo
│   ├── README.md                       # Documentazione tecnica
│   ├── pom.xml                         # Configurazione Maven
│   └── src/main/java/com/ispw2/       # Codice sorgente Java
├── datasets/                           # Dataset generati
│   ├── BOOKKEEPER_*.arff              # Dataset BOOKKEEPER
│   ├── OPENJPA_*.arff                 # Dataset OPENJPA
│   └── *_best.model                   # Modelli ML salvati
└── AFMethod_refactored/               # Metodi refactored
```

## Note Importanti

- Il repository contiene **42 file** con **393,495 righe** di codice
- I dataset sono già processati e pronti per l'uso
- Il report LaTeX è completo e dettagliato
- Tutti i problemi identificati sono stati risolti

## Comandi Utili

```bash
# Verifica lo stato del repository
git status

# Visualizza i commit
git log --oneline

# Aggiungi modifiche future
git add .
git commit -m "Descrizione delle modifiche"
git push origin main

# Clona il repository (per altri sviluppatori)
git clone https://github.com/USERNAME/DefectPredictionProject.git
```

## Supporto

Se hai problemi con l'upload, verifica:
1. Che Git sia configurato correttamente
2. Che tu abbia accesso al repository GitHub
3. Che la connessione internet sia stabile
4. Che non ci siano conflitti di file
