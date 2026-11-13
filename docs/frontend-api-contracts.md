# Frontend API Contracts

Estado leido del codigo actual: autenticacion propia de la API y cuatro roles de acceso (noviembre 2026).

Este documento cubre convenciones, autenticacion, dashboard, catalogos, personas y estrategia. Proyectos, vinculos, reportes, presentacion, consistencia y auditoria estan en [frontend-api-contracts-operacion.md](frontend-api-contracts-operacion.md).

Fuentes revisadas:
- Controllers en `src/main/java/co/edu/icesi/pdg/mte/**/**Controller.java`
- DTOs en `src/main/java/co/edu/icesi/pdg/mte/api/dto/*.java`
- Validaciones relevantes en servicios de dashboard, reportes, presentacion, consistencia, auditoria y seguridad.

## Punto Critico Para Dashboard

En el codigo actual **no existe** `GET /api/v1/dashboard`.

El frontend no debe llamar:

```http
GET /api/v1/dashboard
GET /api/v1/dashboard?period=2026-1
```

El `DashboardController` actual solo expone endpoints separados:

```http
GET /api/v1/dashboard/summary
GET /api/v1/dashboard/projects/by-status
GET /api/v1/dashboard/key-results/by-status
GET /api/v1/dashboard/departments/summary
GET /api/v1/dashboard/strategic-bets/summary
GET /api/v1/dashboard/goals/summary
```

Cada uno acepta `period` opcional con formato `YYYY-Q1..Q4` o `YYYY-1..2`. Si no se envia `period` (o llega vacio), el backend usa el periodo academico `ACTIVO` con fecha de inicio mas reciente y lo devuelve en `DashboardSummaryResponse.period`; solo cuando no hay un periodo activo con nombre valido se cuentan todos los periodos y `period` llega en `null`.

## Convenciones Generales

Base URL:

```text
/api/v1
```

Autenticacion:

```http
Authorization: Bearer <token>
```

El token es el `accessToken` que devuelve `POST /api/v1/auth/login` (JWT HS256 firmado por la API, vence en 1 hora). No hay refresh token: al vencer, la API responde 401 y el usuario vuelve a iniciar sesion.

Roles soportados (salen de `professor.access_role`, no del token):

```text
ADMIN, MANAGER, CONTRIBUTOR, VISITOR
```

`VISITOR` solo consulta: cualquier POST, PUT, PATCH o DELETE responde 403. Las excepciones son `/auth/logout` y `/auth/password`, que solo piden sesion.

Respuestas de seguridad:

```text
401 {"message": "Inicia sesión para continuar."}        sin token, token invalido o vencido
401 {"message": "Tu cuenta no tiene acceso al MTE..."}  persona inactiva o fuera del directorio
403 {"message": "Acceso denegado."}                     el rol no permite la accion
```

Formato de periodo:

```text
YYYY-Q1, YYYY-Q2, YYYY-Q3, YYYY-Q4, YYYY-1, YYYY-2
```

Error JSON comun:

```json
{
  "status": 400,
  "message": "La solicitud tiene campos invalidos.",
  "details": ["field: message"],
  "timestamp": "2026-05-26T00:00:00Z",
  "path": "/api/v1/..."
}
```

Enums:

```text
MeasurementUnitType: NUMERICA, PORCENTAJE, BOOLEANA, OTRA
PeriodStatus: ACTIVO, CERRADO, PLANIFICACION
ProjectStatus: BORRADOR, ACTIVO, FINALIZADO, SUSPENDIDO, ARCHIVADO
ProjectType: GRADO, INVESTIGACION, EXTENSION, MACROPROYECTO
ProjectOrigin: LOCAL, SYNCED
ProjectSyncStatus: LOCAL_ONLY, SYNCED, SYNC_FAILED
KeyResultStatus: BORRADOR, ACTIVO, CERRADO
StrategicStatus: ACTIVA, INACTIVA
AuditAction: CREATE, UPDATE, STATUS_CHANGE, PROGRESS_REGISTERED, LINK_CREATED, LINK_REMOVED, EXTERNAL_SYNC, ACCOUNT_LOCKED, ACCESS_LINK_CREATED, PASSWORD_CHANGED, PASSWORD_SET
ConsistencySeverity: ALTA, MEDIA, BAJA
ConsistencyModule: OKRS, INDICADORES, PROYECTOS
ConsistencyFindingType: KR_WITHOUT_ACTIVE_PROJECTS, ACTIVE_PROJECT_WITHOUT_KR, ACTIVE_PROJECT_WITHOUT_RECENT_PROGRESS
ConsistencyEntityType: KEY_RESULT, PROJECT
```

