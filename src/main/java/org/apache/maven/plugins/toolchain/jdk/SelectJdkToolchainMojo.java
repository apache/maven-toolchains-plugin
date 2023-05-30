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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.MisconfiguredToolchainException;
import org.apache.maven.toolchain.ToolchainFactory;
import org.apache.maven.toolchain.ToolchainManagerPrivate;
import org.apache.maven.toolchain.ToolchainPrivate;
import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Check that toolchains requirements are met by currently configured toolchains and
 * store the selected toolchains in build context for later retrieval by other plugins.
 */
@Mojo(name = "select-jdk-toolchain", defaultPhase = LifecyclePhase.VALIDATE)
public class SelectJdkToolchainMojo extends AbstractMojo {

    public static final String TOOLCHAIN_TYPE_JDK = "jdk";

    /** Jdk usage mode */
    public enum JdkMode {
        /** always ignore the current JDK */
        Never,
        /** to not use a toolchain if the toolchains that would be selected is the current JDK */
        IfSame,
        /** favor the current JDK if it matches the requirements */
        IfMatch
    }

    /**
     */
    @Component
    private ToolchainManagerPrivate toolchainManager;

    /**
     */
    @Component(hint = TOOLCHAIN_TYPE_JDK)
    ToolchainFactory factory;

    /**
     * The current build session instance. This is used for toolchain manager API calls.
     */
    @Component
    private MavenSession session;

    @Parameter(property = "toolchain.jdk.version")
    private String version;

    @Parameter(property = "toolchain.jdk.runtime.name")
    private String runtimeName;

    @Parameter(property = "toolchain.jdk.runtime.version")
    private String runtimeVersion;

    @Parameter(property = "toolchain.jdk.vendor")
    private String vendor;

    @Parameter(property = "toolchain.jdk.mode", defaultValue = "IfMatch")
    private JdkMode useJdk = JdkMode.IfMatch;

    @Parameter(property = "toolchain.jdk.discover", defaultValue = "true")
    private boolean discoverToolchains = true;

    @Override
    public void execute() throws MojoFailureException {
        try {
            doExecute();
        } catch (MisconfiguredToolchainException e) {
            throw new MojoFailureException("Unable to select toolchain: " + e, e);
        }
    }

    private void doExecute() throws MisconfiguredToolchainException, MojoFailureException {
        if (version == null && runtimeName == null && runtimeVersion == null && vendor == null) {
            return;
        }

        ToolchainDiscoverer discoverer = new ToolchainDiscoverer(getLog());

        Map<String, String> requirements = new HashMap<>();
        Optional.ofNullable(version).ifPresent(v -> requirements.put("version", v));
        Optional.ofNullable(runtimeName).ifPresent(v -> requirements.put("runtime.name", v));
        Optional.ofNullable(runtimeVersion).ifPresent(v -> requirements.put("runtime.version", v));
        Optional.ofNullable(vendor).ifPresent(v -> requirements.put("vendor", v));

        ToolchainModel currentJdkToolchainModel = discoverer.getCurrentJdkToolchain();
        ToolchainPrivate currentJdkToolchain = factory.createToolchain(currentJdkToolchainModel);

        if (useJdk == JdkMode.IfMatch
                && currentJdkToolchain != null
                && currentJdkToolchain.matchesRequirements(requirements)) {
            getLog().info("Not using an external toolchain as the current JDK matches the requirements.");
            return;
        }

        ToolchainPrivate toolchain = Stream.of(toolchainManager.getToolchainsForType(TOOLCHAIN_TYPE_JDK, session))
                .filter(tc -> tc.matchesRequirements(requirements))
                .findFirst()
                .orElse(null);
        if (toolchain != null) {
            getLog().info("Found matching JDK toolchain: " + toolchain);
        } else {
            getLog().debug("No matching toolchains configured, trying to discover JDK toolchains");
            PersistedToolchains persistedToolchains = discoverer.discoverToolchains();
            getLog().info("Discovered " + persistedToolchains.getToolchains().size() + " JDK toolchains");
            for (ToolchainModel tcm : persistedToolchains.getToolchains()) {
                ToolchainPrivate tc = factory.createToolchain(tcm);
                if (tc != null && tc.matchesRequirements(requirements)) {
                    toolchain = tc;
                    getLog().debug("Discovered matching JDK toolchain: " + toolchain);
                    break;
                }
            }
        }
        if (toolchain == null) {
            throw new MojoFailureException(
                    "Cannot find matching toolchain definitions for the following toolchain types:" + requirements
                            + System.lineSeparator()
                            + "Please make sure you define the required toolchains in your ~/.m2/toolchains.xml file.");
        }
        if (useJdk == JdkMode.IfSame
                && currentJdkToolchain != null
                && Objects.equals(getJdkHome(currentJdkToolchain), getJdkHome(toolchain))) {
            getLog().info("Not using an external toolchain as the current JDK has been selected.");
            return;
        }
        toolchainManager.storeToolchainToBuildContext(toolchain, session);
        getLog().info("Found matching JDK toolchain: " + toolchain);
    }

    private String getJdkHome(ToolchainPrivate toolchain) {
        return ((Xpp3Dom) toolchain.getModel().getConfiguration())
                .getChild("jdkHome")
                .getValue();
    }
}
