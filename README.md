# PDG MTE API

REST API del Modulo de Trazabilidad Estrategica (MTE) de la Escuela TDI.

El backend gestiona catalogos, jerarquia estrategica, profesores, roles, cargos, proyectos, vinculaciones proyecto-KR, dashboard, reportes, modo presentacion, auditoria y autenticacion propia (cuentas, contrasenas y enlaces de acceso). El esquema inicial esta alineado al MER institucional y la base persistente es PostgreSQL 17 en el servidor Coolify de la universidad.

## Requisitos

- Java 17 o superior.
- Maven Wrapper incluido en el repo: `mvnw.cmd` en Windows, `./mvnw` en Linux/macOS.
- Docker opcional para probar PostgreSQL local o construir la imagen.

## Comandos Rapidos

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd clean package
```

Linux/macOS:

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw verify
./mvnw clean package
```

La API queda por defecto en:

```text
http://localhost:8081
```

Rutas utiles:

```text
Swagger UI: http://localhost:8081/swagger-ui.html
Health:     http://localhost:8081/api/v1/health
H2 console: http://localhost:8081/h2-console
```

`GET /api/v1/health` es publico y valida la conexion a la base de datos: responde `200` con `{"status":"UP","database":"UP",...}` o `503` con `{"status":"DOWN","database":"DOWN",...}` si la base no responde.

## Modelo de Datos

El esquema completo (tablas, columnas, relaciones, diagrama, valores de estados, indices e historial de migraciones `V1` a `V16`) esta en [docs/database-schema.md](docs/database-schema.md).

[V10__retire_objective_links.sql](src/main/resources/db/migration/V10__retire_objective_links.sql) retira el Objetivo como nivel de la jerarquia: crea `key_result_goal` y `key_result_strategic_bet` (vinculos N:M del KR con Metas y Apuestas), traslada los vinculos de cada objetivo a sus KRs y vuelve opcional `key_result.objective_id`. La tabla `objective` queda como legado y ningun codigo la usa.

## Ejecucion Local

El perfil por defecto sigue siendo `dev` para pruebas rapidas. En `dev`, Spring Boot usa H2 en memoria y crea el esquema con Hibernate:

```text
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:h2:mem:mte;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
DB_USERNAME=sa
DB_PASSWORD=
DB_DRIVER=org.h2.Driver
JPA_DDL_AUTO=create-drop
FLYWAY_ENABLED=false
MTE_SEED_ENABLED=true
```

