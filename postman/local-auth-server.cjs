// Local stand-in for the Supabase Auth endpoints the API uses, so the collection can run
// without a real Supabase project. It listens on 127.0.0.1 only, keeps everything in memory
// and signs tokens with an ES256 key generated at startup. Never point it at shared data.
const http = require("http");
const crypto = require("crypto");

const port = Number(process.env.AUTH_MOCK_PORT || 9998);
const issuer = `http://localhost:${port}/auth/v1`;
const kid = crypto.randomUUID();
const { privateKey, publicKey } = crypto.generateKeyPairSync("ec", { namedCurve: "P-256" });
const jwk = { ...publicKey.export({ format: "jwk" }), kid, alg: "ES256", use: "sig" };

const usersByEmail = new Map();
const linkTokens = new Map();

function base64url(value) {
  return Buffer.from(value).toString("base64url");
}

function ensureUser(email) {
  const key = String(email || "").trim().toLowerCase();
  if (!usersByEmail.has(key)) {
    usersByEmail.set(key, { id: crypto.randomUUID(), email: key, password: null });
  }
  return usersByEmail.get(key);
}

function findUserById(id) {
  return [...usersByEmail.values()].find(user => user.id === id);
}

function accessToken(user) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "ES256", typ: "JWT", kid }));
  const payload = base64url(JSON.stringify({
    iss: issuer,
    sub: user.id,
    aud: "authenticated",
    email: user.email,
    role: "authenticated",
    iat: now,
    exp: now + 3600
  }));
  const signature = crypto.sign("sha256", Buffer.from(`${header}.${payload}`), { key: privateKey, dsaEncoding: "ieee-p1363" });
  return `${header}.${payload}.${base64url(signature)}`;
}

function session(user) {
  return {
    access_token: accessToken(user),
    token_type: "bearer",
    expires_in: 3600,
    expires_at: Math.floor(Date.now() / 1000) + 3600,
    user: { id: user.id, email: user.email }
  };
}

function send(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(body === undefined ? "" : JSON.stringify(body));
}

function readJson(req) {
  return new Promise(resolve => {
    let raw = "";
    req.on("data", chunk => { raw += chunk; });
    req.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (error) {
        resolve({});
      }
    });
  });
}

function hasServerKey(req) {
  return Boolean(req.headers.apikey);
}

async function handle(req, res) {
  const url = new URL(req.url, `http://localhost:${port}`);
  const route = `${req.method} ${url.pathname}`;

  if (route === "GET /auth/v1/.well-known/jwks.json") {
    return send(res, 200, { keys: [jwk] });
  }

  // Test helper, not part of Supabase: issues a token for a directory email.
  if (route === "POST /mock/token") {
    const body = await readJson(req);
    if (!body.email) {
      return send(res, 400, { error_code: "validation_failed" });
    }
    return send(res, 200, session(ensureUser(body.email)));
  }

  if (!hasServerKey(req)) {
    return send(res, 401, { error_code: "no_authorization" });
  }

  if (route === "POST /auth/v1/token" && url.searchParams.get("grant_type") === "password") {
    const body = await readJson(req);
    const user = usersByEmail.get(String(body.email || "").toLowerCase());
    if (!user || !user.password || user.password !== body.password) {
      return send(res, 400, { error_code: "invalid_credentials" });
    }
    return send(res, 200, session(user));
  }

  if (route === "POST /auth/v1/logout") {
    return send(res, 204);
  }

  if (route === "POST /auth/v1/admin/generate_link") {
    const body = await readJson(req);
    const email = String(body.email || "").toLowerCase();
    if (body.type === "invite" && usersByEmail.has(email)) {
      return send(res, 422, { error_code: "email_exists" });
    }
    if (body.type === "recovery" && !usersByEmail.has(email)) {
      return send(res, 404, { error_code: "user_not_found" });
    }
    const user = ensureUser(email);
    const hashedToken = crypto.randomBytes(24).toString("hex");
    linkTokens.set(hashedToken, user.id);
    return send(res, 200, { id: user.id, email: user.email, hashed_token: hashedToken, verification_type: body.type });
  }

  if (route === "POST /auth/v1/verify") {
    const body = await readJson(req);
    const userId = linkTokens.get(body.token_hash);
    if (!userId) {
      return send(res, 403, { error_code: "otp_expired" });
    }
    linkTokens.delete(body.token_hash);
    return send(res, 200, session(findUserById(userId)));
  }

  const adminUser = url.pathname.match(/^\/auth\/v1\/admin\/users\/([0-9a-f-]{36})$/);
  if (req.method === "PUT" && adminUser) {
    const user = findUserById(adminUser[1]);
    if (!user) {
      return send(res, 404, { error_code: "user_not_found" });
    }
    const body = await readJson(req);
    if (typeof body.password === "string") {
      if (body.password.length < 10) {
        return send(res, 422, { error_code: "weak_password" });
      }
      user.password = body.password;
    }
    return send(res, 200, { id: user.id, email: user.email });
  }

  return send(res, 404, { error_code: "not_found" });
}

http.createServer((req, res) => {
  handle(req, res).catch(() => send(res, 500, { error_code: "unexpected_failure" }));
}).listen(port, "127.0.0.1", () => {
  console.log(`Local auth server on http://localhost:${port} (issuer ${issuer})`);
});
