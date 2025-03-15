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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.SessionScoped;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.services.xml.ToolchainsXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderException;
import org.apache.maven.api.toolchain.PersistedToolchains;
import org.apache.maven.api.toolchain.ToolchainModel;
import org.apache.maven.api.xml.XmlNode;
import org.apache.maven.internal.xml.XmlNodeImpl;

import static java.util.Comparator.comparing;
import static org.apache.maven.plugins.toolchain.jdk.SelectJdkToolchainMojo.TOOLCHAIN_TYPE_JDK;

/**
 * Toolchain discoverer service: tries {@code JAVA{xx}_HOME} environment variables, third party installers and
 * OS-specific locations.
 *
 * @since 3.2.0
 */
@Named
@SessionScoped
public class ToolchainDiscoverer {

    public static final String JAVA = "java.";
    public static final String VERSION = "version";
    public static final String RUNTIME_NAME = "runtime.name";
    public static final String RUNTIME_VERSION = "runtime.version";
    public static final String VENDOR = "vendor";
    public static final String VENDOR_VERSION = "vendor.version";
    public static final String[] PROPERTIES = {VERSION, RUNTIME_NAME, RUNTIME_VERSION, VENDOR, VENDOR_VERSION};

    public static final String CURRENT = "current";
    public static final String ENV = "env";
    public static final String LTS = "lts";

    public static final List<String> SORTED_PROVIDES = Collections.unmodifiableList(
            Arrays.asList(VERSION, RUNTIME_NAME, RUNTIME_VERSION, VENDOR, VENDOR_VERSION, CURRENT, LTS, ENV));

    public static final String DISCOVERED_TOOLCHAINS_CACHE_XML = ".m2/discovered-jdk-toolchains-cache.xml";

    public static final String JDK_HOME = "jdkHome";
    public static final String JAVA_HOME = "java.home";

    private static final String COMMA = ",";
    public static final String USER_HOME = "user.home";

    private volatile Map<Path, ToolchainModel> cache;
    private volatile boolean cacheModified;
    private volatile Set<Path> foundJdks;

    private final Log log;
    private final Session session;

    @Inject
    public ToolchainDiscoverer(Log log, Session session) {
        this.log = log;
        this.session = session;
    }

    /**
     * Build the model for the current JDK toolchain
     */
    public Optional<ToolchainModel> getCurrentJdkToolchain() {
        Path currentJdkHome = getCanonicalPath(Paths.get(System.getProperty(JAVA_HOME)));
        if (!hasJavaC(currentJdkHome)) {
            // in case the current JVM is not a JDK
            return Optional.empty();
        }
        Map<String, String> provides = new LinkedHashMap<>();
        Stream.of(PROPERTIES).forEach(k -> {
            String v = System.getProperty(JAVA + k);
            if (v != null) {
                provides.put(k, v);
            }
        });
        provides.put(CURRENT, "true");
        XmlNode jdkHome = new XmlNodeImpl(JDK_HOME, currentJdkHome.toString());
        XmlNode config = new XmlNodeImpl("configuration", null, null, List.of(jdkHome), null);
        ToolchainModel model = ToolchainModel.newBuilder()
                .type(TOOLCHAIN_TYPE_JDK)
                .provides(provides)
                .configuration(config)
                .build();
        return Optional.of(model);
    }

    public PersistedToolchains discoverToolchains() {
        return discoverToolchains(LTS + COMMA + VERSION + COMMA + VENDOR);
    }

