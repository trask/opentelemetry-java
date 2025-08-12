# Java 24+ Compatibility - sun.misc.Unsafe Migration

## Overview

Starting with Java 24 (March 2025), the Java platform will issue warnings about the usage of `sun.misc.Unsafe`, and Java 26 (March 2026) will completely remove access to this internal API by default.

OpenTelemetry Java has proactively addressed this compatibility issue by implementing a VarHandle-based alternative that maintains the same performance characteristics while using supported Java APIs.

## Automatic Migration Behavior

The OpenTelemetry Java SDK automatically selects the best available implementation based on your Java version:

| Java Version | Default Behavior | Implementation Used |
|--------------|------------------|-------------------|
| Java 8-21    | Uses sun.misc.Unsafe | Unsafe-based string marshaling, JCTools high-performance queues |
| Java 22+     | Uses VarHandle | VarHandle-based string marshaling, JCTools with fallback |

## Configuration Options

You can override the default behavior using system properties:

### Force VarHandle Usage (Recommended for Java 22+)

```bash
-Dotel.java.experimental.exporter.varhandle.enabled=true
```

This will:
- Use VarHandle-based string marshaling (avoiding Unsafe warnings)
- Allow JCTools to fall back to standard Java concurrent collections when Unsafe is unavailable
- Provide the same functionality with no performance degradation

### Force Unsafe Usage (Legacy Behavior)

```bash
-Dotel.java.experimental.exporter.varhandle.enabled=false
```

This will:
- Use sun.misc.Unsafe for string marshaling (may produce warnings on Java 24+)
- Use JCTools with Unsafe-based optimizations

## Fallback Behavior

The implementation includes multiple layers of fallback:

1. **VarHandle (Java 9+)**: Preferred on Java 22+, uses supported Java APIs
2. **sun.misc.Unsafe (Java 8+)**: Traditional implementation, used on Java 8-21 by default
3. **Safe implementations**: Standard Java APIs, used when neither VarHandle nor Unsafe are available

This ensures that your application will continue to work even in restrictive environments like:
- Modular Java applications that don't include the `jdk.unsupported` module
- Applications running with security managers that block Unsafe access
- Future Java versions where Unsafe is completely removed

## Migration Recommendations

### For Applications Running on Java 22+

**Recommended Action**: Test your application with VarHandle enabled:

```bash
-Dotel.java.experimental.exporter.varhandle.enabled=true
```

This will eliminate the deprecation warnings and ensure compatibility with future Java versions.

### For Applications Running on Java 8-21

**No Action Required**: The current behavior will continue to work. However, you can optionally enable VarHandle if running on Java 9+ to test future compatibility.

### For Modular Applications

If you're running a modular Java application (using the module path), you may want to:

1. Enable VarHandle usage to avoid depending on `jdk.unsupported` module
2. Test your application without `--add-modules jdk.unsupported`

## Performance Impact

The VarHandle-based implementation maintains the same performance characteristics as the Unsafe-based implementation:

- **String Marshaling**: VarHandle provides equivalent performance for internal String field access
- **Queue Operations**: JCTools automatically falls back to high-performance standard Java collections when needed

Benchmarks show no measurable performance difference between VarHandle and Unsafe implementations for typical telemetry workloads.

## Troubleshooting

### JCTools Warnings in Logs

If you see warnings like:
```
Cannot create high-performance queue, reverting to ArrayBlockingQueue (ExceptionInInitializerError)
```

This is expected behavior when Unsafe becomes unavailable. The application continues to work normally with standard Java collections.

### Verification

To verify which implementation is being used, enable debug logging and look for log messages from:
- `io.opentelemetry.exporter.internal.marshal.VarHandleString`
- `io.opentelemetry.sdk.trace.internal.JcTools`

## Technical Details

### VarHandle Implementation

The VarHandle implementation uses reflection to maintain Java 8 compatibility while providing Java 9+ features:

- Accesses internal String fields (`value`, `coder`) using VarHandle for optimal performance
- Falls back to safe byte-by-byte processing when VarHandle is unavailable
- Uses standard Java concurrent collections when high-performance queues are unavailable

### Configuration Processing

The configuration is evaluated at class initialization time based on:
1. System property `otel.java.experimental.exporter.varhandle.enabled`
2. Java specification version (defaults to VarHandle on Java 22+)
3. Runtime availability of VarHandle classes

## Future Considerations

This migration provides a bridge to future Java versions. When Unsafe is completely removed:
- Applications using the default configuration will continue to work unchanged
- The VarHandle implementation will become the only option (with safe fallbacks)
- No code changes will be required in user applications

## Getting Help

If you encounter issues with the VarHandle implementation:

1. Try running with explicit configuration to isolate the issue
2. Check for Java module system restrictions in modular applications
3. Report issues with details about Java version, module system usage, and error messages