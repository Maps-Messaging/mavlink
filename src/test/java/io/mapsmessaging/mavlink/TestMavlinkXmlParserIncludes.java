package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.parser.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

class TestMavlinkXmlParserIncludes {

  @Test
  void commonDialect_resolvesIncludes_andContainsKnownMessages() throws Exception {
    ClassLoader classLoader = getClass().getClassLoader();

    try (InputStream stream = classLoader.getResourceAsStream("mavlink/common.xml")) {
      Assertions.assertNotNull(stream, "Missing resource mavlink/common.xml");

      MavlinkXmlParser parser = new MavlinkXmlParser();
      MavlinkDialectLoader loader = new MavlinkDialectLoader(parser);

      MavlinkIncludeResolver resolver = new ClasspathMavlinkIncludeResolver(classLoader, "mavlink");
      MavlinkDialectDefinition dialect = loader.load("common", stream, resolver);

      Assertions.assertNotNull(dialect);
      Assertions.assertEquals("common", dialect.getName());

      Assertions.assertNotNull(dialect.getMessagesById());
      Assertions.assertTrue(dialect.getMessagesById().size() >= 220,
          "Expected merged dialect size to be large after includes, got: " + dialect.getMessagesById().size());

      // Known core messages
      Assertions.assertTrue(dialect.getMessagesById().containsKey(0), "Expected HEARTBEAT (0)");
      Assertions.assertTrue(dialect.getMessagesById().containsKey(1), "Expected SYS_STATUS (1)");
      Assertions.assertTrue(dialect.getMessagesById().containsKey(33), "Expected GLOBAL_POSITION_INT (33)");
      Assertions.assertTrue(dialect.getMessagesById().containsKey(148), "Expected AUTOPILOT_VERSION (148)");
    }
  }

}
