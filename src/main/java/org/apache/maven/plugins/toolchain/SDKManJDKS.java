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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Find the locally installed JDKs which have been installed via
 * <a href="https://sdkman.io">https://sdkman.io</a>
 *
 * @author Karl Heinz Marbaise
 */
public class SDKManJDKS
{
    private static final String SDKMAN_ROOT = ".sdkman";

    /**
     * We ignore the link in {@code .sdkman/candidates/java/current} for the current JDK.
     */
    private static final Predicate<Path> IGNORE_CURRENT_JDK_LINK = Files::isSymbolicLink;

    private final Path userHome;

    /**
     * @param userHome The users home directory typically something like {@code /home/username}.
     */
    public SDKManJDKS( Path userHome )
    {
        this.userHome = userHome;
    }

    public List<Path> listOfJdks()
    {
        try ( Stream<Path> walk = Files.list(
                this.userHome.resolve( SDKMAN_ROOT ).resolve( "candidates" ).resolve( "java" ) ) )
        {
            return walk.filter( Files::isDirectory )
                    .filter( IGNORE_CURRENT_JDK_LINK.negate() )
                    .collect( Collectors.toList() );
        }
        catch ( IOException e )
        {
            //TODO: Need to reconsider.
            throw new RuntimeException( "IOException happened", e );
        }
    }
}
