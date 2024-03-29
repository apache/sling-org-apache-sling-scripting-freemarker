/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.scripting.freemarker.internal;

import java.io.Reader;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.scripting.api.AbstractSlingScriptEngine;

/**
 * Script Engine using FreeMarker's templates.
 */
public final class FreemarkerScriptEngine extends AbstractSlingScriptEngine {

    private final FreemarkerScriptEngineFactory freemarkerScriptEngineFactory;

    public FreemarkerScriptEngine(final FreemarkerScriptEngineFactory freemarkerScriptEngineFactory) {
        super(freemarkerScriptEngineFactory);
        this.freemarkerScriptEngineFactory = freemarkerScriptEngineFactory;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public Object eval(final Reader reader, final ScriptContext scriptContext) throws ScriptException {
        final Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
        final SlingScriptHelper helper = (SlingScriptHelper) bindings.get(SlingBindings.SLING);
        if (helper == null) {
            throw new ScriptException("SlingScriptHelper missing from bindings");
        }

        bindings.putAll(freemarkerScriptEngineFactory.getTemplateModels());

        final String scriptName = helper.getScript().getScriptResource().getPath();
        final Configuration configuration = freemarkerScriptEngineFactory.getConfiguration();

        try {
            final Template template = new Template(scriptName, reader, configuration);
            template.process(bindings, scriptContext.getWriter());
        } catch (Exception e) {
            final String message = String.format("Failure processing FreeMarker template %s.", scriptName);
            final ScriptException scriptException = new ScriptException(message);
            scriptException.initCause(e);
            throw scriptException;
        }

        return null;
    }

}
