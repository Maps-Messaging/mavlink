package io.mapsmessaging.mavlink.tlog;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class MavlinkTlogWriter implements AutoCloseable {

  private static final System.Logger LOGGER = System.getLogger(MavlinkTlogWriter.class.getName());

  private final BlockingQueue<TlogRecord> queue;
  private final TlogArchiveManager archiveManager;
  private final TlogOutput output;
  private final Thread writerThread;
  private final AtomicBoolean accepting = new AtomicBoolean(true);
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicLong acceptedRecordCount = new AtomicLong();
  private final AtomicLong droppedRecordCount = new AtomicLong();
  private final AtomicLong writtenRecordCount = new AtomicLong();
  private final AtomicReference<Throwable> writerFailure = new AtomicReference<>();

  public MavlinkTlogWriter(TlogConfiguration configuration) throws IOException {
    this(configuration, null);
  }

  MavlinkTlogWriter(TlogConfiguration configuration, TlogOutput output) throws IOException {
    TlogConfiguration validatedConfiguration = Objects.requireNonNull(configuration, "configuration");
    this.queue = new ArrayBlockingQueue<>(validatedConfiguration.getQueueCapacity());
    this.archiveManager = new TlogArchiveManager(validatedConfiguration);

    try {
      archiveManager.recoverAndClean();
      this.output = output == null ? new RotatingTlogFile(validatedConfiguration, archiveManager) : output;
    } catch (IOException | RuntimeException exception) {
      archiveManager.close();
      throw exception;
    }

    this.writerThread = new Thread(this::runWriter, "mavlink-tlog-writer");
    this.writerThread.start();
  }

  public boolean write(long timestampMicros, byte[] mavlinkFrame) {
    Objects.requireNonNull(mavlinkFrame, "mavlinkFrame");
    if (mavlinkFrame.length == 0) {
      throw new IllegalArgumentException("mavlinkFrame must not be empty");
    }
    if (!accepting.get()) {
      droppedRecordCount.incrementAndGet();
      return false;
    }

    TlogRecord record = TlogRecord.data(timestampMicros, Arrays.copyOf(mavlinkFrame, mavlinkFrame.length));
    boolean accepted = queue.offer(record);
    if (accepted) {
      acceptedRecordCount.incrementAndGet();
    } else {
      droppedRecordCount.incrementAndGet();
    }
    return accepted;
  }

  public long getAcceptedRecordCount() {
    return acceptedRecordCount.get();
  }

  public long getWrittenRecordCount() {
    return writtenRecordCount.get();
  }

  public long getDroppedRecordCount() {
    return droppedRecordCount.get();
  }

  public boolean isHealthy() {
    return accepting.get() && writerFailure.get() == null && archiveManager.getLastFailure() == null;
  }

  public Optional<Throwable> getLastFailure() {
    Throwable failure = writerFailure.get();
    if (failure == null) {
      failure = archiveManager.getLastFailure();
    }
    return Optional.ofNullable(failure);
  }

  private void runWriter() {
    try {
      while (true) {
        TlogRecord record = queue.poll(1, TimeUnit.SECONDS);
        if (record == null) {
          output.flushIfDue();
          continue;
        }
        if (record.stop()) {
          drainRemainingRecords();
          output.flushIfDue();
          return;
        }

        output.write(record);
        writtenRecordCount.incrementAndGet();
        output.flushIfDue();
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      fail(exception);
    } catch (Throwable throwable) {
      fail(throwable);
    } finally {
      try {
        output.close();
      } catch (IOException exception) {
        fail(exception);
      }
    }
  }

  private void drainRemainingRecords() throws IOException {
    TlogRecord record;
    while ((record = queue.poll()) != null) {
      if (!record.stop()) {
        output.write(record);
        writtenRecordCount.incrementAndGet();
      }
    }
  }

  private void fail(Throwable throwable) {
    writerFailure.compareAndSet(null, throwable);
    accepting.set(false);
    LOGGER.log(System.Logger.Level.ERROR, "MAVLink tlog writer failed", throwable);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    accepting.set(false);
    boolean interrupted = false;
    while (writerThread.isAlive()) {
      try {
        if (queue.offer(TlogRecord.STOP, 1, TimeUnit.SECONDS)) {
          break;
        }
      } catch (InterruptedException exception) {
        interrupted = true;
      }
    }

    while (true) {
      try {
        writerThread.join();
        break;
      } catch (InterruptedException exception) {
        interrupted = true;
      }
    }

    archiveManager.close();
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
