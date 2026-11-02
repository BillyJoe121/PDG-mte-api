# Respaldo y restauracion de la base de datos

Procedimiento para respaldar la base PostgreSQL de staging (Supabase, Postgres 17.6) que usa el servicio `pdg-mte-api-staging` en Render, y para comprobar que el respaldo se puede restaurar.

Alcance del respaldo:

- Incluye el esquema `public`: todas las tablas de la aplicacion y `flyway_schema_history`.
- No incluye los esquemas que administra Supabase (`auth`, `storage`, `realtime`, `extensions`, etc.). Las cuentas de Supabase Auth no quedan en este respaldo.

## Requisitos

- Docker Desktop en marcha. No hace falta instalar PostgreSQL en el equipo.
- Imagen `postgres:18`. `pg_dump` tiene que ser de la misma version del servidor o mas nueva; la 18 respalda sin problema un servidor 17.6. El archivo que genera `pg_dump` 18 solo lo lee `pg_restore` 18 o superior, asi que la restauracion tambien usa `postgres:18`.
- Datos de conexion del pooler de sesion de Supabase (puerto 5432, no el 6543 del pooler de transacciones):
  - host: `aws-1-us-west-2.pooler.supabase.com`
  - puerto: `5432`
  - base: `postgres`
  - usuario: el valor de `SUPABASE_DB_USERNAME` en `render.yaml` (`postgres.<project-ref>`)
  - contrasena: la de la base en el panel de Supabase o en el gestor de contrasenas. Nunca se escribe en un comando, en un archivo ni en este repositorio.
- Una carpeta de trabajo fuera de cualquier repositorio Git, por ejemplo `C:\Users\<usuario>\respaldos-mte`. El `.dump` tiene datos reales y no debe quedar dentro del proyecto.
- PowerShell 7 para `Read-Host -MaskInput`. En Windows PowerShell 5.1 ver la alternativa del paso 1.

## 1. Sacar el respaldo

Desde la carpeta de trabajo fuera del repositorio:

```powershell
cd C:\Users\<usuario>\respaldos-mte

$env:PGHOST = "aws-1-us-west-2.pooler.supabase.com"
$env:PGPORT = "5432"
$env:PGDATABASE = "postgres"
$env:PGUSER = "postgres.<project-ref>"   # SUPABASE_DB_USERNAME de render.yaml
$env:PGSSLMODE = "require"
$env:PGPASSWORD = Read-Host "Contrasena de la base" -MaskInput

$fecha = Get-Date -Format yyyy-MM-dd
docker run --rm `
  -e PGHOST -e PGPORT -e PGDATABASE -e PGUSER -e PGSSLMODE -e PGPASSWORD `
  -v "${PWD}:/backup" `
  postgres:18 pg_dump -Fc -n public -f "/backup/mte-staging-$fecha.dump"

Remove-Item Env:PGPASSWORD
```

`-e PGPASSWORD` sin valor hace que Docker copie la variable del entorno actual al contenedor; la contrasena no aparece en el comando ni en el historial.

En Windows PowerShell 5.1, que no tiene `-MaskInput`:

```powershell
$s = Read-Host "Contrasena de la base" -AsSecureString
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
```

Banderas de `pg_dump`:

| Bandera | Para que |
|---|---|
| `-Fc` | Formato custom: comprimido y restaurable con `pg_restore`, que permite listar el contenido y restaurar por partes. |
| `-n public` | Solo el esquema de la aplicacion. Los esquemas de Supabase necesitan roles propios de Supabase y no se pueden restaurar en un Postgres comun. |
| `-f` | Archivo de salida dentro de la carpeta montada en `/backup`. |

Comprobar que el archivo es legible y trae las tablas esperadas:

```powershell
docker run --rm -v "${PWD}:/backup:ro" postgres:18 pg_restore --list "/backup/mte-staging-$fecha.dump"
Get-FileHash "mte-staging-$fecha.dump" -Algorithm SHA256
```

La lista debe mostrar entradas `TABLE` y `TABLE DATA` de `public`, entre ellas `professor` y `flyway_schema_history`. Anotar el SHA256 junto al archivo para detectar copias danadas.

## 2. Donde guardarlo

El respaldo contiene datos reales de personas (nombres, correos, cargos). Por eso:

- Se guarda en un almacenamiento privado fuera del portatil: una carpeta de Drive (o similar) con acceso solo para el responsable del proyecto, sin enlaces compartidos.
- Recomendado: comprimirlo cifrado antes de subirlo (por ejemplo 7-Zip con AES-256) y guardar la clave en el gestor de contrasenas.
- Nunca en este repositorio ni en otro, ni en carpetas compartidas con otras personas, ni en Render, Jira, chats o correos.
- Despues de subirlo y comprobar el SHA256 de la copia, se borra la copia local.
- Conservar al menos el respaldo semanal mas reciente y el ultimo previo a cada migracion. Al cerrar el proyecto se eliminan segun el acuerdo de manejo de datos con la institucion.

## 3. Restaurar en una base desechable

La restauracion de prueba se hace siempre en un contenedor nuevo, nunca sobre staging. El contenedor no publica puertos y guarda los datos en memoria (`--tmpfs`), asi que todo desaparece al borrarlo.

Desde la carpeta donde esta el `.dump`:

```powershell
$env:POSTGRES_PASSWORD = [guid]::NewGuid().ToString("N")   # clave de un solo uso
$dump = "mte-staging-<fecha>.dump"

