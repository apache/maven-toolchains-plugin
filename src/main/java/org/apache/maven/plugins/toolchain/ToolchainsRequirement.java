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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.maven.api.xml.XmlNode;

/**
 * Type for plugin's <code>toolchain</code> attribute representing toolchains requirements.
 *
 * @author mkleint
 */
public final class ToolchainsRequirement {
    Map<String, Map<String, String>> toolchains;

    public ToolchainsRequirement(XmlNode node) {
        Map<String, Map<String, String>> toolchains = new HashMap<>();
        for (XmlNode child : node.getChildren()) {
            Map<String, String> cfg = new LinkedHashMap<>();
            for (XmlNode e : child.getChildren()) {
                cfg.put(e.getName(), e.getValue());
            }
            toolchains.put(child.getName(), cfg);
        }
        this.toolchains = toolchains;
    }

    public ToolchainsRequirement(Map<String, Map<String, String>> toolchains) {
        this.toolchains = toolchains;
    }

    public Map<String, Map<String, String>> getToolchains() {
        return Collections.unmodifiableMap(toolchains);
    }

    public Set<String> getToolchainsTypes() {
        return Collections.unmodifiableSet(toolchains.keySet());
    }

    public Map<String, String> getParams(String type) {
        return Collections.unmodifiableMap(toolchains.get(type));
    }
}
