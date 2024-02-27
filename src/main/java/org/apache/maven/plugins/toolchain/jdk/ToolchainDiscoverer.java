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
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.apache.maven.toolchain.model.io.xpp3.MavenToolchainsXpp3Reader;
import org.apache.maven.toolchain.model.io.xpp3.MavenToolchainsXpp3Writer;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Toolchain discoverer service
 */
public class ToolchainDiscoverer {

    private static final String DISCOVERED_TOOLCHAINS_CACHE_XML = ".m2/discovered-toolchains-cache.xml";

    private final Log log;

    private Map<Path, ToolchainModel> cache;
    private boolean cacheModified;

    public ToolchainDiscoverer(Log log) {
        this.log = log;
    }

    /**
     * Build the model for the current JDK toolchain
     */
    public ToolchainModel getCurrentJdkToolchain() {
        Path currentJdkHome = getCanonicalPath(Paths.get(System.getProperty("java.home")));
        if (!Files.exists(currentJdkHome.resolve("bin/javac"))
                && !Files.exists(currentJdkHome.resolve("bin/javac.exe"))) {
            // in case the current JVM is not a JDK
            return null;
        }

        ToolchainModel model = new ToolchainModel();
        model.setType("jdk");
        Stream.of("java.version", "java.runtime.name", "java.runtime.version", "java.vendor", "java.vendor.version")
                .forEach(k -> {
                    String v = System.getProperty(k);
                    if (v != null) {
                        model.addProvide(k.substring(5), v);
                    }
                });
        model.addProvide("current", "true");
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom jdkHome = new Xpp3Dom("jdkHome");
        jdkHome.setValue(currentJdkHome.toString());
        config.addChild(jdkHome);
        model.setConfiguration(config);
        return model;
    }

