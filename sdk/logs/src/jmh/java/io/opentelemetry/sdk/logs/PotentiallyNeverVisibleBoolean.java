/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

public class PotentiallyNeverVisibleBoolean {

  private boolean state;

  public PotentiallyNeverVisibleBoolean(boolean state) {
    this.state = state;
  }

  public boolean get() {
    return state;
  }

  public void set(boolean state) {
    this.state = state;
  }
}