## Auth

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| POST | `/api/v1/auth/login` | - | `LoginRequest` | `LoginResponse` | publico |
| POST | `/api/v1/auth/set-password` | - | `SetPasswordRequest` | 204 | publico |
| POST | `/api/v1/auth/logout` | - | - | 204 | con sesion |
| POST | `/api/v1/auth/password` | - | `ChangePasswordRequest` | 204 | con sesion |
| GET | `/api/v1/auth/me` | - | - | `AuthMeResponse` | con sesion |
| POST | `/api/v1/directory-users/{id}/access-link` | - | - | `AccessLinkResponse` | ADMIN |

Schemas:

```json
LoginRequest: {"email": "persona@icesi.edu.co", "password": "..."}
LoginResponse: {"accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 3600, "expiresAt": 1900000000}
SetPasswordRequest: {"tokenHash": "<token_hash del enlace>", "type": "invite|recovery", "password": "10 a 72 caracteres"}
ChangePasswordRequest: {"currentPassword": "...", "newPassword": "10 a 72 caracteres"}
AccessLinkResponse: {"professorId": 1, "email": "persona@icesi.edu.co", "url": "<MTE_ACCESS_LINK_URL>?token_hash=...&type=invite", "accountCreated": true}
```

Reglas:

- `login`: correo desconocido, persona inactiva, contrasena incorrecta y cuenta bloqueada responden el mismo 401, para no revelar cuales correos existen. Tras 5 fallos seguidos la cuenta se bloquea 15 minutos.
- `set-password`: la pagina `/establecer-contrasena` del frontend recibe `token_hash` y `type` en la URL y los envia con la nueva contrasena. Enlace vencido o usado: 400. Tambien desbloquea la cuenta.
- `password`: valida la contrasena actual (un fallo cuenta para el bloqueo) y exige que la nueva sea distinta.
- `access-link`: crea la cuenta de acceso si no existe (`accountCreated: true`, enlace `invite`) o genera un enlace `recovery`. El enlace es de un solo uso, vence en 72 horas y anula los enlaces anteriores de la persona. El administrador copia la URL y se la entrega a la persona.
- `logout`: la API revoca el token y desde ese momento responde 401 con el; el frontend tambien debe descartarlo.

`AuthMeResponse`:

```json
{
  "user": {
    "externalUserId": 9004,
    "username": "sistemas.mte@icesi.edu.co",
    "email": "sistemas.mte@icesi.edu.co",
    "roles": ["ADMIN"],
    "permissions": [],
    "externalProfessorId": 9004,
    "professorName": "Sistemas MTE",
    "departmentName": "TI Institucional"
  },
  "supportedRoles": ["ADMIN", "MANAGER", "CONTRIBUTOR", "VISITOR"],
  "capabilities": {
    "viewDashboard": true,
    "viewStrategy": true,
    "viewProjects": true,
    "manageCatalogs": true,
    "manageStrategicBets": true,
    "manageGoals": true,
    "manageKeyResults": true,
    "createProjects": true,
    "updateProjects": true,
    "registerProjectProgress": true,
    "changeProjectStatus": true,
    "syncExternalProjects": true,
    "linkProjectsToKeyResults": true,
    "viewReports": true,
    "viewPresentation": true,
    "viewConsistency": true,
    "viewAuditLogs": true
  }
}
```

