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

package io.mapsmessaging.mavlink.message;

import com.google.gson.JsonObject;
import io.mapsmessaging.mavlink.message.fields.AbstractMavlinkFieldCodec;
import io.mapsmessaging.mavlink.message.fields.EnumDefinition;
import io.mapsmessaging.mavlink.message.fields.FieldCodecFactory;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import io.mapsmessaging.mavlink.parser.DialectDefinition;
import io.mapsmessaging.mavlink.schema.JsonSchemaBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
public class MessageRegistry {

  @Setter
  private String dialectName;

  private List<CompiledMessage> compiledMessages;

  private Map<Integer, CompiledMessage> compiledMessagesById;

  private Map<String, EnumDefinition> enumsByName;

  private Map<Integer, JsonObject> jsonSchema;



  public static MessageRegistry fromDialectDefinition(DialectDefinition dialectDefinition) {
    MessageRegistry registry = new MessageRegistry();
    registry.setDialectName(dialectDefinition.getName());

    List<CompiledMessage> compiledMessageList = new ArrayList<>();
    Map<Integer, CompiledMessage> compiledByIdMap = new HashMap<>();
    Map<Integer, JsonObject> schemaMap = new HashMap<>();

    for (MessageDefinition messageDefinition : dialectDefinition.getMessages()) {
      CompiledMessage compiledMessage = compileMessage(messageDefinition);
      compiledMessageList.add(compiledMessage);
      compiledByIdMap.put(compiledMessage.getMessageId(), compiledMessage);
      schemaMap.put(compiledMessage.getMessageId(), JsonSchemaBuilder.buildSchema(compiledMessage, dialectDefinition.getEnumsByName()));
    }

    registry.setCompiledMessages(compiledMessageList);
    registry.setCompiledMessagesById(compiledByIdMap);
    registry.setEnumsByName(new HashMap<>(dialectDefinition.getEnumsByName()));
    registry.setJsonSchema(schemaMap);

    return registry;
  }

  private void setEnumsByName(Map<String, EnumDefinition> stringMavlinkEnumDefinitionHashMap) {
    this.enumsByName = Collections.unmodifiableMap(new HashMap<>(stringMavlinkEnumDefinitionHashMap));
  }

  private void setCompiledMessagesById(Map<Integer, CompiledMessage> compiledByIdMap) {
    this.compiledMessagesById = Collections.unmodifiableMap(new HashMap<>(compiledByIdMap));
  }

  private void setCompiledMessages(List<CompiledMessage> compiledMessageList) {
    this.compiledMessages = Collections.unmodifiableList(new ArrayList<>(compiledMessageList));
  }

  private void setJsonSchema(Map<Integer, JsonObject> jsonSchemaMap ){
    this.jsonSchema = Collections.unmodifiableMap(new HashMap<>(jsonSchemaMap));
  }

  private static CompiledMessage compileMessage(MessageDefinition messageDefinition) {
    CompiledMessage compiledMessage = new CompiledMessage();
    compiledMessage.setMessageId(messageDefinition.getMessageId());
    compiledMessage.setName(messageDefinition.getName());
    compiledMessage.setMessageDefinition(messageDefinition);

    List<CompiledField> compiledFields = new ArrayList<>();

    int currentOffset = 0;
    for (FieldDefinition fieldDefinition : messageDefinition.getFields()) {
      AbstractMavlinkFieldCodec fieldCodec = FieldCodecFactory.createCodec(fieldDefinition);

      CompiledField compiledField = new CompiledField();
      compiledField.setFieldDefinition(fieldDefinition);
      compiledField.setFieldCodec(fieldCodec);
      compiledField.setOffsetInPayload(currentOffset);
      compiledField.setSizeInBytes(fieldCodec.getSizeInBytes());

      compiledFields.add(compiledField);
      currentOffset = currentOffset + fieldCodec.getSizeInBytes();
    }

    compiledMessage.setCompiledFields(compiledFields);
    compiledMessage.setPayloadSizeBytes(currentOffset);

    return compiledMessage;
  }
}
