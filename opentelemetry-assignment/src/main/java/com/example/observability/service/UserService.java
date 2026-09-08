package com.example.observability.service;

import com.example.observability.dto.UserRequest;
import com.example.observability.dto.UserResponse;
import com.example.observability.entity.User;
import com.example.observability.exception.DuplicateEmailException;
import com.example.observability.exception.UserNotFoundException;
import com.example.observability.repository.UserRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service layer that contains business logic for User operations.
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String SERVICE_NAME = "crud-otel-application";

    private final UserRepository userRepository;
    private final Tracer tracer;
    private final LongCounter operationCounter;
    private final DoubleHistogram operationDuration;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.tracer = GlobalOpenTelemetry.getTracer("com.example.observability");
        Meter meter = GlobalOpenTelemetry.getMeter("com.example.observability");

        this.operationCounter = meter.counterBuilder("users.operation.count")
                .setDescription("Total count of user operations")
                .setUnit("operations")
                .build();

        this.operationDuration = meter.histogramBuilder("users.operation.duration")
                .setDescription("Duration of user operations")
                .setUnit("ms")
                .build();
    }

    /**
     * Create a new user.
     *
     * @param request request containing name and email
     * @return created user response
     */
    @Transactional
    public UserResponse createUser(UserRequest request) {
        logger.info("operation=create started");
        long startTime = System.currentTimeMillis();

        // Capture automatic HTTP parent span created by OpenTelemetry Java Agent
        Span parentSpan = Span.current();
        String parentSpanId = parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getSpanId() : "none";

        // Create manual business span for service layer operation
        Span span = tracer.spanBuilder("create-user").startSpan();
        String status = "SUCCESS";
        Long createdUserId = null;

        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("operation.name", "create");

            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateEmailException("Email already exists: " + request.getEmail());
            }

            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());

            User saved = userRepository.save(user);
            createdUserId = saved.getId();
            span.setAttribute("user.id", saved.getId());
            span.setStatus(StatusCode.OK);

            logger.info("operation=create succeeded user.id={}", saved.getId());
            operationCounter.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "create",
                    AttributeKey.stringKey("status"), "success"
            ));

            return toResponse(saved);
        } catch (Exception e) {
            status = "ERROR (" + e.getMessage() + ")";
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());

            logger.error("operation=create failed error={}", e.getMessage());
            operationCounter.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "create",
                    AttributeKey.stringKey("status"), "error"
            ));

            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            operationDuration.record(duration, Attributes.of(
                    AttributeKey.stringKey("operation"), "create"
            ));
            String traceId = span.getSpanContext().getTraceId();
            String spanId = span.getSpanContext().getSpanId();
            span.end();

            printCard("create", status, createdUserId, duration, traceId, spanId, parentSpanId);
        }
    }

    /**
     * Retrieve a user by id.
     *
     * @param id user id
     * @return user response
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        logger.info("operation=get started user.id={}", id);
        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";

        // Automatic HTTP server span only (no manual service span per assignment rules)
        Span currentSpan = Span.current();
        String traceId = currentSpan.getSpanContext().isValid() ? currentSpan.getSpanContext().getTraceId() : "none";
        String spanId = currentSpan.getSpanContext().isValid() ? currentSpan.getSpanContext().getSpanId() : "none";

        try {
            User user = userRepository.findById(id).orElseThrow(() -> {
                logger.error("operation=get failed user.id={}", id);
                return new UserNotFoundException("User not found with id: " + id);
            });

            logger.info("operation=get succeeded user.id={}", id);
            operationCounter.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "get",
                    AttributeKey.stringKey("status"), "success"
            ));
            return toResponse(user);
        } catch (Exception e) {
            status = "ERROR (" + e.getMessage() + ")";
            operationCounter.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "get",
                    AttributeKey.stringKey("status"), "error"
            ));
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            operationDuration.record(duration, Attributes.of(
                    AttributeKey.stringKey("operation"), "get"
            ));
            printCard("get", status, id, duration, traceId, spanId, spanId);
        }
    }

    /**
     * Delete a user by id.
     *
     * @param id user id
     */
    @Transactional
    public void deleteUser(Long id) {
        logger.info("operation=delete started user.id={}", id);
        long startTime = System.currentTimeMillis();

        // Capture automatic HTTP parent span created by OpenTelemetry Java Agent
        Span parentSpan = Span.current();
        String parentSpanId = parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getSpanId() : "none";

        // Create manual business span for service layer operation
        Span span = tracer.spanBuilder("delete-user").startSpan();
        String status = "SUCCESS";

        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("operation.name", "delete");
            span.setAttribute("user.id", id);

            User user = userRepository.findById(id).orElseThrow(() -> {
                logger.error("operation=delete failed user.id={} error=User not found with id: {}", id, id);
                return new UserNotFoundException("User not found with id: " + id);
            });

            userRepository.delete(user);
            span.setStatus(StatusCode.OK);

            logger.info("operation=delete succeeded user.id={}", id);
            operationCounter.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "delete",
                    AttributeKey.stringKey("status"), "success"
            ));
        } catch (Exception e) {
            status = "ERROR (" + e.getMessage() + ")";
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());

            operationCounter.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "delete",
                    AttributeKey.stringKey("status"), "error"
            ));

            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            operationDuration.record(duration, Attributes.of(
                    AttributeKey.stringKey("operation"), "delete"
            ));
            String traceId = span.getSpanContext().getTraceId();
            String spanId = span.getSpanContext().getSpanId();
            span.end();

            printCard("delete", status, id, duration, traceId, spanId, parentSpanId);
        }
    }

    private void printCard(String operation, String status, Long userId, long durationMs, String traceId, String spanId, String parentId) {
        String counterStatus = status.startsWith("SUCCESS") ? "success" : "error";
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String border = "+---------------------------------------------------------------------------------+";
        String header1 = "|                            [TELEMETRY: TRACE & LOG]                             |";
        String header2 = "|                            [TELEMETRY: METRICS]                                 |";

        String hierarchy = operation.equalsIgnoreCase("get")
                ? "HTTP GET [AUTO] (pure automatic instrumentation)"
                : "HTTP " + operation.toUpperCase() + " [AUTO] --> " + operation + "-user [MANUAL]";

        String traceDisplay = operation.equalsIgnoreCase("get")
                ? traceId + " [AUTO]"
                : traceId + " (SHARED BY AUTO & MANUAL)";

        String parentDisplay = parentId + " [AUTO]   (tracer: io.opentelemetry.tomcat)";

        String childDisplay = operation.equalsIgnoreCase("get")
                ? "NONE"
                : spanId + " [MANUAL] (tracer: com.example.observability)";

        String card = String.format("\n%s\n%s\n%s\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "%s\n%s\n%s\n" +
                        "| %-14s : %-62s |\n" +
                        "| %-14s : %-62s |\n" +
                        "%s",
                border, header1, border,
                "Timestamp", timestamp,
                "Service Name", SERVICE_NAME,
                "Trace ID", traceDisplay,
                "Hierarchy", hierarchy,
                "Parent Span ID", parentDisplay,
                "Child Span ID", childDisplay,
                "Operation", operation,
                "Status", status,
                "User ID", userId != null ? String.valueOf(userId) : "N/A",
                "Duration", durationMs + " ms",
                border, header2, border,
                "Counter", "users.operation.count    {op=" + operation + ", status=" + counterStatus + "}",
                "Histogram", "users.operation.duration {op=" + operation + "} = " + durationMs + " ms",
                border);

        logger.info(card);
    }

    /**
     * Map entity to response DTO.
     */
    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}