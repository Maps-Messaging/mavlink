package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.context.Detection;
import io.mapsmessaging.mavlink.context.FrameFailureReason;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MavlinkEventFactory {

  private MavlinkFrameCodec frameCodec;
  private SystemContextManager systemContextManager;

  public MavlinkEventFactory() throws IOException {
    Optional<MavlinkCodec> codec = MavlinkMessageFormatLoader.getInstance().getDialect("common");
    if (codec.isPresent()) {
      MavlinkCodec codecInstance = codec.get();
      frameCodec = new MavlinkFrameCodec(codecInstance);
      systemContextManager = new SystemContextManager();
    }
    else{
      throw new IOException("Mavlink default common codec not found");
    }
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