## Health

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/health` | - | - | `{status, database, service, timestamp}` | publico |

El endpoint obtiene una conexion de la base de datos y la valida con un timeout corto:

- `200`: `{"status":"UP","database":"UP","service":"pdg-mte-api","timestamp":"..."}`.
- `503`: `{"status":"DOWN","database":"DOWN","service":"pdg-mte-api","timestamp":"..."}` cuando la base no responde. La respuesta no incluye mensajes de error ni detalles de conexion.

El `HEALTHCHECK` del `Dockerfile` y Coolify usan esta ruta, asi que una caida de la base de datos marca el contenedor como no saludable.

## Dashboard

Acceso de todos los endpoints dashboard:

```text
ADMIN, MANAGER, CONTRIBUTOR, VISITOR
```

| Metodo | Endpoint | Query | Body | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/dashboard/summary` | `period?` | - | `DashboardSummaryResponse` |
| GET | `/api/v1/dashboard/projects/by-status` | `period?` | - | `CountByStatusResponse[]` |
| GET | `/api/v1/dashboard/key-results/by-status` | `period?` | - | `CountByStatusResponse[]` |
| GET | `/api/v1/dashboard/departments/summary` | `period?` | - | `DepartmentExecutionResponse[]` |
| GET | `/api/v1/dashboard/strategic-bets/summary` | `period?` | - | `StrategicBetExecutionResponse[]` |
| GET | `/api/v1/dashboard/goals/summary` | `period?` | - | `GoalExecutionResponse[]` |

`DashboardSummaryResponse`:

```json
{
  "period": "2026-1",
  "activeProjects": 0,
  "completedProjects": 0,
  "draftProjects": 0,
  "suspendedProjects": 0,
  "archivedProjects": 0,
  "keyResultsInFollowUp": 0,
  "draftKeyResults": 0,
  "activeKeyResults": 0,
  "closedKeyResults": 0
}
```

`keyResultsInFollowUp` cuenta los KR en estado `ACTIVO`. El dashboard solo entrega conteos de datos registrados; no calcula promedios, umbrales, rangos ni estados derivados del avance.

`CountByStatusResponse`:

```json
{"status": "ACTIVO", "count": 3}
```

`/dashboard/key-results/by-status` siempre devuelve los tres estados de `KeyResultStatus` en este orden, incluso con conteo 0:

```json
[
  {"status": "BORRADOR", "count": 0},
  {"status": "ACTIVO", "count": 0},
  {"status": "CERRADO", "count": 0}
]
```

`DepartmentExecutionResponse`:

```json
{
  "departmentId": 1,
  "departmentName": "DCSI",
  "activeProjects": 0,
  "completedProjects": 0,
  "keyResults": 0,
  "draftKeyResults": 0,
  "activeKeyResults": 0,
  "closedKeyResults": 0
}
```

`StrategicBetExecutionResponse`:

```json
{
  "strategicBetId": 1,
  "strategicBetName": "Apuesta",
  "keyResults": 0,
  "draftKeyResults": 0,
  "activeKeyResults": 0,
  "closedKeyResults": 0,
  "completedProjects": 0,
  "inProgressProjects": 0
}
```

`GoalExecutionResponse` usa los mismos campos de apuesta, con `goalId` y `goalName`.

## Catalogos

