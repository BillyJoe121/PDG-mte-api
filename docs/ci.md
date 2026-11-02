# Integracion continua y despliegue

Un fallo en las pruebas impide desplegar la API en Render.

## Workflow

`.github/workflows/ci.yml` corre en cada push y pull request a `develop`:

1. Java 17 (Temurin), la misma version de `pom.xml` y del `Dockerfile`.
2. `sh ./mvnw -B test` contra H2 en memoria; no necesita base de datos ni secretos.

`mvnw` esta en Git sin permiso de ejecucion, por eso se invoca con `sh`.

## Despliegue condicionado

`render.yaml` define `autoDeployTrigger: checksPass`: Render despliega `pdg-mte-api-staging` solo cuando los checks de GitHub del commit en `develop` pasan. Si fallan, el commit no se despliega y el servicio sigue con la version anterior.

Si el servicio de Render no esta sincronizado con el Blueprint, el ajuste se hace a mano en el panel: Settings > Build & Deploy > Auto-Deploy = "After CI Checks Pass".

## Cobertura

La regla de JaCoCo (lineas 91 %, ramas 85 %) esta en la fase `verify` y el workflow no la ejecuta. Medicion local sobre `develop` (2 nov 2026): 252 pruebas, lineas 96,26 %, ramas 86,51 %, `verify` en verde. El margen en ramas es de 1,5 puntos; para exigir la cobertura en CI basta cambiar `test` por `verify` en el workflow.