En `dev` no hace falta ningun servicio externo: sin `MTE_AUTH_JWT_SECRET` la API firma los tokens con una llave temporal y las sesiones terminan al reiniciarla. Para entrar en local, define `MTE_ACCESS_LINK_URL` y `MTE_AUTH_BOOTSTRAP_ADMIN_LINK=true`: al arrancar, el log muestra un enlace de un solo uso para el administrador inicial (ver [Autenticacion](#autenticacion)).

H2 es solo un entorno efimero para desarrollo y tests. La base persistente es PostgreSQL.

## Perfiles prod y staging

[application-prod.yml](src/main/resources/application-prod.yml) y [application-staging.yml](src/main/resources/application-staging.yml):

- Usan PostgreSQL por JDBC con `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` (sin valores por defecto).
- Desactivan H2 console.
- Activan Flyway y usan `JPA_DDL_AUTO=validate`.
- Exigen `MTE_AUTH_JWT_SECRET`: sin el, la API no arranca.
- Dejan la semilla apagada (`MTE_SEED_ENABLED=false`). Cada arranque con la semilla encendida limpia las tablas pobladas por seed y las repuebla.

`staging` ademas fija `MTE_TRAYECTORIA_PROJECTS_MODE=mock` por defecto. El `Dockerfile` arranca con `prod`.

## PostgreSQL Local Opcional

Puedes levantar PostgreSQL local con la misma version del servidor:

```powershell
docker run --name mte-postgres `
  -e POSTGRES_DB=mte `
  -e POSTGRES_USER=mte `
  -e POSTGRES_PASSWORD=mte `
  -p 5432:5432 `
  -d postgres:17
```

Ejecuta con Flyway y validacion JPA:

```powershell
$env:SPRING_PROFILES_ACTIVE='prod'
$env:DB_URL='jdbc:postgresql://localhost:5432/mte'
$env:DB_USERNAME='mte'
$env:DB_PASSWORD='mte'
$env:DB_DRIVER='org.postgresql.Driver'
$env:JPA_DDL_AUTO='validate'
$env:FLYWAY_ENABLED='true'
$env:MTE_SEED_ENABLED='true'
$env:MTE_AUTH_JWT_SECRET='<32 caracteres o mas, solo para esta prueba>'
$env:MTE_ACCESS_LINK_URL='http://localhost:5173/establecer-contrasena'
$env:MTE_AUTH_BOOTSTRAP_ADMIN_LINK='true'
.\mvnw.cmd spring-boot:run
```

Con `MTE_SEED_ENABLED=true`, el arranque limpia auditoria, proyectos, KRs, metas, apuestas, catalogos base y personas antes de poblar de nuevo; entre las personas recrea el administrador inicial (`sistemas.mte@icesi.edu.co`). Usalo solo en bases de desarrollo o de prueba, nunca en la base del servidor.

Para borrar esa base local:

```powershell
docker rm -f mte-postgres
```

## Migraciones

Las migraciones viven en:

```text
src/main/resources/db/migration
```

Las migraciones actuales van de `V1` a `V16`; la lista con lo que cambia cada una esta en [docs/database-schema.md](docs/database-schema.md#historial-de-migraciones). `V2` y `V3` son no-op porque los campos quedaron incorporados en `V1` antes de la primera aplicacion.

Reglas:

- No editar migraciones ya aplicadas en el servidor o en una base compartida: cambia su checksum y Flyway detiene el arranque.
- Para cambios nuevos despues de la primera aplicacion, crear `V4__descripcion.sql`, `V5__...sql`, etc.
- Mantener SQL compatible con PostgreSQL.
- Usar `JPA_DDL_AUTO=validate` en ambientes persistentes.
- Usar `FLYWAY_BASELINE_ON_MIGRATE=true` solo si conectas una base existente con tablas pero sin `flyway_schema_history`.

Flujo recomendado:

```text
1. Cambiar entidades/servicios/DTOs.
2. Crear migracion Flyway nueva.
3. Ejecutar .\mvnw.cmd test.
4. Probar con PostgreSQL local o una copia restaurada (docs/backup-restore.md).
5. Respaldar la base del servidor y desplegar.
```

## Endpoints Principales

Catalogos:

```text
GET/POST/PUT/DELETE /api/v1/schools
GET/POST/PUT/DELETE /api/v1/departments
GET/POST/PUT/DELETE /api/v1/measurement-units
GET/POST/PUT/DELETE /api/v1/academic-periods
```

Personas y roles:

```text
GET/POST/PUT/DELETE /api/v1/roles
GET/POST/PUT/DELETE /api/v1/positions
GET/POST/PUT/DELETE /api/v1/professors
GET/POST/DELETE     /api/v1/professors/{id}/positions
```

Estrategia:

```text
GET/POST/PUT/DELETE /api/v1/worlds
GET/POST/PUT        /api/v1/strategic-bets
GET/POST/PUT        /api/v1/goals
POST/DELETE         /api/v1/goals/{id}/periods/{periodId}
GET/POST            /api/v1/key-results
GET/PUT/DELETE      /api/v1/key-results/{id}
PATCH               /api/v1/key-results/{id}/progress
PATCH               /api/v1/key-results/{id}/status
```

Proyectos:

```text
GET/POST/PUT        /api/v1/projects
PATCH               /api/v1/projects/{id}/status
POST                /api/v1/projects/{id}/progress
GET                 /api/v1/projects/{id}/history
GET/POST/DELETE     /api/v1/projects/{id}/teachers
POST                /api/v1/project-key-result-links
DELETE              /api/v1/project-key-result-links/{id}
POST                /api/v1/projects/sync/trayectoria
```

Dashboard, reportes, presentacion y auditoria:

```text
GET /api/v1/dashboard
GET /api/v1/reports/general
GET /api/v1/presentation
GET /api/v1/audit-logs
```

Swagger muestra el contrato completo en:

```text
http://localhost:8081/swagger-ui.html
```

## Autenticacion

La API autentica por si misma con correo y contrasena; no depende de ningun servicio externo.

```text
MTE_AUTH_JWT_SECRET=<32 bytes o mas, aleatorio>      obligatorio en prod y staging
MTE_ACCESS_LINK_URL=<url-frontend>/establecer-contrasena
MTE_AUTH_ACCESS_TOKEN_TTL=1h                         opcional
MTE_AUTH_ACCESS_LINK_TTL=72h                         opcional
MTE_AUTH_BOOTSTRAP_ADMIN_LINK=false                  true solo para el primer ingreso
```

- La contrasena se guarda solo como hash BCrypt (costo 12) en `auth_credential`. Tiene entre 10 caracteres y 72 bytes, y se rechazan contrasenas comunes, un solo caracter repetido o las que contienen el nombre del correo.
- Los tokens son JWT HS256 firmados con `MTE_AUTH_JWT_SECRET`, con emisor `mte-api` y audiencia `mte`, y vencen en 1 hora. Cambiar el secreto cierra todas las sesiones. Genera uno con `openssl rand -base64 48`.
- La persona se resuelve por el `sub` del token (`professor.auth_user_id`). Una persona inactiva o fuera del directorio no entra.
- El rol sale de `professor.access_role`: `ADMIN`, `MANAGER`, `CONTRIBUTOR` o `VISITOR`. `VISITOR` solo consulta; cualquier POST, PUT, PATCH o DELETE le responde 403.
- Tras 5 intentos fallidos seguidos la cuenta se bloquea 15 minutos (`failed_login_attempts`, `locked_until`). El bloqueo queda en auditoria.
- No hay registro abierto ni correo: el administrador genera un enlace de acceso para cada persona del directorio y se lo entrega. El enlace lleva a `MTE_ACCESS_LINK_URL` con `token_hash` y `type` (`invite` o `recovery`), es de un solo uso, vence en 72 horas y anula los anteriores. En la base solo queda su hash SHA-256.
- Cerrar sesion revoca el token (`auth_revoked_token`): desde ese momento la API lo rechaza.

Primer ingreso en una base nueva: solo un administrador puede generar enlaces. Con `MTE_AUTH_BOOTSTRAP_ADMIN_LINK=true`, y solo mientras ningun administrador activo tenga contrasena, la API escribe en el log al arrancar un enlace de un solo uso por cada administrador activo. Tras fijar la contrasena, vuelve a ponerla en `false`.

Endpoints:

```text
POST /api/v1/auth/login                              publico
POST /api/v1/auth/set-password                       publico, con token_hash del enlace
POST /api/v1/auth/logout                             con sesion
POST /api/v1/auth/password                           con sesion, pide la contrasena actual
GET  /api/v1/auth/me                                 con sesion
POST /api/v1/directory-users/{id}/access-link        solo ADMIN
```

Cada request protegido debe incluir:

```http
Authorization: Bearer <token>
```

## Integracion de Proyectos Externos

Modo mock:

```text
MTE_TRAYECTORIA_PROJECTS_MODE=mock
```

Modo externo:

```text
MTE_TRAYECTORIA_PROJECTS_MODE=external
TRAYECTORIA_BASE_URL=https://<url-trayectoria>/api/v1
TRAYECTORIA_PROJECTS_PATH=/proyectos
TRAYECTORIA_SOURCE_NAME=TRAYECTORIA_DOCENTE
```

Endpoint:

```http
POST /api/v1/projects/sync/trayectoria
```

El MER exige `project.key_result_id`. Si un proyecto externo no trae relacion directa a KR, el servicio intenta asociarlo al primer KR disponible como respaldo. Si no hay ningun KR, el item se reporta como fallido.

## Tests

Todos los tests:

```powershell
.\mvnw.cmd test
```

Una clase especifica:

```powershell
.\mvnw.cmd '-Dtest=ProjectServiceTest' test
```

Compilar tests sin ejecutarlos:

```powershell
.\mvnw.cmd '-DskipTests' test-compile
```

Ultima verificacion conocida (12 nov 2026, `mvnw -B verify`):

```text
Tests run: 503, Failures: 0, Errors: 0, Skipped: 0
```

## Postman

La coleccion se genera desde el contrato OpenAPI con Portman y se ejecuta con Newman. `postman/normalize-collection.cjs` agrega datos validos por endpoint (`collection-requests.cjs`), pruebas de codigo y forma de cada respuesta, y los flujos de `collection-flows.cjs`: usuarios y sesion, permisos por rol, casos negativos, archivo de KR y enlace de Planner. Cada corrida crea sus propios registros con un `runId`, asi que puede repetirse sobre la misma base.

```powershell
npm ci
npm run postman:generate   # requiere la API arriba en http://localhost:8081
npm run postman:normalize
npm run postman:test       # usa postman/mte.local.postman_environment.json
```

Variables: `baseUrl`, `accessToken` (sesion ADMIN), `managerToken`, `visitorToken`, `adminEmail` y `adminPassword`. Si `accessToken` esta vacio y hay `adminEmail` y `adminPassword`, la coleccion inicia sesion con `POST {{baseUrl}}/api/v1/auth/login`. Los tokens de MANAGER y VISITOR que falten se toman del login del flujo de usuarios. `adminPassword` es secreto: se pasa al correr (`--env-var adminPassword=...`) y nunca se guarda en el archivo de entorno.

En local, arranca la API contra una base desechable con la semilla y el enlace inicial:

```powershell
$env:MTE_SEED_ENABLED = "true"
$env:MTE_ACCESS_LINK_URL = "http://localhost:5173/establecer-contrasena"   # sin ella, los enlaces de acceso responden 503
$env:MTE_AUTH_BOOTSTRAP_ADMIN_LINK = "true"
```

La semilla deja a las personas como `CONTRIBUTOR` y recrea el administrador inicial de la migracion V11_1 (`sistemas.mte@icesi.edu.co`, `ADMIN`), que es el `adminEmail` del entorno local. Usa el enlace del log para fijar su contrasena (en el frontend o con `POST /api/v1/auth/set-password`) y pasa esa contrasena como `adminPassword`.

Con `MTE_TRAYECTORIA_PROJECTS_MODE=mock` la sincronizacion con Trayectoria responde 503 a proposito, y la coleccion lo espera asi. Los periodos que crea usan un año lejano derivado del `runId` (`AAAA-1` y `AAAA-2`, entre 3000 y 9999). El reporte `postman/newman-report.json` no se versiona.

## Cobertura

El proyecto usa JaCoCo.

Generar reporte:

```powershell
.\mvnw.cmd test
```

Abrir reporte HTML:

```powershell
Start-Process .\target\site\jacoco\index.html
```

Ejecutar validacion de cobertura:

```powershell
.\mvnw.cmd verify
```

Umbrales configurados:

```text
Line coverage:        95%
Branch coverage:      95%
Instruction coverage: 95%
```

## Build y Despliegue

Build con tests:

```powershell
.\mvnw.cmd clean package
```

Build sin tests:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Ejecutar JAR:

```powershell
java -jar .\target\pdg-mte-api-0.0.1-SNAPSHOT.jar
```

Variables minimas para despliegue productivo:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<host-postgres>:5432/<base>
DB_USERNAME=<usuario>
DB_PASSWORD=<password>
MTE_AUTH_JWT_SECRET=<32 bytes o mas, aleatorio>
MTE_ACCESS_LINK_URL=<url-frontend>/establecer-contrasena
MTE_CORS_ALLOWED_ORIGINS=<url-frontend>
```

No guardar credenciales reales en Git. `.env` esta ignorado.

## Despliegue en Coolify

La API, el frontend y la base se despliegan en el servidor Coolify de la universidad. El `Dockerfile` construye el JAR, corre como usuario sin privilegios, arranca con el perfil `prod` y trae un `HEALTHCHECK` contra `/api/v1/health`, que valida la base de datos (`503` si no responde).

Jenkins construye el repositorio de Bitbucket y despliega `develop` solo cuando pasan las pruebas y la cobertura ([docs/ci.md](docs/ci.md)). La guia paso a paso (base, variables, primer administrador, traslado de datos y respaldos) esta en [docs/despliegue-coolify.md](docs/despliegue-coolify.md), y el resumen para revisar cada despliegue en [docs/checklist-despliegue.md](docs/checklist-despliegue.md).
