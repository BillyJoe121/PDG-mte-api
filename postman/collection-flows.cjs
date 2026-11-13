// Business flows appended after the tag folders: user management and sign-in, role
// checks, negative cases, KR archiving and the Planner link. Each step is a spec the
// normalizer turns into a request:
// { name, method, url, body, auth, status, shapeOf, error, tests, pre, save }
// auth: omitted = ADMIN session ({{accessToken}}), a variable name, "none" or "invalid".
// shapeOf: "METHOD /path" of a generated request whose documented example gives the shape.

const { SET_VAR } = require("./collection-helpers.cjs");
const { krBody, projectBody, PLANNER_KR } = require("./collection-requests.cjs");

const KR_SHAPE = "GET /api/v1/key-results/:id";
const PROJECT_SHAPE = "GET /api/v1/projects/:id";
const DIRECTORY_SHAPE = "POST /api/v1/directory-users";
const PLANNER_OTHER = "https://planner.cloud.microsoft/webui/plan/postman-otro";
const projectWithoutKr = (({ keyResultId, ...rest }) => rest)(projectBody);

const check = (title, body) => `pm.test(${JSON.stringify(title)}, function () { ${body} });`;

// A strong password created at run time; it only lives in the run's variables.
const newPassword = variable => [
  SET_VAR,
  `setVar(${JSON.stringify(variable)}, 'Pm-' + Math.random().toString(36).slice(2, 10) + '-' + Date.now().toString(36) + '!9');`
];

// Reads token_hash and type from the access link the API returns.
const keepLink = prefix => [
  SET_VAR,
  "if (pm.response.code === 200) { const url = pm.response.json().url; const hash = /[?&#]token_hash=([^&#]+)/.exec(url); const type = /[?&#]type=([^&#]+)/.exec(url);",
  `  setVar(${JSON.stringify(prefix + "TokenHash")}, hash ? decodeURIComponent(hash[1]) : ''); setVar(${JSON.stringify(prefix + "LinkType")}, type ? decodeURIComponent(type[1]) : ''); }`,
  check("El enlace trae token_hash y type", "pm.expect(pm.response.json().url).to.match(/token_hash=/).and.to.match(/type=/);")
];

// Stores the session from a login; the role token is filled only when the run did not
// receive one already.
const keepSession = (sessionVar, roleVar) => [
  SET_VAR,
  "if (pm.response.code === 200) { const token = pm.response.json().accessToken;",
  `  setVar(${JSON.stringify(sessionVar)}, token); if (!pm.variables.get(${JSON.stringify(roleVar)})) { setVar(${JSON.stringify(roleVar)}, token); } }`
];

const hasRole = role => check(`La sesion tiene rol ${role}`, `pm.expect(pm.response.json().user.roles).to.include('${role}');`);

function userSetup(role, prefix) {
  const email = `postman.${prefix}.{{runId}}@icesi.edu.co`;
  return [
    {
      name: `Crear usuario ${role}`,
      method: "POST", url: "/api/v1/directory-users", status: 201, shapeOf: DIRECTORY_SHAPE,
      body: { name: `Usuario ${role} Postman`, email, departmentId: 1, accessRole: role, active: true },
      save: [[`${prefix}UserId`, "id"], [`${prefix}UserEmail`, "email"]]
    },
    {
      name: `Enlace de acceso ${role}`,
      method: "POST", url: `/api/v1/directory-users/{{${prefix}UserId}}/access-link`, status: 200,
      shapeOf: `POST /api/v1/directory-users/:id/access-link`, tests: keepLink(prefix)
    },
    {
      name: `Definir contrasena ${role}`,
      method: "POST", url: "/api/v1/auth/set-password", auth: "none", status: 204,
      pre: newPassword(`${prefix}Password`),
      body: { tokenHash: `{{${prefix}TokenHash}}`, type: `{{${prefix}LinkType}}`, password: `{{${prefix}Password}}` }
    }
  ];
}

