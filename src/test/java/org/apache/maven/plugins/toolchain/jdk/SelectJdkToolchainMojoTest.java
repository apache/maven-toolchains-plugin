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

import org.junit.jupiter.api.Test;

import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.VERSION;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectJdkToolchainMojoTest {

    private final SelectJdkToolchainMojo mojo = new SelectJdkToolchainMojo();

    // MTOOLCHAINS-58: DefaultArtifactVersion does not parse the legacy (pre-Java 9) "1.8.0_432" scheme, so a
    // requirement like "8", "1.8" or "1.8.0" must still match such an actual version via the feature-version
    // fallback in SelectJdkToolchainMojo.matches().

    @Test
    void matchesLegacyVersionRequirementAgainstFullyQualifiedActualVersion() {
        assertTrue(mojo.matches(VERSION, "8", "1.8.0_432"));
        assertTrue(mojo.matches(VERSION, "1.8", "1.8.0_432"));
        assertTrue(mojo.matches(VERSION, "1.8.0", "1.8.0_432"));
    }

    @Test
    void stillMatchesExactLegacyVersion() {
        assertTrue(mojo.matches(VERSION, "1.8.0_432", "1.8.0_432"));
    }

    @Test
    void matchesFeatureVersionRequirementAgainstCurrentSchemeActualVersion() {
        // the same gap exists for the current (JEP 223) scheme: a bare feature-version requirement like "21"
        // does not match a fully qualified actual version like "21.0.1" either, unless a version range is used.
        assertTrue(mojo.matches(VERSION, "21", "21.0.1"));
        assertTrue(mojo.matches(VERSION, "11", "11.0.2"));
    }

    @Test
    void doesNotMatchDifferentJdkFeatureVersions() {
        assertFalse(mojo.matches(VERSION, "11", "1.8.0_432"));
        assertFalse(mojo.matches(VERSION, "1.8", "11.0.2"));
        assertFalse(mojo.matches(VERSION, "17", "21.0.1"));
    }
}
