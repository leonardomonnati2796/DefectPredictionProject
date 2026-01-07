@echo off
setlocal enabledelayedexpansion
set "CP=DefectPredictionProject\target\classes"
if exist "DefectPredictionProject\DefectPredictionProject\target\dependency\*" (
  set "DEPDIR=DefectPredictionProject\DefectPredictionProject\target\dependency"
) else (
  set "DEPDIR=DefectPredictionProject\target\dependency"
)
for %%f in (%DEPDIR%\*.jar) do (
  set "CP=!CP!;%%~f"
)

echo Running FeatureImportanceChecker with classpath length: %CP:~0,200%...
java -cp "%CP%" com.ispw2.analysis.FeatureImportanceChecker %1
endlocal
