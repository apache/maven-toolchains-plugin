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
import java.util.Map;

import org.apache.maven.toolchain.ToolchainPrivate;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SelectJdkToolchainMojoTest {

    @Test
    void testGetJdkHomeWithNonXpp3DomConfiguration() throws Exception {
        ToolchainModel model = new ToolchainModel();
        model.setConfiguration("string-config");
        ToolchainPrivate toolchain = createStub(model);
        assertNull(invokeGetJdkHome(toolchain));
    }

    @Test
    void testGetJdkHomeWithNullModel() throws Exception {
        ToolchainPrivate toolchain = createStub(null);
        assertNull(invokeGetJdkHome(toolchain));
    }

    @Test
    void testGetJdkHomeWithNullConfiguration() throws Exception {
        ToolchainModel model = new ToolchainModel();
        model.setConfiguration(null);
        ToolchainPrivate toolchain = createStub(model);
        assertNull(invokeGetJdkHome(toolchain));
    }

    @Test
    void testGetJdkHomeWithMissingJdkHomeChild() throws Exception {
        Xpp3Dom config = new Xpp3Dom("configuration");
        ToolchainModel model = new ToolchainModel();
        model.setConfiguration(config);
        ToolchainPrivate toolchain = createStub(model);
        assertNull(invokeGetJdkHome(toolchain));
    }

    @Test
    void testGetJdkHomeWithValidJdkHome() throws Exception {
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom jdkHome = new Xpp3Dom("jdkHome");
        jdkHome.setValue("/path/to/jdk");
        config.addChild(jdkHome);
        ToolchainModel model = new ToolchainModel();
        model.setConfiguration(config);
        ToolchainPrivate toolchain = createStub(model);
        assertEquals("/path/to/jdk", invokeGetJdkHome(toolchain));
    }

    private String invokeGetJdkHome(ToolchainPrivate toolchain) throws Exception {
        SelectJdkToolchainMojo mojo = new SelectJdkToolchainMojo();
        Method method = SelectJdkToolchainMojo.class.getDeclaredMethod("getJdkHome", ToolchainPrivate.class);
        method.setAccessible(true);
        return (String) method.invoke(mojo, toolchain);
    }

    private ToolchainPrivate createStub(ToolchainModel model) {
        return new ToolchainPrivate() {
            @Override
            public ToolchainModel getModel() {
                return model;
            }

            @Override
            public boolean matchesRequirements(Map<String, String> requirements) {
                return false;
            }

            @Override
            public String getType() {
                return "jdk";
            }

            @Override
            public String findTool(String toolName) {
                return null;
            }
        };
    }
}