Lectura sin `@PreAuthorize` especifico; escritura requiere `ADMIN`.

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/measurement-units` | - | - | `MeasurementUnitResponse[]` | autenticado |
| POST | `/api/v1/measurement-units` | - | `MeasurementUnitRequest` | `MeasurementUnitResponse` | ADMIN |
| PUT | `/api/v1/measurement-units/{id}` | - | `MeasurementUnitRequest` | `MeasurementUnitResponse` | ADMIN |
| PATCH | `/api/v1/measurement-units/{id}/active` | - | `MeasurementUnitActiveRequest` | `MeasurementUnitResponse` | ADMIN |
| DELETE | `/api/v1/measurement-units/{id}` | - | - | 204 | ADMIN |
| GET | `/api/v1/academic-periods` | - | - | `AcademicPeriodResponse[]` | autenticado |
| POST | `/api/v1/academic-periods` | - | `AcademicPeriodRequest` | `AcademicPeriodResponse` | ADMIN |
| PUT | `/api/v1/academic-periods/{id}` | - | `AcademicPeriodRequest` | `AcademicPeriodResponse` | ADMIN |
| PATCH | `/api/v1/academic-periods/{id}/status` | - | `AcademicPeriodStatusRequest` | `AcademicPeriodResponse` | ADMIN |
| PATCH | `/api/v1/academic-periods/{id}/active` | - | `AcademicPeriodActiveRequest` | `AcademicPeriodResponse` | ADMIN |
| DELETE | `/api/v1/academic-periods/{id}` | - | - | 204 | ADMIN |
| GET | `/api/v1/departments` | - | - | `DepartmentResponse[]` | autenticado |
| POST | `/api/v1/departments` | - | `DepartmentRequest` | `DepartmentResponse` | ADMIN |
| PUT | `/api/v1/departments/{id}` | - | `DepartmentRequest` | `DepartmentResponse` | ADMIN |
| DELETE | `/api/v1/departments/{id}` | - | - | 204 | ADMIN |
| GET | `/api/v1/schools` | - | - | `SchoolResponse[]` | autenticado |
| POST | `/api/v1/schools` | - | `SchoolRequest` | `SchoolResponse` | ADMIN |
| PUT | `/api/v1/schools/{id}` | - | `SchoolRequest` | `SchoolResponse` | ADMIN |
| DELETE | `/api/v1/schools/{id}` | - | - | 204 | ADMIN |

Schemas:

```json
MeasurementUnitRequest: {"name": "Porcentaje", "type": "PORCENTAJE", "description": "..."}
MeasurementUnitResponse: {"id": 1, "name": "Porcentaje", "type": "PORCENTAJE", "description": "...", "active": true}
MeasurementUnitActiveRequest: {"active": true}
AcademicPeriodRequest: {"name": "2026-1", "startDate": "2026-01-01", "endDate": "2026-06-30", "status": "ACTIVO"}
AcademicPeriodResponse: {"id": 1, "name": "2026-1", "startDate": "2026-01-01", "endDate": "2026-06-30", "status": "ACTIVO"}
AcademicPeriodStatusRequest: {"status": "ACTIVO"}
AcademicPeriodActiveRequest: {"active": true}
SchoolRequest: {"name": "Escuela TDI", "description": "..."}
SchoolResponse: {"id": 1, "name": "Escuela TDI", "description": "..."}
DepartmentRequest: {"name": "DCSI", "description": "...", "schoolId": 1, "externalDepartmentId": 123}
DepartmentResponse: {"id": 1, "name": "DCSI", "description": "...", "schoolId": 1, "schoolName": "Escuela TDI", "externalDepartmentId": 123}
```

`AcademicPeriodRequest.name` debe tener formato `AAAA-1` o `AAAA-2` (por ejemplo `2026-2`) tanto al crear como al actualizar. Otro valor responde `400` con `details` = `["name: El nombre del periodo debe tener formato AAAA-1 o AAAA-2."]`.

## Personas

Lectura sin `@PreAuthorize` especifico; escritura requiere `ADMIN`.

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/roles` | - | - | `RoleResponse[]` | autenticado |
| POST | `/api/v1/roles` | - | `RoleRequest` | `RoleResponse` | ADMIN |
| PUT | `/api/v1/roles/{id}` | - | `RoleRequest` | `RoleResponse` | ADMIN |
| DELETE | `/api/v1/roles/{id}` | - | - | 204 | ADMIN |
| GET | `/api/v1/positions` | - | - | `PositionResponse[]` | autenticado |
| POST | `/api/v1/positions` | - | `PositionRequest` | `PositionResponse` | ADMIN |
| PUT | `/api/v1/positions/{id}` | - | `PositionRequest` | `PositionResponse` | ADMIN |
| DELETE | `/api/v1/positions/{id}` | - | - | 204 | ADMIN |
| GET | `/api/v1/professors` | `departmentId?` | - | `ProfessorResponse[]` | autenticado |
| POST | `/api/v1/professors` | - | `ProfessorRequest` | `ProfessorResponse` | ADMIN |
| PUT | `/api/v1/professors/{id}` | - | `ProfessorRequest` | `ProfessorResponse` | ADMIN |
| DELETE | `/api/v1/professors/{id}` | - | - | 204 | ADMIN |
| GET | `/api/v1/professors/{id}/positions` | - | - | `TeacherPositionResponse[]` | autenticado |
| POST | `/api/v1/professors/{id}/positions` | - | `TeacherPositionRequest` | `TeacherPositionResponse` | ADMIN |
| DELETE | `/api/v1/professors/{id}/positions/{positionId}` | - | - | 204 | ADMIN |

