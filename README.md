# PDG MTE API

REST API del Modulo de Trazabilidad Estrategica (MTE) de la Escuela TDI.

El backend gestiona catalogos, jerarquia estrategica, proyectos, vinculaciones proyecto-KR, dashboard, reportes, modo presentacion, auditoria y autenticacion contra la plataforma externa cuando esta disponible.

## Requisitos

- Java 17 o superior.
- Maven Wrapper incluido en el repo: `mvnw.cmd` en Windows, `./mvnw` en Linux/macOS.
- PostgreSQL 14+ para ambientes persistentes. Supabase funciona porque expone PostgreSQL.
- Docker opcional para levantar PostgreSQL local.

## Comandos Rapidos

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd clean package
.\mvnw.cmd clean package -DskipTests
```

Linux/macOS:

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw verify
./mvnw clean package
./mvnw clean package -DskipTests
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

## Ejecucion Local con H2

El perfil por defecto es `dev`. No necesitas levantar una base externa. Spring Boot crea una base H2 en memoria cada vez que inicia.

```powershell
.\mvnw.cmd spring-boot:run
```

Configuracion efectiva en `dev`:

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

En H2 console usa:

```text
JDBC URL: jdbc:h2:mem:mte
User:     sa
Password:
```

## Ejecucion con PostgreSQL Local

Puedes levantar PostgreSQL local con Docker:

```powershell
docker run --name mte-postgres `
  -e POSTGRES_DB=mte `
  -e POSTGRES_USER=mte `
  -e POSTGRES_PASSWORD=mte `
  -p 5432:5432 `
  -d postgres:16
```

Luego ejecuta el backend en modo `prod` para probar migraciones Flyway y validacion JPA:

```powershell
$env:SPRING_PROFILES_ACTIVE='prod'
$env:DB_URL='jdbc:postgresql://localhost:5432/mte'
$env:DB_USERNAME='mte'
$env:DB_PASSWORD='mte'
$env:DB_DRIVER='org.postgresql.Driver'
$env:JPA_DDL_AUTO='validate'
$env:FLYWAY_ENABLED='true'
$env:MTE_SEED_ENABLED='false'
$env:MTE_AUTH_MODE='mock'
.\mvnw.cmd spring-boot:run
```

Para borrar la base local y empezar de cero:

```powershell
docker rm -f mte-postgres
```

## Supabase / PostgreSQL Remoto

Supabase debe tratarse como una base PostgreSQL administrada. La aplicacion no debe depender de nada especifico de Supabase, solo de variables de entorno.

1. Crea un proyecto en Supabase.
2. Entra a `Project Settings > Database`.
3. Copia la cadena de conexion PostgreSQL directa. Para migraciones Flyway es preferible usar conexion directa, no transaction pooler.
4. Asegurate de incluir SSL:

```text
jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
```

Variables recomendadas para Supabase:

```powershell
$env:SPRING_PROFILES_ACTIVE='prod'
$env:DB_URL='jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require'
$env:DB_USERNAME='postgres'
$env:DB_PASSWORD='<password>'
$env:DB_DRIVER='org.postgresql.Driver'
$env:JPA_DDL_AUTO='validate'
$env:FLYWAY_ENABLED='true'
$env:FLYWAY_BASELINE_ON_MIGRATE='false'
$env:MTE_SEED_ENABLED='false'
$env:MTE_AUTH_MODE='external'
$env:TRAYECTORIA_BASE_URL='https://<url-trayectoria>/api/v1'
```

Para demo sin autenticacion externa:

```powershell
$env:MTE_AUTH_MODE='mock'
```

Con esas variables, inicia la app:

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway aplicara automaticamente las migraciones pendientes al arrancar.

## Migraciones de Base de Datos

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

Reglas:

- Nunca editar una migracion que ya fue aplicada en Supabase, produccion o una base compartida.
- Para cada cambio nuevo de esquema crea un archivo nuevo: `V4__descripcion_del_cambio.sql`, `V5__...sql`, etc.
- Mantener SQL compatible con PostgreSQL.
- Probar primero en PostgreSQL local o en una base Supabase de staging.
- Usar `JPA_DDL_AUTO=validate` en ambientes persistentes. Hibernate valida, Flyway migra.
- Usar `FLYWAY_BASELINE_ON_MIGRATE=true` solo una vez si se conecta una base existente que ya tiene tablas pero no tiene `flyway_schema_history`.

