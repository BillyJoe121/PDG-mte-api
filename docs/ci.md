# Integracion continua y despliegue

El repositorio de referencia es el de Bitbucket (`https://pi2tools.icesi.edu.co/bitbucket/scm/PDGPROYEC/pdg-mte-api.git`). Jenkins construye cada push a ese repositorio y solo despliega `develop` cuando todo pasa. Un fallo en las pruebas o en la cobertura impide desplegar.

## Pipeline de Jenkins

El `Jenkinsfile` de la raiz define un pipeline declarativo con dos etapas:

1. `Verify`: en un contenedor `eclipse-temurin:17-jdk` (la misma version de `pom.xml` y del `Dockerfile`) corre `sh ./mvnw -B verify` contra H2 en memoria. `verify` ejecuta las pruebas, la regla de JaCoCo y la prueba de presupuesto de consultas por pantalla (`ScreenQueryBudgetE2ETest`). Publica los resultados JUnit y archiva el reporte de JaCoCo.
2. `Deploy`: solo en la rama `develop` y solo si `Verify` paso. Llama al Deploy Webhook de Coolify, que descarga el codigo desde Bitbucket y reconstruye la imagen de la API.

`mvnw` esta en Git sin permiso de ejecucion, por eso se invoca con `sh`.

### Configuracion en Jenkins (una vez)

1. Plugins: Pipeline, Docker Pipeline, Credentials Binding, JUnit y la integracion con Bitbucket Server (por ejemplo "Bitbucket Server Integration").
2. Credenciales (Manage Jenkins > Credentials), tipo "Secret text":

   | ID | Valor |
   |---|---|
   | `coolify-api-webhook` | URL del Deploy Webhook de la API en Coolify (pestana Webhooks) |
   | `coolify-token` | Token de API de Coolify con permiso de despliegue (Keys & Tokens > API tokens). Lo comparte el frontend. |

   Para leer el repositorio, una credencial de Bitbucket (usuario y token HTTP o llave SSH) con permiso de lectura.
3. Crear un job "Multibranch Pipeline" con fuente Bitbucket Server apuntando al repositorio `PDGPROYEC/pdg-mte-api` y script `Jenkinsfile`. Multibranch hace que `when { branch 'develop' }` funcione.
4. En Bitbucket (Repository settings > Webhooks), crear el webhook hacia Jenkins para eventos de push, o activar el disparador que ofrece el plugin. Sin webhook, configurar un escaneo periodico del multibranch.

El nodo que ejecuta `Deploy` necesita `curl`. Si Jenkins no tiene Docker, cambiar el `agent` de `Verify` por un nodo con JDK 17.

### Ninguna credencial en Git

Los valores viven solo en Jenkins y en Coolify. El `Jenkinsfile` los referencia por ID con `withCredentials`, y el comando `curl` va entre comillas simples para que Groovy no los interpole ni aparezcan en el log.

## GitHub Actions

`.github/workflows/ci.yml` se conserva como verificacion del espejo de GitHub: corre `sh ./mvnw -B verify` en cada push y pull request a `develop`, pero ya no despliega.

## Cobertura

La regla de JaCoCo exige 95 % de lineas, ramas e instrucciones sobre las clases medidas (las exclusiones estan en `pom.xml`) y corre en la fase `verify`, asi que Jenkins y GitHub Actions la hacen cumplir. Localmente:

```powershell
.\mvnw.cmd -B verify
```

El reporte queda en `target/site/jacoco/index.html`.

## Presupuesto de consultas

`ScreenQueryBudgetE2ETest` cuenta las sentencias SQL de cada endpoint que alimenta la primera carga de una pantalla y falla si superan su presupuesto. Si un cambio necesita mas consultas, revisar primero que no sea una consulta por fila (N+1) antes de subir el numero.
