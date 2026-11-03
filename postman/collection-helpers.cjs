// Shared builders for the normalized collection: requests, status and shape tests,
// and the scripts that read and store run variables.

// Writes a value where Postman will read it back: an environment variable shadows a
// collection variable with the same name, so update the environment when it defines it.
const SET_VAR = "function setVar(key, value) { if (pm.environment.has(key)) { pm.environment.set(key, value); } else { pm.collectionVariables.set(key, value); } }";

// Values written as "#{name}" become a bare {{name}} in the raw body, so numeric ids
// stay numbers after Postman resolves them.
function rawJson(body) {
  return JSON.stringify(body, null, 2).replace(/"#\{(\w+)\}"/g, "{{$1}}");
}

function setJsonBody(request, body) {
  request.body = { mode: "raw", raw: rawJson(body), options: { raw: { language: "json" } } };
  request.header = (request.header || []).filter(header => String(header.key).toLowerCase() !== "content-type");
  request.header.push({ key: "Content-Type", value: "application/json" });
}

function bearer(variable) {
  return { type: "bearer", bearer: [{ key: "token", value: `{{${variable}}}`, type: "string" }] };
}

function noAuth() {
  return { type: "noauth" };
}

function parseUrl(rawUrl) {
  const [base, search] = rawUrl.split("?");
  const pathPart = base.replace("{{baseUrl}}", "");
  const url = { raw: rawUrl, host: ["{{baseUrl}}"], path: pathPart.split("/").filter(Boolean) };
  if (search) {
    url.query = search.split("&").map(pair => {
      const [key, value = ""] = pair.split("=");
      return { key, value };
    });
  }
  return url;
}

function event(listen, lines) {
  return { listen, script: { type: "text/javascript", exec: lines } };
}

function makeRequest({ name, method, url, body, auth, tests, pre }) {
  const request = { method, header: [], url: parseUrl(`{{baseUrl}}${url}`) };
  if (body !== undefined) {
    setJsonBody(request, body);
  }
  if (auth) {
    request.auth = auth;
  }
  const events = [];
  if (pre && pre.length) {
    events.push(event("prerequest", pre));
  }
  events.push(event("test", tests || []));
  return { name: name || `${method} ${url}`, request, event: events };
}

function statusTest(code) {
  return `pm.test('Responde ${code}', function () { pm.response.to.have.status(${code}); });`;
}

// Shape inferred from a documented example: JSON type plus the documented top-level
// fields. The API omits null fields, so absent fields are fine but unknown ones are not.
function shapeFromExample(example) {
  if (Array.isArray(example)) {
    const first = example.find(value => value && typeof value === "object" && !Array.isArray(value));
    return { type: "array", keys: first ? Object.keys(first) : [] };
  }
  if (example && typeof example === "object") {
    return { type: "object", keys: Object.keys(example) };
  }
  return null;
}

function shapeTests(shape) {
  if (!shape) {
    return [];
  }
  if (shape.type === "empty") {
    return ["pm.test('Sin cuerpo', function () { pm.expect(pm.response.text()).to.equal(''); });"];
  }
  if (shape.type === "csv") {
    return ["pm.test('Devuelve CSV', function () { pm.expect(pm.response.headers.get('Content-Type')).to.include('text/csv'); pm.expect(pm.response.text().length).to.be.above(0); });"];
  }
  if (shape.type === "pdf") {
    return ["pm.test('Devuelve PDF', function () { pm.expect(pm.response.headers.get('Content-Type')).to.include('application/pdf'); pm.expect(pm.response.stream.slice(0, 4).toString()).to.equal('%PDF'); });"];
  }
  const keys = JSON.stringify(shape.keys || []);
  const required = JSON.stringify(shape.required || (shape.keys || []).filter(key => key === "id"));
  const check = `function (item) { const documented = ${keys}; Object.keys(item).forEach(function (key) { pm.expect(documented, 'campo no documentado: ' + key).to.include(key); }); ${required}.forEach(function (key) { pm.expect(item, 'falta ' + key).to.have.property(key); }); }`;
  if (shape.type === "array") {
    return [`pm.test('Forma: lista con campos documentados', function () { const body = pm.response.json(); pm.expect(body).to.be.an('array'); body.forEach(${check}); });`];
  }
  return [`pm.test('Forma: objeto con campos documentados', function () { const body = pm.response.json(); pm.expect(body).to.be.an('object'); (${check})(body); });`];
}

const API_ERROR_KEYS = ["statusCode", "message", "details", "timestamp", "path"];

function errorTests(code, messagePart) {
  const lines = [
    statusTest(code),
    `pm.test('Error con forma ApiError', function () { const body = pm.response.json(); pm.expect(body).to.include.all.keys(${JSON.stringify(API_ERROR_KEYS).slice(1, -1)}); pm.expect(body.statusCode).to.equal(${code}); pm.expect(body.details).to.be.an('array'); });`
  ];
  if (messagePart) {
    lines.push(`pm.test('Mensaje: ${messagePart.replace(/'/g, "\\'")}', function () { pm.expect(pm.response.json().message).to.include(${JSON.stringify(messagePart)}); });`);
  }
  return lines;
}

function okTests(code, shape) {
  return [statusTest(code), ...shapeTests(shape)];
}

// Stores a response field (dotted path) in a run variable after a successful call.
function saveFromResponse(variable, field) {
  return [
    SET_VAR,
    `if (pm.response.code < 300) { const value = ${field.split(".").reduce((expr, part) => `${expr}[${JSON.stringify(part)}]`, "pm.response.json()")}; setVar(${JSON.stringify(variable)}, String(value)); }`
  ];
}

module.exports = {
  SET_VAR,
  bearer,
  errorTests,
  makeRequest,
  noAuth,
  okTests,
  rawJson,
  saveFromResponse,
  setJsonBody,
  shapeFromExample,
  shapeTests,
  statusTest
};
