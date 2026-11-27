package com.sanigest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RemoteCallTest {
  @Test
  void opensAtTheFailureThresholdAndClosesAfterSuccessfulProbes() {
    var clock = mock(Clock.class);
    when(clock.instant()).thenReturn(Instant.EPOCH);
    var meters = new SimpleMeterRegistry();
    var calls =
        new RemoteCall(
            "source",
            Duration.ofSeconds(2),
            failure -> failure instanceof IllegalArgumentException,
            meters,
            clock);
    for (int call = 0; call < 10; call++) {
      assertThatThrownBy(() ->
              calls.execute(
                  () -> {
                    throw new IllegalArgumentException();
                  },
                  IllegalStateException::new))
          .isInstanceOf(IllegalArgumentException.class);
    }
    for (int call = 0; call < 5; call++) {
      assertThat(calls.execute(() -> "ok", IllegalStateException::new)).isEqualTo("ok");
      assertThatThrownBy(() ->
              calls.execute(
                  () -> {
                    throw new UnsupportedOperationException();
                  },
                  IllegalStateException::new))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    assertThatThrownBy(() -> calls.execute(() -> "ok", IllegalStateException::new))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            meters
                .get("resilience4j.circuitbreaker.not.permitted.calls")
                .tag("name", "source")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(state(meters, "open")).isEqualTo(1);

    when(clock.instant()).thenReturn(Instant.EPOCH.plusSeconds(31));
    assertThat(calls.execute(() -> "probe", IllegalStateException::new)).isEqualTo("probe");
    assertThat(state(meters, "half_open")).isEqualTo(1);
    for (int call = 0; call < 9; call++) {
      assertThat(calls.execute(() -> "ok", IllegalStateException::new)).isEqualTo("ok");
    }
    assertThat(state(meters, "closed")).isEqualTo(1);
  }

  @Test
  void cancelsSlowCallsAndRecordsTimeouts() throws InterruptedException {
    var meters = new SimpleMeterRegistry();
    var calls = new RemoteCall("source", Duration.ofMillis(200), failure -> false, meters);
    var cancelled = new CountDownLatch(1);
    assertThatThrownBy(() ->
            calls.execute(
                () -> {
                  try {
                    Thread.sleep(Duration.ofSeconds(10));
                    return "late";
                  } catch (InterruptedException interrupted) {
                    cancelled.countDown();
                    throw interrupted;
                  }
                },
                IllegalStateException::new))
        .isInstanceOf(IllegalStateException.class);
    assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(
            meters
                .get("resilience4j.timelimiter.calls")
                .tags("name", "source", "kind", "timeout")
                .counter()
                .count())
        .isEqualTo(1);
  }

  private static double state(SimpleMeterRegistry meters, String state) {
    return meters
        .get("resilience4j.circuitbreaker.state")
        .tags("name", "source", "state", state)
        .gauge()
        .value();
  }
}
