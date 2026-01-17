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

      XmlParser parser = new XmlParser();
      DialectLoader loader = new DialectLoader(parser);

      IncludeResolver resolver = new ClasspathIncludeResolver(classLoader, "mavlink");
      DialectDefinition dialect = loader.load("common", stream, resolver);

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
