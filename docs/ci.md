# Integracion continua y despliegue

Un fallo en las pruebas impide desplegar la API.

## Workflow

`.github/workflows/ci.yml` corre en cada push y pull request a `develop`:

1. `test`: Java 17 (Temurin), la misma version de `pom.xml` y del `Dockerfile`, y `sh ./mvnw -B test` contra H2 en memoria. No necesita base de datos ni secretos.
2. `deploy`: solo en push a `develop` y solo si `test` paso. Llama al webhook de despliegue de Coolify para que reconstruya la imagen de la API.

`mvnw` esta en Git sin permiso de ejecucion, por eso se invoca con `sh`.

## Despliegue condicionado

El job `deploy` usa dos secretos del repositorio (Settings > Secrets and variables > Actions):

| Secreto | Valor |
|---|---|
| `COOLIFY_WEBHOOK` | URL del webhook de la aplicacion en Coolify (pestana Webhooks, "Deploy Webhook") |
| `COOLIFY_TOKEN` | Token de API de Coolify con permiso de despliegue (Keys & Tokens > API tokens) |

Sin `COOLIFY_WEBHOOK` el paso termina sin desplegar, asi que los forks y los pull requests nunca despliegan. Si las pruebas fallan, el job no corre y el servidor sigue con la version anterior.

En Coolify hay que desactivar el despliegue automatico propio de la aplicacion (Advanced > "Auto Deploy" apagado); si no, Coolify despliega en cada push sin esperar las pruebas.

Si el servidor de la universidad no es alcanzable desde GitHub (red interna), el webhook falla y el job queda en rojo. En ese caso se despliega a mano desde el panel de Coolify ("Redeploy") despues de ver CI en verde. La guia completa esta en [despliegue-coolify.md](despliegue-coolify.md).

## Cobertura

La regla de JaCoCo (lineas 91 %, ramas 85 %) esta en la fase `verify` y el workflow no la ejecuta. Medicion local sobre `develop` (2 nov 2026): 252 pruebas, lineas 96,26 %, ramas 86,51 %, `verify` en verde. El margen en ramas es de 1,5 puntos; para exigir la cobertura en CI basta cambiar `test` por `verify` en el workflow.
