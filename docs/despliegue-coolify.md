# Despliegue en Coolify

Guia para publicar el MTE en el servidor Coolify de la universidad: la base PostgreSQL 17, la API (este repositorio) y el frontend (`PDG-mte-client`). Todo el codigo ya esta listo; aqui solo queda configuracion.

Orden recomendado: base de datos, API, frontend, primer administrador y CI. El traslado de datos desde la base anterior es opcional y va antes de abrir el sistema a los usuarios.

Ninguna credencial real va en Git ni en este documento: los valores se escriben solo en Coolify y en los secretos de GitHub.

## 1. Proyecto en Coolify

1. Crear un proyecto `MTE` con un entorno (por ejemplo `production`).
2. Conectar los repositorios de GitHub de la API y del frontend (GitHub App de Coolify o deploy key). Si la red de la universidad no deja que Coolify llegue a GitHub, avisar a quien administra el servidor antes de seguir.

## 2. Base de datos

1. En el proyecto: New Resource > Database > PostgreSQL, version **17**.
2. Nombre de la base `mte`, usuario `mte` y una contrasena larga generada por Coolify.
3. No publicar el puerto a internet ("Make it publicly available" apagado). La API llega por la red interna de Coolify.
4. Copiar la "Postgres URL (internal)". La API la necesita en formato JDBC:

   ```text
   postgres://mte:<password>@<host-interno>:5432/mte        (lo que muestra Coolify)
   jdbc:postgresql://<host-interno>:5432/mte                (DB_URL)
   ```

   Usuario y contrasena van aparte, en `DB_USERNAME` y `DB_PASSWORD`.
5. En la pestana Backups, activar respaldos programados (por ejemplo diarios, conservando al menos 7) y, si el servidor lo ofrece, un destino S3 fuera de la maquina. Probar una restauracion siguiendo [backup-restore.md](backup-restore.md).

Las tablas no se crean a mano: Flyway aplica `V1` a `V15` en el primer arranque de la API.

## 3. API

New Resource > Application desde el repositorio de la API:

| Campo | Valor |
| --- | --- |
| Rama | `develop` |
| Build Pack | Dockerfile (el `Dockerfile` de la raiz) |
| Puerto expuesto | `8081` |
| Health check | `/api/v1/health` (el `Dockerfile` ya trae `HEALTHCHECK`; responde `503` si la base no contesta) |
| Auto Deploy | apagado (despliega CI, ver seccion 7) |

Variables de entorno (Environment Variables, todas de runtime):

| Variable | Valor | Notas |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` | El `Dockerfile` ya lo fija; dejarlo explicito no estorba. |
| `DB_URL` | `jdbc:postgresql://<host-interno>:5432/mte` | De la seccion 2. |
| `DB_USERNAME` | `mte` | |
| `DB_PASSWORD` | la de Coolify | Marcar como secreta. |
| `MTE_AUTH_JWT_SECRET` | 32 bytes o mas, aleatorio | `openssl rand -base64 48`. Sin ella la API no arranca. Cambiarla cierra todas las sesiones. |
| `MTE_ACCESS_LINK_URL` | `https://<dominio-frontend>/establecer-contrasena` | Sin ella, generar enlaces responde `503`. |
| `MTE_CORS_ALLOWED_ORIGINS` | `https://<dominio-frontend>` | Origen exacto, sin `/` final. Varios se separan con coma. |
| `MTE_SEED_ENABLED` | `false` | Nunca `true` en esta base: la semilla borra y repuebla tablas. |
| `MTE_AUTH_BOOTSTRAP_ADMIN_LINK` | `true` solo para el primer ingreso | Ver seccion 5. |
| `MTE_TRAYECTORIA_PROJECTS_MODE` | `mock` o el modo real | Opcional; con `mock` la sincronizacion responde `503`. |

Opcionales con valores por defecto: `MTE_AUTH_ACCESS_TOKEN_TTL` (`1h`) y `MTE_AUTH_ACCESS_LINK_TTL` (`72h`).

En Domains, asignar el dominio de la API (por ejemplo `https://mte-api.<dominio-u>`). Coolify emite el certificado HTTPS si el dominio apunta al servidor; si la universidad usa certificados propios, configurarlos en el proxy de Coolify.

Desplegar y comprobar:

```powershell
curl.exe -fsS https://<dominio-api>/api/v1/health
```

El primer arranque aplica las migraciones; el log debe mostrar `Successfully applied 15 migrations` (o las que falten).

## 4. Frontend

New Resource > Application desde el repositorio `PDG-mte-client`:

| Campo | Valor |
| --- | --- |
| Rama | `develop` |
| Build Pack | Dockerfile |
| Puerto expuesto | `80` |
| Health check | `/healthz` |
| Auto Deploy | apagado |

Variable de entorno `VITE_API_URL=https://<dominio-api>/api/v1`, marcada como **Build Variable**: se fija al compilar, asi que cambiarla exige redesplegar. Si falta, el build falla a proposito.

Asignar el dominio del frontend en Domains. Debe coincidir con `MTE_CORS_ALLOWED_ORIGINS` y con el inicio de `MTE_ACCESS_LINK_URL` de la API; si cambia, actualizar ambas y redesplegar la API.

## 5. Primer administrador

En una base nueva nadie tiene contrasena y solo un administrador puede generar enlaces de acceso. Por eso:

