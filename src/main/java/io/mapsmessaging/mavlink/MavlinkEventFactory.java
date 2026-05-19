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
import io.mapsmessaging.mavlink.codec.MavlinkFrameCodec;
import io.mapsmessaging.mavlink.context.Detection;
import io.mapsmessaging.mavlink.context.FrameFailureReason;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.Frame;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MavlinkEventFactory {

  private MavlinkFrameCodec frameCodec;
  private SystemContextManager systemContextManager;

  public MavlinkEventFactory() throws IOException {
    this("common");
  }

  public MavlinkEventFactory(String dialectName) throws IOException {
    Optional<MavlinkCodec> codec = MavlinkMessageFormatLoader.getInstance().getDialect(dialectName);
    if (codec.isPresent()) {
      MavlinkCodec codecInstance = codec.get();
      frameCodec = new MavlinkFrameCodec(codecInstance);
      systemContextManager = new SystemContextManager();
    }
    else{
      throw new IOException("Mavlink "+dialectName+" codec not found");
    }
  }


  public MavlinkEventFactory(Path dialectPath) throws IOException, ParserConfigurationException, SAXException {
    MavlinkCodec codec = MavlinkMessageFormatLoader.getInstance().loadDialect(dialectPath);
    frameCodec = new MavlinkFrameCodec(codec);
    systemContextManager = new SystemContextManager();
  }

  public MavlinkEventFactory(MavlinkFrameCodec frameCodec, SystemContextManager systemContextManager){
    this.frameCodec = frameCodec;
    this.systemContextManager = systemContextManager;
  }

  public Optional<ProcessedFrame> unpack(String streamName, ByteBuffer payload) throws IOException {
    long timestamp = System.nanoTime();

    Optional<Frame> frameOptional = frameCodec.tryUnpackFrame(payload);
    if (frameOptional.isEmpty()) {
      return Optional.empty();
    }
    Frame frame = frameOptional.get();
    FrameFailureReason failureReason = frame.getValidated();
    Map<String, Object> fields = frameCodec.parsePayload(frame);
    String name = "";
    CompiledMessage message = frameCodec.getRegistry().getCompiledMessagesById().get(frame.getMessageId());
    if(message != null){
      name = message.getName();
    }
    if (failureReason == FrameFailureReason.OK || failureReason == FrameFailureReason.UNSIGNED) {
      List<Detection> detectionList = systemContextManager.onValidatedFrame(frame, streamName, timestamp);
      return Optional.of(new ProcessedFrame(name, frame, fields, true, detectionList));
    }
    List<Detection> detectionList = systemContextManager.onInvalidFrame(
        frame.getSystemId(),
        streamName,
        timestamp,
        failureReason
    );
    return Optional.of(new ProcessedFrame(name, frame, Map.of(), false, detectionList));
  }



}
