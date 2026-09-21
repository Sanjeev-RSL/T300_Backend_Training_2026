// A skeleton HTTP reverse proxy. The server setup and request-forwarding
// plumbing below is already done for you. You need to implement:
//
//   1. pickBackend()        - round-robin backend selection
//   2. isAllowed(clientId)  - token bucket rate limiting
//        - burst capacity: 5 requests (bucket starts full, max capacity 5)
//        - refill rate: 1 request/second
//
// This starter kit is provided in Node.js (built-in http module only,
// nothing to install). If you'd rather use a different language/runtime
// (Python, Go, Java, etc.), that's fine too — build your own equivalent
// from scratch and proceed with the same task and deliverables described
// in the assignment.
//
// Run two backend instances first (in separate terminals):
//   PORT=4001 node backend.js
//   PORT=4002 node backend.js
//
// Then run this proxy:
//   node proxy.js
//
// And send it traffic:
//   curl http://localhost:3000/
//   for i in $(seq 1 20); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3000/; done

const http = require("http");

const PROXY_PORT = 3000;

const BACKENDS = ["http://localhost:4001", "http://localhost:4002"];

// ---------------------------------------------------------------------
// Round-robin backend selection
// ---------------------------------------------------------------------
let currentIndex = 0;

function pickBackend() {
  const backend = BACKENDS[currentIndex % BACKENDS.length];
  currentIndex++;
  return backend;
}

// ---------------------------------------------------------------------
// Token bucket rate limiting
// ---------------------------------------------------------------------
// Burst capacity: 5 requests per client (bucket starts full, max 5).
// Refill rate: 1 token/second. Returns true = allow, false = block (429).
const buckets = new Map();
const BUCKET_CAPACITY = 5;
const REFILL_RATE = 1; // tokens per second

function isAllowed(clientId) {
  const now = Date.now();

  if (!buckets.has(clientId)) {
    // New client — start with a full bucket
    buckets.set(clientId, { tokens: BUCKET_CAPACITY, lastRefill: now });
  }

  const bucket = buckets.get(clientId);

  // Refill tokens proportional to elapsed time
  const elapsed = (now - bucket.lastRefill) / 1000; // convert ms -> seconds
  bucket.tokens = Math.min(BUCKET_CAPACITY, bucket.tokens + elapsed * REFILL_RATE);
  bucket.lastRefill = now;

  if (bucket.tokens >= 1) {
    bucket.tokens -= 1;
    return true;  // request allowed
  }
  return false;   // bucket empty — block with 429
}

// ---------------------------------------------------------------------
// Request handling / forwarding — this part is done for you.
// ---------------------------------------------------------------------

function getClientId(req) {
  // Good enough for local testing. In a real system you might use an
  // API key or authenticated user ID instead of IP.
  return req.socket.remoteAddress || "unknown";
}

function forwardRequest(targetBaseUrl, req, res) {
  const target = new URL(req.url, targetBaseUrl);

  const proxyReq = http.request(
    {
      hostname: target.hostname,
      port: target.port,
      path: target.pathname + target.search,
      method: req.method,
      headers: req.headers,
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode, proxyRes.headers);
      proxyRes.pipe(res);
    }
  );

  proxyReq.on("error", (err) => {
    console.error(`Error forwarding to ${targetBaseUrl}:`, err.message);
    if (!res.headersSent) {
      res.writeHead(502, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "bad_gateway", detail: err.message }));
    }
  });

  req.pipe(proxyReq);
}

const server = http.createServer((req, res) => {
  const clientId = getClientId(req);

  let allowed;
  try {
    allowed = isAllowed(clientId);
  } catch (err) {
    // isAllowed() not implemented yet — surface it clearly instead of
    // silently proxying every request.
    res.writeHead(500, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: err.message }));
    return;
  }

  if (!allowed) {
    res.writeHead(429, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "rate_limit_exceeded" }));
    return;
  }

  let backend;
  try {
    backend = pickBackend();
  } catch (err) {
    res.writeHead(500, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: err.message }));
    return;
  }

  console.log(`[proxy] ${req.method} ${req.url} -> ${backend} (client: ${clientId})`);
  forwardRequest(backend, req, res);
});

server.listen(PROXY_PORT, () => {
  console.log(`Proxy listening on http://localhost:${PROXY_PORT}`);
  console.log(`Forwarding to: ${BACKENDS.join(", ")}`);
});