Schemas:

```json
RoleRequest: {"name": "PROFESOR", "description": "..."}
RoleResponse: {"id": 1, "name": "PROFESOR", "description": "..."}
PositionRequest: {"name": "Tutor", "description": "..."}
PositionResponse: {"id": 1, "name": "Tutor", "description": "..."}
ProfessorRequest: {"name": "Nombre", "email": "persona@icesi.edu.co", "departmentId": 1}
ProfessorResponse: {"id": 1, "name": "Nombre", "email": "persona@icesi.edu.co", "departmentId": 1, "departmentName": "DCSI"}
TeacherPositionRequest: {"positionId": 1, "active": true}
TeacherPositionResponse: {"positionId": 1, "positionName": "Tutor", "teacherId": 1, "teacherName": "Nombre", "active": true}
```

## Estrategia

### Mundos

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/worlds` | - | - | `WorldResponse[]` | autenticado |
| POST | `/api/v1/worlds` | - | `WorldRequest` | `WorldResponse` | ADMIN |
| PUT | `/api/v1/worlds/{id}` | - | `WorldRequest` | `WorldResponse` | ADMIN |
| DELETE | `/api/v1/worlds/{id}` | - | - | 204 | ADMIN |

### Apuestas estrategicas

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/strategic-bets` | `period?` | - | `StrategicBetResponse[]` | autenticado |
| POST | `/api/v1/strategic-bets` | - | `StrategicBetRequest` | `StrategicBetResponse` | ADMIN, MANAGER |
| GET | `/api/v1/strategic-bets/{id}` | `period?` | - | `StrategicBetResponse` | autenticado |
| PUT | `/api/v1/strategic-bets/{id}` | - | `StrategicBetRequest` | `StrategicBetResponse` | ADMIN, MANAGER |

### Metas institucionales

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/goals` | `period?` | - | `GoalResponse[]` | autenticado |
| POST | `/api/v1/goals` | - | `GoalRequest` | `GoalResponse` | ADMIN, MANAGER |
| GET | `/api/v1/goals/{id}` | `period?` | - | `GoalResponse` | autenticado |
| PUT | `/api/v1/goals/{id}` | - | `GoalRequest` | `GoalResponse` | ADMIN, MANAGER |
| POST | `/api/v1/goals/{id}/periods/{periodId}` | - | - | `GoalResponse` | ADMIN, MANAGER |
| DELETE | `/api/v1/goals/{id}/periods/{periodId}` | - | - | `GoalResponse` | ADMIN, MANAGER |

### Key results

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/key-results` | `goalId?`, `strategicBetId?`, `periodId?` | - | `KeyResultResponse[]` | autenticado |
| POST | `/api/v1/key-results` | - | `KeyResultRequest` | `KeyResultResponse` (201) | ADMIN, MANAGER |
| GET | `/api/v1/key-results/{id}` | - | - | `KeyResultResponse` | autenticado |
| PUT | `/api/v1/key-results/{id}` | - | `KeyResultRequest` | `KeyResultResponse` | ADMIN, MANAGER |
| PATCH | `/api/v1/key-results/{id}/progress` | - | `KeyResultProgressRequest` | `KeyResultResponse` | ADMIN, MANAGER |
| PATCH | `/api/v1/key-results/{id}/status` | - | `KeyResultStatusRequest` | `KeyResultResponse` | ADMIN, MANAGER |
| GET | `/api/v1/key-results/{id}/progress-records` | - | - | `KeyResultProgressRecordResponse[]` | autenticado |
| PATCH | `/api/v1/key-results/{id}/active` | - | `{ "active": boolean }` | `KeyResultResponse` | ADMIN, MANAGER |

