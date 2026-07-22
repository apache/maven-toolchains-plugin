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

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.VERSION;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectJdkToolchainMojoTest {

    private static Method matchesMethod;

    @BeforeAll
    static void setUp() throws Exception {
        matchesMethod =
                SelectJdkToolchainMojo.class.getDeclaredMethod("matches", String.class, String.class, String.class);
        matchesMethod.setAccessible(true);
    }

    private boolean invokeMatches(String key, String reqVal, String tcVal) throws Exception {
        return (boolean) matchesMethod.invoke(new SelectJdkToolchainMojo(), key, reqVal, tcVal);
    }

    @Test
    void versionRangeShouldMatchToolchainVersion() throws Exception {
        // reqVal is the user's requirement (e.g., version range), tcVal is the toolchain's provided version
        assertTrue(invokeMatches(VERSION, "[11,17)", "11.0.1"));
    }

    @Test
    void exactVersionShouldMatchToolchainVersion() throws Exception {
        assertTrue(invokeMatches(VERSION, "11.0.1", "11.0.1"));
    }

    @Test
    void versionRangeShouldRejectToolchainVersionOutsideRange() throws Exception {
        assertFalse(invokeMatches(VERSION, "[11,17)", "17.0.1"));
    }
}
