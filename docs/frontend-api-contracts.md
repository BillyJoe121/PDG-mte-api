# Frontend API Contracts

Estado leido del codigo actual despues del rollback a `0d3efe3` mediante `4dbe868`.

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
GET /api/v1/dashboard/key-results/by-progress
GET /api/v1/dashboard/departments/summary
GET /api/v1/dashboard/strategic-bets/summary
GET /api/v1/dashboard/goals/summary
```

Cada uno acepta `period` opcional con formato `YYYY-Q1..Q4` o `YYYY-1..2`. Si no se envia `period`, el backend usa el periodo activo mas reciente cuando el servicio lo requiere.

## Convenciones Generales

Base URL:

```text
/api/v1
```

Autenticacion:

```http
Authorization: Bearer <token>
```

En modo mock se aceptan tokens como:

```text
mock-token-ad -> ADMIN
mock-token-ha -> DIRECTOR_ESCUELA
mock-token-rs -> JEFE_DPTO
mock-token-lb -> PROFESOR
mock-token-<role> -> rol normalizado desde el token
```

Roles soportados:

```text
ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR
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
ContributionType: DIRECTA, INDIRECTA, SOPORTE
ObjectiveStatus: BORRADOR, ACTIVO, CERRADO
StrategicStatus: ACTIVA, INACTIVA
AuditAction: CREATE, UPDATE, STATUS_CHANGE, PROGRESS_REGISTERED, LINK_CREATED, LINK_REMOVED, EXTERNAL_SYNC
ConsistencySeverity: ALTA, MEDIA, BAJA
ConsistencyModule: OKRS, INDICADORES, PROYECTOS
ConsistencyFindingType: KR_WITHOUT_ACTIVE_PROJECTS, ACTIVE_PROJECT_WITHOUT_KR, ACTIVE_PROJECT_WITHOUT_RECENT_PROGRESS
ConsistencyEntityType: KEY_RESULT, PROJECT
```

## Auth

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/auth/me` | - | - | `AuthMeResponse` | autenticado |

`AuthMeResponse`:

```json
{
  "user": {
    "externalUserId": 9004,
    "username": "ad",
    "email": "sistemas.mte@icesi.edu.co",
    "roles": ["ADMIN"],
    "permissions": ["MTE_READ", "MTE_WRITE"],
    "externalProfessorId": 9004,
    "professorName": "Sistemas MTE",
    "departmentName": "TI Institucional"
  },
  "supportedRoles": ["ADMIN", "DECANO", "DIRECTOR_ESCUELA", "JEFE_DPTO", "PROFESOR"],
  "capabilities": {
    "viewDashboard": true,
    "viewStrategy": true,
    "viewProjects": true,
    "manageCatalogs": true,
    "manageStrategicBets": true,
    "manageGoals": true,
    "manageObjectives": true,
    "manageKeyResults": true,
    "createProjects": true,
    "updateProjects": true,
    "registerProjectProgress": true,
    "changeProjectStatus": true,
    "syncExternalProjects": true,
    "linkProjectsToKeyResults": true,
    "viewReports": true,
    "viewPresentation": true,
    "viewAuditLogs": true
  }
}
```

