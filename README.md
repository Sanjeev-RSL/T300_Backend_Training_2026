## Deliverables

Terminal screenshots are available in the [`images`](images) folder for this repo:

- [Server startup logs](images/server_startup_logs.png)
- [Round-robin and rate-limit responses](images/round_robin_rate_limit_responses.png)
- [Rate-limit status codes](images/rate_limit_status_codes.png)
- [Bucket refill response](images/bucket_refill_response.png)

## How to Run Locally

You need **3 separate Terminal windows or tabs**.

### Terminal 1 — Backend A (port 4001)

```zsh
PORT=4001 node backend.js
```

Expected output:
```
Backend listening on http://localhost:4001
[backend:4001] GET /
[backend:4001] GET /
[backend:4001] GET /
```

### Terminal 2 — Backend B (port 4002)

```zsh
PORT=4002 node backend.js
```

Expected output:
```
Backend listening on http://localhost:4002
[backend:4002] GET /
[backend:4002] GET /
[backend:4002] GET /
```

### Terminal 3 — Proxy (port 3000)

```zsh
node proxy.js
```

Expected output:
```
Proxy listening on http://localhost:3000
Forwarding to: http://localhost:4001, http://localhost:4002
[proxy] GET / -> http://localhost:4001 (client: ::1)
[proxy] GET / -> http://localhost:4002 (client: ::1)
[proxy] GET / -> http://localhost:4001 (client: ::1)
[proxy] GET / -> http://localhost:4002 (client: ::1)
[proxy] GET / -> http://localhost:4001 (client: ::1)
[proxy] GET / -> http://localhost:4002 (client: ::1)
```

All traffic should now go to `http://localhost:3000` — the proxy handles the rest.

---

## Testing

---

### Test A — Round-Robin Load Balancing

For a clean round-robin demonstration, restart the proxy first so its counter
starts at Backend 1. Then send 6 requests. The first five requests are allowed
and the sixth is rate-limited because the client bucket has been exhausted.
The startup screenshot shows the proxy alternating requests between ports 4001
and 4002.

```zsh
# Run this from a separate terminal after restarting the proxy:
for i in {1..6}; do curl -s http://localhost:3000/; echo; done
```

**Expected output after a clean proxy restart:**
```json
{"message":"hello from backend","port":4001,"timestamp":"2026-09-21T09:25:34.121Z"}
{"message":"hello from backend","port":4002,"timestamp":"2026-09-21T09:25:34.144Z"}
{"message":"hello from backend","port":4001,"timestamp":"2026-09-21T09:25:34.160Z"}
{"message":"hello from backend","port":4002,"timestamp":"2026-09-21T09:25:34.174Z"}
{"message":"hello from backend","port":4001,"timestamp":"2026-09-21T09:25:34.188Z"}
{"error":"rate_limit_exceeded"}
```

---

### Test B — Rate Limiting (HTTP 429)

Send 8 rapid requests. First 5 allowed; requests 6-8 blocked with HTTP 429:

```zsh
for i in {1..8}; do curl -s -o /dev/null -w "Request $i: HTTP %{http_code}\n" http://localhost:3000/; done
```

**Expected output:**
```
Request 1: HTTP 200
Request 2: HTTP 200
Request 3: HTTP 200
Request 4: HTTP 200
Request 5: HTTP 200
Request 6: HTTP 429
Request 7: HTTP 429
Request 8: HTTP 429
```

The 429 response body is:
```json
{"error":"rate_limit_exceeded"}
```

---

### Test C — Bucket Refill

After exhausting the bucket, wait 6 seconds and confirm requests are allowed again:

```zsh
sleep 6; curl -s -w "\nHTTP %{http_code}\n" http://localhost:3000/
```

**Expected output:**
```
{"message":"hello from backend","port":4001,"timestamp":"2026-09-21T09:29:11.266Z"}
HTTP 200
```

---

## Architecture

```
Client (curl)
     |
     v
+-------------------------+
|   Proxy  :3000          |
|                         |
|  isAllowed(clientId)?   |---- NO ---> HTTP 429
|         |               |
|        YES              |
|         |               |
|  pickBackend()          |
|  (round-robin)          |
+--------+----------------+
         |
    +----+-----+
    v          v
  :4001       :4002
Backend A   Backend B
```

---

## Key Concepts Demonstrated

| Concept | Implementation |
|---------|----------------|
| **Load Balancing** | Round-robin using a shared counter + modulo |
| **Rate Limiting** | Token bucket — tracks tokens per client IP |
| **Burst Handling** | Bucket starts full (5 tokens), allows initial burst |
| **Auto-Refill** | Elapsed time between requests re-adds tokens continuously |
| **Per-Client Isolation** | Each IP has its own independent Map entry |
