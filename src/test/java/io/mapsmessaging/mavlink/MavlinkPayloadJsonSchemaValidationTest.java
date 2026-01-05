package io.mapsmessaging.mavlink;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
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
    MavlinkMessageRegistry registry = payloadCodec.getRegistry();

    return registry.getCompiledMessages().stream()
        .map(msg -> DynamicTest.dynamicTest(
            msg.getMessageId() + " " + msg.getName(),
            () -> payloadToJsonValidates(payloadCodec, registry, msg)
        ));
  }

  private static void payloadToJsonValidates(
      MavlinkCodec payloadCodec,
      MavlinkMessageRegistry registry,
      MavlinkCompiledMessage msg
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
        io.mapsmessaging.mavlink.schema.MavlinkJsonSchemaBuilder.buildSchema(msg, registry.getEnumsByName());

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
