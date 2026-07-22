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
    void testVersionComparatorSimple() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer();

        List<ToolchainModel> list = new ArrayList<>();
        list.add(toolchain("8"));
        list.add(toolchain("17"));
        list.add(toolchain("11"));

        list.sort(discoverer.version());

        assertEquals("17", list.get(0).getProvides().getProperty("version"));
        assertEquals("11", list.get(1).getProvides().getProperty("version"));
        assertEquals("8", list.get(2).getProvides().getProperty("version"));
    }

    @Test
    void testVersionComparatorMultiPart() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer();

        List<ToolchainModel> list = new ArrayList<>();
        list.add(toolchain("11.0.1"));
        list.add(toolchain("11.0.31"));
        list.add(toolchain("17.0.1"));
        list.add(toolchain("1.8"));

        list.sort(discoverer.version());

        assertEquals("17.0.1", list.get(0).getProvides().getProperty("version"));
        assertEquals("11.0.31", list.get(1).getProvides().getProperty("version"));
        assertEquals("11.0.1", list.get(2).getProvides().getProperty("version"));
        assertEquals("1.8", list.get(3).getProvides().getProperty("version"));
    }

    @Test
    void testVersionComparatorMultiDigitSegments() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer();

        List<ToolchainModel> list = new ArrayList<>();
        list.add(toolchain("17.0.2"));
        list.add(toolchain("17.0.10"));

        list.sort(discoverer.version());

        assertEquals("17.0.10", list.get(0).getProvides().getProperty("version"));
        assertEquals("17.0.2", list.get(1).getProvides().getProperty("version"));
    }

    @Test
    void testVersionComparatorWithNonNumericSuffix() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer();

        List<ToolchainModel> list = new ArrayList<>();
        list.add(toolchain("1.8.0_202"));
        list.add(toolchain("1.8.0_121"));

        list.sort(discoverer.version());

        assertEquals("1.8.0_202", list.get(0).getProvides().getProperty("version"));
        assertEquals("1.8.0_121", list.get(1).getProvides().getProperty("version"));
    }

    @Test
    void testVersionComparatorWithEAPreRelease() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer();

        List<ToolchainModel> list = new ArrayList<>();
        list.add(toolchain("11-ea"));
        list.add(toolchain("11"));

        list.sort(discoverer.version());

        assertEquals("11", list.get(0).getProvides().getProperty("version"));
        assertEquals("11-ea", list.get(1).getProvides().getProperty("version"));
    }

    @Test
    void testVersionComparatorSuffixVsSuffix() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer();

        List<ToolchainModel> list = new ArrayList<>();
        list.add(toolchain("17.0.2+8"));
        list.add(toolchain("17.0.2+4"));

        list.sort(discoverer.version());

        assertEquals("17.0.2+8", list.get(0).getProvides().getProperty("version"));
        assertEquals("17.0.2+4", list.get(1).getProvides().getProperty("version"));
    }

    private static ToolchainModel toolchain(String version) {
        ToolchainModel model = new ToolchainModel();
        model.setType("jdk");
        model.addProvide("version", version);
        return model;
    }
}