docker run -d --name mte-restore-check `
  --tmpfs /var/lib/postgresql `
  -e POSTGRES_PASSWORD `
  -v "${PWD}:/backup:ro" `
  postgres:18

docker exec mte-restore-check pg_isready -U postgres   # repetir hasta "accepting connections"

docker exec mte-restore-check psql -U postgres -d postgres -c "drop schema public cascade;"
docker exec mte-restore-check pg_restore --exit-on-error -U postgres -d postgres --no-owner --no-acl "/backup/$dump"
```

- `drop schema public cascade` vacia el esquema `public` que trae la base nueva. Sin ese paso `pg_restore` termina con un unico error, `schema "public" already exists`, que es inofensivo pero oculta errores reales.
- `--no-owner` y `--no-acl` omiten duenos y permisos de roles que solo existen en Supabase (`anon`, `authenticated`, `service_role`, etc.).
- `--exit-on-error` detiene la restauracion en el primer error; el comando debe terminar sin mensajes.

## 4. Verificar la restauracion

Tablas restauradas:

```powershell
docker exec mte-restore-check psql -U postgres -d postgres -c "\dt public.*"
```

Filas por tabla:

```powershell
docker exec mte-restore-check psql -U postgres -d postgres -c "select table_name, (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from public.%I', table_name), false, true, '')))[1]::text::int as filas from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE' order by table_name;"
```

Historial de Flyway:

```powershell
docker exec mte-restore-check psql -U postgres -d postgres -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"
```

La restauracion es valida cuando:

- estan todas las tablas de la aplicacion y `flyway_schema_history`;
- los conteos coinciden con los de staging en el momento del respaldo (Table Editor de Supabase) o con el registro de la verificacion anterior;
- la ultima version del historial es la ultima migracion aplicada en staging y todas las filas tienen `success = t`.

Al terminar:

```powershell
docker rm -f mte-restore-check
Remove-Item Env:POSTGRES_PASSWORD
```

## 5. Frecuencia

- Respaldo antes de cada migracion que llegue a staging (obligatorio, ver la seccion 6).
- Respaldo semanal mientras staging tenga datos reales, y antes de cargas masivas de datos o de una entrega.
- Restauracion de prueba (secciones 3 y 4) al menos una vez al mes, y siempre que cambie la version de Postgres en Supabase o la imagen usada para `pg_dump`.

## 6. Antes de una migracion

Staging se despliega solo desde `develop` y Flyway aplica las migraciones nuevas al arrancar. Antes de integrar una migracion en `develop`:

1. Sacar un respaldo de staging (seccion 1) y comprobarlo con `pg_restore --list`.
2. Restaurarlo en un contenedor desechable (seccion 3). Para este paso el contenedor si publica un puerto, solo en el equipo local: agregar `-p 127.0.0.1:55434:5432` al `docker run`.
3. Arrancar la API contra la copia para que Flyway aplique la migracion nueva, con la semilla apagada:

   ```powershell
   $env:SPRING_PROFILES_ACTIVE = "prod"
   $env:DB_URL = "jdbc:postgresql://127.0.0.1:55434/postgres"
   $env:DB_USERNAME = "postgres"
   $env:DB_PASSWORD = $env:POSTGRES_PASSWORD
   $env:JPA_DDL_AUTO = "validate"
   $env:FLYWAY_ENABLED = "true"
   $env:MTE_SEED_ENABLED = "false"   # con true se borran los datos de la copia
   .\mvnw.cmd spring-boot:run
   ```

4. Confirmar que la API arranca, que el historial de Flyway muestra la version nueva con `success = t` y que los conteos de la seccion 4 siguen teniendo sentido (la migracion no borro datos que no debia).
5. Solo entonces integrar en `develop`. Despues del despliegue, revisar `/api/v1/health` y el historial de Flyway en staging.
6. Si la migracion falla en staging, lo preferible es una migracion correctiva nueva. Restaurar el respaldo encima de staging no esta probado: requiere detener el servicio en Render, sacar antes un respaldo del estado actual y planearlo aparte.

## Registro de pruebas

**2 nov 2026.** Respaldo `mte-staging-2026-11-02.dump` de staging (Supabase Postgres 17.6) con `pg_dump` 18 (`postgres:18`), formato `-Fc` y `-n public`. `pg_restore --list` mostro las tablas, entre ellas `professor` y `flyway_schema_history`. Se restauro con `--no-owner --no-acl` en un Postgres 18 desechable en memoria: historial de Flyway en V10 y los 9 profesores de staging. Sobre esa copia se aplicaron V11 y V11_1, y la API arranco con el esquema validado. El contenedor se borro al terminar.

**2 nov 2026.** Los comandos de esta guia (variables `PG*`, `pg_dump`, `pg_restore --list`, `drop schema`, `pg_restore --exit-on-error` y las tres consultas de verificacion) se repitieron con datos de prueba entre contenedores desechables, usando como origen la imagen de Supabase Postgres 17.6. La restauracion termino sin errores y los contenedores se borraron.
