# Contrato Backend-Frontend MTE

## Base URL y proxy

El backend expone todo bajo `/api/v1`.

En desarrollo con Vite, el navegador puede mostrar `http://localhost:5173/api/v1/...`; eso es correcto si Vite proxy reenvia a Render.

Configuracion recomendada:

```ts
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      "/api": {
        target: "https://pdg-mte-api-staging.onrender.com",
        changeOrigin: true,
        secure: true,
      },
    },
  },
});
```

Regla importante: si el frontend llama `/api/v1/dashboard`, el `target` del proxy debe ser solo el host (`https://pdg-mte-api-staging.onrender.com`), no `https://.../api/v1`.

Para un build desplegado sin proxy, usa:

```ts
const API_BASE_URL = "https://pdg-mte-api-staging.onrender.com/api/v1";
```

Para dev con proxy:

```ts
const API_BASE_URL = "/api/v1";
```

Todas las rutas protegidas requieren:

```http
Authorization: Bearer mock-token-ha
Content-Type: application/json
```

En staging segun `render.yaml`, el backend esta en `MTE_AUTH_MODE=mock`. Tokens mock utiles:

- `mock-token-ha`: DIRECTOR_ESCUELA
- `mock-token-rs`: JEFE_DPTO
- `mock-token-lb`: PROFESOR
- `mock-token-ad`: ADMIN

Si una respuesta viene realmente de Spring, debe traer `Server-Timing` y `X-SQL-Query-Count`. Si faltan en errores `502/503`, normalmente el error viene del proxy o de Render antes de entrar a la app.

## Wrapper recomendado

```ts
type ApiOptions = RequestInit & { params?: Record<string, string | number | boolean | null | undefined> };

const API_BASE_URL = import.meta.env.DEV
  ? "/api/v1"
  : "https://pdg-mte-api-staging.onrender.com/api/v1";

function buildUrl(path: string, params?: ApiOptions["params"]) {
  const url = new URL(`${API_BASE_URL}${path}`, window.location.origin);
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  });
  return url.pathname + url.search;
}

export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const token = localStorage.getItem("mte.token") ?? "mock-token-ha";
  const res = await fetch(buildUrl(path, options.params), {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });

  if (res.status === 204) return undefined as T;
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  return res.json() as Promise<T>;
}
```

Para CSV/PDF usa `blob()` o `text()`, no `json()`.

## Formatos y enums

- Fechas: `YYYY-MM-DD`.
- Instants: ISO date-time (`2026-05-26T03:09:42Z`).
- Periodos: `YYYY-1`, `YYYY-2`, `YYYY-Q1`, `YYYY-Q2`, `YYYY-Q3`, `YYYY-Q4`.
- `ProjectStatus`: `BORRADOR`, `ACTIVO`, `FINALIZADO`, `SUSPENDIDO`, `ARCHIVADO`.
- `ProjectType`: `GRADO`, `INVESTIGACION`, `EXTENSION`, `MACROPROYECTO`.
- `ContributionType`: `DIRECTA`, `INDIRECTA`, `SOPORTE`.
- `MeasurementUnitType`: `NUMERICA`, `PORCENTAJE`, `BOOLEANA`, `OTRA`.
- `PeriodStatus`: `ACTIVO`, `CERRADO`, `PLANIFICACION`.
- `StrategicStatus`: `ACTIVA`, `INACTIVA`.
- `ObjectiveStatus`: `BORRADOR`, `ACTIVO`, `CERRADO`.
- `AuditAction`: `CREATE`, `UPDATE`, `STATUS_CHANGE`, `PROGRESS_REGISTERED`, `LINK_CREATED`, `LINK_REMOVED`, `EXTERNAL_SYNC`.

## Salud y autenticacion

| Metodo | Ruta | Query/body | Respuesta |
| --- | --- | --- | --- |
| GET | `/health` | publico | `{ service, timestamp, status }` |
| GET | `/auth/me` | auth | `{ user, supportedRoles, capabilities }` |

## Catalogos

