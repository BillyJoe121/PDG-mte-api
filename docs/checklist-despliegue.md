# Lista de despliegue

Resumen para preparar y revisar un despliegue de la API y el frontend. Los pasos detallados estan en [despliegue-coolify.md](despliegue-coolify.md), el pipeline en [ci.md](ci.md) y los respaldos en [backup-restore.md](backup-restore.md).

Ningun valor de esta lista va en Git: los secretos viven solo en Coolify y en Jenkins.

## 1. Variables de entorno de la API

Se configuran en Coolify como variables de runtime. Las marcadas como secretas se guardan como tales.

| Variable | Para que sirve | Obligatoria en `prod` |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Perfil de Spring; en el servidor, `prod`. El `Dockerfile` ya lo fija. | si |
| `PORT` | Puerto HTTP de la API (por defecto `8081`). | no |
| `DB_URL` | URL JDBC de PostgreSQL. | si |
| `DB_USERNAME` | Usuario de la base. | si |
| `DB_PASSWORD` | Contrasena de la base. Secreta. | si |
| `DB_DRIVER` | Clase del driver JDBC; el valor por defecto sirve para PostgreSQL. | no |
| `JPA_DDL_AUTO` | Modo de Hibernate sobre el esquema; en el servidor solo valida, el esquema lo maneja Flyway. | no |
| `FLYWAY_ENABLED` | Activa las migraciones al arrancar. | no |
| `FLYWAY_BASELINE_ON_MIGRATE` | Solo para adoptar una base que ya tenia tablas sin historial de Flyway. | no |
| `MTE_AUTH_JWT_SECRET` | Llave para firmar las sesiones (32 bytes o mas, aleatoria). Secreta. Cambiarla cierra todas las sesiones. | si |
| `MTE_AUTH_REQUIRE_JWT_SECRET` | Impide arrancar sin `MTE_AUTH_JWT_SECRET` en otros perfiles; `prod` ya lo exige siempre. | no |
| `MTE_AUTH_ISSUER` | Emisor que se escribe y se exige en los tokens. | no |
| `MTE_AUTH_ACCESS_TOKEN_TTL` | Duracion de una sesion. | no |
| `MTE_AUTH_ACCESS_LINK_TTL` | Vigencia de un enlace para establecer contrasena. | no |
| `MTE_ACCESS_LINK_URL` | Pagina del frontend que abre los enlaces de acceso. | si |
| `MTE_CORS_ALLOWED_ORIGINS` | Origenes del frontend con permiso de llamar a la API, exactos y separados por coma. | si |
| `MTE_SEED_ENABLED` | Datos de demostracion; siempre `false` en el servidor (la semilla borra tablas). | si |
| `MTE_AUTH_BOOTSTRAP_ADMIN_LINK` | Permite crear el enlace del primer administrador; se apaga despues del primer ingreso. | solo al inicio |
| `MTE_API_DOCS_ENABLED` | Publica Swagger y `/v3/api-docs`. Apagado por defecto en `prod` y `staging`. | no |
| `MTE_TRAYECTORIA_PROJECTS_MODE` | Origen de los proyectos de Trayectoria (`mock` o el modo real). | no |
| `TRAYECTORIA_BASE_URL` | Direccion del servicio de Trayectoria en el modo real. | con el modo real |
| `TRAYECTORIA_PROJECTS_PATH` | Ruta de los proyectos dentro de ese servicio. | con el modo real |
| `TRAYECTORIA_SOURCE_NAME` | Nombre con el que se registra ese origen. | no |

Frontend: una sola variable, `VITE_API_URL` (direccion publica de la API terminada en `/api/v1`), como **Build Variable**. Se fija al construir la imagen y tambien define el unico origen externo que la politica de seguridad del navegador deja llamar. Cambiarla exige redesplegar.

## 2. Credenciales de Jenkins

Tipo "Secret text", en Manage Jenkins > Credentials:

- [ ] `coolify-api-webhook`: Deploy Webhook de la aplicacion de la API en Coolify.
- [ ] `coolify-client-webhook`: Deploy Webhook de la aplicacion del frontend.
- [ ] `coolify-token`: token de API de Coolify con permiso de despliegue; lo usan los dos pipelines.
- [ ] Una credencial de Bitbucket con permiso de solo lectura para que el Multibranch Pipeline lea cada repositorio.

Los `Jenkinsfile` corren lo mismo que se corre en local: la API `./mvnw -B verify`; el frontend `npm ci`, `npm run typecheck`, `npm run test:coverage`, `npm run test:e2e` y `npm run build:verified`. Solo la rama `develop` despliega, y solo si todo paso.

## 3. Bitbucket

- [ ] Webhook de push de cada repositorio hacia Jenkins (Repository settings > Webhooks), o escaneo periodico del Multibranch si no hay webhook.
- [ ] La llave publica de despliegue que genera Coolify agregada como Access key **de solo lectura** en cada repositorio.

## 4. Coolify

- [ ] Fuente: Private Repository (with deploy key) apuntando a Bitbucket, rama `develop`.
- [ ] Build Pack: Dockerfile en las dos aplicaciones.
- [ ] Puertos: API `8081`, frontend `8080` (nginx corre sin root).
- [ ] Health checks: API `/api/v1/health`, frontend `/healthz`. Las dos imagenes ya traen `HEALTHCHECK`.
- [ ] **Auto Deploy apagado** en las dos aplicaciones: despliega Jenkins por webhook.
- [ ] Dominios con HTTPS; el del frontend coincide con `MTE_CORS_ALLOWED_ORIGINS` y con el inicio de `MTE_ACCESS_LINK_URL`.
- [ ] PostgreSQL 17 sin puerto publico.

## 5. Respaldo antes de desplegar

- [ ] Si el despliegue trae migraciones nuevas: respaldo reciente de la base, comprobado con `pg_restore --list` y probado en una base desechable ([backup-restore.md](backup-restore.md), seccion 6).
- [ ] Respaldos programados y una restauracion probada.

## 6. Reversion

- **Codigo sin migraciones nuevas:** volver a desplegar el commit anterior (Redeploy de ese commit en Coolify, o revertir en `develop` y dejar que Jenkins despliegue).
- **Con migraciones:** Flyway no deshace migraciones. Lo preferible es una migracion correctiva nueva. Restaurar el respaldo encima de la base del servidor requiere detener la API, respaldar antes el estado actual y planearlo aparte ([backup-restore.md](backup-restore.md)).
- **Frontend:** es estatico; basta con redesplegar el commit anterior.

## 7. Comprobacion final

- [ ] `https://<dominio-api>/api/v1/health` responde `200`.
- [ ] `https://<dominio-api>/swagger-ui.html` y `/v3/api-docs` responden `404` (salvo que se active `MTE_API_DOCS_ENABLED` a proposito).
- [ ] `https://<dominio-frontend>/healthz` responde `ok` y recargar `/login` muestra la pagina.
- [ ] Inicio de sesion desde el frontend sin errores de CORS ni de politica de seguridad en la consola del navegador.
- [ ] `MTE_AUTH_BOOTSTRAP_ADMIN_LINK=false` despues del primer administrador.
