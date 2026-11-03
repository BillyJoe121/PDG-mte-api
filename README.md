# PDG MTE API

REST API del Modulo de Trazabilidad Estrategica (MTE) de la Escuela TDI.

El backend gestiona catalogos, jerarquia estrategica, profesores, roles, cargos, proyectos, vinculaciones proyecto-KR, dashboard, reportes, modo presentacion, auditoria e integracion de autenticacion. El esquema inicial esta alineado al MER institucional y la base persistente objetivo es Supabase/PostgreSQL.

## Requisitos

- Java 17 o superior.
- Maven Wrapper incluido en el repo: `mvnw.cmd` en Windows, `./mvnw` en Linux/macOS.
- Un proyecto Supabase para la base persistente unica.
- Docker opcional si quieres probar PostgreSQL local antes de Supabase.

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

El esquema completo (tablas, columnas, relaciones, diagrama, valores de estados, indices e historial de migraciones `V1` a `V14`) esta en [docs/database-schema.md](docs/database-schema.md).

[V10__retire_objective_links.sql](src/main/resources/db/migration/V10__retire_objective_links.sql) retira el Objetivo como nivel de la jerarquia: crea `key_result_goal` y `key_result_strategic_bet` (vinculos N:M del KR con Metas y Apuestas), traslada los vinculos de cada objetivo a sus KRs y vuelve opcional `key_result.objective_id`. La tabla `objective` queda como legado y ningun codigo la usa.

## Supabase como Base Persistente

Supabase debe tratarse como PostgreSQL administrado. El backend no depende de APIs especiales de Supabase para datos; solo usa JDBC, Flyway y JPA.

1. Crea un proyecto Supabase vacio.
2. Entra a `Project Settings > Database`.
3. Copia la cadena PostgreSQL directa. Para Flyway usa conexion directa, no transaction pooler.
4. Incluye SSL:

```text
jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
```

Variables recomendadas para la primera conexion:

```powershell
$env:SPRING_PROFILES_ACTIVE='supabase'
$env:SUPABASE_DB_URL='jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require'
$env:SUPABASE_DB_USERNAME='postgres'
$env:SUPABASE_DB_PASSWORD='<password>'
$env:DB_DRIVER='org.postgresql.Driver'
$env:JPA_DDL_AUTO='validate'
$env:FLYWAY_ENABLED='true'
$env:FLYWAY_BASELINE_ON_MIGRATE='false'
$env:MTE_SEED_ENABLED='true'
$env:SUPABASE_URL='https://<auth-project-ref>.supabase.co'
$env:SUPABASE_SECRET_KEY='<server-side-secret-key>'
$env:MTE_ACCESS_LINK_URL='http://localhost:5173/establecer-contrasena'
$env:MTE_TRAYECTORIA_PROJECTS_MODE='mock'
.\mvnw.cmd spring-boot:run
```

Para produccion real, cambia:

```powershell
$env:MTE_SEED_ENABLED='false'
```

Hay una plantilla sin secretos en [supabase.env.example](supabase.env.example). Copiala a un archivo local no versionado si lo necesitas. Spring Boot no carga `.env` automaticamente; puedes exportar variables desde PowerShell, el IDE o la plataforma de despliegue.

Flujo recomendado con archivo local:

```powershell
Copy-Item .\supabase.env.example .\.env.supabase
notepad .\.env.supabase
.\scripts\supabase-validate.ps1
.\scripts\supabase-run.ps1
```

`.env.supabase` esta ignorado por Git. La primera ejecucion contra una base Supabase vacia aplicara Flyway y creara el esquema del MER. Si el arranque termina correctamente, Hibernate tambien habra validado el modelo con `JPA_DDL_AUTO=validate`.

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

