package org.apache.hadoop.hdds.scm;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.hdds.scm.pipeline.Pipeline;
import org.apache.hadoop.hdds.scm.pipeline.PipelineID;
import org.apache.hadoop.metrics2.MetricsSystem;
import org.apache.hadoop.metrics2.annotation.Metric;
import org.apache.hadoop.metrics2.annotation.Metrics;
import org.apache.hadoop.metrics2.lib.DefaultMetricsSystem;
import org.apache.hadoop.metrics2.lib.Interns;
import org.apache.hadoop.metrics2.lib.MetricsRegistry;
import org.apache.hadoop.metrics2.lib.MutableCounterLong;
import org.apache.hadoop.ozone.OzoneConsts;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Container client metrics that describe how data writes are distributed to
 * pipelines.
 */
@Metrics(about = "Client Metrics", context = OzoneConsts.OZONE)
public final class ContainerClientMetrics {
  private static final String SOURCE_NAME =
      ContainerClientMetrics.class.getSimpleName();

  @Metric
  private MutableCounterLong totalWriteChunkCalls;
  @Metric
  private MutableCounterLong totalWriteChunkBytes;
  private Map<PipelineID, MutableCounterLong> writeChunkCallsByPipeline;
  private Map<PipelineID, MutableCounterLong> writeChunkBytesByPipeline;
  private Map<UUID, MutableCounterLong> writeChunksCallsByLeaders;
  private MetricsRegistry registry;

  private ContainerClientMetrics() {
    this.registry = new MetricsRegistry(SOURCE_NAME);
    writeChunkCallsByPipeline = new ConcurrentHashMap<>();
    writeChunkBytesByPipeline = new ConcurrentHashMap<>();
    writeChunksCallsByLeaders = new ConcurrentHashMap<>();
  }

  public static ContainerClientMetrics create() {
    MetricsSystem ms = DefaultMetricsSystem.instance();
    ContainerClientMetrics clientMetrics = ms.register(SOURCE_NAME,
        "Ozone Client Metrics", new ContainerClientMetrics());
    return clientMetrics;
  }

  public void recordWriteChunk(Pipeline pipeline, long chunkSizeBytes) {
    writeChunkCallsByPipeline.computeIfAbsent(pipeline.getId(),
        pipelineID -> registry.newCounter(
            Interns.info("writeChunkCallsPipeline-" + pipelineID.getId(),
                "Number of writeChunk calls on a pipelines"),
            0L)
    ).incr();
    writeChunkBytesByPipeline.computeIfAbsent(pipeline.getId(),
        pipelineID -> registry.newCounter(
            Interns.info("writeChunkBytesPipeline-" + pipelineID.getId(),
                "Number of bytes written on a pipelines"),
            0L)
    ).incr(chunkSizeBytes);
    writeChunksCallsByLeaders.computeIfAbsent(pipeline.getLeaderId(),
        leader -> registry.newCounter(
            Interns.info("writeChunkCallsLeader-" + leader,
                "Number of writeChunk calls on a leader node"),
            0L)
    ).incr();
    totalWriteChunkCalls.incr();
    totalWriteChunkBytes.incr(chunkSizeBytes);
  }

  @VisibleForTesting
  public MutableCounterLong getTotalWriteChunkBytes() {
    return totalWriteChunkBytes;
  }

  @VisibleForTesting
  public MutableCounterLong getTotalWriteChunkCalls() {
    return totalWriteChunkCalls;
  }

  @VisibleForTesting
  public Map<PipelineID, MutableCounterLong> getWriteChunkBytesByPipeline() {
    return writeChunkBytesByPipeline;
  }

  @VisibleForTesting
  public Map<PipelineID, MutableCounterLong> getWriteChunkCallsByPipeline() {
    return writeChunkCallsByPipeline;
  }

  @VisibleForTesting
  public Map<UUID, MutableCounterLong> getWriteChunksCallsByLeaders() {
    return writeChunksCallsByLeaders;
  }
}
