# OpenTelemetry Practical Assessment CRUD Application

> **Note**: The build, execution, and telemetry verification steps documented below were performed on **Windows (PowerShell)** using native `curl.exe`.

This repository contains a Spring Boot CRUD application instrumented with **OpenTelemetry (OTel)** using a combination of **Automatic Instrumentation** (via OpenTelemetry Java Agent) and **Manual Instrumentation** (via OpenTelemetry API/SDK for spans, metrics, and correlated logs).

---

## 1. Prerequisites & Setup

### Prerequisites
- **Java**: JDK 17 or higher (`java -version`)
- **Maven**: 3.8+ (`mvn -v`)
- **OpenTelemetry Java Agent**: `opentelemetry-javaagent.jar` (v2.0.0+) placed in the project directory.

### Build Application
From the `opentelemetry-assignment` directory:
```powershell
mvn clean package -DskipTests
```
This produces `target/observability-skeleton-0.0.1-SNAPSHOT.jar`.

---

## 2. Running with OpenTelemetry Java Agent

The application uses `otel.properties` to configure telemetry export and suppress extraneous background noise (such as internal JDBC/HikariCP queries and JVM runtime metrics), focusing purely on business transactions.

```powershell
$env:OTEL_JAVAAGENT_CONFIGURATION_FILE="otel.properties"
java -javaagent:opentelemetry-javaagent.jar -jar target/observability-skeleton-0.0.1-SNAPSHOT.jar
```

### Configuration (`otel.properties`)
```properties
otel.service.name=crud-otel-application
otel.traces.exporter=logging
otel.metrics.exporter=none
otel.logs.exporter=none

# Disable background noise
otel.instrumentation.jdbc.enabled=false
otel.instrumentation.hibernate.enabled=false
otel.instrumentation.spring-data.enabled=false
otel.instrumentation.hikaricp.enabled=false
otel.instrumentation.runtime-telemetry.enabled=false
```

---

## 3. Traffic Generation & Verification (PowerShell)

Use the following `curl.exe` commands in PowerShell to generate traffic and verify telemetry:

### A. Success Scenarios

1. **Create User (POST /users)**
   ```powershell
   $body = @'
   {
     "name": "David Warner",
     "email": "david@test.com"
   }
   '@

   $body | curl.exe -i -X POST http://localhost:8080/users `
     -H "Content-Type: application/json" `
     -d "@-"
   ```
   - *Expected Response*: `HTTP/1.1 201 Created`
   - *Telemetry*: Auto Tomcat span + Manual `create-user` span + Metric increment `{op=create, status=success}`.

2. **Get User (GET /users/1)**
   ```powershell
   curl.exe -i `
     -X GET `
     http://localhost:8080/users/1
   ```
   - *Expected Response*: `HTTP/1.1 200 OK`
   - *Telemetry*: Automatic HTTP span ONLY (`Child Span ID : NONE`, per specification).

3. **Delete User (DELETE /users/1)**
   ```powershell
   curl.exe -i `
     -X DELETE `
     http://localhost:8080/users/1
   ```
   - *Expected Response*: `HTTP/1.1 204 No Content`
   - *Telemetry*: Auto Tomcat span + Manual nested `delete-user` child span + Metric increment `{op=delete, status=success}`.

### B. Failure Scenarios

1. **Delete Non-existent User (DELETE /users/999)**
   ```powershell
   curl.exe -i `
     -X DELETE `
     http://localhost:8080/users/999
   ```
   - *Expected Response*: `HTTP/1.1 404 Not Found`
   - *Telemetry*: Span marked with `StatusCode.ERROR`, `UserNotFoundException` recorded, Metric increment `{op=delete, status=error}`.

---

## 4. Where Telemetry Is Viewed

All telemetry is emitted directly to the application's standard output / terminal console:

1. **Traces**:
   - The Java Agent's `LoggingSpanExporter` outputs the completed spans with trace ID, span ID, and attributes.
   - The application prints a structured telemetry summary card displaying the correlation between the **Parent Auto Span** and **Child Manual Span**.
2. **Metrics**:
   - Counter (`users.operation.count`) with dimensions `operation` and `status`.
   - Histogram (`users.operation.duration`) with dimension `operation` measuring execution latency in milliseconds.
3. **Logs**:
   - Logback MDC automatically captures `trace_id` and `span_id` injected by the OTel Java Agent.
   - High-visibility formatted logs with timestamps, status, duration, and user ID.

---

## 5. Automatic vs. Manual Instrumentation

| Feature | Automatic Instrumentation | Manual Instrumentation |
|---|---|---|
| **Mechanism** | OpenTelemetry Java Agent (`opentelemetry-javaagent.jar`) attached via `-javaagent`. | OpenTelemetry API (`Tracer`, `Meter`, `Span`, `Scope`) coded in `UserService.java`. |
| **Tracer Name** | `io.opentelemetry.tomcat` / `io.opentelemetry.spring-webmvc` | `com.example.observability` |
| **Responsibility** | Captures incoming HTTP server requests, HTTP status codes, method, URL, and initializes MDC context (`trace_id`, `span_id`). | Captures domain-specific business operations (`create-user`, `delete-user`), business attributes (`operation.name`, `user.id`), error tracking, and custom metrics. |
| **Span Hierarchy** | Acts as the **Parent Span** (e.g., `HTTP DELETE /users/{id}`). | Acts as the **Child Span** nested inside the active HTTP context. |
| **Endpoint Coverage** | All endpoints (`POST`, `GET`, `DELETE`). | Business logic spans on `POST /users` and `DELETE /users/{id}`; `GET /users/{id}` intentionally uses automatic HTTP span only. |

---

## 6. Troubleshooting Q&A (Failed Request Analysis)

Based on the execution of failed request:
```powershell
curl.exe -i `
  -X DELETE `
  http://localhost:8080/users/999
```

### Q1: Which operation failed?
**Answer**: The `delete` operation failed.

### Q2: Where did it fail?
**Answer**: It failed inside `UserService.deleteUser(Long id)` when verifying user existence:
```java
User user = userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
```
Because user `999` does not exist in the H2 in-memory database, `userRepository.findById(999)` returned an empty `Optional`, throwing a `UserNotFoundException`.

### Q3: How is the error represented in the trace?
**Answer**:
1. The manual span `delete-user` status was explicitly set to error: `span.setStatus(StatusCode.ERROR, e.getMessage())`.
2. The exception was recorded directly into the span events via `span.recordException(e)`.
3. In the console trace exporter, the span is tagged with `otel.status_code=ERROR` and contains the exception stack trace and error message `"User not found with id: 999"`.

### Q4: Which log belongs to the trace?
**Answer**: The error log line emitted by `UserService.deleteUser()` that shares the exact same `trace_id` in its MDC context:
- **Trace ID**: `1d8babfc76765d4c9ae4f489d80a216a`
- **Span ID**: `a387995f75d32f0a` (child span `delete-user`)
- **Log Message**: `[trace_id=1d8babfc76765d4c9ae4f489d80a216a span_id=a387995f75d32f0a] ... operation=delete failed: User not found with id: 999`

### Q5: How is it reflected in the metrics?
**Answer**:
1. The counter `users.operation.count` was incremented with attributes `{operation="delete", status="error"}`:
   ```java
   operationCount.add(1, Attributes.of(
       AttributeKey.stringKey("operation"), "delete",
       AttributeKey.stringKey("status"), "error"
   ));
   ```
2. The histogram `users.operation.duration` recorded the latency of the failed operation (`5 ms`) with `{operation="delete"}`. Note: `user.id` is strictly excluded from metrics to prevent high cardinality.