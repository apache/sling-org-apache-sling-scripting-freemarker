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

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;

import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.utility.DeepUnwrap;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.scripting.core.servlet.CaptureResponseWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support for Sling Include in FreeMarker templates.<br>Includes a Resource rendering into the current template.
 */
@Component(
    service = {
        TemplateModel.class
    },
    property = {
        "namespace=sling",
        "name=include"
    }
)
public final class IncludeDirective implements TemplateDirectiveModel {

    private static final String ADD_SELECTORS_PARAMETER_NAME = "addSelectors";

    private static final String REPLACE_SELECTORS_PARAMETER_NAME = "replaceSelectors";

    private static final String REPLACE_SUFFIX_PARAMETER_NAME = "replaceSuffix";

    private static final String RESOURCE_TYPE_PARAMETER_NAME = "resourceType";

    private final Logger logger = LoggerFactory.getLogger(IncludeDirective.class);

    public IncludeDirective() { //
    }

    @Override
    @SuppressWarnings("checkstyle:AvoidInlineConditionals")
    public void execute(final Environment environment, final Map parameters, final TemplateModel[] loopVars, final TemplateDirectiveBody body) throws TemplateException, IOException {

        final SlingHttpServletRequest slingHttpServletRequest = (SlingHttpServletRequest) DeepUnwrap.unwrap(environment.getVariable(SlingBindings.REQUEST));
        if (Objects.isNull(slingHttpServletRequest)) {
            throw new TemplateException("request is null", environment);
        }

        final SlingHttpServletResponse slingHttpServletResponse = (SlingHttpServletResponse) DeepUnwrap.unwrap(environment.getVariable(SlingBindings.RESPONSE));
        if (Objects.isNull(slingHttpServletResponse)) {
            throw new TemplateException("response is null", environment);
        }

        final TemplateModel templateModel = (TemplateModel) parameters.get("include");
        if (Objects.isNull(templateModel)) {
            throw new TemplateException("include is null", environment);
        }
        final Object include = DeepUnwrap.unwrap(templateModel);
        if (Objects.isNull(include)) {
            throw new TemplateException("unwrapping include failed", environment);
        }

        final Resource resource = include instanceof Resource ? (Resource) include : null;
        final String path = include instanceof String ? (String) include : null;
        final String content;
        if (!Objects.isNull(resource)) {
            content = dispatch(resource, requestDispatcherOptions(parameters), slingHttpServletRequest, slingHttpServletResponse);
        } else if (!Objects.isNull(path)) {
            content = dispatch(path, requestDispatcherOptions(parameters), slingHttpServletRequest, slingHttpServletResponse);
        } else {
            throw new TemplateException("resource and path are null", environment);
        }

        if (!Objects.isNull(content)) {
            environment.getOut().write(content);
        } else {
            throw new TemplateException("dispatching request failed, content is null", environment);
        }
    }

    private String unwrapParameter(final String name, final Map<?, ?> parameters) throws TemplateModelException {
        final TemplateModel parameter = (TemplateModel) parameters.get(name);
        return (String) DeepUnwrap.unwrap(parameter);
    }

    private RequestDispatcherOptions requestDispatcherOptions(final Map<?, ?> parameters) throws TemplateModelException {
        final String resourceType = unwrapParameter(RESOURCE_TYPE_PARAMETER_NAME, parameters);
        final String replaceSelectors = unwrapParameter(REPLACE_SELECTORS_PARAMETER_NAME, parameters);
        final String addSelectors = unwrapParameter(ADD_SELECTORS_PARAMETER_NAME, parameters);
        final String replaceSuffix = unwrapParameter(REPLACE_SUFFIX_PARAMETER_NAME, parameters);

        final RequestDispatcherOptions options = new RequestDispatcherOptions();
        options.setForceResourceType(resourceType);
        options.setReplaceSelectors(replaceSelectors);
        options.setAddSelectors(addSelectors);
        options.setReplaceSuffix(replaceSuffix);
        return options;
    }

    private String dispatch(final RequestDispatcher requestDispatcher, final SlingHttpServletRequest slingHttpServletRequest, final SlingHttpServletResponse slingHttpServletResponse) {
        try {
            final CaptureResponseWrapper wrapper = new CaptureResponseWrapper(slingHttpServletResponse);
            requestDispatcher.include(slingHttpServletRequest, wrapper);
            if (!wrapper.isBinaryResponse()) {
                return wrapper.getCapturedCharacterResponse();
            }
        } catch (ServletException | IOException e) {
            logger.error("dispatching include failed", e);
        }
        return null;
    }

    private String dispatch(final Resource resource, final RequestDispatcherOptions requestDispatcherOptions, final SlingHttpServletRequest slingHttpServletRequest, final SlingHttpServletResponse slingHttpServletResponse) {
        final RequestDispatcher requestDispatcher = slingHttpServletRequest.getRequestDispatcher(resource, requestDispatcherOptions);
        Objects.requireNonNull(requestDispatcher, String.format("getting RequestDispatcher for resource '%s' failed", resource));
        return dispatch(requestDispatcher, slingHttpServletRequest, slingHttpServletResponse);
    }

    @SuppressWarnings("checkstyle:AvoidInlineConditionals")
    private String dispatch(final String path, final RequestDispatcherOptions requestDispatcherOptions, final SlingHttpServletRequest slingHttpServletRequest, final SlingHttpServletResponse slingHttpServletResponse) {
        // ensure the path is absolute and normalized
        final String absolutePath = path.startsWith("/") ? path : String.format("%s/%s", slingHttpServletRequest.getResource().getPath(), path);
        final String normalizedAbsolutePath = ResourceUtil.normalize(absolutePath);
        final RequestDispatcher requestDispatcher = slingHttpServletRequest.getRequestDispatcher(normalizedAbsolutePath, requestDispatcherOptions);
        Objects.requireNonNull(requestDispatcher, String.format("getting RequestDispatcher for path '%s' failed", normalizedAbsolutePath));
        return dispatch(requestDispatcher, slingHttpServletRequest, slingHttpServletResponse);
    }

}
