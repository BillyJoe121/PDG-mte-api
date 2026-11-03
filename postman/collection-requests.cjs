// Settings for the requests Portman generates from the OpenAPI contract, grouped by tag.
// Each folder creates its own records first and edits or deletes only those, so the seed
// data keeps its shape and the collection can run again on the same database.
// Keys: op "METHOD path", vars (path values), query (enabled values; other params are
// disabled), body, status (default by method), save [[variable, field]], shape, tests.

const PERIOD = "2026-1";
const PLANNER_KR = "https://tasks.office.com/icesi.edu.co/Home/PlanViews/postman-kr";
const PLANNER_PROJECT = "https://planner.cloud.microsoft/webui/plan/postman-project";

const krBody = suffix => ({
  name: `KR Postman {{runId}}${suffix}`,
  description: "Creado por la coleccion Postman",
  metric: "Porcentaje de avance",
  baseValue: 0,
  targetValue: 100,
  measurementUnitId: 1,
  academicPeriodId: 1,
  goalIds: [1],
  strategicBetIds: [1],
  plannerUrl: PLANNER_KR
});

const projectBody = {
  name: "Proyecto Postman {{runId}}",
  description: "Creado por la coleccion Postman",
  type: "EXTENSION",
  departmentId: 1,
  startPeriod: "2026-1",
  endPeriod: "2026-2",
  startDate: "2026-02-01",
  endDate: "2026-11-30",
  keyResultId: 2,
  contributionWeight: 15,
  tutors: ["Tutor Postman"],
  plannerUrl: PLANNER_PROJECT
};

const nameBody = label => ({ name: `${label} Postman {{runId}}`, description: "Creado por la coleccion Postman" });

