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
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.xml.ToolchainsXmlFactory;
import org.apache.maven.api.toolchain.PersistedToolchains;

/**
 * Run the JDK toolchain discovery mechanism and generates a toolchains XML.
 *
 * TODO: Maven 4 plugins need to be thread safe. Please verify and fix thread safety issues.
 */
@Mojo(name = "generate-jdk-toolchains-xml", projectRequired = false)
public class GenerateJdkToolchainsXmlMojo implements org.apache.maven.api.plugin.Mojo {

    /**
     * The path and name pf the toolchain XML file that will be generated.
     * If not provided, the XML will be written to the standard output.
     */
    @Parameter(property = "toolchain.file")
    String file;

    /**
     * Toolchain discoverer
     */
    @Inject
    ToolchainDiscoverer discoverer;

    @Inject
    Session session;

    @Override
    public void execute() throws org.apache.maven.api.plugin.MojoException {
        try {
            PersistedToolchains toolchains = discoverer.discoverToolchains();
            if (file != null) {
                Path file = Paths.get(this.file).toAbsolutePath();
                Files.createDirectories(file.getParent());
                session.getService(ToolchainsXmlFactory.class).write(toolchains, file);
            } else {
                StringWriter writer = new StringWriter();
                session.getService(ToolchainsXmlFactory.class).write(toolchains, writer);
                System.out.println(writer);
            }
        } catch (IOException e) {
            throw new org.apache.maven.api.plugin.MojoException("Unable to generate toolchains.xml", e);
        }
    }
}
