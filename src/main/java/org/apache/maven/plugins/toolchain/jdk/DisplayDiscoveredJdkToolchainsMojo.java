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

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.toolchain.PersistedToolchains;
import org.apache.maven.api.toolchain.ToolchainModel;

import static java.util.Comparator.comparing;
import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.SORTED_PROVIDES;

/**
 * Discover the JDK toolchains and print them to the console.
 *
 * TODO: Maven 4 plugins need to be thread safe. Please verify and fix thread safety issues.
 */
@Mojo(name = "display-discovered-jdk-toolchains", projectRequired = false)
public class DisplayDiscoveredJdkToolchainsMojo implements org.apache.maven.api.plugin.Mojo {

    /**
     * Comparator used to sort JDK toolchains for selection.
     * This property is a comma separated list of values which may contains:
     * <ul>
     * <li>{@code lts}: prefer JDK with LTS version</li>
     * <li>{@code current}: prefer the current JDK</li>
     * <li>{@code env}: prefer JDKs defined using {@code JAVA\{xx\}_HOME} environment variables</li>
     * <li>{@code version}: prefer JDK with higher versions</li>
     * <li>{@code vendor}: order JDK by vendor name (usually as a last comparator to ensure a stable order)</li>
     * </ul>
     */
    @Parameter(property = "toolchain.jdk.comparator", defaultValue = "lts,current,env,version,vendor")
    String comparator;

    /**
     * Toolchain discoverer
     */
    @Inject
    ToolchainDiscoverer discoverer;

    @Override
    public void execute() {
        PersistedToolchains toolchains = discoverer.discoverToolchains(comparator);
        List<ToolchainModel> models = toolchains.getToolchains();
        getLog().info("Discovered " + models.size() + " JDK toolchains:");
        for (ToolchainModel model : models) {
            getLog().info("  - " + model.getConfiguration().getChild("jdkHome").getValue());
            getLog().info("    provides:");
            model.getProvides().entrySet().stream()
                    .sorted(comparing(e -> SORTED_PROVIDES.indexOf(e.getKey().toString())))
                    .forEach(e -> getLog().info("      " + e.getKey() + ": " + e.getValue()));
        }
    }

    @Inject()
    private Log log;

    protected Log getLog() {
        return log;
    }
}
