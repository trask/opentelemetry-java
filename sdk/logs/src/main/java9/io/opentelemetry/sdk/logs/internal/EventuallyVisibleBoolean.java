/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs.internal;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
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

  public EventuallyVisibleBoolean(boolean initialState) {
    STATE_HANDLE.setRelease(this, initialState);
  }

  public boolean get() {
    return (boolean) STATE_HANDLE.getAcquire(this);
  }

  public void set(boolean newState) {
    STATE_HANDLE.setRelease(this, newState);
  }
}
