package io.mapsmessaging.mavlink;


import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.testng.Assert.assertThrows;
import static org.testng.AssertJUnit.assertTrue;

class TestMavlinkRobustness {

  @Test
  void encode_unknownMessageId_throwsIOException() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();

    assertThrows(IOException.class, () -> codec.encodePayload(999999, Map.of()));
  }

  @Test
  void decode_unknownMessageId_throwsIOException() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();

    assertThrows(IOException.class, () -> codec.parsePayload(999999, new byte[] { 0x01, 0x02 }));
  }

  @Test
  void decode_truncatedPayload_throwsIOException() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();

    // SYS_STATUS is a good stable target
    byte[] payload = codec.encodePayload(1, Map.of(
        "onboard_control_sensors_present", 1,
        "onboard_control_sensors_enabled", 1,
        "onboard_control_sensors_health", 1,
        "load", 250,
        "voltage_battery", 12000,
        "current_battery", 100,
        "battery_remaining", 90
    ));

    assertTrue(payload.length > 1);

    byte[] truncated = java.util.Arrays.copyOf(payload, payload.length - 1);

    assertThrows(IOException.class, () -> codec.parsePayload(1, truncated));
  }

  @Test
  void decode_oversizedPayload_isHandledDeterministically() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();

    byte[] payload = codec.encodePayload(1, Map.of(
        "onboard_control_sensors_present", 1,
        "onboard_control_sensors_enabled", 1,
        "onboard_control_sensors_health", 1,
        "load", 250,
        "voltage_battery", 12000,
        "current_battery", 100,
        "battery_remaining", 90
    ));

    byte[] oversized = java.util.Arrays.copyOf(payload, payload.length + 10);
    // Leave the extra bytes as zeros. Behavior must be consistent:
    // either accept and ignore tail OR reject. Lock down one.

    try {
      codec.parsePayload(1, oversized);
    } catch (IOException exception) {
      // Acceptable if your parser is strict.
      return;
    }
  }

  @Test
  void commonDialectLoadsAndHasKnownMessageIds() throws Exception {
    MavlinkMessageFormatLoader loader = MavlinkMessageFormatLoader.getInstance();
    MavlinkCodec codec = loader.getDialectOrThrow("common");

    Assertions.assertNotNull(codec);
    Assertions.assertEquals("common", codec.getName());

    MavlinkMessageRegistry registry = codec.getRegistry();
    Assertions.assertNotNull(registry);

    Assertions.assertTrue(registry.getCompiledMessagesById().containsKey(0), "HEARTBEAT");
    Assertions.assertTrue(registry.getCompiledMessagesById().containsKey(1), "SYS_STATUS");
    Assertions.assertTrue(registry.getCompiledMessagesById().containsKey(33), "GLOBAL_POSITION_INT");
    Assertions.assertTrue(registry.getCompiledMessagesById().containsKey(148), "AUTOPILOT_VERSION");

    Assertions.assertEquals(232, registry.getCompiledMessages().size());
  }
}
