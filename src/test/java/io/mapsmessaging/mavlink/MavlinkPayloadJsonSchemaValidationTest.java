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


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.mapsmessaging.mavlink.codec.MavlinkCodec;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.schema.JsonSchemaBuilder;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MavlinkPayloadJsonSchemaValidationTest extends BaseRoudTripTest {

  private static final Gson GSON = new Gson();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @TestFactory
  Stream<DynamicTest> allMessages_payload_toJson_validatesAgainstSchema() throws Exception {
    MavlinkCodec payloadCodec = MavlinkTestSupport.codec();
    MessageRegistry registry = payloadCodec.getRegistry();

    return registry.getCompiledMessages().stream()
        .map(msg -> DynamicTest.dynamicTest(
            msg.getMessageId() + " " + msg.getName(),
            () -> payloadToJsonValidates(payloadCodec, registry, msg)
        ));
  }

  private static void payloadToJsonValidates(
      MavlinkCodec payloadCodec,
      MessageRegistry registry,
      CompiledMessage msg
  ) throws Exception {

    Map<String, Object> values =
        RandomValueFactory.buildValues(registry, msg, MavlinkRoundTripAllMessagesTest.ExtensionMode.SOME_PRESENT, BASE_SEED);

    byte[] payload = payloadCodec.encodePayload(msg.getMessageId(), values);
    assertNotNull(payload);

    Map<String, Object> parsed = payloadCodec.parsePayload(msg.getMessageId(), payload);
    assertNotNull(parsed);

    JsonObject jsonObject = GSON.toJsonTree(parsed).getAsJsonObject();

    // Build schema for this message
    JsonObject schemaObject =
        JsonSchemaBuilder.buildSchema(msg, registry.getEnumsByName());

    JsonSchema schema = compileSchema(schemaObject);
    Set<ValidationMessage> errors = schema.validate(toJsonNode(jsonObject));

    assertTrue(errors.isEmpty(),
        () -> "Schema validation failed for msgId=" + msg.getMessageId() + " " + msg.getName() + "\n" +
            String.join("\n", errors.stream().map(ValidationMessage::getMessage).toList()));
  }

  private static JsonSchema compileSchema(JsonObject schemaObject) throws Exception {
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    JsonNode schemaNode = toJsonNode(schemaObject);
    return factory.getSchema(schemaNode);
  }

  private static JsonNode toJsonNode(JsonObject gsonObject) throws Exception {
    return OBJECT_MAPPER.readTree(GSON.toJson(gsonObject));
  }
}
