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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.MisconfiguredToolchainException;
import org.apache.maven.toolchain.ToolchainsBuilder;
import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.apache.maven.toolchain.model.io.xpp3.MavenToolchainsXpp3Writer;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.XmlStreamWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Add a toolchain to the persisted toolchain configuration of the user.
 * The persisted toolchain configuration is located in {@code ~/.m2/toolchains.xml} by default.
 */
@Mojo( name = "add-toolchain", requiresProject = false, requiresDirectInvocation = true )
public class AddToolchainMojo extends AbstractMojo
{

    private static final String JDK_RELEASE_FILENAME = "release";
    private static final String JDK_VERSION_PROPERTY = "JAVA_VERSION";
    private static final String JDK_VENDOR_PROPERTY = "IMPLEMENTOR";
    private static final String TOOLCHAIN_TYPE_JDK = "jdk";

    /**
     * The path to the JDK home directory to add to the toolchains file.
     */
    @Parameter( property = "toolchains.jdkHome", required = true )
    private File jdkHome;

    /**
     * The ID to be assigned to the new toolchain.
     */
    @Parameter( property = "toolchains.id", required = true )
    private String toolchainId;

    /**
     * The vendor of the JDK.
     * In case this property is not given the vendor is automatically determined for JDK 9+ and not set otherwise.
     */
    @Parameter( property = "toolchains.jdkVendor" )
    private String jdkVendor;

    /**
     * The current build session instance.
     * This is used to determine the toolchain file.
     */
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;

    @Requirement
    private ToolchainsBuilder toolchainsBuilder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if ( jdkHome == null )
        {
            throw new MojoFailureException( "Missing required parameter jdkHome" );
        }
        else if ( !jdkHome.isDirectory() )
        {
            throw new MojoFailureException( "JDK home " + jdkHome.getAbsolutePath() + " does not exist" );
        }

        // Fetch information about the JDK from the JDK release file
        final Properties jdkReleaseProperties = fetchJdkReleaseProperties( jdkHome );

        // Determine the JDK version
        final String jdkVersion = jdkReleaseProperties.getProperty( JDK_VERSION_PROPERTY );
        if ( jdkVersion == null )
        {
            throw new MojoExecutionException(
                    JDK_VERSION_PROPERTY + " missing in release file in JDK " + jdkHome.getAbsolutePath()
            );
        }

        // Attempt to determine the vendor of the JDK in case it was not specified.
        // This only works for JDK 9+.
        final String effectiveJdkVendor =
                jdkVendor != null ? jdkVendor : jdkReleaseProperties.getProperty( JDK_VENDOR_PROPERTY );

        // Fetch already persisted toolchains
        final File toolchainsFile = session.getRequest().getUserToolchainsFile();
        final PersistedToolchains persistedToolchains = fetchPersistedToolchains( toolchainsFile, toolchainsBuilder );

        // Add the new toolchain to the already persisted ones
        final ToolchainModel toolchain = buildToolchain( toolchainId, jdkVersion, effectiveJdkVendor, jdkHome );
        persistedToolchains.addToolchain( toolchain );

        // Persist all toolchains
        try ( final XmlStreamWriter toolchainWriter = WriterFactory.newXmlWriter( toolchainsFile ) )
        {
            new MavenToolchainsXpp3Writer().write( toolchainWriter, persistedToolchains );
        }
        catch ( final IOException e )
        {
            throw new MojoExecutionException( "Cannot persist toolchains to " + toolchainsFile.getAbsolutePath() );
        }
    }

    /**
     * Fetches information about a JDK by reading the contained release properties file.
     *
     * @param jdkHome The home directory of the JDK to fetch information for.
     * @return the properties of the JDK release file.
     * @throws MojoExecutionException if the JDK release file cannot be read.
     */
    private static Properties fetchJdkReleaseProperties( final File jdkHome ) throws MojoExecutionException
    {
        final File jdkReleaseFile = new File( jdkHome, JDK_RELEASE_FILENAME );
        if ( !jdkReleaseFile.isFile() )
        {
            throw new MojoExecutionException(
                    "Release file " + jdkReleaseFile.getAbsolutePath() + " missing in JDK"
            );
        }

        final Properties jdkReleaseProperties = new Properties();
        try ( final Reader jdkReleaseFileReader =
                      Files.newBufferedReader( jdkReleaseFile.toPath(), StandardCharsets.UTF_8 )
        )
        {
            jdkReleaseProperties.load( jdkReleaseFileReader );
        }
        catch ( final IOException e )
        {
            throw new MojoExecutionException(
                    "Cannot read JDK release properties file " + jdkReleaseFile.getAbsolutePath(), e
            );
        }

        return jdkReleaseProperties;
    }

    /**
     * Fetches the currently persisted toolchains from the specified toolchains.xml file.
     *
     * @param toolchainsFile The Maven toolchains.xml file.
     * @param toolchainsBuilder The Maven toolchains deserializer.
     * @return the currently persisted toolchains or an empty toolchains list; never null.
     * @throws MojoExecutionException if the toolchains file exists, but can not be read.
     */
    private static PersistedToolchains fetchPersistedToolchains(
            final File toolchainsFile, final ToolchainsBuilder toolchainsBuilder
    ) throws MojoExecutionException
    {
        final PersistedToolchains persistedToolchains;
        if ( toolchainsFile.isFile() )
        {
            try
            {
                persistedToolchains = toolchainsBuilder.build( toolchainsFile );
            }
            catch ( final MisconfiguredToolchainException e )
            {
                throw new MojoExecutionException(
                        "Cannot acquire persisted toolchains from " + toolchainsFile.getAbsolutePath(), e
                );
            }
        }
        else
        {
            persistedToolchains = new PersistedToolchains();
        }

        return persistedToolchains;
    }

    /**
     * Builds a model for a new toolchain.
     *
     * @param toolchainId The ID of the new toolchain.
     * @param jdkVersion The JDK version of the new toolchain.
     * @param jdkVendor The vendor of the new toolchain; may be null.
     * @param jdkHome The JDK home directory of the new toolchain.
     * @return the model of the new toolchain.
     */
    private static ToolchainModel buildToolchain(
            final String toolchainId, final String jdkVersion, final String jdkVendor, final File jdkHome
    )
    {
        final Xpp3Dom toolchainProvides = new Xpp3Dom( "provides" );

        final Xpp3Dom toolchainProvidesId = new Xpp3Dom( "id" );
        toolchainProvidesId.setValue( toolchainId );
        toolchainProvides.addChild( toolchainProvidesId );

        final Xpp3Dom toolchainProvidesVersion = new Xpp3Dom( "version" );
        toolchainProvidesVersion.setValue( jdkVersion );
        toolchainProvides.addChild( toolchainProvidesVersion );

        if ( jdkVendor != null )
        {
            final Xpp3Dom toolchainProvidesVendor = new Xpp3Dom( "vendor" );
            toolchainProvidesVendor.setValue( jdkVendor );
            toolchainProvides.addChild( toolchainProvidesVendor );
        }

        final Xpp3Dom toolchainConfig = new Xpp3Dom( "configuration" );

        final Xpp3Dom toolchainConfigJdkHome = new Xpp3Dom( "jdkHome" );
        toolchainConfigJdkHome.setValue( jdkHome.getAbsolutePath() );
        toolchainConfig.addChild( toolchainConfigJdkHome );

        final ToolchainModel toolchain = new ToolchainModel();
        toolchain.setType( TOOLCHAIN_TYPE_JDK );
        toolchain.setProvides( toolchainProvides );
        toolchain.setConfiguration( toolchainConfig );

        return toolchain;
    }

}