| Metodo | Ruta | Query/body | Roles escritura | Respuesta |
| --- | --- | --- | --- | --- |
| GET | `/catalogs/bootstrap` | - | - | `{ academicPeriods, departments, measurementUnits, schools }` |
| GET | `/measurement-units` | - | - | `MeasurementUnitResponse[]` |
| POST | `/measurement-units` | `{ name, type, description? }` | ADMIN | `MeasurementUnitResponse` |
| PUT | `/measurement-units/{id}` | `{ name, type, description? }` | ADMIN | `MeasurementUnitResponse` |
| PATCH | `/measurement-units/{id}/active` | `{ active }` | ADMIN | `MeasurementUnitResponse` |
| DELETE | `/measurement-units/{id}` | - | ADMIN | 204 |
| GET | `/academic-periods` | - | - | `AcademicPeriodResponse[]` |
| POST | `/academic-periods` | `{ name, startDate, endDate, status }` | ADMIN | `AcademicPeriodResponse` |
| PUT | `/academic-periods/{id}` | `{ name, startDate, endDate, status }` | ADMIN | `AcademicPeriodResponse` |
| PATCH | `/academic-periods/{id}/status` | `{ status }` | ADMIN | `AcademicPeriodResponse` |
| PATCH | `/academic-periods/{id}/active` | `{ active }` | ADMIN | `AcademicPeriodResponse` |
| DELETE | `/academic-periods/{id}` | - | ADMIN | 204 |
| GET | `/departments` | - | - | `DepartmentResponse[]` |
| POST | `/departments` | `{ name, description?, schoolId, externalDepartmentId? }` | ADMIN | `DepartmentResponse` |
| PUT | `/departments/{id}` | `{ name, description?, schoolId, externalDepartmentId? }` | ADMIN | `DepartmentResponse` |
| DELETE | `/departments/{id}` | - | ADMIN | 204 |
| GET | `/schools` | - | - | `SchoolResponse[]` |
| POST | `/schools` | `{ name, description? }` | ADMIN | `SchoolResponse` |
| PUT | `/schools/{id}` | `{ name, description? }` | ADMIN | `SchoolResponse` |
| DELETE | `/schools/{id}` | - | ADMIN | 204 |

## Personas, roles y cargos

| Metodo | Ruta | Query/body | Roles escritura | Respuesta |
| --- | --- | --- | --- | --- |
| GET | `/roles` | - | - | `RoleResponse[]` |
| POST | `/roles` | `{ name, description? }` | ADMIN | `RoleResponse` |
| PUT | `/roles/{id}` | `{ name, description? }` | ADMIN | `RoleResponse` |
| DELETE | `/roles/{id}` | - | ADMIN | 204 |
| GET | `/positions` | - | - | `PositionResponse[]` |
| POST | `/positions` | `{ name, description? }` | ADMIN | `PositionResponse` |
| PUT | `/positions/{id}` | `{ name, description? }` | ADMIN | `PositionResponse` |
| DELETE | `/positions/{id}` | - | ADMIN | 204 |
| GET | `/professors` | `departmentId?`, `page?`, `size?` | - | lista o `PageResponse<ProfessorResponse>` |
| POST | `/professors` | `{ name, email, departmentId }` | ADMIN | `ProfessorResponse` |
| PUT | `/professors/{id}` | `{ name, email, departmentId }` | ADMIN | `ProfessorResponse` |
| DELETE | `/professors/{id}` | - | ADMIN | 204 |
| GET | `/professors/{id}/positions` | - | - | `TeacherPositionResponse[]` |
| POST | `/professors/{id}/positions` | `{ positionId, active? }` | ADMIN | `TeacherPositionResponse` |
| DELETE | `/professors/{id}/positions/{positionId}` | - | ADMIN | 204 |

## Estrategia

| Metodo | Ruta | Query/body | Roles escritura | Respuesta |
| --- | --- | --- | --- | --- |
| GET | `/worlds` | - | - | `WorldResponse[]` |
| POST | `/worlds` | `{ name, description? }` | ADMIN | `WorldResponse` |
| PUT | `/worlds/{id}` | `{ name, description? }` | ADMIN | `WorldResponse` |
| DELETE | `/worlds/{id}` | - | ADMIN | 204 |
| GET | `/strategic-bets` | - | - | `StrategicBetResponse[]` |
| POST | `/strategic-bets` | `{ name, description, worldId?, startDate?, endDate? }` | ADMIN, DECANO, DIRECTOR_ESCUELA | `StrategicBetResponse` |
| GET | `/strategic-bets/{id}` | - | - | `StrategicBetResponse` |
| PUT | `/strategic-bets/{id}` | `{ name, description, worldId?, startDate?, endDate? }` | ADMIN, DECANO, DIRECTOR_ESCUELA | `StrategicBetResponse` |
| GET | `/goals` | - | - | `GoalResponse[]` |
| POST | `/goals` | `{ name, description, referenceIndicator?, expectedValue, measurementUnitId, worldId?, startDate?, endDate? }` | ADMIN, DECANO, DIRECTOR_ESCUELA | `GoalResponse` |
| GET | `/goals/{id}` | - | - | `GoalResponse` |
| PUT | `/goals/{id}` | igual a POST | ADMIN, DECANO, DIRECTOR_ESCUELA | `GoalResponse` |
| POST | `/goals/{id}/periods/{periodId}` | - | ADMIN, DECANO, DIRECTOR_ESCUELA | `GoalResponse` |
| DELETE | `/goals/{id}/periods/{periodId}` | - | ADMIN, DECANO, DIRECTOR_ESCUELA | `GoalResponse` |
| GET | `/strategic-hierarchy/tree` | `period?` | - | `StrategicHierarchyNodeResponse[]` |

