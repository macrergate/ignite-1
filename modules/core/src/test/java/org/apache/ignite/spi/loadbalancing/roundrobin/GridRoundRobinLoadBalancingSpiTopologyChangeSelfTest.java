/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.spi.loadbalancing.roundrobin;

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.compute.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.testframework.*;
import org.apache.ignite.testframework.junits.spi.*;

import java.util.*;

import static org.apache.ignite.spi.loadbalancing.roundrobin.GridRoundRobinTestUtils.*;

/**
 * Tests round robin load balancing with topology changes.
 */
@GridSpiTest(spi = RoundRobinLoadBalancingSpi.class, group = "Load Balancing SPI")
public class GridRoundRobinLoadBalancingSpiTopologyChangeSelfTest
    extends GridSpiAbstractTest<RoundRobinLoadBalancingSpi> {
    /**
     * @return Per-task configuration parameter.
     */
    @GridSpiTestConfig
    public boolean getPerTask() { return false; }

    /** {@inheritDoc} */
    @Override protected GridSpiTestContext initSpiContext() throws Exception {
        GridSpiTestContext spiCtx = super.initSpiContext();

        spiCtx.createLocalNode();
        spiCtx.createRemoteNodes(10);

        return spiCtx;
    }

    /**
     * @throws Exception If failed.
     */
    public void testTopologyChange() throws Exception {
        ComputeTaskSession ses = new GridTestTaskSession(IgniteUuid.randomUuid());

        // Warm up.
        List<ClusterNode> allNodes = (List<ClusterNode>)getSpiContext().nodes();

        List<UUID> orderedNodes = getSpi().getNodeIds(ses);

        checkCyclicBalancing(getSpi(), allNodes, orderedNodes, ses);

        // Remove node.
        UUID doomed = orderedNodes.get(0);

        if (getSpiContext().localNode().id().equals(doomed))
            doomed = orderedNodes.get(1);

        getSpiContext().removeNode(doomed);

        assertTrue(allNodes.remove(new GridTestNode(doomed)));

        orderedNodes = getSpi().getNodeIds(ses);

        assertFalse("Balancer uses removed node", orderedNodes.contains(doomed));

        checkCyclicBalancing(getSpi(), allNodes, orderedNodes, ses);

        // Add node.
        ClusterNode newNode = new GridTestNode(UUID.randomUUID());

        getSpiContext().addNode(newNode);

        assertTrue(allNodes.add(newNode));

        // Check that new node was added to balancing.
        boolean foundNewNode = false;

        for (int i = 0; i < allNodes.size(); i++) {
            ClusterNode node = getSpi().getBalancedNode(ses, allNodes, new GridTestJob());
            if (newNode.id().equals(node.id())) {
                foundNewNode = true;
                break;
            }
        }

        assertTrue("Balancer doesn't use added node", foundNewNode);

        orderedNodes = getSpi().getNodeIds(ses);

        checkCyclicBalancing(getSpi(), allNodes, orderedNodes, ses);
    }
}
