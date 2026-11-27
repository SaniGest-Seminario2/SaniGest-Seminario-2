package com.sanigest.web;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

public final class RemoteCall {
  private final CircuitBreaker breaker;
  private final TimeLimiter limiter;

  public RemoteCall(
      String name, Duration timeout, Predicate<Throwable> ignore, @Nullable MeterRegistry meters) {
    this(name, timeout, ignore, meters, Clock.systemUTC());
  }

  RemoteCall(
      String name,
      Duration timeout,
      Predicate<Throwable> ignore,
      @Nullable MeterRegistry meters,
      Clock clock) {
    var breakers =
        CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .clock(clock)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .ignoreException(
                    failure -> failure instanceof InterruptedException || ignore.test(failure))
                .build());
    var limiters =
        TimeLimiterRegistry.of(
            TimeLimiterConfig.custom().timeoutDuration(timeout).cancelRunningFuture(true).build());
    breaker = breakers.circuitBreaker(name);
    limiter = limiters.timeLimiter(name);
    if (meters != null) {
      TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(meters);
      TaggedTimeLimiterMetrics.ofTimeLimiterRegistry(limiters).bindTo(meters);
    }
  }

  public <T> T execute(Callable<T> call, Supplier<? extends RuntimeException> unavailable) {
    var context = MDC.getCopyOfContextMap();
    var task =
        new FutureTask<>(() -> {
          if (context != null) {
            MDC.setContextMap(context);
          }
          try {
            return call.call();
          } finally {
            MDC.clear();
          }
        });
    try {
      return breaker.executeCallable(() ->
          limiter.executeFutureSupplier(() -> {
            Thread.startVirtualThread(task);
            return task;
          }));
    } catch (InterruptedException interrupted) {
      task.cancel(true);
      Thread.currentThread().interrupt();
      throw unavailable.get();
    } catch (TimeoutException | CallNotPermittedException failure) {
      throw unavailable.get();
    } catch (RuntimeException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalStateException("Unexpected checked exception in remote call", failure);
    }
  }
}
