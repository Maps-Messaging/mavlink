/*
 *
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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.mapsmessaging.mavlink.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.fields.MavlinkEnumDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkEnumEntry;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkWireType;

import java.util.Map;

public final class MavlinkJsonSchemaBuilder {

  private MavlinkJsonSchemaBuilder() {
  }

  public static JsonObject buildSchema(
      MavlinkCompiledMessage message,
      Map<String, MavlinkEnumDefinition> enumsByName) {

    JsonObject schema = new JsonObject();
    schema.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.addProperty("title", message.getName());
    schema.addProperty("type", "object");
    schema.addProperty("description",
        message.getMessageDefinition().getDescription());

    JsonObject properties = new JsonObject();
    JsonArray required = new JsonArray();

    for (MavlinkCompiledField compiledField : message.getCompiledFields()) {
      MavlinkFieldDefinition field = compiledField.getFieldDefinition();

      JsonObject fieldSchema = buildFieldSchema(field, enumsByName);
      properties.add(field.getName(), fieldSchema);

      if (!field.isExtension()) {
        required.add(field.getName());
      }
    }

    schema.add("properties", properties);
    if (!required.isEmpty()) {
      schema.add("required", required);
    }

    schema.addProperty("additionalProperties", false);
    schema.addProperty("x-message-id", message.getMessageId());
    schema.addProperty("x-crc-extra", message.getCrcExtra());

    return schema;
  }

  private static JsonObject buildFieldSchema(
      MavlinkFieldDefinition field,
      Map<String, MavlinkEnumDefinition> enumsByName) {

    JsonObject schema = new JsonObject();

    if (field.getDescription() != null) {
      schema.addProperty("description", field.getDescription());
    }

    MavlinkWireType wireType = field.getWireType();

    // char[N] → string
    if (field.isArray() && wireType.isChar()) {
      schema.addProperty("type", "string");
      schema.addProperty("maxLength", field.getArrayLength());
      return schema;
    }

    // Arrays
    if (field.isArray()) {
      schema.addProperty("type", "array");
      schema.addProperty("minItems", field.getArrayLength());
      schema.addProperty("maxItems", field.getArrayLength());

      JsonObject itemSchema = scalarSchema(field, enumsByName);
      schema.add("items", itemSchema);
      return schema;
    }

    // Scalar
    return scalarSchema(field, enumsByName);
  }

  private static JsonObject scalarSchema(
      MavlinkFieldDefinition field,
      Map<String, MavlinkEnumDefinition> enumsByName) {

    JsonObject schema = new JsonObject();
    MavlinkWireType wireType = field.getWireType();

    if (wireType.isFloat() || wireType.isDouble()) {
      schema.addProperty("type", "number");
    } else {
      schema.addProperty("type", "integer");
    }

    if (field.getEnumName() != null) {
      MavlinkEnumDefinition enumDef = enumsByName.get(field.getEnumName());
      if (enumDef != null) {
        applyEnum(schema, enumDef);
      }
    }

    return schema;
  }

  private static void applyEnum(
      JsonObject schema,
      MavlinkEnumDefinition enumDef) {

    schema.addProperty("x-enum-name", enumDef.getName());

    if (enumDef.isBitmask()) {
      schema.addProperty("description",
          "Bitmask enum: " + enumDef.getName());
      return;
    }

    JsonArray oneOf = new JsonArray();
    for (MavlinkEnumEntry entry : enumDef.getEntries()) {
      JsonObject option = new JsonObject();
      option.addProperty("const", entry.getValue());
      option.addProperty("title", entry.getName());
      if (entry.getDescription() != null) {
        option.addProperty("description", entry.getDescription());
      }
      oneOf.add(option);
    }

    schema.add("oneOf", oneOf);
  }
}
