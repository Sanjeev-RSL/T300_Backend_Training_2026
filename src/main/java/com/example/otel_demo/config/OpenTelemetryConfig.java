package com.example.otel_demo.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfig {
    @Value("${otel.exporter.otlp.endpoint}")
    private String endpoint;

    @Value("${otel.exporter.otlp.auth:${otel.exporter.otlp.headers:}}")
    private String auth;
    @Value("${spring.application.name:otel-demo}")
    private String serviceName;

    @Bean
    public OpenTelemetry initialize() {
        String otlpEndpoint = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
        // -----------------------------
        // Trace Exporter
        // -----------------------------
        var spanExporterBuilder = OtlpHttpSpanExporter.builder()
                .setEndpoint(otlpEndpoint + "/v1/traces");
        // -----------------------------
        // Metric Exporter
        // -----------------------------
        var metricExporterBuilder = OtlpHttpMetricExporter.builder()
                .setEndpoint(otlpEndpoint + "/v1/metrics");
        // -----------------------------
        // Log Exporter
        // -----------------------------
        var logExporterBuilder = OtlpHttpLogRecordExporter.builder()
                .setEndpoint(otlpEndpoint + "/v1/logs");
        // -----------------------------
        // Authentication Header
        // -----------------------------
        if (auth != null && !auth.isBlank()) {
            String headerName = "Authorization";
            String headerValue = auth.trim();
            if (headerValue.startsWith("Authorization=")) {
                String[] parts = headerValue.split("=", 2);
                headerName = parts[0].trim();
                headerValue = parts[1].trim();
            } else if (!headerValue.regionMatches(true, 0, "Basic ", 0, 6)) {
                headerValue = "Basic " + headerValue;
            }
            spanExporterBuilder.addHeader(headerName, headerValue);
            metricExporterBuilder.addHeader(headerName, headerValue);

            logExporterBuilder.addHeader(headerName, headerValue);
        }
        var spanExporter = spanExporterBuilder.build();
        var metricExporter = metricExporterBuilder.build();
        var logExporter = logExporterBuilder.build();
        // -----------------------------
        // Resource
        // -----------------------------
        Resource resource = Resource.getDefault().merge(
                Resource.create(
                        Attributes.of(
                                AttributeKey.stringKey("service.name"),

                                serviceName)));

        // -----------------------------
        // Tracer Provider
        // -----------------------------
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(
                        BatchSpanProcessor.builder(spanExporter).build())

                .build();
        // -----------------------------
        // Meter Provider
        // -----------------------------
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(
                        PeriodicMetricReader.builder(metricExporter).build())

                .build();
        // -----------------------------
        // Logger Provider
        // -----------------------------
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(

                        BatchLogRecordProcessor.builder(logExporter).build())

                .build();
        // -----------------------------
        // OpenTelemetry SDK
        // -----------------------------
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()

                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setLoggerProvider(loggerProvider)
                .build();
        GlobalOpenTelemetry.set(sdk);
        System.out.println(
                "[OpenTelemetryConfig] Initialized OpenTelemetry " +
                        "(Traces + Metrics + Logs) -> " +
                        endpoint +
                        " (service.name=" + serviceName + ")");
        return sdk;
    }
}