## Health

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/health` | - | - | `{status, service, timestamp}` | publico |

## Dashboard

Acceso de todos los endpoints dashboard:

```text
ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR
```

| Metodo | Endpoint | Query | Body | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/dashboard/summary` | `period?` | - | `DashboardSummaryResponse` |
| GET | `/api/v1/dashboard/projects/by-status` | `period?` | - | `CountByStatusResponse[]` |
| GET | `/api/v1/dashboard/key-results/by-progress` | `period?` | - | `ProgressBucketResponse[]` |
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
  "objectivesInFollowUp": 0,
  "lowCompletionObjectives": 0,
  "completedObjectives": 0,
  "objectivesAbove50": 0,
  "objectivesBetween0And50": 0,
  "objectivesAtZero": 0,
  "completedKeyResults": 0,
  "inProgressKeyResults": 0,
  "averageKeyResultCoverage": 0.00
}
```

`CountByStatusResponse`:

```json
{"status": "ACTIVO", "count": 3}
```

`ProgressBucketResponse.bucket`:

```text
COMPLETED, ON_TRACK, AT_RISK, LOW
```

`DepartmentExecutionResponse`:

```json
{
  "departmentId": 1,
  "departmentName": "DCSI",
  "activeProjects": 0,
  "completedProjects": 0,
  "objectives": 0,
  "completedObjectives": 0,
  "objectivesAbove50": 0,
  "objectivesBetween0And50": 0,
  "objectivesAtZero": 0,
  "completedKeyResults": 0,
  "inProgressKeyResults": 0
}
```

`StrategicBetExecutionResponse`:

```json
{
  "strategicBetId": 1,
  "strategicBetName": "Apuesta",
  "objectives": 0,
  "completedObjectives": 0,
  "objectivesAbove50": 0,
  "objectivesBetween0And50": 0,
  "objectivesAtZero": 0,
  "keyResults": 0,
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
| POST | `/api/v1/strategic-bets` | - | `StrategicBetRequest` | `StrategicBetResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA |
| GET | `/api/v1/strategic-bets/{id}` | `period?` | - | `StrategicBetResponse` | autenticado |
| PUT | `/api/v1/strategic-bets/{id}` | - | `StrategicBetRequest` | `StrategicBetResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA |

### Metas institucionales

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/goals` | `period?` | - | `GoalResponse[]` | autenticado |
| POST | `/api/v1/goals` | - | `GoalRequest` | `GoalResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA |
| GET | `/api/v1/goals/{id}` | `period?` | - | `GoalResponse` | autenticado |
| PUT | `/api/v1/goals/{id}` | - | `GoalRequest` | `GoalResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA |
| POST | `/api/v1/goals/{id}/periods/{periodId}` | - | - | `GoalResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA |
| DELETE | `/api/v1/goals/{id}/periods/{periodId}` | - | - | `GoalResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA |

### Objetivos

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/objectives` | `strategicBetId?`, `goalId?`, `departmentId?`, `periodId?` | - | `ObjectiveResponse[]` | autenticado |
| GET | `/api/v1/objectives/cards` | `strategicBetId?`, `goalId?`, `departmentId?`, `periodId?` | - | `ObjectiveCardResponse[]` | autenticado |
| POST | `/api/v1/objectives` | - | `ObjectiveRequest` | `ObjectiveResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |
| GET | `/api/v1/objectives/{id}` | - | - | `ObjectiveResponse` | autenticado |
| GET | `/api/v1/objectives/{id}/detail` | - | - | `ObjectiveDetailResponse` | autenticado |
| PATCH | `/api/v1/objectives/{id}` | - | `ObjectiveUpdateRequest` | `ObjectiveResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |
| GET | `/api/v1/objectives/{id}/key-results` | - | - | `KeyResultResponse[]` | autenticado |
| POST | `/api/v1/objectives/{id}/key-results` | - | `KeyResultRequest` | `KeyResultResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |
| GET | `/api/v1/objectives/{id}/coverage-trend` | - | - | `CoverageTrendPointResponse[]` | autenticado |

### Key results

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| PUT | `/api/v1/key-results/{id}` | - | `KeyResultRequest` | `KeyResultResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |
| DELETE | `/api/v1/key-results/{id}` | - | - | 204 | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |

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

KeyResultRequest: {"name": "KR", "description": "...", "metric": "...", "baseValue": 0, "targetValue": 100, "measurementUnitId": 1, "academicPeriodId": 1}
KeyResultResponse: {
  "id": 1,
  "name": "KR",
  "description": "...",
  "metric": "...",
  "baseValue": 0,
  "targetValue": 100,
  "currentValue": 50,
  "progressPercentage": 50,
  "measurementUnitId": 1,
  "measurementUnitName": "Porcentaje",
  "academicPeriodId": 1,
  "academicPeriodName": "2026-1",
  "createdAt": "2026-05-26T00:00:00Z"
}

ObjectiveRequest: {
  "name": "Objetivo",
  "description": "...",
  "departmentId": 1,
  "academicPeriodId": 1,
  "goalId": 1,
  "strategicBetId": 1,
  "estimatedWeight": 25,
  "quarter": 1,
  "createdByProfessorId": 1,
  "keyResults": []
}
ObjectiveUpdateRequest: {"name": "Objetivo", "description": "..."}
```

`ExecutionSummaryResponse`:

```json
{
  "summaryText": "0 objetivos completos, 0 en desarrollo...",
  "completedObjectives": 0,
  "inProgressObjectives": 0,
  "completedKeyResults": 0,
  "inProgressKeyResults": 0,
  "completedProjects": 0,
  "inProgressProjects": 0,
  "periods": []
}
```

## Proyectos

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| POST | `/api/v1/projects` | - | `ProjectRequest` | `ProjectResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR |
| GET | `/api/v1/projects` | `search?`, `status?`, `type?`, `departmentId?`, `period?` | - | `ProjectResponse[]` | autenticado |
| GET | `/api/v1/projects/{id}` | - | - | `ProjectResponse` | autenticado |
| GET | `/api/v1/projects/{id}/detail` | - | - | `ProjectDetailResponse` | autenticado |
| PUT | `/api/v1/projects/{id}` | - | `ProjectUpdateRequest` | `ProjectResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |
| PATCH | `/api/v1/projects/{id}/status` | - | `ProjectStatusRequest` | `ProjectResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA |
| POST | `/api/v1/projects/{id}/progress` | - | `ProjectProgressRequest` | `ProjectProgressResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR |
| GET | `/api/v1/projects/{id}/history` | - | - | `ProjectProgressResponse[]` | autenticado |
| GET | `/api/v1/projects/{id}/impact-chain` | - | - | `ImpactChainResponse` | autenticado |
| GET | `/api/v1/projects/{id}/contribution-chain` | - | - | `ImpactChainResponse` | autenticado |
| GET | `/api/v1/projects/{id}/teachers` | - | - | `ProjectTeacherResponse[]` | autenticado |
| POST | `/api/v1/projects/{id}/teachers` | - | `ProjectTeacherRequest` | `ProjectTeacherResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |
| DELETE | `/api/v1/projects/{id}/teachers/{teacherId}/roles/{roleId}` | - | - | 204 | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |
| POST | `/api/v1/projects/sync/trayectoria` | - | - | `ProjectSyncResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA |

Schemas:

```json
ProjectRequest: {
  "name": "Proyecto",
  "description": "...",
  "type": "INVESTIGACION",
  "departmentId": 1,
  "status": "ACTIVO",
  "startPeriod": "2026-1",
  "endPeriod": "2026-2",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "keyResultId": 1,
  "contributionWeight": 50,
  "linkStatus": "ACTIVE",
  "jiraKey": "ABC-123",
  "tutors": ["Tutor"],
  "keyResultLinks": [{"keyResultId": 1, "contributionWeight": 50, "contributionType": "DIRECTA"}]
}

ProjectUpdateRequest: {
  "name": "Proyecto",
  "description": "...",
  "type": "INVESTIGACION",
  "departmentId": 1,
  "startPeriod": "2026-1",
  "endPeriod": "2026-2",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "actualEndDate": null,
  "keyResultId": 1,
  "contributionWeight": 50,
  "linkStatus": "ACTIVE",
  "jiraKey": "ABC-123",
  "tutors": ["Tutor"]
}

ProjectStatusRequest: {"status": "ACTIVO"}
ProjectProgressRequest: {"progressPercent": 75, "comment": "Avance", "milestones": "..."}
ProjectTeacherRequest: {"teacherId": 1, "roleId": 1, "joinedAt": "2026-01-01", "leftAt": null}
```

`ProjectResponse`:

```json
{
  "id": 1,
  "externalProjectId": null,
  "externalSource": null,
  "name": "Proyecto",
  "description": "...",
  "type": "INVESTIGACION",
  "departmentId": 1,
  "departmentName": "DCSI",
  "status": "ACTIVO",
  "startPeriod": "2026-1",
  "endPeriod": "2026-2",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "actualEndDate": null,
  "globalProgress": 75,
  "keyResultId": 1,
  "keyResultName": "KR",
  "contributionWeight": 50,
  "linkStatus": "ACTIVE",
  "jiraKey": "ABC-123",
  "tutors": [],
  "linkedKeyResults": [],
  "origin": "LOCAL",
  "syncStatus": "LOCAL_ONLY",
  "lastSyncedAt": null,
  "createdAt": "2026-05-26T00:00:00Z",
  "updatedAt": "2026-05-26T00:00:00Z"
}
```

## Vinculos Proyecto-KR

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| POST | `/api/v1/project-key-result-links` | - | `ProjectKeyResultLinkRequest` | `ProjectKeyResultLinkResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |
| GET | `/api/v1/project-key-result-links` | `projectId?`, `keyResultId?` | - | `ProjectKeyResultLinkResponse[]` | autenticado |
| DELETE | `/api/v1/project-key-result-links/{id}` | - | - | 204 | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO |

Schemas:

```json
ProjectKeyResultLinkRequest: {"projectId": 1, "keyResultId": 1, "contributionWeight": 50, "contributionType": "DIRECTA"}
ProjectKeyResultLinkResponse: {
  "id": 1,
  "projectId": 1,
  "projectName": "Proyecto",
  "keyResultId": 1,
  "keyResultDescription": "KR",
  "contributionWeight": 50,
  "contributionType": "DIRECTA",
  "totalWeightForKeyResult": 100,
  "overweightWarning": false,
  "active": true,
  "createdAt": "2026-05-26T00:00:00Z"
}
```

## Reportes

Acceso:

```text
ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO
```

| Metodo | Endpoint | Query | Body | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/reports/general` | `period?`, `departmentId?`, `objectiveId?` | - | `GeneralReportResponse` |
| GET | `/api/v1/reports/departments` | `period?` | - | `DepartmentReportResponse[]` |
| GET | `/api/v1/reports/objectives/ranking` | `period?`, `departmentId?` | - | `ObjectiveRankingResponse[]` |
| GET | `/api/v1/reports/period-comparison` | `basePeriod`, `comparePeriod`, `departmentId?`, `objectiveId?` | - | `PeriodComparisonResponse` |
| GET | `/api/v1/reports` | `period?`, `departmentId?`, `objectiveId?` | - | `ConsolidatedReportResponse` |
| GET | `/api/v1/reports/export.csv` | `period?`, `departmentId?`, `objectiveId?` | - | `text/csv`, attachment `msp-report.csv` |
| GET | `/api/v1/reports/export.pdf` | `period?`, `departmentId?`, `objectiveId?` | - | `application/pdf`, attachment `msp-report.pdf` |

`basePeriod` y `comparePeriod` son obligatorios en `/period-comparison`.

Schemas:

```json
GeneralReportResponse: {
  "period": "2026-1",
  "departmentId": 1,
  "objectiveId": 1,
  "totalProjects": 0,
  "activeProjects": 0,
  "completedProjects": 0,
  "totalObjectives": 0,
  "totalKeyResults": 0,
  "averageObjectiveCoverage": 0.00,
  "averageKeyResultCoverage": 0.00
}

DepartmentReportResponse: {"departmentId": 1, "departmentName": "DCSI", "projects": 0, "objectives": 0, "keyResults": 0, "averageObjectiveCoverage": 0.00}
ObjectiveRankingResponse: {"objectiveId": 1, "objectiveName": "Objetivo", "departmentName": "DCSI", "period": "2026-1", "coveragePercentage": 0.00, "keyResults": 0}
ConsolidatedReportResponse: {"general": {}, "departments": [], "objectiveRanking": []}
```

## Presentacion

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/presentation` | `period?` | - | `PresentationResponse` | ADMIN, DECANO, DIRECTOR_ESCUELA |

`PresentationResponse`:

```json
{
  "period": "2026-1",
  "title": "Modo presentacion MSP",
  "controls": {
    "fullscreenEnabled": true,
    "keyboardNavigationEnabled": true,
    "nextKeys": ["ArrowRight", "Space"],
    "previousKeys": ["ArrowLeft"],
    "exitKeys": ["Escape"]
  },
  "slides": [
    {
      "order": 1,
      "type": "COVER",
      "title": "Seguimiento estrategico MSP",
      "subtitle": "Periodo 2026-1",
      "content": {}
    }
  ]
}
```

Tipos de slide generados por el servicio:

```text
COVER, STRATEGIC_BET, CLOSING
```

## Consistencia

Acceso:

```text
ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO
```

| Metodo | Endpoint | Query | Body | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/consistency/check` | `severity?`, `module?`, `staleDays?` | - | `ConsistencyCheckResponse` |
| GET | `/api/v1/consistency/check/export.csv` | `severity?`, `module?`, `staleDays?` | - | `text/csv`, attachment `msp-consistency-findings.csv` |

Reglas:

```text
severity: ALTA, MEDIA, BAJA
module: OKRS, INDICADORES, PROYECTOS
staleDays: entero >= 1; default 15
```

`ConsistencyCheckResponse`:

```json
{
  "summary": {"total": 0, "high": 0, "medium": 0, "low": 0},
  "findings": [
    {
      "id": "KR_WITHOUT_ACTIVE_PROJECTS-KR-1",
      "type": "KR_WITHOUT_ACTIVE_PROJECTS",
      "severity": "ALTA",
      "module": "INDICADORES",
      "entityType": "KEY_RESULT",
      "entityId": 1,
      "entityCode": "KR-1",
      "entityName": "KR",
      "description": "...",
      "recommendedAction": "...",
      "actionLabel": "Abrir KR",
      "actionUrl": "/objectives/1/key-results/1",
      "detectedAt": "2026-05-26T00:00:00Z"
    }
  ]
}
```

## Auditoria

Acceso:

```text
ADMIN
```

| Metodo | Endpoint | Query | Body | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/audit-logs` | `action?`, `entityType?`, `entityId?`, `from?`, `to?` | - | `AuditLogResponse[]` |
| GET | `/api/v1/audit-logs/summary` | `action?`, `entityType?`, `entityId?`, `from?`, `to?` | - | `AuditSummaryResponse` |

`from` y `to` usan `Instant` ISO date-time. Si `from > to`, responde 400.

`AuditLogResponse`:

```json
{
  "id": 1,
  "action": "CREATE",
  "entityType": "PROJECT",
  "entityId": "1",
  "summary": "...",
  "actorExternalUserId": 9004,
  "actorUsername": "ad",
  "actorRoles": "ADMIN",
  "beforeSnapshot": null,
  "afterSnapshot": "{}",
  "createdAt": "2026-05-26T00:00:00Z"
}
```

`AuditSummaryResponse`:

```json
{
  "totalEvents": 1,
  "byAction": [{"key": "CREATE", "count": 1}],
  "byEntityType": [{"key": "PROJECT", "count": 1}]
}
```

## Acciones Requeridas En Frontend Por El Rollback

1. Reemplazar `dashboardApi.consolidated(period)` si llama `/dashboard`.
2. Volver a la carga separada de dashboard:
   - `/dashboard/summary`
   - `/dashboard/projects/by-status`
   - `/dashboard/key-results/by-progress`
   - `/dashboard/departments/summary`
   - `/dashboard/strategic-bets/summary`
   - `/dashboard/goals/summary`
3. Mantener `period` como query param opcional en cada llamada.
4. No depender de `periods` dentro de una respuesta consolidada de dashboard: ese campo no existe en el backend actual.

