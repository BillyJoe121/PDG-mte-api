const fs = require("fs");
const path = require("path");

const inputPath = path.join(__dirname, "mte.generated.postman_collection.json");
const outputPath = path.join(__dirname, "mte.normalized.postman_collection.json");

const collection = JSON.parse(fs.readFileSync(inputPath, "utf8"));
collection.info.name = "PDG MTE API - Portman + Newman";

const seedIds = {
  id: "1",
  periodId: "1",
  positionId: "2",
  teacherId: "4",
  roleId: "2"
};

const validQueryValues = {
  action: "UPDATE",
  basePeriod: "2026-1",
  comparePeriod: "2026-2",
  departmentId: "1",
  entityId: "1",
  entityType: "Project",
  from: "2026-01-01T00:00:00Z",
  goalId: "1",
  keyResultId: "1",
  objectiveId: "1",
  period: "2026-1",
  periodId: "1",
  projectId: "1",
  search: "Portal",
  status: "ACTIVO",
  strategicBetId: "1",
  to: "2026-12-31T23:59:59Z",
  type: "EXTENSION"
};

const bodies = [
  [/\/api\/v1\/worlds$/, { name: "Mundo Postman {{$timestamp}}", description: "Creado por Portman/Newman" }],
  [/\/api\/v1\/worlds\/:id$/, { name: "Mundo Institucional Actualizado", description: "Actualizado por Portman/Newman" }],
  [/\/api\/v1\/schools$/, { name: "Escuela Postman {{$timestamp}}", description: "Creada por Portman/Newman" }],
  [/\/api\/v1\/schools\/:id$/, { name: "Escuela TDI Actualizada", description: "Actualizada por Portman/Newman" }],
  [/\/api\/v1\/measurement-units$/, { name: "Unidad Postman {{$timestamp}}", type: "NUMERICA", description: "Creada por Portman/Newman" }],
  [/\/api\/v1\/measurement-units\/:id$/, { name: "Porcentaje", type: "PORCENTAJE", description: "Avance o cumplimiento expresado de 0 a 100." }],
  [/\/api\/v1\/departments$/, { name: "Departamento Postman {{$timestamp}}", schoolId: 1, description: "Creado por Portman/Newman", externalDepartmentId: 9001 }],
  [/\/api\/v1\/departments\/:id$/, { name: "Departamento de Computación y Sistemas inteligentes.", schoolId: 1, description: "Departamento base actualizado por Postman/Newman", externalDepartmentId: 1 }],
  [/\/api\/v1\/academic-periods$/, { name: "2027-{{$timestamp}}", startDate: "2027-01-15", endDate: "2027-06-30", status: "PLANIFICACION" }],
  [/\/api\/v1\/academic-periods\/:id$/, { name: "2026-1", startDate: "2026-01-15", endDate: "2026-06-30", status: "ACTIVO" }],
  [/\/api\/v1\/measurement-units\/:id\/active$/, { active: true }],
  [/\/api\/v1\/academic-periods\/:id\/status$/, { status: "ACTIVO" }],
  [/\/api\/v1\/academic-periods\/:id\/active$/, { active: true }],
  [/\/api\/v1\/roles$/, { name: "Rol Postman {{$timestamp}}", description: "Creado por Portman/Newman" }],
  [/\/api\/v1\/roles\/:id$/, { name: "Lider", description: "Responsable principal de un proyecto." }],
  [/\/api\/v1\/positions$/, { name: "Cargo Postman {{$timestamp}}", description: "Creado por Portman/Newman" }],
  [/\/api\/v1\/positions\/:id$/, { name: "Profesor", description: "Cargo docente base." }],
  [/\/api\/v1\/professors$/, { name: "Profesor Postman", email: "postman.{{$timestamp}}@icesi.edu.co", departmentId: 1 }],
  [/\/api\/v1\/professors\/:id$/, { name: "Profesor Demo", email: "demo.profesor@icesi.edu.co", departmentId: 1 }],
  [/\/api\/v1\/professors\/:id\/positions$/, { positionId: 2, active: true }],
  [/\/api\/v1\/projects$/, {
    name: "Proyecto Postman {{$timestamp}}",
    description: "Creado por Portman/Newman",
    type: "EXTENSION",
    status: "ACTIVO",
    departmentId: 1,
    keyResultId: 1,
    contributionWeight: 15,
    linkStatus: "ACTIVO",
    startPeriod: "2026-1",
    endPeriod: "2026-2",
    startDate: "2026-02-01",
    endDate: "2026-11-30",
    tutors: ["Tutor Postman"],
    keyResultLinks: [{ keyResultId: 2, contributionWeight: 10, contributionType: "SOPORTE" }]
  }],
  [/\/api\/v1\/projects\/:id$/, {
    name: "Portal de servicios academicos MTE",
    description: "Actualizado por Portman/Newman",
    type: "EXTENSION",
    departmentId: 1,
    keyResultId: 1,
    contributionWeight: 35,
    linkStatus: "ACTIVO",
    startPeriod: "2026-1",
    endPeriod: "2026-2",
    startDate: "2026-01-15",
    endDate: "2026-11-30",
    tutors: ["Comite MTE"]
  }],
  [/\/api\/v1\/projects\/:id\/teachers$/, { teacherId: 4, roleId: 2, joinedAt: "2026-02-01" }],
  [/\/api\/v1\/projects\/:id\/progress$/, { progressPercent: 60, comment: "Avance registrado por Newman", milestones: "Validacion automatizada" }],
  [/\/api\/v1\/projects\/:id\/status$/, { status: "ACTIVO" }],
  [/\/api\/v1\/key-results\/:id$/, { name: "Aumentar adopcion LMS", description: "Actualizado por Portman/Newman", metric: "Porcentaje de cursos activos", baseValue: 40, targetValue: 85, currentValue: 65, measurementUnitId: 1, academicPeriodId: 1 }],
  [/\/api\/v1\/strategic-bets$/, { name: "Apuesta Postman {{$timestamp}}", description: "Creada por Portman/Newman", worldId: 1, startDate: "2026-01-01", endDate: "2026-12-31" }],
  [/\/api\/v1\/strategic-bets\/:id$/, { name: "Apuesta Postman Actualizada", description: "Actualizada por Portman/Newman", worldId: 1, startDate: "2026-02-01", endDate: "2026-11-30" }],
  [/\/api\/v1\/project-key-result-links$/, { projectId: 1, keyResultId: 3, contributionWeight: 5, contributionType: "SOPORTE" }],
  [/\/api\/v1\/objectives$/, {
    name: "Objetivo Postman {{$timestamp}}",
    description: "Creado por Portman/Newman",
    academicPeriodId: 1,
    departmentId: 1,
    goalId: 1,
    strategicBetId: 1,
    estimatedWeight: 10,
    quarter: 1,
    keyResults: [{ name: "KR Postman {{$timestamp}}", description: "KR creado por Newman", metric: "Porcentaje", baseValue: 0, targetValue: 100, currentValue: 10, measurementUnitId: 1, academicPeriodId: 1 }]
  }],
  [/\/api\/v1\/objectives\/:id\/key-results$/, { name: "KR adicional Postman {{$timestamp}}", description: "Creado por Portman/Newman", metric: "Porcentaje", baseValue: 0, targetValue: 100, currentValue: 20, measurementUnitId: 1, academicPeriodId: 1 }],
  [/\/api\/v1\/objectives\/:id$/, { name: "Optimizar servicios digitales academicos", description: "Actualizado por Portman/Newman" }],
  [/\/api\/v1\/goals$/, { name: "Meta Postman {{$timestamp}}", description: "Creada por Portman/Newman", referenceIndicator: "Indicador Postman", expectedValue: 75, measurementUnitId: 1, worldId: 1, startDate: "2026-01-01", endDate: "2026-12-31" }],
  [/\/api\/v1\/goals\/:id$/, { name: "Meta Postman Actualizada", description: "Actualizada por Portman/Newman", referenceIndicator: "Indicador actualizado", expectedValue: 90, measurementUnitId: 1, worldId: 1, startDate: "2026-02-01", endDate: "2026-11-30" }]
];

