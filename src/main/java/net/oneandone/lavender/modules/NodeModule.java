/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.lavender.modules;

import net.oneandone.lavender.config.Secrets;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class NodeModule extends Module<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(Module.class);
    // used to detect a recent parent pom

    public static List<Module> fromWebapp(FileNode cache, boolean prod, Node<?> webapp, Secrets secrets)
            throws IOException {
        List<Module> result;
        WarConfig rootConfig;
        ModuleProperties application;
        List<String> legacy;
        PustefixJar pustefixJar;


        LOG.trace("scanning " + webapp);
        legacy = new ArrayList<>();
        application = ModuleProperties.loadApp(prod, webapp, legacy);
        LOG.info("legacy modules: " + legacy);
        result = new ArrayList<>();
        rootConfig = WarConfig.fromXml(webapp);
        // add modules before webapp, because they have a prefix
        for (Node<?> jar : webapp.find("WEB-INF/lib/*.jar")) {
            pustefixJar = PustefixJar.forNodeOpt(prod, jar, rootConfig);
            if (pustefixJar != null) {
                if (legacy.contains(pustefixJar.config.getModuleName())) {
                    result.add(pustefixJar.createLegacyModule(ModuleProperties.defaultFilter()));
                } else {
                    if (pustefixJar.moduleProperties != null) {
                        pustefixJar.moduleProperties.createModules(cache, prod, secrets, result, pustefixJar.config);
                    }
                }
            }
        }
        application.createModules(cache, prod, secrets, result, null);
        return result;
    }

    //--

    /** @return list of legacy modules */
    public static List<String> scanLegacy(Node<?> webapp) throws Exception {
        List<String> result;
        WarConfig rootConfig;
        PustefixJar pustefixJar;
        Module module;

        result = new ArrayList<>();
        rootConfig = WarConfig.fromXml(webapp);
        // add modules before webapp, because they have a prefix
        for (Node<?> jar : webapp.find("WEB-INF/lib/*.jar")) {
            pustefixJar = PustefixJar.forNodeOpt(true, jar, rootConfig);
            if (pustefixJar != null && pustefixJar.moduleProperties == null) {
                module = pustefixJar.createLegacyModule(ModuleProperties.defaultFilter());
                if (!module.loadEntries().isEmpty()) {
                    result.add(module.getName());
                }
            }
        }
        return result;
    }

    //--

    public NodeModule(Node origin, String type, String name, boolean lavendelize, String resourcePathPrefix, String targetPathPrefix, Filter filter) {
        this(origin.getUri().toString(), type, name, lavendelize, resourcePathPrefix, targetPathPrefix, filter);
    }

    public NodeModule(String origin, String type, String name, boolean lavendelize, String resourcePathPrefix, String targetPathPrefix, Filter filter) {
        super(origin, type, name, lavendelize, resourcePathPrefix, targetPathPrefix, filter);
    }

    protected Resource createResource(String resourcePath, Node file) throws IOException {
        return NodeResource.forNode(file, resourcePath);
    }
}
