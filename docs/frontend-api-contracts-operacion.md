# Frontend API Contracts: Operacion

Continuacion de [frontend-api-contracts.md](frontend-api-contracts.md), donde estan las convenciones generales, la autenticacion y los roles.

## Proyectos

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| POST | `/api/v1/projects` | - | `ProjectRequest` | `ProjectResponse` | ADMIN, MANAGER |
| GET | `/api/v1/projects` | `search?`, `status?`, `type?`, `departmentId?`, `period?` | - | `ProjectResponse[]` | autenticado |
| GET | `/api/v1/projects/{id}` | - | - | `ProjectResponse` | autenticado |
| GET | `/api/v1/projects/{id}/detail` | - | - | `ProjectDetailResponse` | autenticado |
| PUT | `/api/v1/projects/{id}` | - | `ProjectUpdateRequest` | `ProjectResponse` | ADMIN, MANAGER |
| PATCH | `/api/v1/projects/{id}/status` | - | `ProjectStatusRequest` | `ProjectResponse` | ADMIN, MANAGER |
| POST | `/api/v1/projects/{id}/progress` | - | `ProjectProgressRequest` | `ProjectProgressResponse` | ADMIN, MANAGER, CONTRIBUTOR |
| GET | `/api/v1/projects/{id}/history` | - | - | `ProjectProgressResponse[]` | autenticado |
| GET | `/api/v1/projects/{id}/impact-chain` | - | - | `ImpactChainResponse` | autenticado |
| GET | `/api/v1/projects/{id}/contribution-chain` | - | - | `ImpactChainResponse` | autenticado |
| GET | `/api/v1/projects/{id}/teachers` | - | - | `ProjectTeacherResponse[]` | autenticado |
| POST | `/api/v1/projects/{id}/teachers` | - | `ProjectTeacherRequest` | `ProjectTeacherResponse` | ADMIN, MANAGER |
| DELETE | `/api/v1/projects/{id}/teachers/{teacherId}/roles/{roleId}` | - | - | 204 | ADMIN, MANAGER |
| POST | `/api/v1/projects/sync/trayectoria` | - | - | `ProjectSyncResponse` | ADMIN |

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

`ImpactChainResponse` (`/impact-chain` y `/contribution-chain`) muestra solo el peso de contribucion declarado en cada vinculo:

```json
{
  "projectId": 1,
  "projectName": "Proyecto",
  "globalProgress": 75,
  "status": "ACTIVO",
  "impacts": [{
    "linkId": 1,
    "keyResultId": 1,
    "keyResultDescription": "KR",
    "goalNames": ["Meta"],
    "strategicBetNames": ["Apuesta"],
    "contributionWeight": 50,
    "contributionType": "DIRECTA",
    "projectCompleted": false,
    "period": "2026-1"
  }]
}
```

## Vinculos Proyecto-KR

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| POST | `/api/v1/project-key-result-links` | - | `ProjectKeyResultLinkRequest` | `ProjectKeyResultLinkResponse` | ADMIN, MANAGER |
| GET | `/api/v1/project-key-result-links` | `projectId?`, `keyResultId?` | - | `ProjectKeyResultLinkResponse[]` | autenticado |
| DELETE | `/api/v1/project-key-result-links/{id}` | - | - | 204 | ADMIN, MANAGER |

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
ADMIN, MANAGER
```

| Metodo | Endpoint | Query | Body | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/reports/general` | `period?`, `departmentId?` | - | `GeneralReportResponse` |
| GET | `/api/v1/reports/departments` | `period?` | - | `DepartmentReportResponse[]` |
| GET | `/api/v1/reports/key-results/ranking` | `period?`, `departmentId?` | - | `KeyResultRankingResponse[]` |
| GET | `/api/v1/reports/period-comparison` | `basePeriod`, `comparePeriod`, `departmentId?` | - | `PeriodComparisonResponse` |
| GET | `/api/v1/reports` | `period?`, `departmentId?` | - | `ConsolidatedReportResponse` |
| GET | `/api/v1/reports/export.csv` | `period?`, `departmentId?` | - | `text/csv`, attachment `msp-report.csv` |
| GET | `/api/v1/reports/export.pdf` | `period?`, `departmentId?` | - | `application/pdf`, attachment `msp-report.pdf` |

`basePeriod` y `comparePeriod` son obligatorios en `/period-comparison`.

Schemas:

```json
GeneralReportResponse: {
  "period": "2026-1",
  "departmentId": 1,
  "totalProjects": 0,
  "activeProjects": 0,
  "completedProjects": 0,
  "totalKeyResults": 0,
  "activeKeyResults": 0,
  "closedKeyResults": 0
}

DepartmentReportResponse: {"departmentId": 1, "departmentName": "DCSI", "projects": 0, "keyResults": 0, "closedKeyResults": 0}
KeyResultRankingResponse: {"keyResultId": 1, "keyResultName": "KR", "goalNames": ["Meta"], "strategicBetNames": ["Apuesta"], "period": "2026-1", "progressPercentage": 35, "linkedProjects": 0}
PeriodComparisonResponse: {"basePeriod": "2026-1", "comparePeriod": "2026-2", "base": {}, "compare": {}}
ConsolidatedReportResponse: {"general": {}, "departments": [], "keyResultRanking": []}
```

- `KeyResultRankingResponse.progressPercentage` es el avance registrado manualmente (puede ser `null`); el ranking se ordena por ese valor de mayor a menor, con los `null` al final.
- `PeriodComparisonResponse` entrega los dos reportes generales lado a lado, sin diferencias calculadas.
- El CSV exporta conteos (`general,krsActivos`, `general,krsCerrados`, `departamentoProyectos`, `departamentoKRs`, `departamentoKRsCerrados`) y el avance registrado de cada KR en filas `krAvanceRegistrado`; no incluye promedios.

## Presentacion

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| GET | `/api/v1/presentation` | `period?` | - | `PresentationResponse` | ADMIN, MANAGER, CONTRIBUTOR, VISITOR |

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
ADMIN, MANAGER
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
      "actionUrl": "/krs?keyResultId=1",
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
   - `/dashboard/key-results/by-status`
   - `/dashboard/departments/summary`
   - `/dashboard/strategic-bets/summary`
   - `/dashboard/goals/summary`
3. Mantener `period` como query param opcional en cada llamada.
4. No depender de `periods` dentro de una respuesta consolidada de dashboard: ese campo no existe en el backend actual.