function requestPath(item) {
  const url = item.request.url;
  const parts = Array.isArray(url.path) ? url.path : [];
  return "/" + parts.join("/");
}

function setJsonBody(item, body) {
  item.request.body = {
    mode: "raw",
    raw: JSON.stringify(body, null, 2),
    options: { raw: { language: "json" } }
  };
  item.request.header = item.request.header || [];
  if (!item.request.header.some(header => header.key && header.key.toLowerCase() === "content-type")) {
    item.request.header.push({ key: "Content-Type", value: "application/json" });
  }
}

function setTests(item, lines) {
  item.event = [{
    listen: "test",
    script: {
      type: "text/javascript",
      exec: lines
    }
  }];
}

function successTests(label) {
  return [
    `pm.test('${label} responde 2xx', function () { pm.expect(pm.response.code).to.be.within(200, 299); });`,
    "pm.test('No debe responder con 5xx', function () { pm.expect(pm.response.code).to.be.below(500); });"
  ];
}

function sadTests(label) {
  return [
    `pm.test('${label} responde 4xx', function () { pm.expect(pm.response.code).to.be.within(400, 499); });`,
    "pm.test('No debe responder con 5xx', function () { pm.expect(pm.response.code).to.be.below(500); });"
  ];
}

function normalizeItem(item) {
  const url = item.request.url;
  const p = requestPath(item);
  const method = item.request.method;

  if (Array.isArray(url.variable)) {
    url.variable.forEach(variable => {
      variable.value = seedIds[variable.key] || "1";
    });
  }

  if (Array.isArray(url.query)) {
    url.query.forEach(query => {
      query.value = validQueryValues[query.key] || query.value;
      query.disabled = false;
    });
  }

  const bodyMatch = bodies.find(([pattern]) => pattern.test(p));
  if (bodyMatch && ["POST", "PUT", "PATCH"].includes(method)) {
    setJsonBody(item, bodyMatch[1]);
  }

  if (method === "DELETE") {
    if (Array.isArray(url.variable)) {
      url.variable.forEach(variable => {
        variable.value = "999999";
      });
    }
    setTests(item, sadTests(`${method} ${p} con recurso inexistente`));
  } else {
    setTests(item, successTests(`${method} ${p}`));
  }
}

