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
package org.apache.maven.plugins.toolchain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.Lifecycle;
import org.apache.maven.api.Session;
import org.apache.maven.api.Toolchain;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.MavenException;
import org.apache.maven.api.services.ToolchainManager;
import org.apache.maven.api.xml.XmlNode;

/**
 * Check that toolchains requirements are met by currently configured toolchains in {@code toolchains.xml} and
 * store the selected toolchain in build context for later retrieval by other plugins.
 *
 * @author mkleint
 */
@Mojo(name = "toolchain", defaultPhase = Lifecycle.Phase.VALIDATE)
public class ToolchainMojo implements org.apache.maven.api.plugin.Mojo {

    private static final Object LOCK = new Object();

    /**
     * The current build session instance. This is used for toolchain manager API calls.
     */
    @Inject
    private Session session;

    /**
     * Toolchains requirements, specified by one
     * <pre>{@code   <toolchain-type>
     *     <param>expected value</param>
     *     ...
     *   </toolchain-type>}</pre>
     * element for each required toolchain.
     */
    @Parameter(required = true)
    private XmlNode toolchains;

    @Override
    public void execute() throws org.apache.maven.api.plugin.MojoException {
        if (toolchains == null) {
            // should not happen since parameter is required...
            getLog().warn("No toolchains requirements configured.");
            return;
        }
        ToolchainsRequirement toolchains = new ToolchainConverter().fromConfiguration(this.toolchains);
        List<String> nonMatchedTypes = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry :
                toolchains.getToolchains().entrySet()) {
            String type = entry.getKey();
            if (!selectToolchain(type, entry.getValue())) {
                nonMatchedTypes.add(type);
            }
        }
        if (!nonMatchedTypes.isEmpty()) {
            // TODO add the default toolchain instance if defined??
            StringBuilder buff = new StringBuilder();
            buff.append("Cannot find matching toolchain definitions for the following toolchain types:");
            for (String type : nonMatchedTypes) {
                buff.append(System.lineSeparator());
                buff.append(getToolchainRequirementAsString(type, toolchains.getParams(type)));
            }
            getLog().error(buff.toString());
            throw new org.apache.maven.api.plugin.MojoException(buff.toString() + System.lineSeparator()
                    + "Please make sure you define the required toolchains in your ~/.m2/toolchains.xml file.");
        }
    }

    protected String getToolchainRequirementAsString(String type, Map<String, String> params) {
        if (params.isEmpty()) {
            return type + " [ any ]";
        } else {
            StringBuilder buff = new StringBuilder();
            buff.append(type);
            buff.append(" [");
            params.forEach(
                    (k, v) -> buff.append(" ").append(k).append("='").append(v).append("'"));
            buff.append(" ]");
            return buff.toString();
        }
    }

    protected boolean selectToolchain(String type, Map<String, String> params)
            throws org.apache.maven.api.plugin.MojoException {
        getLog().info("Required toolchain: " + getToolchainRequirementAsString(type, params));
        int typeFound = 0;
        try {
            ToolchainManager toolchainManager = session.getService(ToolchainManager.class);
            List<Toolchain> tcs = getToolchains(type);
            for (Toolchain tc : tcs) {
                if (!type.equals(tc.getType())) {
                    // useful because of MNG-5716
                    continue;
                }
                typeFound++;
                if (tc.matchesRequirements(params)) {
                    getLog().info("Found matching toolchain for type " + type + ": " + tc);
                    // store matching toolchain to build context
                    synchronized (LOCK) {
                        toolchainManager.storeToolchainToBuildContext(session, tc);
                    }
                    return true;
                }
            }
        } catch (MavenException ex) {
            throw new MojoException("Misconfigured toolchains.", ex);
        }
        getLog().error("No toolchain " + ((typeFound == 0) ? "found" : ("matched from " + typeFound + " found"))
                + " for type " + type);
        return false;
    }

    private List<Toolchain> getToolchains(String type) {
        ToolchainManager toolchainManager = session.getService(ToolchainManager.class);
        return toolchainManager.getToolchains(session, type);
    }

    @Inject()
    private Log log;

    protected Log getLog() {
        return log;
    }
}
