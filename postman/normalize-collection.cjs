// Turns the collection Portman generates from the OpenAPI contract into the runnable
// collection: real request data per endpoint (collection-requests.cjs), status and shape
// tests, the business flows (collection-flows.cjs) and token handling for each role.
const fs = require("fs");
const path = require("path");
const { SET_VAR, bearer, errorTests, makeRequest, noAuth, okTests, saveFromResponse, setJsonBody, shapeFromExample } = require("./collection-helpers.cjs");
const { folders } = require("./collection-requests.cjs");
const { coveredByFlows, flows } = require("./collection-flows.cjs");

const inputPath = path.join(__dirname, "mte.generated.postman_collection.json");
const outputPath = path.join(__dirname, "mte.normalized.postman_collection.json");
const generated = JSON.parse(fs.readFileSync(inputPath, "utf8"));

const VARIABLES = [
  "baseUrl", "accessToken", "managerToken", "visitorToken", "authMockUrl",
  "adminEmail", "managerEmail", "visitorEmail", "runId"
];

// Before each request: a run id for unique names and, when authMockUrl points at the
// local auth stand-in, a token for every role that has an email but no token yet.
const COLLECTION_PRE_REQUEST = [
  SET_VAR,
  "if (!pm.variables.get('runId')) { setVar('runId', Date.now().toString(36)); }",
  // Period names must look like YYYY-1 or YYYY-2: a far-future year taken from the run id
  // (seconds, cycling through 3000-9999) keeps them clear of real periods and of other runs.
  "pm.variables.set('periodYear', String(3000 + Math.floor(parseInt(pm.variables.get('runId'), 36) / 1000) % 7000));",
  "const authMockUrl = pm.variables.get('authMockUrl');",
  "if (authMockUrl) {",
  "  [['accessToken', 'adminEmail'], ['managerToken', 'managerEmail'], ['visitorToken', 'visitorEmail']].forEach(function (pair) {",
  "    const email = pm.variables.get(pair[1]);",
  "    if (pm.variables.get(pair[0]) || !email) { return; }",
  "    pm.sendRequest({ url: authMockUrl.replace(/\\/$/, '') + '/mock/token', method: 'POST', header: { 'Content-Type': 'application/json' }, body: { mode: 'raw', raw: JSON.stringify({ email: email }) } }, function (error, response) {",
  "      if (!error && response.code === 200) { setVar(pair[0], response.json().access_token); }",
  "    });",
  "  });",
  "}"
];

// Requests marked allow5xx expect a 5xx on purpose and skip this check.
function collectionTest(allowed) {
  return [
    `if (!${JSON.stringify(allowed)}.includes(pm.info.requestName)) { pm.test('Sin errores 5xx', function () { pm.expect(pm.response.code).to.be.below(500); }); }`
  ];
}

const DEFAULT_STATUS = { GET: 200, POST: 201, PUT: 200, PATCH: 200, DELETE: 204 };

function walk(items, visit, tag) {
  items.forEach(item => (item.item ? walk(item.item, visit, item.name) : visit(item, tag)));
}

function opOf(item) {
  return `${item.request.method} /${item.request.url.path.join("/")}`;
}

// Generated requests and the shape of their documented success example, by operation.
const generatedByOp = new Map();
const shapes = new Map();
walk(generated.item, (item, tag) => {
  const op = opOf(item);
  generatedByOp.set(op, { item, tag });
  const success = (item.response || []).find(response => Number(response.code) < 300);
  if (!success) {
    return;
  }
  if (!success.body) {
    shapes.set(op, { type: "empty" });
    return;
  }
  try {
    shapes.set(op, shapeFromExample(JSON.parse(success.body)));
  } catch (error) {
    shapes.set(op, null);
  }
});

function resolveAuth(auth) {
  if (!auth) {
    return undefined;
  }
  if (auth === "none") {
    return noAuth();
  }
  if (auth === "invalid") {
    return { type: "bearer", bearer: [{ key: "token", value: "token.invalido.postman", type: "string" }] };
  }
  return bearer(auth);
}

