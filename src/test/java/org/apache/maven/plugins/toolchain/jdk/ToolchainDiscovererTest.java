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

import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.services.xml.ToolchainsXmlFactory;
import org.apache.maven.api.toolchain.PersistedToolchains;
import org.apache.maven.impl.DefaultToolchainsXmlFactory;
import org.apache.maven.internal.impl.DefaultLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.CURRENT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class ToolchainDiscovererTest {

    final Log logger = new DefaultLog(LoggerFactory.getLogger(ToolchainDiscovererTest.class));

    @Test
    @DisabledOnJre(JRE.JAVA_8) // java 8 often has jdk != jre
    void testDiscovery() {
        Session session = Mockito.mock(Session.class);
        ToolchainsXmlFactory xml = new DefaultToolchainsXmlFactory();
        when(session.getService(ToolchainsXmlFactory.class)).thenReturn(xml);
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer(logger, session);
        PersistedToolchains persistedToolchains = discoverer.discoverToolchains();
        assertNotNull(persistedToolchains);

        persistedToolchains.getToolchains().forEach(model -> {
            logger.info("  - " + model.getConfiguration().getChild("jdkHome").getValue());
            logger.info("    provides:");
            model.getProvides().forEach((k, v) -> logger.info("      " + k + ": " + v));
        });

        assertTrue(persistedToolchains.getToolchains().stream()
                .anyMatch(tc -> tc.getProvides().containsKey(CURRENT)));
    }
}