Un KR no se borra: se archiva o restaura con `PATCH /key-results/{id}/active` (ver [key-result-lifecycle-contracts.md](key-result-lifecycle-contracts.md)).

Registro de avance de un KR:

- El valor actual y el porcentaje de avance se registran manualmente y juntos con `PATCH /key-results/{id}/progress`; el backend nunca calcula uno a partir del otro ni a partir de los proyectos vinculados.
- Al crear un KR, `currentValue` arranca en `baseValue` y `progressPercentage` en 0. `PUT /key-results/{id}` no modifica los valores registrados.
- Registrar avance de un proyecto, cambiar su estado o crear, editar o eliminar un vinculo proyecto-KR no altera los valores del KR.
- 400 si falta alguno de los dos campos o si `progressPercentage` esta fuera de 0..100; 409 si el KR esta `CERRADO`. Cada registro queda auditado como `PROGRESS_REGISTERED` con el estado anterior y el nuevo.

### Jerarquia estrategica

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/strategic-hierarchy/tree` | `period?` | - | `StrategicHierarchyNodeResponse[]` | autenticado |

Schemas estrategia:

```json
WorldRequest: {"name": "Mundo", "description": "..."}
WorldResponse: {"id": 1, "name": "Mundo", "description": "..."}

StrategicBetRequest: {"name": "Apuesta", "description": "...", "worldId": 1, "startDate": "2026-01-01", "endDate": "2026-12-31"}
StrategicBetResponse: {
  "id": 1,
  "name": "Apuesta",
  "description": "...",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "status": "ACTIVA",
  "createdAt": "2026-05-26T00:00:00Z",
  "worldId": 1,
  "worldName": "Mundo",
  "executionSummary": {}
}

GoalRequest: {"name": "Meta", "description": "...", "referenceIndicator": "...", "expectedValue": 100, "measurementUnitId": 1, "worldId": 1, "startDate": "2026-01-01", "endDate": "2026-12-31"}
GoalResponse: {
  "id": 1,
  "name": "Meta",
  "description": "...",
  "referenceIndicator": "...",
  "expectedValue": 100,
  "measurementUnitId": 1,
  "measurementUnitName": "Porcentaje",
  "worldId": 1,
  "worldName": "Mundo",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "status": "ACTIVA",
  "createdAt": "2026-05-26T00:00:00Z",
  "periods": [],
  "executionSummary": {}
}

KeyResultRequest: {"name": "KR", "description": "...", "metric": "...", "baseValue": 0, "targetValue": 100, "measurementUnitId": 1, "academicPeriodId": 1, "goalIds": [1], "strategicBetIds": [1]}
KeyResultProgressRequest: {"currentValue": 50, "progressPercentage": 35}
KeyResultStatusRequest: {"status": "ACTIVO"}
KeyResultResponse: {
  "id": 1,
  "name": "KR",
  "description": "...",
  "metric": "...",
  "baseValue": 0,
  "targetValue": 100,
  "currentValue": 50,
  "progressPercentage": 35,
  "measurementUnitId": 1,
  "measurementUnitName": "Porcentaje",
  "academicPeriodId": 1,
  "academicPeriodName": "2026-1",
  "goals": [{"id": 1, "name": "Meta"}],
  "strategicBets": [{"id": 1, "name": "Apuesta"}],
  "status": "ACTIVO",
  "createdAt": "2026-05-26T00:00:00Z"
}

`KeyResultRequest` exige al menos un id en `goalIds` o `strategicBetIds` (400 si ambos van vacíos).
```

`ExecutionSummaryResponse` (un KR cuenta como completo solo cuando su estado es `CERRADO`, sin importar el avance registrado):

```json
{
  "summaryText": "0 KRs completos, 0 en desarrollo...",
  "completedKeyResults": 0,
  "inProgressKeyResults": 0,
  "completedProjects": 0,
  "inProgressProjects": 0,
  "periods": []
}
```