const login = (name, prefix, passwordVar, roleVar) => ({
  name, method: "POST", url: "/api/v1/auth/login", auth: "none", status: 200, shapeOf: "POST /api/v1/auth/login",
  body: { email: `{{${prefix}UserEmail}}`, password: `{{${passwordVar}}}` },
  tests: keepSession(`${prefix}SessionToken`, roleVar)
});

const authFlow = [
  ...userSetup("MANAGER", "manager"),
  {
    name: "Reutilizar enlace usado",
    method: "POST", url: "/api/v1/auth/set-password", auth: "none", status: 400, error: "",
    body: { tokenHash: "{{managerTokenHash}}", type: "{{managerLinkType}}", password: "{{managerPassword}}" }
  },
  login("Login MANAGER", "manager", "managerPassword", "managerToken"),
  {
    name: "Sesion MANAGER", method: "GET", url: "/api/v1/auth/me", auth: "managerSessionToken", status: 200,
    shapeOf: "GET /api/v1/auth/me",
    tests: [hasRole("MANAGER"), check("Correo de la sesion", "pm.expect(pm.response.json().user.email).to.equal(pm.variables.get('managerUserEmail'));")]
  },
  {
    name: "Cambiar contrasena MANAGER", method: "POST", url: "/api/v1/auth/password", auth: "managerSessionToken", status: 204,
    pre: newPassword("managerNewPassword"),
    body: { currentPassword: "{{managerPassword}}", newPassword: "{{managerNewPassword}}" }
  },
  {
    name: "Login con contrasena anterior", method: "POST", url: "/api/v1/auth/login", auth: "none", status: 401, error: "",
    body: { email: "{{managerUserEmail}}", password: "{{managerPassword}}" }
  },
  login("Login con contrasena nueva", "manager", "managerNewPassword", "managerToken"),
  { name: "Logout MANAGER", method: "POST", url: "/api/v1/auth/logout", auth: "managerSessionToken", status: 204 },
  ...userSetup("VISITOR", "visitor"),
  login("Login VISITOR", "visitor", "visitorPassword", "visitorToken"),
  { name: "Sesion VISITOR", method: "GET", url: "/api/v1/auth/me", auth: "visitorSessionToken", status: 200, shapeOf: "GET /api/v1/auth/me", tests: [hasRole("VISITOR")] }
];

const DENIED = "Acceso denegado.";
const roleFlow = [
  { name: "VISITOR lee proyectos", method: "GET", url: "/api/v1/projects", auth: "visitorToken", status: 200, shapeOf: "GET /api/v1/projects" },
  { name: "VISITOR lee dashboard", method: "GET", url: "/api/v1/dashboard/summary?period=2026-1", auth: "visitorToken", status: 200, shapeOf: "GET /api/v1/dashboard/summary" },
  { name: "VISITOR crea mundo", method: "POST", url: "/api/v1/worlds", auth: "visitorToken", status: 403, error: DENIED, body: { name: "Mundo visitante", description: "No debe crearse" } },
  { name: "VISITOR edita proyecto", method: "PUT", url: "/api/v1/projects/16", auth: "visitorToken", status: 403, error: DENIED, body: projectBody },
  { name: "VISITOR archiva KR", method: "PATCH", url: "/api/v1/key-results/13/active", auth: "visitorToken", status: 403, error: DENIED, body: { active: false } },
  { name: "VISITOR borra mundo", method: "DELETE", url: "/api/v1/worlds/1", auth: "visitorToken", status: 403, error: DENIED },
  { name: "MANAGER crea mundo", method: "POST", url: "/api/v1/worlds", auth: "managerToken", status: 403, error: DENIED, body: { name: "Mundo gestor", description: "No debe crearse" } },
  { name: "MANAGER lista usuarios", method: "GET", url: "/api/v1/directory-users", auth: "managerToken", status: 403, error: DENIED },
  {
    name: "MANAGER crea KR", method: "POST", url: "/api/v1/key-results", auth: "managerToken", status: 201, shapeOf: KR_SHAPE,
    body: krBody(" gestor"), save: [["managerKeyResultId", "id"]]
  },
  { name: "MANAGER archiva su KR", method: "PATCH", url: "/api/v1/key-results/{{managerKeyResultId}}/active", auth: "managerToken", status: 200, shapeOf: KR_SHAPE, body: { active: false } }
];

