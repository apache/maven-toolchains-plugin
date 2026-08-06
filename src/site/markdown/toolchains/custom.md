<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Custom Toolchains

You can create custom toolchains to use with standard plugins (such as the maven-compiler-plugin) or with custom plugins.

A full working sample is included in the `maven-toolchains-plugin` integration tests, which are part of [the plugin source tree](../scm.html):

- See `src/it/setup-custom-toolchain` for the custom toolchain and plugin,
- See `src/it/use-custom-toolchain` for a sample project using the toolchain through its plugin.

Following instructions are explanations of key points of the sample.

## Creating a Custom Toolchain

A toolchain consists of:

- an interface extending [`org.apache.maven.toolchain.Toolchain`](/ref/current/maven-core/apidocs/org/apache/maven/toolchain/Toolchain.html),
- an implementation of this interface. Extending [`org.apache.maven.toolchain.DefaultToolchain`](/ref/current/maven-core/apidocs/org/apache/maven/toolchain/DefaultToolchain.html) is strongly encouraged, since it provides [`org.apache.maven.toolchain.ToolchainPrivate`](/ref/current/maven-core/apidocs/org/apache/maven/toolchain/ToolchainPrivate.html), which is an internal requirement,
- a [`org.apache.maven.toolchain.ToolchainFactory`](/ref/current/maven-core/apidocs/org/apache/maven/toolchain/ToolchainFactory.html), provided as [JSR-330 component](https://maven.apache.org/maven-jsr330.html) annotated with `@Named("<toolchainType>")` (e.g. `@Named("custom")`) and `@Singleton`.
## Creating a Plugin Using a Toolchain

To find a tool, a plugin uses [`ToolchainManager`](/ref/current/maven-core/apidocs/org/apache/maven/toolchain/ToolchainManager.html) API to load the toolchain, then uses that toolchain object to find the tool's path:

```java
    @Component
    private ToolchainManager toolchainManager;

    @Component
    private MavenSession session;

    public void execute()
        throws MojoExecutionException
    {
        // get the custom toolchain
        CustomToolchain toolchain = (CustomToolchain) toolchainManager.getToolchainFromBuildContext( "custom", session );

        if ( toolchain == null )
        {
            throw new MojoExecutionException( "Could not find 'custom' toolchain: please check maven-toolchains-plugin configuration." );
        }

        getLog().info( "Found 'custom' toolchain in build context." );

        // get a tool from the toolchain
        String path = toolchain.findTool( "tool" );

        getLog().info( "Found expected tool named 'tool' at following location: " + path );
    }
```

This code uses [Maven Plugin Tool Annotations](/plugin-tools/maven-plugin-plugin/examples/using-annotations.html).

## Using the Custom Toolchain and its Plugin

The custom toolchain implementation needs to be shared between the toolchain-aware plugin and `maven-toolchains-plugin`. This is done using Maven extension:

- if the toolchain is packaged with the plugin, this is done by declaring the plugin as an extension:

    ```xml
          <plugin>
            <groupId>...</groupId>
            <artifactId>...</artifactId>
            <version>...</version>
            <extensions>true</extensions><!-- to share the custom toolchain with maven-toolchains-plugin -->
          </plugin>
    ```

- if the toolchain is packaged separately, to be shared by multiple plugins, it has to be declared as a build extension:

    ```xml
    <project>
      <build>
        <extensions>
          <extension>
            <groupId>...</groupId>
            <artifactId>...</artifactId>
            <version>...</version>
          </extension>
        </extensions>
      </build>
    </project>
    ```

Packaging a toolchain in its own artifact separate from the plugin is only useful when there are multiple plugins using the toolchain. If a custom toolchain will only be used by one plugin (eventually providing multiple goals), it is simpler to package the toolchain with the plugin in a single artifact.
