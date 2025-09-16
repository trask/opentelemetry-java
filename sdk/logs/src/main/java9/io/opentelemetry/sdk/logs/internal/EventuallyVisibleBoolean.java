/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs.internal;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Java 9+ implementation of EventuallyVisibleBoolean using VarHandle.
 *
 * <p>This implementation uses VarHandle with opaque/acquire access modes to provide eventual
 * visibility with lighter memory barriers than volatile operations.
 *
 * <ul>
 *   <li>Most reads use {@link VarHandle#getOpaque(Object...)} (no memory barriers).
 *   <li>Every Nth read uses {@link VarHandle#getAcquire(Object...)} (acquire barrier only, lighter
 *       than volatile).
 *   <li>Writes use {@link VarHandle#setRelease(Object...)} (release barrier, pairs with acquire)
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class EventuallyVisibleBoolean {

  private static final VarHandle STATE_HANDLE;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      STATE_HANDLE = lookup.findVarHandle(EventuallyVisibleBoolean.class, "state", boolean.class);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @SuppressWarnings("UnusedVariable") // Used by VarHandle
  private boolean state;

  private int readCounter = 0;

  // Check with acquire semantics every 100 reads
  private static final int REFRESH_READ_COUNT = 100;

  public EventuallyVisibleBoolean(boolean initialState) {
    STATE_HANDLE.setRelease(this, initialState);
  }

  public boolean get() {
    if (++readCounter >= REFRESH_READ_COUNT) {
      // Reset counter and use acquire semantics for memory visibility
      readCounter = 0;
      return (boolean) STATE_HANDLE.getAcquire(this);
    }

    // Use opaque access - no memory barriers
    return (boolean) STATE_HANDLE.getOpaque(this);
  }

  public void set(boolean newState) {
    // Use release semantics to ensure visibility to acquire reads
    STATE_HANDLE.setRelease(this, newState);
  }
}