function walk(items) {
  (items || []).forEach(item => {
    if (item.item) {
      walk(item.item);
    } else {
      normalizeItem(item);
    }
  });
}

function hasRequest(method, rawUrl) {
  let found = false;
  function visit(items) {
    (items || []).forEach(item => {
      if (item.item) {
        visit(item.item);
        return;
      }
      const request = item.request || {};
      if (request.method === method && request.url === rawUrl) {
        found = true;
      }
    });
  }
  visit(collection.item);
  return found;
}

function makeRequest(name, method, rawUrl, body, tests) {
  const request = {
    method,
    header: [],
    url: rawUrl
  };
  if (body !== undefined) {
    request.header.push({ key: "Content-Type", value: "application/json" });
    request.body = {
      mode: "raw",
      raw: JSON.stringify(body, null, 2),
      options: { raw: { language: "json" } }
    };
  }
  return {
    name,
    request,
    event: [{
      listen: "test",
      script: {
        type: "text/javascript",
        exec: tests
      }
    }]
  };
}

walk(collection.item);

const frontendContractRequests = [
  makeRequest(
    "PUT apuesta estrategica",
    "PUT",
    "{{baseUrl}}/api/v1/strategic-bets/1",
    { name: "Apuesta Postman Actualizada", description: "Actualizada por Portman/Newman", worldId: 1, startDate: "2026-02-01", endDate: "2026-11-30" },
    successTests("PUT /api/v1/strategic-bets/:id")
  ),
  makeRequest(
    "PUT meta institucional",
    "PUT",
    "{{baseUrl}}/api/v1/goals/1",
    { name: "Meta Postman Actualizada", description: "Actualizada por Portman/Newman", referenceIndicator: "Indicador actualizado", expectedValue: 90, measurementUnitId: 1, worldId: 1, startDate: "2026-02-01", endDate: "2026-11-30" },
    successTests("PUT /api/v1/goals/:id")
  )
];

const frontendContractItems = frontendContractRequests.filter(item => !hasRequest(item.request.method, item.request.url));
if (frontendContractItems.length > 0) {
  collection.item.push({
    name: "Contratos requeridos por frontend",
    item: frontendContractItems
  });
}

collection.item.push({
  name: "Sad paths generados",
  item: [
    makeRequest("GET proyecto inexistente", "GET", "{{baseUrl}}/api/v1/projects/999999", undefined, sadTests("GET proyecto inexistente")),
    makeRequest("GET objetivo inexistente", "GET", "{{baseUrl}}/api/v1/objectives/999999", undefined, sadTests("GET objetivo inexistente")),
    makeRequest("GET meta inexistente", "GET", "{{baseUrl}}/api/v1/goals/999999", undefined, sadTests("GET meta inexistente")),
    makeRequest("POST proyecto sin campos requeridos", "POST", "{{baseUrl}}/api/v1/projects", {}, sadTests("POST proyecto sin campos requeridos")),
    makeRequest("POST objetivo sin campos requeridos", "POST", "{{baseUrl}}/api/v1/objectives", {}, sadTests("POST objetivo sin campos requeridos")),
    makeRequest("POST enlace con relaciones inexistentes", "POST", "{{baseUrl}}/api/v1/project-key-result-links", { projectId: 999999, keyResultId: 999999, contributionWeight: 10, contributionType: "DIRECTA" }, sadTests("POST enlace con relaciones inexistentes")),
    makeRequest("PATCH proyecto con estado invalido", "PATCH", "{{baseUrl}}/api/v1/projects/1/status", { status: "NO_EXISTE" }, sadTests("PATCH proyecto con estado invalido")),
    makeRequest("GET reporte con periodo inexistente", "GET", "{{baseUrl}}/api/v1/reports/general?period=NO_EXISTE", undefined, sadTests("GET reporte con periodo inexistente"))
  ]
});

fs.writeFileSync(outputPath, JSON.stringify(collection, null, 2));
console.log(`Normalized collection written to ${outputPath}`);
