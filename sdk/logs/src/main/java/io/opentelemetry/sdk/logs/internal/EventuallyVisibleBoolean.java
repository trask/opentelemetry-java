/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class EventuallyVisibleBoolean {

  private volatile boolean state;

  public EventuallyVisibleBoolean(boolean state) {
    this.state = state;
  }

  public boolean get() {
    return state;
  }

  public void set(boolean state) {
    this.state = state;
  }
}
