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

import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Run the JDK discoverer and display a summary of found toolchains.
 */
@Mojo(name = "display-discovered-jdk-toolchains", requiresProject = false)
public class DisplayDiscoveredJdkToolchainsMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoFailureException {
        try {
            PersistedToolchains toolchains = new ToolchainDiscoverer(getLog()).discoverToolchains();
            List<ToolchainModel> models = toolchains.getToolchains();
            getLog().info("Discovered " + models.size() + " JDK toolchains:");
            for (ToolchainModel model : models) {
                getLog().info("  - "
                        + ((Xpp3Dom) model.getConfiguration())
                                .getChild("jdkHome")
                                .getValue());
                getLog().info("    provides:");
                model.getProvides().forEach((k, v) -> getLog().info("      " + k + ": " + v));
            }
        } catch (Exception e) {
            throw new MojoFailureException("Unable to retrieve discovered toolchains", e);
        }
    }
}
