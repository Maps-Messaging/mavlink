package io.mapsmessaging.mavlink.tlog;

record TlogRecord(long timestampMicros, byte[] frame, boolean stop) {

  static final TlogRecord STOP = new TlogRecord(0, new byte[0], true);

  static TlogRecord data(long timestampMicros, byte[] frame) {
    return new TlogRecord(timestampMicros, frame, false);
  }
}
