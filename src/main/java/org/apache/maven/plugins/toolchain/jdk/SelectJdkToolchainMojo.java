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

import javax.inject.Inject;
import javax.inject.Named;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.MisconfiguredToolchainException;
import org.apache.maven.toolchain.RequirementMatcherFactory;
import org.apache.maven.toolchain.ToolchainFactory;
import org.apache.maven.toolchain.ToolchainManagerPrivate;
import org.apache.maven.toolchain.ToolchainPrivate;
import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.ENV;
import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.RUNTIME_NAME;
import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.RUNTIME_VERSION;
import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.VENDOR;
import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.VERSION;

/**
 * Discover JDK toolchains and select a matching one.
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
     * The version constraint for the JDK toolchain to select.
     */
    @Parameter(property = "toolchain.jdk.version")
    private String version;

    /**
     * The runtime name constraint for the JDK toolchain to select.
     */
    @Parameter(property = "toolchain.jdk.runtime.name")
    private String runtimeName;

    /**
     * The runtime version constraint for the JDK toolchain to select.
     */
    @Parameter(property = "toolchain.jdk.runtime.version")
    private String runtimeVersion;

    /**
     * The vendor constraint for the JDK toolchain to select.
     */
    @Parameter(property = "toolchain.jdk.vendor")
    private String vendor;

    /**
     * The env constraint for the JDK toolchain to select.
     * To match the constraint, an environment variable with the given name must point to the JDK.
     * For example, if you define {@code JAVA11_HOME=~/jdks/my-jdk-11.0.1}, you can specify
     * {@code env=JAVA11_HOME} to match the given JDK.
     */
    @Parameter(property = "toolchain.jdk.env")
    private String env;

    /**
     * The matching mode, either {@code IfMatch} (the default), {@code IfSame}, or {@code Never}.
     * If {@code IfMatch} is used, a toolchain will not be selected if the running JDK does
     * match the provided constraints. This is the default and provides better performances as it
     * avoids forking a different process when it's not required. The {@code IfSame} avoids
     * selecting a toolchain if the toolchain selected is exactly the same as the running JDK.
     * THe {@code Never} option will always select the toolchain.
     */
    @Parameter(property = "toolchain.jdk.mode", defaultValue = "IfMatch")
    private JdkMode useJdk = JdkMode.IfMatch;

    /**
     * Automatically discover JDK toolchains using the built-in heuristic.
     * The default value is {@code true}.
     */
    @Parameter(property = "toolchain.jdk.discover", defaultValue = "true")
    private boolean discoverToolchains = true;

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
    private String comparator;

    /**
     * Toolchain manager
     */
    @Inject
    private ToolchainManagerPrivate toolchainManager;

    /**
     * Toolchain factory
     */
    @Inject
    @Named(TOOLCHAIN_TYPE_JDK)
    ToolchainFactory factory;

    /**
     * The current build session instance. This is used for toolchain manager API calls.
     */
    @Inject
    private MavenSession session;

    /**
     * Toolchain discoverer
     */
    @Inject
    ToolchainDiscoverer discoverer;

    @Override
    public void execute() throws MojoFailureException {
        try {
            doExecute();
        } catch (MisconfiguredToolchainException e) {
            throw new MojoFailureException("Unable to select toolchain: " + e, e);
        }
    }

    private void doExecute() throws MisconfiguredToolchainException, MojoFailureException {
        if (version == null && runtimeName == null && runtimeVersion == null && vendor == null && env == null) {
            return;
        }

        Map<String, String> requirements = new HashMap<>();
        Optional.ofNullable(version).ifPresent(v -> requirements.put(VERSION, v));
        Optional.ofNullable(runtimeName).ifPresent(v -> requirements.put(RUNTIME_NAME, v));
        Optional.ofNullable(runtimeVersion).ifPresent(v -> requirements.put(RUNTIME_VERSION, v));
        Optional.ofNullable(vendor).ifPresent(v -> requirements.put(VENDOR, v));
        Optional.ofNullable(env).ifPresent(v -> requirements.put(ENV, v));

        ToolchainModel currentJdkToolchainModel =
                discoverer.getCurrentJdkToolchain().orElse(null);
        ToolchainPrivate currentJdkToolchain =
                currentJdkToolchainModel != null ? factory.createToolchain(currentJdkToolchainModel) : null;

        if (useJdk == JdkMode.IfMatch && currentJdkToolchain != null && matches(currentJdkToolchain, requirements)) {
            getLog().info("Not using an external toolchain as the current JDK matches the requirements.");
            return;
        }

        ToolchainPrivate toolchain = Stream.of(toolchainManager.getToolchainsForType(TOOLCHAIN_TYPE_JDK, session))
                .filter(tc -> matches(tc, requirements))
                .findFirst()
                .orElse(null);
        if (toolchain != null) {
            getLog().info("Found matching JDK toolchain: " + toolchain);
        }

        if (toolchain == null && discoverToolchains) {
            getLog().debug("No matching toolchains configured, trying to discover JDK toolchains");
            PersistedToolchains persistedToolchains = discoverer.discoverToolchains(comparator);
            getLog().debug("Discovered " + persistedToolchains.getToolchains().size() + " JDK toolchains");

            for (ToolchainModel tcm : persistedToolchains.getToolchains()) {
                ToolchainPrivate tc = factory.createToolchain(tcm);
                if (tc != null && matches(tc, requirements)) {
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
                            + "Define the required toolchains in your ~/.m2/toolchains.xml file.");
        }

        if (useJdk == JdkMode.IfSame
                && currentJdkToolchain != null
                && Objects.equals(getJdkHome(currentJdkToolchain), getJdkHome(toolchain))) {
            getLog().debug("Not using an external toolchain as the current JDK has been selected.");
            return;
        }

        toolchainManager.storeToolchainToBuildContext(toolchain, session);
        getLog().debug("Found matching JDK toolchain: " + toolchain);
    }

    private boolean matches(ToolchainPrivate tc, Map<String, String> requirements) {
        ToolchainModel model = tc.getModel();
        for (Map.Entry<String, String> req : requirements.entrySet()) {
            String key = req.getKey();
            String reqVal = req.getValue();
            String tcVal = model.getProvides().getProperty(key);
            if (tcVal == null) {
                getLog().debug("Toolchain " + tc + " is missing required property: " + key);
                return false;
            }
            if (!matches(key, reqVal, tcVal)) {
                getLog().debug("Toolchain " + tc + " doesn't match required property: " + key);
                return false;
            }
        }
        return true;
    }

    private boolean matches(String key, String reqVal, String tcVal) {
        switch (key) {
            case VERSION:
                return RequirementMatcherFactory.createVersionMatcher(tcVal).matches(reqVal);
            case ENV:
                return reqVal.matches("(.*,|^)\\Q" + tcVal + "\\E(,.*|$)");
            default:
                return RequirementMatcherFactory.createExactMatcher(tcVal).matches(reqVal);
        }
    }

    private String getJdkHome(ToolchainPrivate toolchain) {
        return ((Xpp3Dom) toolchain.getModel().getConfiguration())
                .getChild("jdkHome")
                .getValue();
    }
}
