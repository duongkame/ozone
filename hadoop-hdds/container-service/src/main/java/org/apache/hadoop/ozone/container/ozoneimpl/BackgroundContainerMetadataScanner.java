/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.hadoop.ozone.container.ozoneimpl;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.ozone.container.common.helpers.ContainerUtils;
import org.apache.hadoop.ozone.container.common.interfaces.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;

/**
 * This class is responsible to perform metadata verification of the
 * containers.
 * Only one thread will be responsible for scanning all volumes.
 */
public class BackgroundContainerMetadataScanner extends
    AbstractBackgroundContainerScanner {
  public static final Logger LOG =
      LoggerFactory.getLogger(BackgroundContainerMetadataScanner.class);

  private final ContainerMetadataScannerMetrics metrics;
  private final ContainerController controller;
  private final long minScanGap;

  public BackgroundContainerMetadataScanner(ContainerScannerConfiguration conf,
                                            ContainerController controller) {
    super("ContainerMetadataScanner", conf.getMetadataScanInterval());
    this.controller = controller;
    this.metrics = ContainerMetadataScannerMetrics.create();
    this.minScanGap = conf.getContainerScanMinGap();
  }

  @Override
  public Iterator<Container<?>> getContainerIterator() {
    return controller.getContainers().iterator();
  }

  @VisibleForTesting
  @Override
  public void scanContainer(Container<?> container) throws IOException {
    // Full data scan also does a metadata scan. If a full data scan was done
    // recently, we can skip this metadata scan.
    if (ContainerUtils.recentlyScanned(container, minScanGap, LOG)) {
      return;
    }

    // Do not update the scan timestamp since this was just a metadata scan,
    // not a full scan.
    if (!container.scanMetaData()) {
      metrics.incNumUnHealthyContainers();
      controller.markContainerUnhealthy(
          container.getContainerData().getContainerID());
    }
    metrics.incNumContainersScanned();
  }

  @Override
  public ContainerMetadataScannerMetrics getMetrics() {
    return this.metrics;
  }
}
