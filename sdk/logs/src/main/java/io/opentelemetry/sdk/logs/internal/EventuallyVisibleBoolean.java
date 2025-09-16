/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs.internal;

/**
 * Handles boolean state checking with eventual visibility guarantees.
 *
 * <p>This class provides a performant way to check a boolean state while ensuring that state
 * changes are eventually visible across threads without requiring volatile reads on every call.
 *
 * <p>The implementation uses a periodic volatile check pattern where most reads access a cached
 * value, and every N reads perform a volatile check to refresh the cache with any state changes.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class EventuallyVisibleBoolean {

  private volatile boolean stateVolatile;
  private boolean stateCached;

  private int readCounter = 0;

  // Check volatile field every 100 reads
  private static final int REFRESH_READ_COUNT = 100;

  public EventuallyVisibleBoolean(boolean initialState) {
    this.stateVolatile = initialState;
    this.stateCached = initialState;
  }

  public boolean get() {
    if (++readCounter >= REFRESH_READ_COUNT) {
      // Reset counter and use read volatile field for memory visibility
      readCounter = 0;
      stateCached = stateVolatile;
    }
    return stateCached;
  }

  public void set(boolean newState) {
    // Update volatile field for inter-thread communication
    this.stateVolatile = newState;
    // Update cached value for immediate visibility in this thread
    this.stateCached = newState;
  }
}