    /**
     * Returns a PersistedToolchains object containing a list of discovered toolchains,
     * never <code>null</code>.
     */
    public PersistedToolchains discoverToolchains(String comparator) {
        try {
            Set<Path> jdks = findJdks();
            log.info("Found " + jdks.size() + " possible jdks: " + jdks);
            readCache();
            Map<Path, Map<String, String>> flags = new HashMap<>();
            Path currentJdkHome = getCanonicalPath(Paths.get(System.getProperty(JAVA_HOME)));
            flags.computeIfAbsent(currentJdkHome, p -> new HashMap<>()).put(CURRENT, "true");
            // check environment variables for JAVA{xx}_HOME
            System.getenv().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("JAVA") && e.getKey().endsWith("_HOME"))
                    .forEach(e -> {
                        Path path = getCanonicalPath(Paths.get(e.getValue()));
                        Map<String, String> f = flags.computeIfAbsent(path, p -> new HashMap<>());
                        String val = f.getOrDefault(ENV, "");
                        f.put(ENV, (val.isEmpty() ? "" : val + ",") + e.getKey());
                    });

            List<ToolchainModel> tcs = jdks.parallelStream()
                    .map(s -> {
                        ToolchainModel tc = getToolchainModel(s);
                        Map<String, String> provides = new LinkedHashMap<>(tc.getProvides());
                        provides.putAll(flags.getOrDefault(s, Collections.emptyMap()));
                        String version = provides.get(VERSION);
                        if (isLts(version)) {
                            provides.put(LTS, "true");
                        }
                        return tc.withProvides(provides);
                    })
                    .sorted(getToolchainModelComparator(comparator))
                    .collect(Collectors.toList());
            writeCache();
            return PersistedToolchains.newBuilder().toolchains(tcs).build();
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.warn("Error discovering toolchains: " + e, e);
            } else {
                log.warn("Error discovering toolchains (enable debug level for more information): " + e);
            }
            return PersistedToolchains.newInstance();
        }
    }

    private static boolean isLts(String version) {
        return Stream.of("1.8", "8", "11", "17", "21", "25")
                .anyMatch(v -> version.equals(v) || version.startsWith(v + "."));
    }

    private synchronized void readCache() {
        if (cache == null) {
            try {
                cache = new ConcurrentHashMap<>();
                cacheModified = false;
                Path cacheFile = getCacheFile();
                if (Files.isRegularFile(cacheFile)) {
                    try (Reader r = Files.newBufferedReader(cacheFile)) {
                        PersistedToolchains pt =
                                session.getService(ToolchainsXmlFactory.class).read(r, false);
                        cache = pt.getToolchains().stream()
                                // Remove stale entries
                                .filter(tc -> {
                                    // If the bin/java executable is not available anymore, remove this TC
                                    if (!hasJavaC(getJdkHome(tc))) {
                                        cacheModified = true;
                                        return false;
                                    } else {
                                        return true;
                                    }
                                })
                                .collect(Collectors.toConcurrentMap(this::getJdkHome, Function.identity()));
                    }
                }
            } catch (IOException | XmlReaderException e) {
                log.debug("Error reading toolchains cache: " + e, e);
            }
        }
    }

    private synchronized void writeCache() {
        if (cacheModified) {
            try {
                Path cacheFile = getCacheFile();
                Files.createDirectories(cacheFile.getParent());
                PersistedToolchains pt = PersistedToolchains.newBuilder()
                        .toolchains(cache.values().stream()
                                .map(tc ->
                                        // Remove transient information
                                        tc.withProvides(tc.getProvides().entrySet().stream()
                                                .filter(e -> !e.getKey().equals(CURRENT))
                                                .filter(e -> !e.getKey().equals(ENV))
                                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))))
                                .sorted(version().thenComparing(vendor()))
                                .collect(Collectors.toList()))
                        .build();

                session.getService(ToolchainsXmlFactory.class).write(pt, cacheFile);
            } catch (Exception e) {
                log.debug("Error writing toolchains cache: " + e, e);
            }
            cacheModified = false;
        }
    }

    ToolchainModel getToolchainModel(Path jdk) {
        ToolchainModel model = cache.get(jdk);
        if (model == null) {
            model = doGetToolchainModel(jdk);
            cache.put(jdk, model);
            cacheModified = true;
        }
        return model;
    }

    private static Path getCacheFile() {
        return Paths.get(System.getProperty(USER_HOME)).resolve(DISCOVERED_TOOLCHAINS_CACHE_XML);
    }

    public Path getJdkHome(ToolchainModel toolchain) {
        XmlNode dom = toolchain.getConfiguration();
        XmlNode javahome = dom != null ? dom.getChild(JDK_HOME) : null;
        String jdk = javahome != null ? javahome.getValue() : null;
        return Paths.get(Objects.requireNonNull(jdk));
    }

    ToolchainModel doGetToolchainModel(Path jdk) {
        Path java = jdk.resolve("bin").resolve("java");
        if (!Files.exists(java)) {
            java = jdk.resolve("bin").resolve("java.exe");
            if (!Files.exists(java)) {
                log.debug("JDK toolchain discovered at " + jdk
                        + " will be ignored: unable to find bin/java or bin\\java.exe");
                return null;
            }
        }
        if (!java.toFile().canExecute()) {
            log.debug("JDK toolchain discovered at " + jdk
                    + " will be ignored: the bin/java or bin\\java.exe is not executable");
            return null;
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
            log.debug("JDK toolchain discovered at " + jdk + " will be ignored: error executing java: " + e);
            return null;
        }

        Map<String, String> properties = new LinkedHashMap<>();
        Stream.of(PROPERTIES).forEach(name -> {
            lines.stream()
                    .filter(l -> l.contains(JAVA + name))
                    .map(l -> l.replaceFirst(".*=\\s*(.*)", "$1"))
                    .findFirst()
                    .ifPresent(value -> properties.put(name, value));
        });
        if (!properties.containsKey(VERSION)) {
            log.debug("JDK toolchain discovered at " + jdk + " will be ignored: could not obtain " + JAVA + VERSION);
            return null;
        }

        XmlNodeImpl jdkHome = new XmlNodeImpl(JDK_HOME, jdk.toString());
        XmlNodeImpl configuration = new XmlNodeImpl("configuration", null, null, List.of(jdkHome), null);
        return ToolchainModel.newBuilder()
                .type(TOOLCHAIN_TYPE_JDK)
                .provides(properties)
                .configuration(configuration)
                .build();
    }

    private static Path getCanonicalPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return getCanonicalPath(path.getParent()).resolve(path.getFileName());
        }
    }

    Comparator<ToolchainModel> getToolchainModelComparator(String comparator) {
        Comparator<ToolchainModel> c = null;
        for (String part : comparator.split(COMMA)) {
            c = c == null ? getComparator(part) : c.thenComparing(getComparator(part));
        }
        return c;
    }

    private Comparator<ToolchainModel> getComparator(String part) {
        return switch (part.trim().toLowerCase(Locale.ROOT)) {
            case LTS -> lts();
            case VENDOR -> vendor();
            case ENV -> env();
            case CURRENT -> current();
            case VERSION -> version();
            default -> throw new IllegalArgumentException("Unsupported comparator: " + part
                    + ". Supported comparators are: vendor, env, current, lts and version.");
        };
    }

    Comparator<ToolchainModel> lts() {
        return comparing((ToolchainModel tc) -> tc.getProvides().containsKey(LTS) ? -1 : +1);
    }

    Comparator<ToolchainModel> vendor() {
        return comparing((ToolchainModel tc) -> tc.getProvides().get(VENDOR));
    }

    Comparator<ToolchainModel> env() {
        return comparing((ToolchainModel tc) -> tc.getProvides().containsKey(ENV) ? -1 : +1);
    }

    Comparator<ToolchainModel> current() {
        return comparing((ToolchainModel tc) -> tc.getProvides().containsKey(CURRENT) ? -1 : +1);
    }

    Comparator<ToolchainModel> version() {
        return comparing((ToolchainModel tc) -> tc.getProvides().get(VERSION), (v1, v2) -> {
                    String[] a = v1.split("\\.");
                    String[] b = v2.split("\\.");
                    int length = Math.min(a.length, b.length);
                    for (int i = 0; i < length; i++) {
                        String oa = a[i];
                        String ob = b[i];
                        if (!Objects.equals(oa, ob)) {
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
                })
                .reversed();
    }

    private Set<Path> findJdks() {
        if (foundJdks == null) {
            synchronized (this) {
                if (foundJdks == null) {
                    foundJdks = doFindJdks();
                }
            }
        }
        return foundJdks;
    }

    /**
     * Find JDKs in known classical locations.
     *
     * @return a set of path where JDKs were found.
     */
    private Set<Path> doFindJdks() {
        List<Path> dirsToTest = new ArrayList<>();

        // add current JDK
        dirsToTest.add(Paths.get(System.getProperty(JAVA_HOME)));

        // check environment variables for JAVA{xx}_HOME
        System.getenv().entrySet().stream()
                .filter(e -> e.getKey().startsWith("JAVA") && e.getKey().endsWith("_HOME"))
                .map(e -> Paths.get(e.getValue()))
                .forEach(dirsToTest::add);

        final Path userHome = Paths.get(System.getProperty(USER_HOME));
        List<Path> installedDirs = new ArrayList<>();

        // JDK installed by third-party tool managers
        installedDirs.add(userHome.resolve(".jdks"));
        installedDirs.add(userHome.resolve(".m2").resolve("jdks"));
        installedDirs.add(userHome.resolve(".sdkman").resolve("candidates").resolve("java"));
        installedDirs.add(userHome.resolve(".gradle").resolve("jdks"));
        installedDirs.add(userHome.resolve(".jenv").resolve("versions"));
        installedDirs.add(userHome.resolve(".jbang").resolve("cache").resolve("jdks"));
        installedDirs.add(userHome.resolve(".asdf").resolve("installs"));
        installedDirs.add(userHome.resolve(".jabba").resolve("jdk"));

        // OS related directories
        String osname = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        boolean macos = osname.startsWith("mac");
        boolean win = osname.startsWith("win");
        if (macos) {
            installedDirs.add(Paths.get("/Library/Java/JavaVirtualMachines"));
            installedDirs.add(userHome.resolve("Library/Java/JavaVirtualMachines"));
        } else if (win) {
            installedDirs.add(Paths.get("C:\\Program Files\\Java\\"));
            Path scoop = userHome.resolve("scoop").resolve("apps");
            if (Files.isDirectory(scoop)) {
                try (Stream<Path> stream = Files.list(scoop)) {
                    stream.forEach(installedDirs::add);
                } catch (IOException e) {
                    // ignore
                }
            }
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
                        if (macos) {
                            dirsToTest.add(dir.resolve("Contents").resolve("Home"));
                        }
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
        return Files.exists(subdir.resolve(Paths.get("bin", "javac")))
                || Files.exists(subdir.resolve(Paths.get("bin", "javac.exe")));
    }
}