const negativeFlow = [
  { name: "Sin token", method: "GET", url: "/api/v1/projects", auth: "none", status: 401, error: "Inicia sesión para continuar." },
  { name: "Token invalido", method: "GET", url: "/api/v1/projects", auth: "invalid", status: 401, error: "Inicia sesión para continuar." },
  { name: "Proyecto sin KR", method: "POST", url: "/api/v1/projects", status: 400, error: "Selecciona al menos un Key Result para la iniciativa.", body: projectWithoutKr },
  { name: "Periodo de proyecto invalido", method: "POST", url: "/api/v1/projects", status: 400, error: "", body: { ...projectBody, startPeriod: "2026" } },
  {
    name: "Periodo academico con nombre invalido", method: "POST", url: "/api/v1/academic-periods", status: 400, error: "",
    body: { name: "P-{{runId}}", startDate: "2031-01-15", endDate: "2031-06-30", status: "PLANIFICACION" },
    tests: [check("Detalle del formato AAAA-1 o AAAA-2", "pm.expect(pm.response.json().details.some(function (detail) { return detail.indexOf('AAAA-1 o AAAA-2') >= 0; })).to.equal(true);")]
  },
  { name: "Archivar KR con proyectos", method: "PATCH", url: "/api/v1/key-results/1/active", status: 409, error: "No se puede archivar un KR con proyectos vinculados.", body: { active: false } },
  {
    name: "Archivar KR sin cuerpo", method: "PATCH", url: "/api/v1/key-results/1/active", status: 400, error: "", body: {},
    tests: [check("Detalle del campo active", "pm.expect(pm.response.json().details.some(function (detail) { return detail.indexOf('active:') === 0; })).to.equal(true);")]
  },
  { name: "KR sin metas ni apuestas", method: "POST", url: "/api/v1/key-results", status: 400, error: "", body: { ...krBody(" sin metas"), goalIds: [], strategicBetIds: [] } },
  { name: "KR inexistente", method: "GET", url: "/api/v1/key-results/999999", status: 404, error: "" },
  { name: "Proyecto inexistente", method: "GET", url: "/api/v1/projects/999999", status: 404, error: "" },
  {
    name: "Correo duplicado", method: "POST", url: "/api/v1/directory-users", status: 409, error: "",
    body: { name: "Duplicado", email: "demo.profesor@icesi.edu.co", departmentId: 1, accessRole: "CONTRIBUTOR", active: true }
  },
  { name: "Estado de proyecto invalido", method: "PATCH", url: "/api/v1/projects/16/status", status: 400, error: "", body: { status: "INEXISTENTE" } },
  { name: "Login sin datos", method: "POST", url: "/api/v1/auth/login", auth: "none", status: 400, error: "", body: {} }
];

const archivedIs = value => check(`archived = ${value}`, `pm.expect(pm.response.json().archived).to.equal(${value});`);
const listHas13 = expected => check(expected ? "La lista incluye el KR 13" : "La lista no incluye el KR 13",
  `pm.expect(pm.response.json().map(function (kr) { return kr.id; }).includes(13)).to.equal(${expected});`);

const archiveFlow = [
  { name: "KR 13 antes de archivar", method: "GET", url: "/api/v1/key-results/13", status: 200, shapeOf: KR_SHAPE, tests: [archivedIs(false)] },
  {
    name: "Archivar KR 13", method: "PATCH", url: "/api/v1/key-results/13/active", status: 200, shapeOf: KR_SHAPE, body: { active: false },
    tests: [archivedIs(true), check("Fecha de archivo", "pm.expect(pm.response.json().archivedAt).to.be.a('string');")]
  },
  { name: "Lista sin archivados", method: "GET", url: "/api/v1/key-results", status: 200, shapeOf: "GET /api/v1/key-results", tests: [listHas13(false)] },
  { name: "Lista con archivados", method: "GET", url: "/api/v1/key-results?includeArchived=true", status: 200, shapeOf: "GET /api/v1/key-results", tests: [listHas13(true)] },
  { name: "Editar KR archivado", method: "PUT", url: "/api/v1/key-results/13", status: 409, error: "", body: krBody(" archivado") },
  { name: "Proyecto con KR archivado", method: "POST", url: "/api/v1/projects", status: 409, error: "", body: { ...projectBody, keyResultId: 13 } },
  { name: "Reactivar KR 13", method: "PATCH", url: "/api/v1/key-results/13/active", status: 200, shapeOf: KR_SHAPE, body: { active: true }, tests: [archivedIs(false)] },
  { name: "Lista tras reactivar", method: "GET", url: "/api/v1/key-results", status: 200, shapeOf: "GET /api/v1/key-results", tests: [listHas13(true)] }
];

