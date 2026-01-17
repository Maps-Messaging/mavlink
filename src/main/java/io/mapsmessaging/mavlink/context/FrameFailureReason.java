package io.mapsmessaging.mavlink.context;

public enum FrameFailureReason {
  OK,
  CRC_FAILED,
  SIGNATURE_FAILED,
  CRC_AND_SIGNATURE_FAILED,
  MALFORMED,
  UNKNOWN
}
