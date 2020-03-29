# OTLP Example

This is a simple example that demonstrates how to use the OpenTelemetry SDK 
to instrument a simple application using OTLP as trace exporter. 

# How to run

## Prerequisites
* Java 1.8.231
* Docker 19.03
* OpenTelemetry Collector


## 1 - Compile 
```bash
gradlew fatJar
```
## 2 - Run OpenTelemetry Collector

Example configuration

```
receivers:
  otlp:
    endpoint: "localhost:55678"

exporters:
  logging:

processors:
  batch:
  queued_retry:

service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [logging]
      processors: [batch, queued_retry]
```

## 3 - Start the Application
```bash
java -cp build/libs/opentelemetry-example-otlp-all-0.3.0.jar io.opentelemetry.example.OtlpSpanExample localhost 55678
```

## 4 - Check docker logs for traces