### Objetivos y KR

| Metodo | Ruta | Query/body | Roles escritura | Respuesta |
| --- | --- | --- | --- | --- |
| GET | `/objectives` | `strategicBetId?`, `goalId?`, `departmentId?`, `periodId?` | - | `ObjectiveResponse[]` |
| GET | `/objectives/cards` | mismos filtros | - | `ObjectiveCardResponse[]` |
| GET | `/objectives/screen-data` | mismos filtros | - | `{ objectiveCards, strategicBets, goals, academicPeriods, measurementUnits, departments }` |
| POST | `/objectives` | ver `ObjectiveRequest` abajo | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | `ObjectiveResponse` |
| GET | `/objectives/{id}` | - | - | `ObjectiveResponse` |
| GET | `/objectives/{id}/detail` | - | - | `{ objective, coverageTrend }` |
| PATCH | `/objectives/{id}` | `{ name, description }` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | `ObjectiveResponse` |
| GET | `/objectives/{id}/key-results` | - | - | `KeyResultResponse[]` |
| POST | `/objectives/{id}/key-results` | `{ name, description, metric, baseValue, targetValue, measurementUnitId, academicPeriodId? }` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | `KeyResultResponse` |
| GET | `/objectives/{id}/coverage-trend` | - | - | `{ timestamp, coveragePercentage, source }[]` |
| PUT | `/key-results/{id}` | `KeyResultRequest` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | `KeyResultResponse` |
| DELETE | `/key-results/{id}` | - | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | 204 |

`ObjectiveRequest`:

```json
{
  "name": "Objetivo",
  "description": "Descripcion",
  "departmentId": 1,
  "academicPeriodId": 1,
  "goalId": 1,
  "strategicBetId": 1,
  "estimatedWeight": 20,
  "quarter": 1,
  "createdByProfessorId": 1,
  "keyResults": [
    {
      "name": "KR",
      "description": "Descripcion",
      "metric": "Numero",
      "baseValue": 0,
      "targetValue": 100,
      "measurementUnitId": 1,
      "academicPeriodId": 1
    }
  ]
}
```

## Proyectos e impactos

| Metodo | Ruta | Query/body | Roles escritura | Respuesta |
| --- | --- | --- | --- | --- |
| GET | `/projects` | `search?`, `status?`, `type?`, `departmentId?`, `period?`, `page?`, `size?` | - | lista o `PageResponse<ProjectResponse>` |
| GET | `/projects/screen-data` | `search?`, `status?`, `type?`, `departmentId?`, `period?` | - | `{ projects, departments, academicPeriods, objectiveCards }` |
| POST | `/projects` | `ProjectRequest` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR | `ProjectResponse` |
| GET | `/projects/{id}` | - | - | `ProjectResponse` |
| GET | `/projects/{id}/detail` | - | - | `{ project, kpis, history, linkedKeyResults, contributionChain }` |
| PUT | `/projects/{id}` | `ProjectUpdateRequest` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | `ProjectResponse` |
| PATCH | `/projects/{id}/status` | `{ status }` | ADMIN, DECANO, DIRECTOR_ESCUELA | `ProjectResponse` |
| POST | `/projects/{id}/progress` | `{ progressPercent, comment, milestones? }` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR | `ProjectProgressMutationResponse` |
| GET | `/projects/{id}/history` | - | - | `ProjectProgressResponse[]` |
| GET | `/projects/{id}/impact-chain` | - | - | `ImpactChainResponse` |
| GET | `/projects/{id}/contribution-chain` | - | - | `ImpactChainResponse` |
| GET | `/projects/{id}/teachers` | - | - | `ProjectTeacherResponse[]` |
| POST | `/projects/{id}/teachers` | `{ teacherId, roleId, joinedAt, leftAt? }` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | `ProjectTeacherResponse` |
| DELETE | `/projects/{id}/teachers/{teacherId}/roles/{roleId}` | - | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | 204 |
| POST | `/projects/sync/trayectoria` | - | ADMIN, DECANO, DIRECTOR_ESCUELA | `{ imported, updated, failed, warnings }` |

`ProjectRequest` minimo:

```json
{
  "name": "Proyecto",
  "description": "Descripcion",
  "type": "GRADO",
  "departmentId": 1,
  "status": "ACTIVO",
  "startPeriod": "2026-1",
  "endPeriod": "2026-2",
  "startDate": "2026-01-15",
  "endDate": "2026-11-30",
  "tutors": ["Ana Torres"],
  "keyResultLinks": [
    { "keyResultId": 1, "contributionWeight": 40, "contributionType": "DIRECTA" }
  ]
}
```

### Links proyecto-KR