const folders = [
  { tag: "health-controller", name: "Salud", requests: [{ op: "GET /api/v1/health", shape: { type: "object", keys: ["status", "database", "timestamp", "service"], required: ["status"] } }] },
  {
    tag: "auth-context-controller",
    name: "Sesion",
    requests: [{
      op: "GET /api/v1/auth/me",
      tests: ["pm.test('La sesion principal es ADMIN', function () { pm.expect(pm.response.json().user.roles, 'promueve la cuenta de accessToken a ADMIN').to.include('ADMIN'); });"]
    }]
  },
  {
    tag: "catalog-controller",
    name: "Catalogos",
    requests: [
      { op: "GET /api/v1/schools" },
      { op: "POST /api/v1/schools", body: nameBody("Escuela"), save: [["newSchoolId", "id"]] },
      { op: "PUT /api/v1/schools/:id", vars: { id: "{{newSchoolId}}" }, body: nameBody("Escuela editada") },
      { op: "DELETE /api/v1/schools/:id", vars: { id: "{{newSchoolId}}" } },
      { op: "GET /api/v1/measurement-units" },
      { op: "POST /api/v1/measurement-units", body: { ...nameBody("Unidad"), type: "NUMERICA" }, save: [["newUnitId", "id"]] },
      { op: "PUT /api/v1/measurement-units/:id", vars: { id: "{{newUnitId}}" }, body: { ...nameBody("Unidad editada"), type: "NUMERICA" } },
      { op: "PATCH /api/v1/measurement-units/:id/active", vars: { id: "{{newUnitId}}" }, body: { active: false } },
      { op: "DELETE /api/v1/measurement-units/:id", vars: { id: "{{newUnitId}}" } },
      { op: "GET /api/v1/departments" },
      { op: "POST /api/v1/departments", body: { ...nameBody("Departamento"), schoolId: 1 }, save: [["newDepartmentId", "id"]] },
      { op: "PUT /api/v1/departments/:id", vars: { id: "{{newDepartmentId}}" }, body: { ...nameBody("Departamento editado"), schoolId: 1 } },
      { op: "DELETE /api/v1/departments/:id", vars: { id: "{{newDepartmentId}}" } },
      { op: "GET /api/v1/academic-periods" },
      { op: "POST /api/v1/academic-periods", body: { name: "{{periodYear}}-1", startDate: "2031-01-15", endDate: "2031-06-30", status: "PLANIFICACION" }, save: [["newPeriodId", "id"]] },
      { op: "PUT /api/v1/academic-periods/:id", vars: { id: "{{newPeriodId}}" }, body: { name: "{{periodYear}}-2", startDate: "2031-01-20", endDate: "2031-06-30", status: "PLANIFICACION" } },
      { op: "PATCH /api/v1/academic-periods/:id/status", vars: { id: "{{newPeriodId}}" }, body: { status: "CERRADO" } },
      { op: "PATCH /api/v1/academic-periods/:id/active", vars: { id: "{{newPeriodId}}" }, body: { active: false } },
      { op: "DELETE /api/v1/academic-periods/:id", vars: { id: "{{newPeriodId}}" } }
    ]
  },
  {
    tag: "world-controller",
    name: "Mundos",
    requests: [
      { op: "GET /api/v1/worlds" },
      { op: "POST /api/v1/worlds", body: nameBody("Mundo"), save: [["newWorldId", "id"]] },
      { op: "PUT /api/v1/worlds/:id", vars: { id: "{{newWorldId}}" }, body: nameBody("Mundo editado") },
      { op: "DELETE /api/v1/worlds/:id", vars: { id: "{{newWorldId}}" } }
    ]
  },
  {
    tag: "strategic-bet-controller",
    name: "Apuestas estrategicas",
    requests: [
      { op: "GET /api/v1/strategic-bets", query: { period: PERIOD } },
      { op: "POST /api/v1/strategic-bets", body: { ...nameBody("Apuesta"), worldId: 1, startDate: "2026-01-01", endDate: "2026-12-31" }, save: [["newBetId", "id"]] },
      { op: "GET /api/v1/strategic-bets/:id", vars: { id: "{{newBetId}}" } },
      { op: "PUT /api/v1/strategic-bets/:id", vars: { id: "{{newBetId}}" }, body: { ...nameBody("Apuesta editada"), worldId: 1, startDate: "2026-02-01", endDate: "2026-11-30" } },
      { op: "PATCH /api/v1/strategic-bets/:id/active", vars: { id: "{{newBetId}}" }, body: { active: false } }
    ]
  },
  {
    tag: "goal-controller",
    name: "Metas",
    requests: [
      { op: "GET /api/v1/goals", query: { period: PERIOD } },
      { op: "POST /api/v1/goals", body: { ...nameBody("Meta"), referenceIndicator: "Indicador Postman", expectedValue: 75, measurementUnitId: 1, worldId: 1, startDate: "2026-01-01", endDate: "2026-12-31" }, save: [["newGoalId", "id"]] },
      { op: "GET /api/v1/goals/:id", vars: { id: "{{newGoalId}}" } },
      { op: "PUT /api/v1/goals/:id", vars: { id: "{{newGoalId}}" }, body: { ...nameBody("Meta editada"), referenceIndicator: "Indicador editado", expectedValue: 90, measurementUnitId: 1, worldId: 1, startDate: "2026-02-01", endDate: "2026-11-30" } },
      { op: "POST /api/v1/goals/:id/periods/:periodId", vars: { id: "{{newGoalId}}", periodId: "2" }, status: 200 },
      { op: "DELETE /api/v1/goals/:id/periods/:periodId", vars: { id: "{{newGoalId}}", periodId: "2" }, status: 200 },
      { op: "PATCH /api/v1/goals/:id/active", vars: { id: "{{newGoalId}}" }, body: { active: false } }
    ]
  },
  {
    tag: "key-result-controller",
    name: "Key Results",
    requests: [
      { op: "GET /api/v1/key-results" },
      { op: "POST /api/v1/key-results", body: krBody(""), save: [["newKeyResultId", "id"]] },
      { op: "GET /api/v1/key-results/:id", vars: { id: "{{newKeyResultId}}" } },
      { op: "PUT /api/v1/key-results/:id", vars: { id: "{{newKeyResultId}}" }, body: krBody(" editado") },
      { op: "PATCH /api/v1/key-results/:id/status", vars: { id: "{{newKeyResultId}}" }, body: { status: "ACTIVO" } },
      { op: "PATCH /api/v1/key-results/:id/progress", vars: { id: "{{newKeyResultId}}" }, body: { currentValue: 40, progressPercentage: 40 } },
      { op: "GET /api/v1/key-results/:id/progress-records", vars: { id: "{{newKeyResultId}}" } },
      {
        op: "PATCH /api/v1/key-results/:id/active",
        vars: { id: "{{newKeyResultId}}" },
        body: { active: false },
        tests: ["pm.test('Queda archivado', function () { pm.expect(pm.response.json().archived).to.equal(true); });"]
      }
    ]
  },
  {
    tag: "project-controller",
    name: "Proyectos",
    requests: [
      { op: "GET /api/v1/projects" },
      { op: "POST /api/v1/projects", body: projectBody, save: [["newProjectId", "id"]] },
      { op: "GET /api/v1/projects/:id", vars: { id: "{{newProjectId}}" } },
      { op: "GET /api/v1/projects/:id/detail", vars: { id: "{{newProjectId}}" } },
      { op: "GET /api/v1/projects/:id/history", vars: { id: "{{newProjectId}}" } },
      { op: "GET /api/v1/projects/:id/impact-chain", vars: { id: "{{newProjectId}}" } },
      { op: "GET /api/v1/projects/:id/contribution-chain", vars: { id: "{{newProjectId}}" } },
      { op: "PUT /api/v1/projects/:id", vars: { id: "{{newProjectId}}" }, body: { ...projectBody, name: "Proyecto Postman {{runId}} editado" } },
      { op: "POST /api/v1/projects/:id/teachers", vars: { id: "{{newProjectId}}" }, body: { teacherId: 4, roleId: 2, joinedAt: "2026-02-01" } },
      { op: "GET /api/v1/projects/:id/teachers", vars: { id: "{{newProjectId}}" } },
      { op: "DELETE /api/v1/projects/:id/teachers/:teacherId/roles/:roleId", vars: { id: "{{newProjectId}}", teacherId: "4", roleId: "2" } },
      { op: "POST /api/v1/projects/:id/progress", vars: { id: "{{newProjectId}}" }, body: { progressPercent: 40, comment: "Avance registrado por Postman", milestones: "Validacion automatizada" } },
      { op: "PATCH /api/v1/projects/:id/status", vars: { id: "{{newProjectId}}" }, body: { status: "ACTIVO" } },
      // With MTE_TRAYECTORIA_PROJECTS_MODE=mock (local default) the sync is disabled on purpose.
      { op: "POST /api/v1/projects/sync/trayectoria", status: 503, error: "no está configurada en este ambiente", allow5xx: true }
    ]
  },
  {
    tag: "project-key-result-link-controller",
    name: "Vinculos proyecto-KR",
    requests: [
      { op: "GET /api/v1/project-key-result-links", query: { projectId: "{{newProjectId}}" } },
      { op: "POST /api/v1/project-key-result-links", body: { projectId: "#{newProjectId}", keyResultId: 3, contributionWeight: 5, contributionType: "SOPORTE" }, save: [["newLinkId", "id"]] },
      { op: "PATCH /api/v1/project-key-result-links/:id", vars: { id: "{{newLinkId}}" }, body: { contributionType: "INDIRECTA", contributionWeight: 8 } },
      { op: "DELETE /api/v1/project-key-result-links/:id", vars: { id: "{{newLinkId}}" } }
    ]
  },
  {
    tag: "people-controller",
    name: "Personas y usuarios",
    requests: [
      { op: "GET /api/v1/roles" },
      { op: "POST /api/v1/roles", body: nameBody("Rol"), save: [["newRoleId", "id"]] },
      { op: "PUT /api/v1/roles/:id", vars: { id: "{{newRoleId}}" }, body: nameBody("Rol editado") },
      { op: "DELETE /api/v1/roles/:id", vars: { id: "{{newRoleId}}" } },
      { op: "GET /api/v1/positions" },
      { op: "POST /api/v1/positions", body: nameBody("Cargo"), save: [["newPositionId", "id"]] },
      { op: "PUT /api/v1/positions/:id", vars: { id: "{{newPositionId}}" }, body: nameBody("Cargo editado") },
      { op: "GET /api/v1/professors", query: { departmentId: "1" } },
      { op: "POST /api/v1/professors", body: { name: "Profesor Postman", email: "postman.prof.{{runId}}@icesi.edu.co", departmentId: 1 }, save: [["newProfessorId", "id"]] },
      { op: "PUT /api/v1/professors/:id", vars: { id: "{{newProfessorId}}" }, body: { name: "Profesor Postman editado", email: "postman.prof.{{runId}}@icesi.edu.co", departmentId: 1 } },
      { op: "POST /api/v1/professors/:id/positions", vars: { id: "{{newProfessorId}}" }, body: { positionId: "#{newPositionId}", active: true } },
      { op: "GET /api/v1/professors/:id/positions", vars: { id: "{{newProfessorId}}" } },
      { op: "DELETE /api/v1/professors/:id/positions/:positionId", vars: { id: "{{newProfessorId}}", positionId: "{{newPositionId}}" } },
      { op: "DELETE /api/v1/professors/:id", vars: { id: "{{newProfessorId}}" } },
      { op: "DELETE /api/v1/positions/:id", vars: { id: "{{newPositionId}}" } },
      { op: "GET /api/v1/directory-users", query: { accessRole: "CONTRIBUTOR" } },
      { op: "POST /api/v1/directory-users", body: { name: "Colaborador Postman", email: "postman.contrib.{{runId}}@icesi.edu.co", departmentId: 1, accessRole: "CONTRIBUTOR", active: true }, save: [["newDirectoryUserId", "id"]] },
      { op: "PUT /api/v1/directory-users/:id", vars: { id: "{{newDirectoryUserId}}" }, body: { name: "Colaborador Postman editado", email: "postman.contrib.{{runId}}@icesi.edu.co", departmentId: 1, accessRole: "CONTRIBUTOR", active: true } },
      { op: "POST /api/v1/directory-users/:id/access-link", vars: { id: "{{newDirectoryUserId}}" }, status: 200 },
      { op: "PATCH /api/v1/directory-users/:id/active", vars: { id: "{{newDirectoryUserId}}" }, body: { active: false } },
      { op: "DELETE /api/v1/directory-users/:id", vars: { id: "{{newDirectoryUserId}}" } }
    ]
  },
  { tag: "strategic-hierarchy-controller", name: "Jerarquia estrategica", requests: [{
      op: "GET /api/v1/strategic-hierarchy/tree",
      query: { period: PERIOD },
      // The documented example drops the recursive children field, so list the schema fields.
      shape: { type: "array", keys: ["nodeType", "id", "label", "description", "progressPercentage", "executionSummary", "badge", "children"], required: ["nodeType", "id", "label"] }
    }] },
  {
    tag: "dashboard-controller",
    name: "Dashboard",
    requests: ["summary", "strategic-bets/summary", "projects/by-status", "key-results/by-status", "goals/summary", "departments/summary"]
      .map(part => ({ op: `GET /api/v1/dashboard/${part}`, query: { period: PERIOD } }))
  },
  {
    tag: "report-controller",
    name: "Reportes",
    requests: [
      { op: "GET /api/v1/reports", query: { period: PERIOD } },
      { op: "GET /api/v1/reports/general", query: { period: PERIOD } },
      { op: "GET /api/v1/reports/departments", query: { period: PERIOD } },
      { op: "GET /api/v1/reports/key-results/ranking", query: { period: PERIOD } },
      { op: "GET /api/v1/reports/period-comparison", query: { basePeriod: "2026-1", comparePeriod: "2026-2" } },
      { op: "GET /api/v1/reports/export.csv", query: { period: PERIOD }, shape: { type: "csv" } },
      { op: "GET /api/v1/reports/export.pdf", query: { period: PERIOD }, shape: { type: "pdf" } }
    ]
  },
  { tag: "presentation-controller", name: "Presentacion", requests: [{ op: "GET /api/v1/presentation", query: { period: PERIOD } }] },
  {
    tag: "consistency-controller",
    name: "Consistencia",
    requests: [
      { op: "GET /api/v1/consistency/check" },
      { op: "GET /api/v1/consistency/check/export.csv", shape: { type: "csv" } }
    ]
  },
  {
    tag: "audit-controller",
    name: "Auditoria",
    requests: [
      { op: "GET /api/v1/audit-logs", query: { entityType: "Project" } },
      { op: "GET /api/v1/audit-logs/summary" }
    ]
  }
];

module.exports = { folders, krBody, projectBody, PLANNER_KR, PLANNER_PROJECT };
