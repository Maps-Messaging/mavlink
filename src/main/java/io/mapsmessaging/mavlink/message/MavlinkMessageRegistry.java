/*
 *
 *     Copyright [ 2020 - 2026 ] [Matthew Buckton]
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package io.mapsmessaging.mavlink.message;

import io.mapsmessaging.mavlink.message.fields.AbstractMavlinkFieldCodec;
import io.mapsmessaging.mavlink.message.fields.MavlinkEnumDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldCodecFactory;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import io.mapsmessaging.mavlink.parser.MavlinkDialectDefinition;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
public class MavlinkMessageRegistry {

  @Setter
  private String dialectName;

  private List<MavlinkCompiledMessage> compiledMessages;

  private Map<Integer, MavlinkCompiledMessage> compiledMessagesById;

  private Map<String, MavlinkEnumDefinition> enumsByName;

  public static MavlinkMessageRegistry fromDialectDefinition(MavlinkDialectDefinition dialectDefinition) {
    MavlinkMessageRegistry registry = new MavlinkMessageRegistry();
    registry.setDialectName(dialectDefinition.getName());

    List<MavlinkCompiledMessage> compiledMessageList = new ArrayList<>();
    Map<Integer, MavlinkCompiledMessage> compiledByIdMap = new HashMap<>();

    for (MavlinkMessageDefinition messageDefinition : dialectDefinition.getMessages()) {
      MavlinkCompiledMessage compiledMessage = compileMessage(messageDefinition);
      compiledMessageList.add(compiledMessage);
      compiledByIdMap.put(compiledMessage.getMessageId(), compiledMessage);
    }

    registry.setCompiledMessages(compiledMessageList);
    registry.setCompiledMessagesById(compiledByIdMap);
    registry.setEnumsByName(new HashMap<>(dialectDefinition.getEnumsByName()));

    return registry;
  }

  private void setEnumsByName(Map<String, MavlinkEnumDefinition> stringMavlinkEnumDefinitionHashMap) {
    this.enumsByName = Collections.unmodifiableMap(new HashMap<>(stringMavlinkEnumDefinitionHashMap));
  }

  private void setCompiledMessagesById(Map<Integer, MavlinkCompiledMessage> compiledByIdMap) {
    this.compiledMessagesById = Collections.unmodifiableMap(new HashMap<>(compiledByIdMap));
  }

  private void setCompiledMessages(List<MavlinkCompiledMessage> compiledMessageList) {
    this.compiledMessages = Collections.unmodifiableList(new ArrayList<>(compiledMessageList));
  }


  private static MavlinkCompiledMessage compileMessage(MavlinkMessageDefinition messageDefinition) {
    MavlinkCompiledMessage compiledMessage = new MavlinkCompiledMessage();
    compiledMessage.setMessageId(messageDefinition.getMessageId());
    compiledMessage.setName(messageDefinition.getName());
    compiledMessage.setMessageDefinition(messageDefinition);

    List<MavlinkCompiledField> compiledFields = new ArrayList<>();

    int currentOffset = 0;
    for (MavlinkFieldDefinition fieldDefinition : messageDefinition.getFields()) {
      AbstractMavlinkFieldCodec fieldCodec = MavlinkFieldCodecFactory.createCodec(fieldDefinition);

      MavlinkCompiledField compiledField = new MavlinkCompiledField();
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
