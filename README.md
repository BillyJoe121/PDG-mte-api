# PDG-mte-api

REST API del Modulo de Trazabilidad Estrategica (MTE) de la Escuela TDI.

El servicio es dueno del dominio estrategico y operativo: Apuestas, Metas, Objetivos, Key Results, Proyectos, avances, periodos, unidades de medida y vinculaciones futuras con proyectos externos. Esta preparado para integrarse con el backend de Trayectoria Docente sin depender de que ese equipo haya terminado sus endpoints.

## Ejecutar

```powershell
.\mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8081`.

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- H2 console: `http://localhost:8081/h2-console`
- Health: `GET http://localhost:8081/api/v1/health`

## Autenticacion

Por defecto corre en modo demo:

```text
MTE_AUTH_MODE=mock
```

Para validar el token Bearer contra Trayectoria Docente:

```text
MTE_AUTH_MODE=external
TRAYECTORIA_BASE_URL=http://localhost:8080/api/v1
```

En modo `external`, cada request protegido debe incluir:

```http
Authorization: Bearer <token>
```

El MTE llama a `GET /auth/me` del backend externo para obtener roles, permisos y contexto de usuario.

## Proyectos MSP

El MSP gestiona sus propios proyectos. Un proyecto puede ser:

- `LOCAL`: creado directamente en el MSP.
- `SYNCED`: importado desde Trayectoria Docente y enriquecido localmente.

Endpoints principales:

```http
POST  /api/v1/projects
GET   /api/v1/projects?search=&status=&type=&departmentId=&period=
GET   /api/v1/projects/{id}
PUT   /api/v1/projects/{id}
PATCH /api/v1/projects/{id}/status
POST  /api/v1/projects/{id}/progress
GET   /api/v1/projects/{id}/history
POST  /api/v1/projects/sync/trayectoria
```

La sincronizacion de proyectos corre en modo mock por defecto:

```text
MTE_TRAYECTORIA_PROJECTS_MODE=mock
```

Para intentar consumir proyectos desde la plataforma externa:

```text
MTE_TRAYECTORIA_PROJECTS_MODE=external
TRAYECTORIA_BASE_URL=http://localhost:8080/api/v1
TRAYECTORIA_PROJECTS_PATH=/proyectos
```

El modo externo requiere enviar `Authorization: Bearer <token>` al endpoint de sincronizacion.

## Historias cubiertas en esta base

- HU 1.1: registrar y consultar Apuestas Estrategicas.
- HU 1.2: registrar Metas Institucionales y asociar/desasociar periodos.
- HU 1.3: registrar Objetivos con Meta, Apuesta, Departamento, Periodo y al menos un Key Result.
- HU 1.4: listar Objetivos como tarjetas con filtros y alerta de bajo cumplimiento.
- HU 1.5: listar, crear, editar, actualizar valor actual y eliminar Key Results si no tienen proyectos vinculados.
- HU 1.6: editar informacion basica de Objetivos.
- HU 1.7: visualizar jerarquia estrategica como arbol.
- HU 2.1: registrar proyectos propios en MSP.
- HU 2.2: listar y filtrar proyectos.
- HU 2.3: consultar ficha base de proyecto e historial.
- HU 2.4: registrar avance de proyecto.
- HU 2.5: gestionar estado del proyecto.
- Catalogos minimos: periodos, unidades de medida y departamentos semilla.
