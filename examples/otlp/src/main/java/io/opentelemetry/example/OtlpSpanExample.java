package io.opentelemetry.example;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class OtlpSpanExample {
  // OTLP Endpoint URL and PORT
  private String ip; // = "collector";
  private int port; // = 55678;

  // OTel API
  private Tracer tracer =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.example.OtlpSpanExample");
  // Export traces to OTLP
  private OtlpGrpcSpanExporter otlpExporter;

  public OtlpSpanExample(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  private void setupOtlpExporter() {
    // Create a channel towards Jaeger end point
    ManagedChannel otlpChannel = ManagedChannelBuilder.forAddress(ip, port).usePlaintext().build();
    // Export traces to OTLP
    this.otlpExporter =
        OtlpGrpcSpanExporter.newBuilder()
            .setChannel(otlpChannel)
            .setDeadlineMs(30000)
            .build();

    // Set to process the spans by the OTLP Exporter
    OpenTelemetrySdk.getTracerProvider()
        .addSpanProcessor(SimpleSpansProcessor.newBuilder(this.otlpExporter).build());
  }

  private void myWonderfulUseCase() {
    // Generate a span
    Span span = this.tracer.spanBuilder("Start my wonderful use case").startSpan();
    span.addEvent("Event 0");
    // execute my use case - here we simulate a wait
    doWork();
    span.addEvent("Event 1");
    span.end();
  }

  private void doWork() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
    }
  }

  public static void main(String[] args) {
    // Parsing the input
    if (args.length < 2) {
      System.out.println("Missing [hostname] [port]");
      System.exit(1);
    }
    String ip = args[0];
    int port = Integer.parseInt(args[1]);

    // Start the example
    OtlpSpanExample example = new OtlpSpanExample(ip, port);
    example.setupOtlpExporter();
    example.myWonderfulUseCase();
    // wait some seconds
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
    }
    System.out.println("Bye");
  }
}
