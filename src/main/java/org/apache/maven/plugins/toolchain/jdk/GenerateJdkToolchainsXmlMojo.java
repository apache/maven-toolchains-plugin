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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.io.xpp3.MavenToolchainsXpp3Writer;

/**
 * Run the JDK discovery mechanism and generates the toolchains XML.
 */
@Mojo(name = "generate-jdk-toolchains-xml", requiresProject = false)
public class GenerateJdkToolchainsXmlMojo extends AbstractMojo {

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

    @Override
    public void execute() throws MojoFailureException {
        try {
            PersistedToolchains toolchains = discoverer.discoverToolchains();
            if (file != null) {
                Path file = Paths.get(this.file).toAbsolutePath();
                Files.createDirectories(file.getParent());
                try (Writer writer = Files.newBufferedWriter(file)) {
                    new MavenToolchainsXpp3Writer().write(writer, toolchains);
                }
            } else {
                StringWriter writer = new StringWriter();
                new MavenToolchainsXpp3Writer().write(writer, toolchains);
                System.out.println(writer);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Unable to generate toolchains.xml", e);
        }
    }
}
