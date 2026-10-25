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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.CURRENT;
import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.VERSION;
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
    void timedOutJdkDoesNotDiscardHealthyToolchains() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer() {
            @Override
            ToolchainModel getToolchainModel(Path jdk) {
                if ("timed-out".equals(jdk.getFileName().toString())) {
                    return null;
                }
                ToolchainModel model = new ToolchainModel();
                model.setType("jdk");
                model.addProvide(VERSION, "21");
                return model;
            }
        };
        Set<Path> jdks = new HashSet<>(Arrays.asList(Paths.get("healthy"), Paths.get("timed-out")));
        Map<Path, Map<String, String>> flags = Collections.emptyMap();

        List<ToolchainModel> models = discoverer.discoverToolchainModels(jdks, flags, VERSION);

        assertEquals(1, models.size());
        assertEquals("21", models.get(0).getProvides().getProperty(VERSION));
    }

    @Test
    @DisabledOnJre(JRE.JAVA_8) // java 8 often has jdk != jre
    void testInvalidJdkDoesNotPreventDiscovery(@TempDir Path tempDir) throws IOException {
        Path invalidJdk = tempDir.resolve(".jdks").resolve("invalid-jdk");
        Path bin = invalidJdk.resolve("bin");
        Files.createDirectories(bin);
        Path java = bin.resolve("java");
        Files.write(java, "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
        Files.write(bin.resolve("javac"), new byte[0]);
        java.toFile().setExecutable(true);

        String originalUserHome = System.getProperty(ToolchainDiscoverer.USER_HOME);
        System.setProperty(ToolchainDiscoverer.USER_HOME, tempDir.toString());
        try {
            PersistedToolchains toolchains = new ToolchainDiscoverer().discoverToolchains();
            assertTrue(toolchains.getToolchains().stream()
                    .anyMatch(tc -> tc.getProvides().containsKey(CURRENT)));
        } finally {
            System.setProperty(ToolchainDiscoverer.USER_HOME, originalUserHome);
        }
    }
}
