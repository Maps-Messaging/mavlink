package io.mapsmessaging.mavlink;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestMavlinkConcurrency {

  @Test
  void parallel_encodeDecode_sameMessage_isStable() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();

    int threadCount = 12;
    int iterationsPerThread = 200;

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicReference<Throwable> failure = new AtomicReference<>();

    for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
      Thread thread = new Thread(() -> {
        try {
          startLatch.await();

          for (int i = 0; i < iterationsPerThread; i++) {
            byte[] payload = codec.encodePayload(1, Map.of(
                "onboard_control_sensors_present", 1,
                "onboard_control_sensors_enabled", 1,
                "onboard_control_sensors_health", 1,
                "load", 250,
                "voltage_battery", 12000,
                "current_battery", 100,
                "battery_remaining", 90
            ));

            Map<String, Object> decoded = codec.parsePayload(1, payload);

            assertEquals(250, ((Number) decoded.get("load")).intValue());
            assertEquals(12000, ((Number) decoded.get("voltage_battery")).intValue());
            assertEquals(90, ((Number) decoded.get("battery_remaining")).intValue());
          }
        } catch (Throwable t) {
          failure.compareAndSet(null, t);
        } finally {
          doneLatch.countDown();
        }
      });

      thread.start();
    }

    startLatch.countDown();
    doneLatch.await();

    Throwable thrown = failure.get();
    if (thrown != null) {
      throw new AssertionError("Concurrency test failed", thrown);
    }
  }
}
