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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.CURRENT;
import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.JDK_HOME;
import static org.apache.maven.plugins.toolchain.jdk.ToolchainDiscoverer.VERSION;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolchainDiscovererTest {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisabledOnJre(JRE.JAVA_8) // java 8 often has jdk != jre
    void testDiscovery() {
        ToolchainDiscoverer discoverer = new ToolchainDiscoverer();
        PersistedToolchains persistedToolchains = discoverer.discoverToolchains();
        assertNotNull(persistedToolchains);

        persistedToolchains.getToolchains().forEach(model -> {
            logger.info("  - "
                    + ((Xpp3Dom) model.getConfiguration()).getChild("jdkHome").getValue());
            logger.info("    provides:");
            model.getProvides().forEach((k, v) -> logger.info("      " + k + ": " + v));
        });

        assertTrue(persistedToolchains.getToolchains().stream()
                .anyMatch(tc -> tc.getProvides().containsKey(CURRENT)));
    }

    @Test
    void cacheUpdateDuringWriteRemainsPending() throws Exception {
        String originalUserHome = System.getProperty(ToolchainDiscoverer.USER_HOME);
        System.setProperty(ToolchainDiscoverer.USER_HOME, temporaryDirectory.toString());
        try {
            BlockingCache cache = new BlockingCache();
            TestToolchainDiscoverer discoverer = new TestToolchainDiscoverer();
            setField(discoverer, "cache", cache);

            Path firstJdk = temporaryDirectory.resolve("jdk-1");
            Path secondJdk = temporaryDirectory.resolve("jdk-2");
            discoverer.getToolchainModel(firstJdk);
            cache.blockNextSnapshot();

            AtomicReference<Throwable> writerFailure = new AtomicReference<>();
            AtomicReference<Throwable> updaterFailure = new AtomicReference<>();
            Thread writer = new Thread(() -> {
                try {
                    invokeWriteCache(discoverer);
                } catch (Throwable t) {
                    writerFailure.set(t);
                }
            });
            writer.start();

            assertTrue(cache.snapshotStarted.await(10, TimeUnit.SECONDS), "cache snapshot did not start");
            Thread updater = new Thread(() -> {
                try {
                    discoverer.getToolchainModel(secondJdk);
                } catch (Throwable t) {
                    updaterFailure.set(t);
                }
            });
            updater.start();
            assertTrue(discoverer.secondModelCreated.await(10, TimeUnit.SECONDS), "cache update did not start");
            cache.allowSnapshot.countDown();
            writer.join(TimeUnit.SECONDS.toMillis(10));
            updater.join(TimeUnit.SECONDS.toMillis(10));

            assertTrue(!writer.isAlive(), "cache writer did not finish");
            assertTrue(writerFailure.get() == null, "cache writer failed: " + writerFailure.get());
            assertTrue(!updater.isAlive(), "cache updater did not finish");
            assertTrue(updaterFailure.get() == null, "cache updater failed: " + updaterFailure.get());

            invokeWriteCache(discoverer);

            Path cacheFile = temporaryDirectory.resolve(ToolchainDiscoverer.DISCOVERED_TOOLCHAINS_CACHE_XML);
            String cacheContents = new String(Files.readAllBytes(cacheFile), StandardCharsets.UTF_8);
            assertTrue(
                    cacheContents.contains(secondJdk.toString()),
                    "concurrent cache update was not persisted in: " + cacheContents);
        } finally {
            if (originalUserHome == null) {
                System.clearProperty(ToolchainDiscoverer.USER_HOME);
            } else {
                System.setProperty(ToolchainDiscoverer.USER_HOME, originalUserHome);
            }
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = ToolchainDiscoverer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invokeWriteCache(ToolchainDiscoverer discoverer) throws Exception {
        Method method = ToolchainDiscoverer.class.getDeclaredMethod("writeCache");
        method.setAccessible(true);
        method.invoke(discoverer);
    }

    private static ToolchainModel toolchain(Path jdk, String version) {
        ToolchainModel model = new ToolchainModel();
        model.setType("jdk");
        model.addProvide(VERSION, version);
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom jdkHome = new Xpp3Dom(JDK_HOME);
        jdkHome.setValue(jdk.toString());
        configuration.addChild(jdkHome);
        model.setConfiguration(configuration);
        return model;
    }

    private static final class TestToolchainDiscoverer extends ToolchainDiscoverer {
        private final CountDownLatch secondModelCreated = new CountDownLatch(1);

        @Override
        ToolchainModel doGetToolchainModel(Path jdk) {
            if ("jdk-2".equals(jdk.getFileName().toString())) {
                secondModelCreated.countDown();
            }
            return toolchain(jdk, jdk.getFileName().toString());
        }
    }

    private static final class BlockingCache extends ConcurrentHashMap<Path, ToolchainModel> {
        private final AtomicBoolean blockSnapshot = new AtomicBoolean();
        private final CountDownLatch snapshotStarted = new CountDownLatch(1);
        private final CountDownLatch allowSnapshot = new CountDownLatch(1);

        void blockNextSnapshot() {
            blockSnapshot.set(true);
        }

        @Override
        public Set<Map.Entry<Path, ToolchainModel>> entrySet() {
            if (!blockSnapshot.compareAndSet(true, false)) {
                return super.entrySet();
            }
            Set<Map.Entry<Path, ToolchainModel>> snapshot = new HashSet<>(super.entrySet());
            waitForSnapshot();
            return snapshot;
        }

        @Override
        public Collection<ToolchainModel> values() {
            if (!blockSnapshot.compareAndSet(true, false)) {
                return super.values();
            }
            Collection<ToolchainModel> snapshot = new ArrayList<>(super.values());
            waitForSnapshot();
            return snapshot;
        }

        private void waitForSnapshot() {
            snapshotStarted.countDown();
            try {
                if (!allowSnapshot.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to resume cache snapshot");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting to resume cache snapshot", e);
            }
        }
    }
}
