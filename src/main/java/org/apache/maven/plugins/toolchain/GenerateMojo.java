package org.apache.maven.plugins.toolchain;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.nio.file.Paths;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author khmarbaise
 */
@Mojo( name = "sdkman", defaultPhase = LifecyclePhase.NONE,
       configurator = "toolchains-requirement-configurator",
       threadSafe = true )
public class GenerateMojo
        extends AbstractMojo
{
    /**
     * The current build session instance. This is used for toolchain manager API calls.
     */
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;

    @Override
    public void execute()
    {
        SDKManJDKS sdkManJDKS = new SDKManJDKS( Paths.get( System.getProperty( "user.home" ) ) );
        sdkManJDKS.listOfJdks().stream()
                .forEach( jdk -> getLog().info( " JDK:" + jdk.getFileName() + " " + jdk.toAbsolutePath() ) );
    }

}