function testsFor(spec, op) {
  const status = spec.status || DEFAULT_STATUS[spec.method];
  const lines = [];
  if (spec.error !== undefined) {
    lines.push(...errorTests(status, spec.error || undefined));
  } else {
    const shape = spec.shape || shapes.get(spec.shapeOf || op) || (status === 204 ? { type: "empty" } : null);
    lines.push(...okTests(status, shape));
  }
  (spec.save || []).forEach(([variable, field]) => lines.push(...saveFromResponse(variable, field)));
  lines.push(...(spec.tests || []));
  return lines;
}

// A generated request filled with the table's data: path values, enabled query values,
// body and tests. Other optional query parameters stay disabled.
function fromTable(spec) {
  const entry = generatedByOp.get(spec.op);
  if (!entry) {
    throw new Error(`The contract no longer has ${spec.op}; update collection-requests.cjs`);
  }
  const item = JSON.parse(JSON.stringify(entry.item));
  const [method, opPath] = spec.op.split(" ");
  const url = item.request.url;
  url.host = ["{{baseUrl}}"];
  (url.variable || []).forEach(variable => {
    if (!spec.vars || spec.vars[variable.key] === undefined) {
      throw new Error(`${spec.op} needs a value for :${variable.key}`);
    }
    variable.value = spec.vars[variable.key];
  });
  (url.query || []).forEach(param => {
    const value = spec.query && spec.query[param.key];
    param.value = value === undefined ? param.value : value;
    param.disabled = value === undefined;
  });
  const enabled = (url.query || []).filter(param => !param.disabled).map(param => `${param.key}=${param.value}`);
  url.raw = `{{baseUrl}}${opPath}${enabled.length ? `?${enabled.join("&")}` : ""}`;
  delete item.request.auth;
  if (spec.body !== undefined) {
    setJsonBody(item.request, spec.body);
  } else if (item.request.body) {
    delete item.request.body;
  }
  item.name = spec.op;
  item.response = [];
  item.event = [{ listen: "test", script: { type: "text/javascript", exec: testsFor({ ...spec, method }, spec.op) } }];
  return item;
}

function fromFlow(spec) {
  return makeRequest({
    name: spec.name,
    method: spec.method,
    url: spec.url,
    body: spec.body,
    auth: resolveAuth(spec.auth),
    pre: spec.pre,
    tests: testsFor(spec)
  });
}

const tableOps = new Set();
const tagItems = folders.map(folder => ({
  name: folder.name,
  description: `Endpoints del tag ${folder.tag}.`,
  item: folder.requests.map(spec => {
    tableOps.add(spec.op);
    return fromTable(spec);
  })
}));

// The health check opens every run with a fresh run id.
tagItems[0].item[0].event.unshift({
  listen: "prerequest",
  script: { type: "text/javascript", exec: [SET_VAR, "setVar('runId', Date.now().toString(36));"] }
});

const flowItems = flows.map(flow => ({ name: flow.name, item: flow.steps.map(fromFlow) }));

const missing = [...generatedByOp.keys()].filter(op => !tableOps.has(op) && !coveredByFlows.includes(op));
missing.forEach(op => console.warn(`Without data in collection-requests.cjs: ${op}`));

const collection = {
  info: { ...generated.info, name: "PDG MTE API", description: "Coleccion generada desde el contrato OpenAPI y normalizada con datos, pruebas y flujos." },
  auth: bearer("accessToken"),
  variable: VARIABLES.map(key => ({ key, value: "", type: "string" })),
  event: [
    { listen: "prerequest", script: { type: "text/javascript", exec: COLLECTION_PRE_REQUEST } },
    { listen: "test", script: { type: "text/javascript", exec: collectionTest(folders.flatMap(folder => folder.requests).filter(spec => spec.allow5xx).map(spec => spec.op)) } }
  ],
  item: [...tagItems, ...flowItems]
};
delete collection.info._postman_id;

fs.writeFileSync(outputPath, `${JSON.stringify(collection, null, 2)}\n`);
let requests = 0;
walk(collection.item, () => { requests += 1; });
console.log(`Normalized ${requests} requests in ${collection.item.length} folders -> ${path.relative(process.cwd(), outputPath)}`);
