// A minimal "backend" HTTP server for the load-balancing/rate-limiting
// assignment. This file is complete — you don't need to change anything
// in here. Run two copies of it on two different ports to act as your
// two backend instances.
//
// Usage:
//   PORT=4001 node backend.js
//   PORT=4002 node backend.js   (in a second terminal)

const http = require("http");

const PORT = process.env.PORT || 4001;

const server = http.createServer((req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok", port: PORT }));
    return;
  }

  // Log every request so you can see distribution across backends
  // in your terminal while testing.
  console.log(`[backend:${PORT}] ${req.method} ${req.url}`);

  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(
    JSON.stringify({
      message: "hello from backend",
      port: Number(PORT),
      timestamp: new Date().toISOString(),
    })
  );
});

server.listen(PORT, () => {
  console.log(`Backend listening on http://localhost:${PORT}`);
});