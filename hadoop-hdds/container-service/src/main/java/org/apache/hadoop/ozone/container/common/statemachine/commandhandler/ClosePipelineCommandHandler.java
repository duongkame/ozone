/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.ozone.container.common.statemachine.commandhandler;

import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos;
import org.apache.hadoop.hdds.protocol.proto.
    StorageContainerDatanodeProtocolProtos.SCMCommandProto;
import org.apache.hadoop.hdds.scm.pipeline.PipelineID;
import org.apache.hadoop.ozone.container.common.statemachine
    .SCMConnectionManager;
import org.apache.hadoop.ozone.container.common.statemachine.StateContext;
import org.apache.hadoop.ozone.container.common.transport.server
    .XceiverServerSpi;
import org.apache.hadoop.ozone.container.ozoneimpl.OzoneContainer;
import org.apache.hadoop.ozone.protocol.commands.ClosePipelineCommand;
import org.apache.hadoop.ozone.protocol.commands.SCMCommand;
import org.apache.hadoop.util.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handler for close pipeline command received from SCM.
 */
public class ClosePipelineCommandHandler implements CommandHandler {

  private static final Logger LOG =
      LoggerFactory.getLogger(ClosePipelineCommandHandler.class);

  private final AtomicLong invocationCount = new AtomicLong(0);
  private final AtomicInteger queuedCount = new AtomicInteger(0);
  private long totalTime;
  private final Executor executor;

  /**
   * Constructs a closePipelineCommand handler.
   */
  public ClosePipelineCommandHandler(Executor executor) {
    this.executor = executor;
  }

  /**
   * Handles a given SCM command.
   *
   * @param command           - SCM Command
   * @param ozoneContainer    - Ozone Container.
   * @param context           - Current Context.
   * @param connectionManager - The SCMs that we are talking to.
   */
  @Override
  public void handle(SCMCommand command, OzoneContainer ozoneContainer,
      StateContext context, SCMConnectionManager connectionManager) {
    queuedCount.incrementAndGet();
    CompletableFuture.runAsync(() -> {
      invocationCount.incrementAndGet();
      final long startTime = Time.monotonicNow();
      final DatanodeDetails dn = context.getParent().getDatanodeDetails();
      ClosePipelineCommand closePipelineCommand =
          (ClosePipelineCommand) command;
      final PipelineID pipelineID = closePipelineCommand.getPipelineID();
      final HddsProtos.PipelineID pipelineIdProto = pipelineID.getProtobuf();

      try {
        XceiverServerSpi server = ozoneContainer.getWriteChannel();
        if (server.isExist(pipelineIdProto)) {
          server.removeGroup(pipelineIdProto);
          LOG.info("Close Pipeline {} command on datanode {}.", pipelineID,
              dn.getUuidString());
        } else {
          LOG.debug("Ignoring close pipeline command for pipeline {} " +
              "as it does not exist", pipelineID);
        }
      } catch (IOException e) {
        LOG.error("Can't close pipeline {}", pipelineID, e);
      } finally {
        long endTime = Time.monotonicNow();
        totalTime += endTime - startTime;
      }
    }, executor).whenComplete((v, e) -> queuedCount.decrementAndGet());
  }

  /**
   * Returns the command type that this command handler handles.
   *
   * @return Type
   */
  @Override
  public SCMCommandProto.Type getCommandType() {
    return SCMCommandProto.Type.closePipelineCommand;
  }

  /**
   * Returns number of times this handler has been invoked.
   *
   * @return int
   */
  @Override
  public int getInvocationCount() {
    return (int)invocationCount.get();
  }

  /**
   * Returns the average time this function takes to run.
   *
   * @return long
   */
  @Override
  public long getAverageRunTime() {
    if (invocationCount.get() > 0) {
      return totalTime / invocationCount.get();
    }
    return 0;
  }

  @Override
  public int getQueuedCount() {
    return queuedCount.get();
  }
}
