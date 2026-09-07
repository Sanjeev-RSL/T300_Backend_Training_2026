package com.example.otel_demo.controller;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	private static final Logger logger = LoggerFactory.getLogger(HelloController.class);
	private final Tracer tracer;
	private final LongCounter requestCounter;

	public HelloController(OpenTelemetry openTelemetry) {
		this.tracer = openTelemetry.getTracer("otel-demo");
		Meter meter = openTelemetry.getMeter("otel-demo");
		this.requestCounter = meter.counterBuilder("http.requests.total")
				.setDescription("Total number of HTTP requests")
				.setUnit("requests")
				.build();

	}

	@GetMapping("/hello")
	public String hello() {
		var span = tracer.spanBuilder("hello-span").startSpan();
		try {
			// ---------------------------
			// TRACE
			// ---------------------------
			span.setAttribute("user.id", 1001);
			span.setAttribute("user.role", "admin");
			span.setAttribute("environment", "development");
			span.addEvent("Request Started");
			// ---------------------------
			// METRIC
			// ---------------------------
			requestCounter.add(1);
			// ---------------------------
			// LOG
			// ---------------------------
			logger.info("Processing /hello request");
			span.setStatus(StatusCode.OK);
			return "Hello OpenTelemetry!";
		} catch (Exception ex) {
			logger.error("Error while processing request", ex);
			span.recordException(ex);
			span.setStatus(StatusCode.ERROR);
			throw ex;
		} finally {
			span.end();
		}
	}
}