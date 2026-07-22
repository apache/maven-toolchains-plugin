/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.toolchain.jdk;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.CURRENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolchainDiscovererTest {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Test
    @DisabledOnJre(JRE.JAVA_8) // java 8 often has jdk != jre
    void testDiscovery() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer();
        PersistedToolchains persistedToolchains = discoverer.discoverToolchains();
        assertNotNull(persistedToolchains);

        persistedToolchains.getToolchains().forEach(model -> {
            logger.info("  - "
                    + ((Xpp3Dom) model.getConfiguration()).getChild("jdkHome").getValue());
            logger.info("    provides:");
            model.getProvides().forEach((k, v) -> logger.info("      " + k + ": " + v));
        });

        assertTrue(persistedToolchains.getToolchains().stream()
                .anyMatch(tc -> tc.getProvides().containsKey(CURRENT)));
    }

    @Test
    void testVersionComparator() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer();

        ToolchainModel jdk8 = new ToolchainModel();
        jdk8.setType("jdk");
        jdk8.addProvide("version", "8");

        ToolchainModel jdk11 = new ToolchainModel();
        jdk11.setType("jdk");
        jdk11.addProvide("version", "11");

        ToolchainModel jdk17 = new ToolchainModel();
        jdk17.setType("jdk");
        jdk17.addProvide("version", "17");

        List<ToolchainModel> list = new ArrayList<>();
        list.add(jdk8);
        list.add(jdk17);
        list.add(jdk11);

        list.sort(discoverer.version());

        assertEquals("17", list.get(0).getProvides().getProperty("version"));
        assertEquals("11", list.get(1).getProvides().getProperty("version"));
        assertEquals("8", list.get(2).getProvides().getProperty("version"));
    }
}
