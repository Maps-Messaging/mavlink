package io.mapsmessaging.mavlink.tlog;

import java.io.IOException;

interface TlogOutput extends AutoCloseable {

  void write(TlogRecord record) throws IOException;

  void flushIfDue() throws IOException;

  @Override
  void close() throws IOException;
}