    /**
     * Returns a PersistedToolchains object containing a list of discovered toolchains,
     * never <code>null</code>.
     */
    public PersistedToolchains discoverToolchains() {
        try {
            Set<Path> jdks = findJdks();
            log.info("Found " + jdks.size() + " possible jdks: " + jdks);
            cacheModified = false;
            readCache();
            Path currentJdkHome = getCanonicalPath(Paths.get(System.getProperty("java.home")));
            List<ToolchainModel> tcs = jdks.parallelStream()
                    .map(s -> getToolchainModel(currentJdkHome, s))
                    .filter(Objects::nonNull)
                    .sorted(getToolchainModelComparator())
                    .collect(Collectors.toList());
            if (this.cacheModified) {
                writeCache();
            }
            PersistedToolchains ps = new PersistedToolchains();
            ps.setToolchains(tcs);
            return ps;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.warn("Error discovering toolchains: " + e, e);
            } else {
                log.warn("Error discovering toolchains (enable debug level for more information): " + e);
            }
            return new PersistedToolchains();
        }
    }

    private void readCache() {
        try {
            cache = new ConcurrentHashMap<>();
            Path cacheFile = getCacheFile();
            if (Files.isRegularFile(cacheFile)) {
                try (Reader r = Files.newBufferedReader(cacheFile)) {
                    PersistedToolchains pt = new MavenToolchainsXpp3Reader().read(r, false);
                    cache = pt.getToolchains().stream()
                            .collect(Collectors.toConcurrentMap(this::getJdkHome, Function.identity()));
                }
            }
        } catch (IOException | XmlPullParserException e) {
            log.warn("Error reading toolchains cache: " + e);
        }
    }

    private void writeCache() {
        try {
            Path cacheFile = getCacheFile();
            Files.createDirectories(cacheFile.getParent());
            try (Writer w = Files.newBufferedWriter(cacheFile)) {
                PersistedToolchains pt = new PersistedToolchains();
                List<ToolchainModel> toolchains = new ArrayList<>();
                for (ToolchainModel tc : cache.values()) {
                    tc = tc.clone();
                    tc.getProvides().remove("current");
                    toolchains.add(tc);
                }
                pt.setToolchains(toolchains);
                new MavenToolchainsXpp3Writer().write(w, pt);
            }
        } catch (IOException e) {
            log.warn("Error writing toolchains cache: " + e);
        }
    }

    private static Path getCacheFile() {
        return Paths.get(System.getProperty("user.home")).resolve(DISCOVERED_TOOLCHAINS_CACHE_XML);
    }

    private Path getJdkHome(ToolchainModel toolchain) {
        Xpp3Dom dom = (Xpp3Dom) toolchain.getConfiguration();
        Xpp3Dom javahome = dom != null ? dom.getChild("jdkHome") : null;
        String jdk = javahome != null ? javahome.getValue() : null;
        return Paths.get(Objects.requireNonNull(jdk));
    }

    ToolchainModel getToolchainModel(Path currentJdkHome, Path jdk) {
        log.debug("Computing model for " + jdk);

        ToolchainModel model = cache.get(jdk);
        if (model == null) {
            model = doGetToolchainModel(jdk);
            cache.put(jdk, model);
            cacheModified = true;
        }

        if (Objects.equals(jdk, currentJdkHome)) {
            model.getProvides().setProperty("current", "true");
        }
        return model;
    }

    ToolchainModel doGetToolchainModel(Path jdk) {
        Path bin = jdk.resolve("bin");
        Path java = bin.resolve("java");
        if (!java.toFile().canExecute()) {
            java = bin.resolve("java.exe");
            if (!java.toFile().canExecute()) {
                log.debug("JDK toolchain discovered at " + jdk + " will be ignored: unable to find java executable");
                return null;
            }
        }
        List<String> lines;
        try {
            Path temp = Files.createTempFile("jdk-opts-", ".out");
            try {
                new ProcessBuilder()
                        .command(java.toString(), "-XshowSettings:properties", "-version")
                        .redirectError(temp.toFile())
                        .start()
                        .waitFor();
                lines = Files.readAllLines(temp);
            } finally {
                Files.delete(temp);
            }
        } catch (IOException | InterruptedException e) {
            log.debug("JDK toolchain discovered at " + jdk + " will be ignored: unable to execute java: " + e);
            return null;
        }

        Map<String, String> properties = new LinkedHashMap<>();
        for (String name : Arrays.asList("version", "runtime.name", "runtime.version", "vendor", "vendor.version")) {
            lines.stream()
                    .filter(l -> l.contains("java." + name))
                    .map(l -> l.replaceFirst(".*=\\s*(.*)", "$1"))
                    .findFirst()
                    .ifPresent(value -> properties.put(name, value));
        }
        if (!properties.containsKey("version")) {
            log.debug("JDK toolchain discovered at " + jdk + " will be ignored: could not obtain java.version");
            return null;
        }

        ToolchainModel model = new ToolchainModel();
        model.setType("jdk");
        properties.forEach(model::addProvide);
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom jdkHome = new Xpp3Dom("jdkHome");
        jdkHome.setValue(jdk.toString());
        configuration.addChild(jdkHome);
        model.setConfiguration(configuration);
        return model;
    }

    private static Path getCanonicalPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return getCanonicalPath(path.getParent()).resolve(path.getFileName());
        }
    }

    Comparator<ToolchainModel> getToolchainModelComparator() {
        return Comparator.comparing(
                        (ToolchainModel tc) -> tc.getProvides().getProperty("version"), this::compareVersion)
                .reversed()
                .thenComparing(tc -> tc.getProvides().getProperty("vendor"));
    }

    int compareVersion(String v1, String v2) {
        String[] s1 = v1.split("\\.");
        String[] s2 = v2.split("\\.");
        return compare(s1, s2);
    }

    static <T extends Comparable<? super T>> int compare(T[] a, T[] b) {
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            T oa = a[i];
            T ob = b[i];
            if (oa != ob) {
                // A null element is less than a non-null element
                if (oa == null || ob == null) {
                    return oa == null ? -1 : 1;
                }
                int v = oa.compareTo(ob);
                if (v != 0) {
                    return v;
                }
            }
        }
        return a.length - b.length;
    }

    private Set<Path> findJdks() {
        // check environment variables for JAVA{xx}_HOME
        List<Path> dirsToTest = System.getenv().entrySet().stream()
                .filter(e -> e.getKey().startsWith("JAVA") && e.getKey().endsWith("_HOME"))
                .map(e -> Paths.get(e.getValue()))
                .collect(Collectors.toList());
        final String userHome = System.getProperty("user.home");
        List<Path> installedDirs = new ArrayList<>();
        // jdk installed by third
        installedDirs.add(Paths.get(userHome, ".jdks"));
        installedDirs.add(Paths.get(userHome, ".m2/jdks"));
        installedDirs.add(Paths.get(userHome, ".sdkman/candidates/java"));
        installedDirs.add(Paths.get(userHome, ".gradle/jdks"));
        installedDirs.add(Paths.get(userHome, ".jenv/versions"));
        installedDirs.add(Paths.get(userHome, ".jbang/cache/jdks"));
        installedDirs.add(Paths.get(userHome, ".asdf/installs"));
        installedDirs.add(Paths.get(userHome, ".jabba/jdk"));
        // os related directories
        String osname = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        boolean macos = osname.startsWith("mac");
        boolean win = osname.startsWith("win");
        if (macos) {
            installedDirs.add(Paths.get("/Library/Java/JavaVirtualMachines"));
            installedDirs.add(Paths.get(userHome, "Library/Java/JavaVirtualMachines"));
        } else if (win) {
            installedDirs.add(Paths.get("C:\\Program Files\\Java\\"));
        } else {
            installedDirs.add(Paths.get("/usr/jdk"));
            installedDirs.add(Paths.get("/usr/java"));
            installedDirs.add(Paths.get("/opt/java"));
            installedDirs.add(Paths.get("/usr/lib/jvm"));
        }
        for (Path dest : installedDirs) {
            if (Files.isDirectory(dest)) {
                try (Stream<Path> stream = Files.list(dest)) {
                    stream.forEach(dir -> {
                        dirsToTest.add(dir);
                        dirsToTest.add(dir.resolve("Contents/Home"));
                    });
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        // only keep directories that have a javac file
        return dirsToTest.stream()
                .filter(ToolchainDiscoverer::hasJavaC)
                .map(ToolchainDiscoverer::getCanonicalPath)
                .collect(Collectors.toSet());
    }

    private static boolean hasJavaC(Path subdir) {
        return Files.exists(subdir.resolve("bin/javac")) || Files.exists(subdir.resolve("bin/javac.exe"));
    }
}
