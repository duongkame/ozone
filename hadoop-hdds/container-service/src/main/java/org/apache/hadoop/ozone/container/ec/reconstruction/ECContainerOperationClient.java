/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.ozone.container.ec.reconstruction;

import com.google.common.collect.ImmutableList;
import org.apache.hadoop.hdds.client.ECReplicationConfig;
import org.apache.hadoop.hdds.conf.ConfigurationSource;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.protocol.datanode.proto.ContainerProtos;
import org.apache.hadoop.hdds.scm.XceiverClientManager;
import org.apache.hadoop.hdds.scm.XceiverClientSpi;
import org.apache.hadoop.hdds.scm.pipeline.Pipeline;
import org.apache.hadoop.hdds.scm.pipeline.PipelineID;
import org.apache.hadoop.hdds.scm.storage.ContainerProtocolCalls;
import org.apache.hadoop.hdds.security.x509.certificate.client.CertificateClient;
import org.apache.hadoop.hdds.utils.HAUtils;
import org.apache.hadoop.ozone.container.common.helpers.BlockData;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class wraps necessary container-level rpc calls
 * during ec offline reconstruction.
 *   - ListBlock
 *   - CloseContainer
 */
public class ECContainerOperationClient implements Closeable {
  private static final Logger LOG =
      LoggerFactory.getLogger(ECContainerOperationClient.class);
  private final XceiverClientManager xceiverClientManager;

  public ECContainerOperationClient(XceiverClientManager clientManager) {
    this.xceiverClientManager = clientManager;
  }

  public ECContainerOperationClient(ConfigurationSource conf,
      CertificateClient certificateClient) throws IOException {
    this(createClientManager(conf, certificateClient));
  }

  @NotNull
  private static XceiverClientManager createClientManager(
      ConfigurationSource conf, CertificateClient certificateClient)
      throws IOException {
    return new XceiverClientManager(conf,
        new XceiverClientManager.XceiverClientManagerConfigBuilder()
            .setMaxCacheSize(256).setStaleThresholdMs(10 * 1000).build(),
        certificateClient != null ?
            HAUtils.buildCAX509List(certificateClient, conf) :
            null);
  }

  public BlockData[] listBlock(long containerId, DatanodeDetails dn,
      ECReplicationConfig repConfig, Token<? extends TokenIdentifier> token)
      throws IOException {
    XceiverClientSpi xceiverClient = this.xceiverClientManager
        .acquireClient(singleNodePipeline(dn, repConfig));
    try {
      List<ContainerProtos.BlockData> blockDataList = ContainerProtocolCalls
          .listBlock(xceiverClient, containerId, null, Integer.MAX_VALUE, token)
          .getBlockDataList();
      return blockDataList.stream().map(i -> {
        try {
          return BlockData.getFromProtoBuf(i);
        } catch (IOException e) {
          LOG.debug("Failed while converting to protobuf BlockData. Returning"
                  + " null for listBlock from DN: " + dn,
              e);
          // TODO: revisit here.
          return null;
        }
      }).collect(Collectors.toList())
          .toArray(new BlockData[blockDataList.size()]);
    } finally {
      this.xceiverClientManager.releaseClient(xceiverClient, false);
    }
  }

  public void closeContainer(long containerID, DatanodeDetails dn,
      ECReplicationConfig repConfig, String encodedToken) throws IOException {
    XceiverClientSpi xceiverClient = this.xceiverClientManager
        .acquireClient(singleNodePipeline(dn, repConfig));
    try {
      ContainerProtocolCalls
          .closeContainer(xceiverClient, containerID, encodedToken);
    } finally {
      this.xceiverClientManager.releaseClient(xceiverClient, false);
    }
  }

  public void createRecoveringContainer(long containerID, DatanodeDetails dn,
      ECReplicationConfig repConfig, String encodedToken, int replicaIndex)
      throws IOException {
    XceiverClientSpi xceiverClient = this.xceiverClientManager.acquireClient(
        singleNodePipeline(dn, repConfig));
    try {
      ContainerProtocolCalls
          .createRecoveringContainer(xceiverClient, containerID, encodedToken,
              replicaIndex);
    } finally {
      this.xceiverClientManager.releaseClient(xceiverClient, false);
    }
  }

  Pipeline singleNodePipeline(DatanodeDetails dn,
      ECReplicationConfig repConfig) {
    // To get the same client from cache, we try to use the DN UUID as
    // pipelineID for uniqueness. Please note, pipeline does not have any
    // significance after it's close. So, we are ok to use any ID.
    return Pipeline.newBuilder().setId(PipelineID.valueOf(dn.getUuid()))
        .setReplicationConfig(repConfig).setNodes(ImmutableList.of(dn))
        .setState(Pipeline.PipelineState.CLOSED).build();
  }

  public XceiverClientManager getXceiverClientManager() {
    return xceiverClientManager;
  }

  @Override
  public void close() throws IOException {
    if (xceiverClientManager != null) {
      xceiverClientManager.close();
    }
  }
}