1. Con `MTE_AUTH_BOOTSTRAP_ADMIN_LINK=true`, desplegar la API. Mientras ningun administrador activo tenga contrasena, el log de arranque muestra un enlace de un solo uso por cada administrador activo (la migracion V11_1 crea `sistemas.mte@icesi.edu.co` como `ADMIN`).
2. Abrir el enlace en el navegador y fijar la contrasena (10 caracteres a 72 bytes).
3. Poner `MTE_AUTH_BOOTSTRAP_ADMIN_LINK=false` y redesplegar. Con la contrasena ya fijada la API deja de emitir el enlace de todas formas, pero la variable no debe quedar encendida.
4. Iniciar sesion como administrador y, desde la gestion de usuarios, generar el enlace de acceso de cada persona y entregarselo por un canal privado. Cada enlace vence en 72 horas y anula los anteriores.

El enlace del log da acceso de administrador: no copiarlo a chats ni tickets, y si se filtra, generar uno nuevo (un enlace nuevo anula el anterior).

## 6. Traslado de datos desde la base anterior (opcional)

Solo si hay que conservar los datos que ya existian. Lo ejecuta una persona del equipo, no CI, y antes de abrir el sistema a los usuarios.

1. Respaldar la base anterior y comprobar el archivo (ver [backup-restore.md](backup-restore.md)).
2. Exportar solo el esquema `public`, sin propietarios ni permisos:

   ```powershell
   pg_dump --format=custom --no-owner --no-privileges --schema=public `
     --file "mte-origen.dump" "<url-de-la-base-anterior>"
   ```

3. Con la API **detenida** en Coolify y la base nueva vacia, abrir el tunel SSH y definir las variables `PG*` como en [backup-restore.md](backup-restore.md), y restaurar:

   ```powershell
   pg_restore --no-owner --no-privileges --dbname mte "mte-origen.dump"
   ```

   El dump trae `flyway_schema_history`, asi que al arrancar la API solo aplica las migraciones que falten (por ejemplo `V15`). `V15` crea una credencial vacia para cada persona que ya tenia cuenta (`professor.auth_user_id`).
4. Arrancar la API y verificar `/api/v1/health` y los conteos de las tablas principales contra la base anterior.

Sin mas pasos, las personas que ya tenian cuenta conservan su rol y sus datos, pero necesitan un enlace de acceso nuevo para fijar contrasena (seccion 5, paso 4).

### Conservar las contrasenas anteriores (opcional)

El servicio de cuentas anterior guardaba hashes BCrypt en `auth.users.encrypted_password`, compatibles con la API. Si se quiere que cada persona siga entrando con su contrasena de siempre:

1. Exportar los hashes desde la base anterior a un archivo local (contiene hashes de contrasenas: guardarlo fuera del repositorio y borrarlo al terminar):

   ```powershell
   psql "<url-de-la-base-anterior>" -c "\copy (select id, encrypted_password from auth.users where encrypted_password like '`$2%') to 'hashes.csv' csv"
   ```

2. Cargarlos en la base nueva, despues del paso 3 y del primer arranque de la API (cuando `auth_credential` ya existe):

   ```sql
   BEGIN;
   CREATE TEMP TABLE imported_hash (user_id uuid, password_hash varchar(100));
   \copy imported_hash from 'hashes.csv' csv
   UPDATE auth_credential c
      SET password_hash = i.password_hash, updated_at = now()
     FROM imported_hash i
    WHERE c.user_id = i.user_id;
   COMMIT;
   ```

   Solo se actualizan personas que ya estan en el directorio del MTE; el resto de filas se ignora.
3. Borrar `hashes.csv`.

## 7. CI y despliegue automatico

Cada repositorio tiene un job `deploy` que corre solo en push a `develop` y solo si pasan las pruebas ([ci.md](ci.md)).

1. En cada aplicacion de Coolify, dejar Auto Deploy apagado y copiar su Deploy Webhook (pestana Webhooks).
2. En Coolify, Keys & Tokens > API tokens: crear un token con permiso de despliegue.
3. En cada repositorio de GitHub (Settings > Secrets and variables > Actions):
   - `COOLIFY_WEBHOOK`: el webhook de esa aplicacion.
   - `COOLIFY_TOKEN`: el token del paso 2.

Sin `COOLIFY_WEBHOOK`, el job se salta. Si GitHub no alcanza el servidor de la universidad, el job falla y se despliega a mano con Redeploy en Coolify despues de ver CI en verde.

## 8. Lista de comprobacion

- [ ] PostgreSQL 17 creado, sin puerto publico, con respaldos programados y una restauracion probada.
- [ ] API desplegada con `prod`, `DB_*`, `MTE_AUTH_JWT_SECRET`, `MTE_ACCESS_LINK_URL`, `MTE_CORS_ALLOWED_ORIGINS` y `MTE_SEED_ENABLED=false`.
- [ ] `https://<dominio-api>/api/v1/health` responde `200`.
- [ ] Frontend desplegado con `VITE_API_URL` como Build Variable; `https://<dominio-frontend>/healthz` responde `ok`.
- [ ] Recargar `https://<dominio-frontend>/login` muestra la pagina (no un 404).
- [ ] Administrador inicial con contrasena; `MTE_AUTH_BOOTSTRAP_ADMIN_LINK=false`.
- [ ] Inicio de sesion desde el frontend sin errores de CORS.
- [ ] Datos trasladados y verificados, si aplica; archivos de dump y `hashes.csv` fuera del repositorio o borrados.
- [ ] `COOLIFY_WEBHOOK` y `COOLIFY_TOKEN` en ambos repositorios; Auto Deploy apagado.
- [ ] Un push a `develop` despliega solo despues de CI en verde (o queda el Redeploy manual documentado).