| Metodo | Ruta | Query/body | Roles escritura | Respuesta |
| --- | --- | --- | --- | --- |
| GET | `/project-key-result-links` | `projectId?`, `keyResultId?` | - | `ProjectKeyResultLinkResponse[]` |
| POST | `/project-key-result-links` | `{ projectId, keyResultId, contributionWeight, contributionType }` | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | `ProjectKeyResultLinkResponse` |
| DELETE | `/project-key-result-links/{id}` | - | ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO | 204 |

## Dashboard

Todos aceptan `period?`.

| Metodo | Ruta | Respuesta |
| --- | --- | --- |
| GET | `/dashboard` | `DashboardScreenResponse` con periodos, resumen y graficas |
| GET | `/dashboard/summary` | `DashboardSummaryResponse` |
| GET | `/dashboard/projects/by-status` | `{ status, count }[]` |
| GET | `/dashboard/key-results/by-progress` | `{ bucket, count }[]` |
| GET | `/dashboard/departments/summary` | `DepartmentExecutionResponse[]` |
| GET | `/dashboard/strategic-bets/summary` | `StrategicBetExecutionResponse[]` |
| GET | `/dashboard/goals/summary` | `GoalExecutionResponse[]` |

Roles: ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR.

## Consistencia

Roles: ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO.

| Metodo | Ruta | Query | Respuesta |
| --- | --- | --- | --- |
| GET | `/consistency/check` | `severity?`, `module?`, `staleDays?` | `{ summary, findings }` |
| GET | `/consistency/check/export.csv` | mismos filtros | CSV |

Valores:

- `severity`: `ALTA`, `MEDIA`, `BAJA`
- `module`: `OKRS`, `INDICADORES`, `PROYECTOS`

## Reportes

Roles: ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO.

| Metodo | Ruta | Query | Respuesta |
| --- | --- | --- | --- |
| GET | `/reports` | `period?`, `departmentId?`, `objectiveId?` | `ConsolidatedReportResponse` |
| GET | `/reports/general` | mismos filtros | `GeneralReportResponse` |
| GET | `/reports/departments` | `period?` | `DepartmentReportResponse[]` |
| GET | `/reports/objectives/ranking` | `period?`, `departmentId?` | `ObjectiveRankingResponse[]` |
| GET | `/reports/period-comparison` | `basePeriod`, `comparePeriod`, `departmentId?`, `objectiveId?` | `PeriodComparisonResponse` |
| GET | `/reports/export.csv` | `period?`, `departmentId?`, `objectiveId?` | CSV attachment |
| GET | `/reports/export.pdf` | `period?`, `departmentId?`, `objectiveId?` | PDF attachment |

## Presentacion

Roles: ADMIN, DECANO, DIRECTOR_ESCUELA.

| Metodo | Ruta | Query | Respuesta |
| --- | --- | --- | --- |
| GET | `/presentation` | `period?` | `{ period, title, controls, slides }` |

## Auditoria

Solo ADMIN.

| Metodo | Ruta | Query | Respuesta |
| --- | --- | --- | --- |
| GET | `/audit-logs` | `action?`, `entityType?`, `entityId?`, `from?`, `to?`, `page?`, `size?` | lista o `PageResponse<AuditLogResponse>` |
| GET | `/audit-logs/summary` | `action?`, `entityType?`, `entityId?`, `from?`, `to?` | `{ totalEvents, byAction, byEntityType }` |

`from` y `to` son ISO date-time.

## Mapa sugerido en frontend

Mantener un cliente por modulo ayuda a evitar contratos cruzados:

- `catalogsApi.ts`: `/catalogs/bootstrap`, `/measurement-units`, `/academic-periods`, `/departments`, `/schools`
- `peopleApi.ts`: `/roles`, `/positions`, `/professors`
- `strategicApi.ts`: `/worlds`, `/strategic-bets`, `/goals`, `/objectives`, `/key-results`, `/strategic-hierarchy/tree`
- `projectsApi.ts`: `/projects`, `/project-key-result-links`
- `dashboardApi.ts`: `/dashboard`
- `consistencyApi.ts`: `/consistency/check`
- `reportsApi.ts`: `/reports`
- `presentationApi.ts`: `/presentation`
- `auditApi.ts`: `/audit-logs`

Checklist rapido para depurar contratos:

1. En dev, `Request URL` puede ser `localhost:5173`; revisar el `target` del proxy.
2. Si `Server-Timing` falta, la respuesta probablemente no salio de Spring.
3. No duplicar `/api/v1` entre `baseURL` y `proxy.target`.
4. Enviar enums exactamente en mayusculas como los define Java.
5. Para endpoints paginables, si mandas `page` y `size`, cambia el contrato de respuesta a `PageResponse<T>`.
6. Para DELETE 204, no llamar `response.json()`.
7. Para CSV/PDF, usar `response.text()` o `response.blob()`.
