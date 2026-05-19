/*
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.codec.MavlinkCodec;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MavlinkDialectLoadTest {

  @Test
  void testLoadStandardCommonDialectFromPath() throws Exception {
    Path commonPath = resourcePath("mavlink/common.xml");

    MavlinkCodec codec = MavlinkMessageFormatLoader.getInstance().loadDialect(commonPath);

    assertNotNull(codec);

    MessageRegistry registry = codec.getRegistry();

    assertNotNull(registry);
    assertMessageExists(registry, 0, "HEARTBEAT");
    assertTrue(
        registry.getCompiledMessagesById().containsKey(411), "mavlink dialect should resolve ardupilot/common.xml."
    );
  }

  @Test
  void testLoadArduPilotAllDialectFromPath() throws Exception {
    Path ardupilotAllPath = resourcePath("mavlink/ardupilot/all.xml");

    MavlinkCodec codec = MavlinkMessageFormatLoader.getInstance().loadDialect(ardupilotAllPath);

    assertNotNull(codec);

    MessageRegistry registry = codec.getRegistry();

    assertNotNull(registry);
    assertMessageExists(registry, 0, "HEARTBEAT");
    assertMessageExists(registry, 1, "SYS_STATUS");
    assertFalse(
        registry.getCompiledMessagesById().containsKey(411), "ArduPilot dialect should resolve ardupilot/common.xml. Message 411 CURRENT_EVENT_SEQUENCE must not be present when loading ardupilot/all.xml"
    );
  }

  @Test
  void testArduPilotDialectLoadsFullIncludeChain() throws Exception {
    Path ardupilotAllPath = resourcePath("mavlink/ardupilot/all.xml");

    MavlinkCodec codec = MavlinkMessageFormatLoader.getInstance().loadDialect(ardupilotAllPath);
    MessageRegistry registry = codec.getRegistry();

    assertNotNull(registry);
    assertNotNull(registry.getCompiledMessages());
    assertNotNull(registry.getCompiledMessagesById());

    assertTrue(
        registry.getCompiledMessages().size() > 100,
        "ArduPilot dialect should load the full include chain"
    );

    assertTrue(
        registry.getCompiledMessagesById().size() > 100,
        "ArduPilot dialect should index the full include chain"
    );
  }

  public static Path resourcePath(String resourceName) throws URISyntaxException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    URL resource = classLoader.getResource(resourceName);

    assertNotNull(resource, "Missing test resource: " + resourceName);

    return Path.of(resource.toURI());
  }

  private static void assertMessageExists(
      MessageRegistry registry,
      int messageId,
      String expectedMessageName
  ) {

    Map<Integer, CompiledMessage> compiledMessagesById = registry.getCompiledMessagesById();

    assertTrue(
        compiledMessagesById.containsKey(messageId),
        "Missing MAVLink message id " + messageId + " (" + expectedMessageName + ")"
    );

    CompiledMessage compiledMessage = compiledMessagesById.get(messageId);

    assertTrue(
        expectedMessageName.equals(compiledMessage.getName()),
        "Expected message id " + messageId + " to be " + expectedMessageName
            + " but was " + compiledMessage.getName()
    );
  }
}