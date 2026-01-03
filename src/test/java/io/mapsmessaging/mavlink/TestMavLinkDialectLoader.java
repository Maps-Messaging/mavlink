package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestMavlinkDialectLoader {

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