Sin `SUPABASE_URL` y `SUPABASE_SECRET_KEY` la API arranca, pero rechaza todo token: para iniciar sesion en local configura un proyecto de Supabase Auth (ver [Autenticacion](#autenticacion)). Los tests no lo necesitan.

H2 es solo un entorno efimero para desarrollo y tests. La base persistente del proyecto debe ser Supabase/PostgreSQL.

## Perfil Supabase

El perfil `supabase` vive en [application-supabase.yml](src/main/resources/application-supabase.yml). Este perfil:

- Usa PostgreSQL por JDBC.
- Desactiva H2 console.
- Activa Flyway.
- Usa `JPA_DDL_AUTO=validate`.
- Habilita seeds por defecto para el primer arranque controlado. Cada arranque con `MTE_SEED_ENABLED=true` limpia las tablas pobladas por seed y las repuebla.

Cuando la base ya este creada y el despliegue sea real, usa:

```powershell
$env:MTE_SEED_ENABLED='false'
```

## PostgreSQL Local Opcional

Puedes levantar PostgreSQL local antes de tocar Supabase:

```powershell
docker run --name mte-postgres `
  -e POSTGRES_DB=mte `
  -e POSTGRES_USER=mte `
  -e POSTGRES_PASSWORD=mte `
  -p 5432:5432 `
  -d postgres:16
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
.\mvnw.cmd spring-boot:run
```

Con `MTE_SEED_ENABLED=true`, el arranque limpia auditoria, proyectos, KRs, metas, apuestas, catalogos base y personas antes de poblar de nuevo. Usalo solo en bases de desarrollo, staging controlado o Supabase de prueba.

Para borrar esa base local:

```powershell
docker rm -f mte-postgres
```

## Migraciones

Las migraciones viven en:

```text
src/main/resources/db/migration
```

Las migraciones actuales van de `V1` a `V14`; la lista con lo que cambia cada una esta en [docs/database-schema.md](docs/database-schema.md#historial-de-migraciones). `V2` y `V3` son no-op porque los campos quedaron incorporados en `V1` antes de aplicar Supabase.

Reglas:

- No editar migraciones ya aplicadas en Supabase o una base compartida.
- Para cambios nuevos despues de la primera aplicacion, crear `V4__descripcion.sql`, `V5__...sql`, etc.
- Mantener SQL compatible con PostgreSQL.
- Usar `JPA_DDL_AUTO=validate` en ambientes persistentes.
- Usar `FLYWAY_BASELINE_ON_MIGRATE=true` solo si conectas una base existente con tablas pero sin `flyway_schema_history`.

Flujo recomendado:

```text
1. Cambiar entidades/servicios/DTOs.
2. Crear migracion Flyway nueva.
3. Ejecutar .\mvnw.cmd test.
4. Probar con PostgreSQL local o Supabase staging.
5. Desplegar contra Supabase productivo.
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

El MTE usa Supabase Auth con correo y contrasena. El frontend nunca habla con Supabase: la API hace el inicio de sesion, valida los tokens y decide el rol.

```text
SUPABASE_URL=https://<auth-project-ref>.supabase.co
SUPABASE_SECRET_KEY=<server-side-secret-key>
MTE_ACCESS_LINK_URL=<url-frontend>/establecer-contrasena
```

- `SUPABASE_SECRET_KEY` es la clave de servidor (`sb_secret_...` o la `service_role` heredada). Solo vive en el backend.
- Los tokens se validan con las llaves publicas del proyecto (JWKS, ES256 o RS256), el emisor `<SUPABASE_URL>/auth/v1` y la audiencia `authenticated`. Sin `SUPABASE_URL` y la clave, todo token se rechaza.
- La persona se resuelve por el `sub` del token (`professor.auth_user_id`); en el primer acceso se vincula por correo. Una persona inactiva o fuera del directorio no entra.
- El rol sale de `professor.access_role`: `ADMIN`, `MANAGER`, `CONTRIBUTOR` o `VISITOR`. `VISITOR` solo consulta; cualquier POST, PUT, PATCH o DELETE le responde 403.
- Tras 5 intentos fallidos seguidos la cuenta se bloquea 15 minutos (`failed_login_attempts`, `locked_until`). El bloqueo queda en auditoria.
- La contrasena tiene entre 10 y 72 caracteres. El MTE no guarda contrasenas ni refresh tokens.
- No hay registro abierto: el administrador genera un enlace de acceso para cada persona del directorio. El enlace lleva a `MTE_ACCESS_LINK_URL` con `token_hash` y `type`, y alli la persona fija su contrasena.
- Cerrar sesion revoca la sesion en Supabase, pero el token ya emitido sigue siendo valido hasta que vence (1 hora por defecto).

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

Configuracion del proyecto de Supabase Auth: registro abierto desactivado, llaves de firma asimetricas y longitud minima de contrasena 10.

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

Ultima verificacion conocida:

```text
Tests run: 160, Failures: 0, Errors: 0, Skipped: 0
```

## Postman

La coleccion se genera desde el contrato OpenAPI con Portman y se ejecuta con Newman. `postman/normalize-collection.cjs` agrega datos validos por endpoint (`collection-requests.cjs`), pruebas de codigo y forma de cada respuesta, y los flujos de `collection-flows.cjs`: usuarios y sesion, permisos por rol, casos negativos, archivo de KR y enlace de Planner. Cada corrida crea sus propios registros con un `runId`, asi que puede repetirse sobre la misma base.

```powershell
npm ci
npm run postman:generate   # requiere la API arriba en http://localhost:8081
npm run postman:normalize
npm run postman:test       # usa postman/mte.local.postman_environment.json
```

Variables: `baseUrl`, `accessToken` (sesion ADMIN), `managerToken`, `visitorToken` y opcionalmente `authMockUrl` con `adminEmail`, `managerEmail` y `visitorEmail`. Si `authMockUrl` esta definido y un token esta vacio, la coleccion lo pide con `POST {{authMockUrl}}/mock/token`. Los tokens de MANAGER y VISITOR que falten se toman del login del flujo de usuarios. No guardes tokens reales en el archivo de entorno.

En local, `npm run postman:auth` levanta un sustituto de Supabase Auth en `http://localhost:9998` (solo 127.0.0.1, datos en memoria). Arranca la API contra una base desechable apuntando a el:

```powershell
$env:SUPABASE_URL = "http://localhost:9998"
$env:SUPABASE_SECRET_KEY = "local-placeholder-key"
$env:MTE_SEED_ENABLED = "true"
```

La semilla deja a todas las personas como `CONTRIBUTOR`, asi que en esa base desechable hay que promover la cuenta de `adminEmail` antes de correr la coleccion:

```sql
UPDATE professor SET access_role = 'ADMIN' WHERE email = 'demo.profesor@icesi.edu.co';
```

Con `MTE_TRAYECTORIA_PROJECTS_MODE=mock` la sincronizacion con Trayectoria responde 503 a proposito, y la coleccion lo espera asi. Los periodos que crea usan un año lejano derivado del `runId` (`AAAA-1` y `AAAA-2`, entre 3000 y 9999). La coleccion solo se ha probado contra este sustituto local de autenticacion, no contra Supabase real. El reporte `postman/newman-report.json` no se versiona.

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
Line coverage:   91%
Branch coverage: 85%
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
SPRING_PROFILES_ACTIVE=supabase
SUPABASE_DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=<password>
DB_DRIVER=org.postgresql.Driver
JPA_DDL_AUTO=validate
FLYWAY_ENABLED=true
MTE_SEED_ENABLED=false
MTE_CORS_ALLOWED_ORIGINS=<url-frontend>
SUPABASE_URL=https://<auth-project-ref>.supabase.co
SUPABASE_SECRET_KEY=<server-side-secret-key>
MTE_ACCESS_LINK_URL=<url-frontend>/establecer-contrasena
```

No guardar credenciales reales en Git. `.env` esta ignorado, pero `supabase.env.example` no contiene secretos y sirve como plantilla.

## Despliegue en Render

El repositorio incluye `Dockerfile`, `.dockerignore`, `render.yaml` y el perfil `staging`. Render solo despliega cuando pasan las pruebas de CI; ver [docs/ci.md](docs/ci.md).

Flujo recomendado:

1. Sube estos archivos al repositorio remoto.
2. En Render crea un Blueprint desde el repositorio o un Web Service con runtime Docker.
3. Si usas Blueprint, Render leera `render.yaml` desde la raiz.
4. Cuando Render pida variables con `sync: false`, define:

```text
SUPABASE_DB_PASSWORD=<password de Supabase>
SUPABASE_URL=https://<auth-project-ref>.supabase.co
SUPABASE_SECRET_KEY=<clave de servidor de Supabase Auth>
MTE_ACCESS_LINK_URL=<url-frontend>/establecer-contrasena
MTE_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000,http://localhost:4200
```

Para staging, el servicio usa:

```text
SPRING_PROFILES_ACTIVE=staging
SUPABASE_DB_URL=jdbc:postgresql://aws-1-us-west-2.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USERNAME=postgres.otqqavjfriyqxntpwcgm
JPA_DDL_AUTO=validate
FLYWAY_ENABLED=true
MTE_SEED_ENABLED=false
MTE_TRAYECTORIA_PROJECTS_MODE=mock
```

El health check de Render apunta a:

```text
/api/v1/health
```

El endpoint valida la conexion a la base de datos, por lo que una caida de Supabase marca el servicio como no saludable (`503`) en Render.

Si el frontend ya esta desplegado, reemplaza o agrega su origen publico en `MTE_CORS_ALLOWED_ORIGINS`, por ejemplo:

```text
MTE_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://mte-frontend.onrender.com
```
