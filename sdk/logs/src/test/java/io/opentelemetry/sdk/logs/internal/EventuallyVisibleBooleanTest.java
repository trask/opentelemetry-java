/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class EventuallyVisibleBooleanTest {

  @Test
  void initialState() {
    EventuallyVisibleBoolean state = new EventuallyVisibleBoolean(true);
    assertThat(state.get()).isTrue();

    EventuallyVisibleBoolean disabledState = new EventuallyVisibleBoolean(false);
    assertThat(disabledState.get()).isFalse();
  }

  @Test
  void immediateVisibility_sameThread() {
    EventuallyVisibleBoolean state = new EventuallyVisibleBoolean(true);
    assertThat(state.get()).isTrue();

    // Update state
    state.set(false);

    // Should be immediately visible to the updating thread
    assertThat(state.get()).isFalse();

    // Enable again
    state.set(true);
    assertThat(state.get()).isTrue();
  }

  @Test
  void eventualVisibility_multiThread() throws Exception {
    EventuallyVisibleBoolean state = new EventuallyVisibleBoolean(false);

    CountDownLatch startLatch = new CountDownLatch(1);

    Thread thread =
        new Thread(
            () -> {
              try {
                startLatch.await();
                state.set(true);
                // Sleep to keep thread alive - ensures no memory barrier semantics
                // are triggered by thread termination that could produce false positives
                Thread.sleep(5000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    thread.setDaemon(true);

    assertThat(state.get()).isFalse();

    thread.start();
    startLatch.countDown();

    // Sleep until thread has time execute state.set(true) - ensures ordering without
    // introducing memory barriers that could produce false positives
    Thread.sleep(1000);

    for (int i = 0; i < 200; i++) {
      state.get();
    }

    assertThat(state.get()).isTrue();
  }
}
