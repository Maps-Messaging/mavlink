package io.mapsmessaging.mavlink.tlog;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public final class TlogConfiguration {

  private static final long DEFAULT_MAXIMUM_FILE_SIZE_BYTES = 64L * 1024L * 1024L;
  private static final Duration DEFAULT_MAXIMUM_FILE_AGE = Duration.ofHours(1);
  private static final int DEFAULT_RETENTION_DAYS = 7;
  private static final int DEFAULT_QUEUE_CAPACITY = 8192;
  private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(1);

  private final Path filePath;
  private final long maximumFileSizeBytes;
  private final Duration maximumFileAge;
  private final int retentionDays;
  private final int queueCapacity;
  private final Duration flushInterval;
  private final boolean compressRotatedFiles;
  private final boolean rotateExistingFileOnStartup;

  private TlogConfiguration(Builder builder) {
    this.filePath = validateFilePath(builder.filePath);
    this.maximumFileSizeBytes = validateNonNegative(builder.maximumFileSizeBytes, "maximumFileSizeBytes");
    this.maximumFileAge = validateDuration(builder.maximumFileAge, "maximumFileAge");
    this.retentionDays = validateNonNegative(builder.retentionDays, "retentionDays");
    this.queueCapacity = validatePositive(builder.queueCapacity, "queueCapacity");
    this.flushInterval = validateDuration(builder.flushInterval, "flushInterval");
    this.compressRotatedFiles = builder.compressRotatedFiles;
    this.rotateExistingFileOnStartup = builder.rotateExistingFileOnStartup;
  }

  public static Builder builder(Path filePath) {
    return new Builder(filePath);
  }

  public Path getFilePath() {
    return filePath;
  }

  public long getMaximumFileSizeBytes() {
    return maximumFileSizeBytes;
  }

  public Duration getMaximumFileAge() {
    return maximumFileAge;
  }

  public int getRetentionDays() {
    return retentionDays;
  }

  public int getQueueCapacity() {
    return queueCapacity;
  }

  public Duration getFlushInterval() {
    return flushInterval;
  }

  public boolean isCompressRotatedFiles() {
    return compressRotatedFiles;
  }

  public boolean isRotateExistingFileOnStartup() {
    return rotateExistingFileOnStartup;
  }

  private static Path validateFilePath(Path filePath) {
    Path value = Objects.requireNonNull(filePath, "filePath").toAbsolutePath().normalize();
    Path fileName = value.getFileName();
    if (fileName == null || !fileName.toString().endsWith(".tlog")) {
      throw new IllegalArgumentException("filePath must name a .tlog file");
    }
    return value;
  }

  private static long validateNonNegative(long value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
    return value;
  }

  private static int validateNonNegative(int value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
    return value;
  }

  private static int validatePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be greater than zero");
    }
    return value;
  }

  private static Duration validateDuration(Duration value, String name) {
    Duration duration = Objects.requireNonNull(value, name);
    if (duration.isNegative()) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
    return duration;
  }

  public static final class Builder {

    private final Path filePath;
    private long maximumFileSizeBytes = DEFAULT_MAXIMUM_FILE_SIZE_BYTES;
    private Duration maximumFileAge = DEFAULT_MAXIMUM_FILE_AGE;
    private int retentionDays = DEFAULT_RETENTION_DAYS;
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
    private Duration flushInterval = DEFAULT_FLUSH_INTERVAL;
    private boolean compressRotatedFiles = true;
    private boolean rotateExistingFileOnStartup = true;

    private Builder(Path filePath) {
      this.filePath = filePath;
    }

    public Builder maximumFileSizeBytes(long maximumFileSizeBytes) {
      this.maximumFileSizeBytes = maximumFileSizeBytes;
      return this;
    }

    public Builder maximumFileAge(Duration maximumFileAge) {
      this.maximumFileAge = maximumFileAge;
      return this;
    }

    public Builder retentionDays(int retentionDays) {
      this.retentionDays = retentionDays;
      return this;
    }

    public Builder queueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
      return this;
    }

    public Builder flushInterval(Duration flushInterval) {
      this.flushInterval = flushInterval;
      return this;
    }

    public Builder compressRotatedFiles(boolean compressRotatedFiles) {
      this.compressRotatedFiles = compressRotatedFiles;
      return this;
    }

    public Builder rotateExistingFileOnStartup(boolean rotateExistingFileOnStartup) {
      this.rotateExistingFileOnStartup = rotateExistingFileOnStartup;
      return this;
    }

    public TlogConfiguration build() {
      return new TlogConfiguration(this);
    }
  }
}
