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

## Modelo de Datos

La migracion inicial [V1__initial_schema.sql](src/main/resources/db/migration/V1__initial_schema.sql) crea las 16 entidades fisicas del MER:

```text
unit_of_measure
goal_period
role
objective
school
department
position
professor
teacher_position
period
world
strategic_bet
goal
key_result
project
project_teacher
```

Tambien se conservan tablas operativas del backend:

```text
audit_log
project_progress_entry
project_key_result_link
project_tutor
```

Nota importante: como todavia no hay base productiva aplicada, `V1` representa el esquema canonical para Supabase. No conectes una base compartida hasta que estes listo para que Flyway aplique este esquema.

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
$env:MTE_AUTH_MODE='mock'
$env:MTE_TRAYECTORIA_PROJECTS_MODE='mock'
.\mvnw.cmd spring-boot:run
```

Para produccion real, cambia:

```powershell
$env:MTE_SEED_ENABLED='false'
$env:MTE_AUTH_MODE='external'
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
MTE_AUTH_MODE=mock
```

H2 es solo un entorno efimero para desarrollo y tests. La base persistente del proyecto debe ser Supabase/PostgreSQL.

## Perfil Supabase

El perfil `supabase` vive en [application-supabase.yml](src/main/resources/application-supabase.yml). Este perfil:

- Usa PostgreSQL por JDBC.
- Desactiva H2 console.
- Activa Flyway.
- Usa `JPA_DDL_AUTO=validate`.
- Habilita seeds por defecto para el primer arranque controlado.
- Usa `MTE_AUTH_MODE=mock` por defecto para facilitar la primera validacion de base.

Cuando la base ya este creada y el despliegue sea real, usa:

```powershell
$env:MTE_SEED_ENABLED='false'
$env:MTE_AUTH_MODE='external'
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
$env:MTE_AUTH_MODE='mock'
.\mvnw.cmd spring-boot:run
```

Para borrar esa base local:

```powershell
docker rm -f mte-postgres
```

## Migraciones

Las migraciones viven en:

```text
src/main/resources/db/migration
```

Migraciones actuales:

```text
V1__initial_schema.sql
V2__complete_msp_story_fields.sql
V3__rename_future_period_status.sql
```

`V2` y `V3` son no-op porque los campos quedaron incorporados en `V1` antes de aplicar Supabase.

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
GET/POST            /api/v1/strategic-bets
GET/POST            /api/v1/goals
POST/DELETE         /api/v1/goals/{id}/periods/{periodId}
GET/POST/PATCH      /api/v1/objectives
GET/POST            /api/v1/objectives/{id}/key-results
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

Modo local/demo:

```text
MTE_AUTH_MODE=mock
```

En modo mock, el backend autentica como:

```text
Usuario: demo.admin
Roles:   ADMIN, DIRECTOR_ESCUELA
```

Modo externo:

```text
MTE_AUTH_MODE=external
TRAYECTORIA_BASE_URL=https://<url-auth>/api/v1
TRAYECTORIA_ME_PATH=/auth/me
```

Cada request protegido debe incluir:

```http
Authorization: Bearer <token>
```

Supabase Auth se integrara como proveedor de tokens/autenticacion. La tabla `professor` es dominio interno: guarda nombre, correo unico y departamento, no credenciales.

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
MTE_AUTH_MODE=external
MTE_CORS_ALLOWED_ORIGINS=<url-frontend>
```

No guardar credenciales reales en Git. `.env` esta ignorado, pero `supabase.env.example` no contiene secretos y sirve como plantilla.