const plannerIs = value => check(value ? "plannerUrl guardado" : "plannerUrl vacio",
  value ? `pm.expect(pm.response.json().plannerUrl).to.equal(${JSON.stringify(value)});` : "pm.expect(pm.response.json().plannerUrl == null).to.equal(true);");
const INVALID_PLANNER = "El enlace de Planner no es valido";

const plannerFlow = [
  { name: "KR con Planner", method: "POST", url: "/api/v1/key-results", status: 201, shapeOf: KR_SHAPE, body: krBody(" planner"), save: [["plannerKeyResultId", "id"]], tests: [plannerIs(PLANNER_KR)] },
  { name: "Cambiar Planner del KR", method: "PUT", url: "/api/v1/key-results/{{plannerKeyResultId}}", status: 200, shapeOf: KR_SHAPE, body: { ...krBody(" planner"), plannerUrl: PLANNER_OTHER }, tests: [plannerIs(PLANNER_OTHER)] },
  { name: "Quitar Planner del KR", method: "PUT", url: "/api/v1/key-results/{{plannerKeyResultId}}", status: 200, shapeOf: KR_SHAPE, body: { ...krBody(" planner"), plannerUrl: null }, tests: [plannerIs(null)] },
  { name: "Planner http en KR", method: "PUT", url: "/api/v1/key-results/{{plannerKeyResultId}}", status: 400, error: INVALID_PLANNER, body: { ...krBody(" planner"), plannerUrl: "http://tasks.office.com/icesi.edu.co/plan" } },
  { name: "Planner fuera de Microsoft en KR", method: "PUT", url: "/api/v1/key-results/{{plannerKeyResultId}}", status: 400, error: INVALID_PLANNER, body: { ...krBody(" planner"), plannerUrl: "https://example.com/plan" } },
  { name: "Cambiar Planner del proyecto", method: "PUT", url: "/api/v1/projects/{{newProjectId}}", status: 200, shapeOf: PROJECT_SHAPE, body: { ...projectBody, plannerUrl: PLANNER_OTHER }, tests: [plannerIs(PLANNER_OTHER)] },
  { name: "Quitar Planner del proyecto", method: "PUT", url: "/api/v1/projects/{{newProjectId}}", status: 200, shapeOf: PROJECT_SHAPE, body: { ...projectBody, plannerUrl: null }, tests: [plannerIs(null)] },
  { name: "Planner http en proyecto", method: "POST", url: "/api/v1/projects", status: 400, error: INVALID_PLANNER, body: { ...projectBody, plannerUrl: "http://planner.cloud.microsoft/plan" } },
  { name: "Archivar KR de Planner", method: "PATCH", url: "/api/v1/key-results/{{plannerKeyResultId}}/active", status: 200, shapeOf: KR_SHAPE, body: { active: false } }
];

const flows = [
  { name: "Autenticacion y usuarios", steps: authFlow },
  { name: "Permisos por rol", steps: roleFlow },
  { name: "Casos negativos", steps: negativeFlow },
  { name: "Archivo de KR 13", steps: archiveFlow },
  { name: "Enlace de Planner", steps: plannerFlow }
];

// Generated requests whose behavior the flows cover, so the normalizer drops them.
const coveredByFlows = [
  "POST /api/v1/auth/login",
  "POST /api/v1/auth/logout",
  "POST /api/v1/auth/password",
  "POST /api/v1/auth/set-password"
];

module.exports = { coveredByFlows, flows };