Flujo recomendado para cambiar esquema:

```text
1. Crear entidad/campo en Java.
2. Crear nueva migracion Vn__descripcion.sql.
3. Probar con PostgreSQL local en perfil prod.
4. Ejecutar .\mvnw.cmd test.
5. Desplegar a Supabase/staging.
6. Desplegar a produccion.
```

Cuando la Universidad quiera desplegar en su propia base, solo debe crear una base PostgreSQL y usar las mismas variables:

```text
DB_URL=jdbc:postgresql://<host>:<port>/<database>
DB_USERNAME=<usuario>
DB_PASSWORD=<password>
DB_DRIVER=org.postgresql.Driver
FLYWAY_ENABLED=true
JPA_DDL_AUTO=validate
```

Si su infraestructura exige SSL, agregar `?sslmode=require` o el modo SSL que indique el DBA.

## Tests

Todos los tests:

```powershell
.\mvnw.cmd test
```

Una clase especifica:

```powershell
.\mvnw.cmd '-Dtest=ProjectServiceTest' test
```

Varias clases:

```powershell
.\mvnw.cmd '-Dtest=ProjectServiceTest,StrategyServiceObjectiveTest' test
```

Un metodo especifico:

```powershell
.\mvnw.cmd '-Dtest=ProjectServiceTest#createsLocalProjectWhenPayloadIsValid' test
```

Compilar tests sin ejecutarlos:

```powershell
.\mvnw.cmd '-DskipTests' test-compile
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

## Build y Ejecucion del JAR

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

Con perfil productivo:

```powershell
$env:SPRING_PROFILES_ACTIVE='prod'
java -jar .\target\pdg-mte-api-0.0.1-SNAPSHOT.jar
```

## Autenticacion

Modo demo/local:

```text
MTE_AUTH_MODE=mock
```

En modo mock, el backend autentica como:

```text
Usuario: demo.admin
Roles:   ADMIN, DIRECTOR_ESCUELA
```

Modo integrado:

```text
MTE_AUTH_MODE=external
TRAYECTORIA_BASE_URL=https://<url-trayectoria>/api/v1
TRAYECTORIA_ME_PATH=/auth/me
```

En modo externo cada request protegido debe incluir:

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

## CORS

Configura los dominios del frontend:

```text
MTE_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:4200
```

Ejemplo produccion:

```text
MTE_CORS_ALLOWED_ORIGINS=https://mte.icesi.edu.co,https://mte-front.vercel.app
```

## Variables de Entorno Principales

```text
PORT=8081
SPRING_PROFILES_ACTIVE=dev|prod
DB_URL=jdbc:...
DB_USERNAME=...
DB_PASSWORD=...
DB_DRIVER=org.h2.Driver|org.postgresql.Driver
JPA_DDL_AUTO=create-drop|validate
FLYWAY_ENABLED=false|true
FLYWAY_BASELINE_ON_MIGRATE=false|true
MTE_SEED_ENABLED=true|false
MTE_AUTH_MODE=mock|external
TRAYECTORIA_BASE_URL=http://localhost:8080/api/v1
TRAYECTORIA_ME_PATH=/auth/me
MTE_TRAYECTORIA_PROJECTS_MODE=mock|external
TRAYECTORIA_PROJECTS_PATH=/proyectos
TRAYECTORIA_SOURCE_NAME=TRAYECTORIA_DOCENTE
MTE_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:4200
```

No guardar credenciales reales en Git. El archivo `.env` esta ignorado por `.gitignore`, pero Spring Boot no lo carga automaticamente si no lo configura el IDE, el sistema operativo o la plataforma de despliegue.

## Despliegue

Build:

```bash
./mvnw clean package -DskipTests
```

Start:

```bash
java -jar target/pdg-mte-api-0.0.1-SNAPSHOT.jar
```

Variables minimas para un despliegue productivo:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<host>:<port>/<database>?sslmode=require
DB_USERNAME=<usuario>
DB_PASSWORD=<password>
DB_DRIVER=org.postgresql.Driver
JPA_DDL_AUTO=validate
FLYWAY_ENABLED=true
MTE_SEED_ENABLED=false
MTE_AUTH_MODE=external
TRAYECTORIA_BASE_URL=<url-backend-trayectoria>
MTE_CORS_ALLOWED_ORIGINS=<url-frontend>
```

