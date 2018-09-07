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
package org.apache.sling.scripting.freemarker.internal;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import freemarker.template.Configuration;
import freemarker.template.TemplateModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.osgi.SortingServiceTracker;
import org.apache.sling.scripting.api.AbstractScriptEngineFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = ScriptEngineFactory.class,
    immediate = true,
    property = {
        Constants.SERVICE_DESCRIPTION + "=Apache Sling Scripting FreeMarker ScriptEngineFactory",
        Constants.SERVICE_VENDOR + "=The Apache Software Foundation"
    }
)
@Designate(
    ocd = FreemarkerScriptEngineFactoryConfiguration.class
)
public class FreemarkerScriptEngineFactory extends AbstractScriptEngineFactory {

    @Reference(
        cardinality = ReferenceCardinality.OPTIONAL,
        policy = ReferencePolicy.DYNAMIC,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private volatile Configuration configuration;

    private BundleContext bundleContext;

    private SortingServiceTracker<TemplateModel> templateModelTracker;

    private final Configuration defaultConfiguration;

    private static final String FREEMARKER_NAME = "FreeMarker";

    private final Logger logger = LoggerFactory.getLogger(FreemarkerScriptEngineFactory.class);

    public FreemarkerScriptEngineFactory() {
        defaultConfiguration = new Configuration(Configuration.getVersion());
        defaultConfiguration.setDefaultEncoding(StandardCharsets.UTF_8.name());
    }

    @Activate
    private void activate(final FreemarkerScriptEngineFactoryConfiguration configuration, final BundleContext bundleContext) {
        logger.debug("activate");
        configure(configuration);
        this.bundleContext = bundleContext;
        templateModelTracker = new SortingServiceTracker<>(bundleContext, TemplateModel.class.getName());
        templateModelTracker.open();
    }

    @Modified
    private void modified(final FreemarkerScriptEngineFactoryConfiguration configuration) {
        logger.debug("modified");
        configure(configuration);
    }

    @Deactivate
    private void deactivate() {
        logger.debug("deactivate");
        templateModelTracker.close();
        templateModelTracker = null;
        bundleContext = null;
    }

    private void configure(final FreemarkerScriptEngineFactoryConfiguration configuration) {
        setExtensions(configuration.extensions());
        setMimeTypes(configuration.mimeTypes());
        setNames(configuration.names());
    }

    public ScriptEngine getScriptEngine() {
        return new FreemarkerScriptEngine(this);
    }

    public String getLanguageName() {
        return FREEMARKER_NAME;
    }

    public String getLanguageVersion() {
        return Configuration.getVersion().toString();
    }

    Configuration getConfiguration() {
        final Configuration configuration = this.configuration;
        if (configuration != null) {
            return configuration;
        } else {
            return defaultConfiguration;
        }
    }

    Map<String, Map<String, TemplateModel>> getTemplateModels() {
        final Map<String, Map<String, TemplateModel>> models = new HashMap<>();
        for (final ServiceReference<TemplateModel> serviceReference : templateModelTracker.getSortedServiceReferences()) {
            final String namespace = (String) serviceReference.getProperty("namespace");
            final String name = (String) serviceReference.getProperty("name");
            if (StringUtils.isNotBlank(namespace) && StringUtils.isNotBlank(name)) {
                if (!models.containsKey(namespace)) {
                    models.put(namespace, new HashMap<>());
                }
                models.get(namespace).put(name, bundleContext.getService(serviceReference));
            }
        }
        return models;
    }

}
