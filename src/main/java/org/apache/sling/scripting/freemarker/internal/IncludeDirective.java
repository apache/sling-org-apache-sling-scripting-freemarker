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
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.scripting.core.servlet.CaptureResponseWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = {
        TemplateModel.class
    },
    property = {
        "namespace=sling",
        "name=include"
    }
)
public class IncludeDirective implements TemplateDirectiveModel {

    private static final String ADD_SELECTORS_PARAMETER_NAME = "addSelectors";

    private static final String REPLACE_SELECTORS_PARAMETER_NAME = "replaceSelectors";

    private static final String REPLACE_SUFFIX_PARAMETER_NAME = "replaceSuffix";

    private static final String RESOURCE_TYPE_PARAMETER_NAME = "resourceType";

    private final Logger logger = LoggerFactory.getLogger(IncludeDirective.class);

    public IncludeDirective() {
    }

    @Override
    public void execute(final Environment environment, final Map parameters, final TemplateModel[] loopVars, final TemplateDirectiveBody body) throws TemplateException, IOException {

        final SlingHttpServletRequest slingHttpServletRequest = (SlingHttpServletRequest) DeepUnwrap.unwrap(environment.getVariable(SlingBindings.REQUEST));
        if (slingHttpServletRequest == null) {
            throw new TemplateException("request is null", environment);
        }

        final SlingHttpServletResponse slingHttpServletResponse = (SlingHttpServletResponse) DeepUnwrap.unwrap(environment.getVariable(SlingBindings.RESPONSE));
        if (slingHttpServletResponse == null) {
            throw new TemplateException("response is null", environment);
        }

        final TemplateModel templateModel = (TemplateModel) parameters.get("include");
        if (templateModel == null) {
            throw new TemplateException("include is null", environment);
        }
        final Object include = DeepUnwrap.unwrap(templateModel);
        if (include == null) {
            throw new TemplateException("unwrapping include failed", environment);
        }

        String path = null;
        if (include instanceof String) {
            path = (String) include;
        }
        Resource resource = null;
        if (include instanceof Resource) {
            resource = (Resource) include;
        }

        if (path == null && resource == null) {
            throw new TemplateException("path and resource are null", environment);
        }

        // request dispatcher options
        final RequestDispatcherOptions requestDispatcherOptions = prepareRequestDispatcherOptions(parameters);
        // dispatch
        final String content = dispatch(resource, path, slingHttpServletRequest, slingHttpServletResponse, requestDispatcherOptions);
        if (content == null) {
            throw new TemplateException("dispatching request failed, content is null", environment);
        }
        environment.getOut().write(content);
    }

    protected <T> T unwrapParameter(final String name, final Map params, final Class<T> type) throws TemplateModelException {
        final Object parameter = params.get(name);
        final TemplateModel templateModel = (TemplateModel) parameter;
        return (T) DeepUnwrap.unwrap(templateModel);
    }

    protected RequestDispatcherOptions prepareRequestDispatcherOptions(final Map params) throws TemplateModelException {
        final String resourceType = unwrapParameter(RESOURCE_TYPE_PARAMETER_NAME, params, String.class);
        final String replaceSelectors = unwrapParameter(REPLACE_SELECTORS_PARAMETER_NAME, params, String.class);
        final String addSelectors = unwrapParameter(ADD_SELECTORS_PARAMETER_NAME, params, String.class);
        final String replaceSuffix = unwrapParameter(REPLACE_SUFFIX_PARAMETER_NAME, params, String.class);

        final RequestDispatcherOptions options = new RequestDispatcherOptions();
        options.setForceResourceType(resourceType);
        options.setReplaceSelectors(replaceSelectors);
        options.setAddSelectors(addSelectors);
        options.setReplaceSuffix(replaceSuffix);
        return options;
    }

    /**
     * @param resource                 the resource to include
     * @param path                     the path to include
     * @param slingHttpServletRequest  the current request
     * @param slingHttpServletResponse the current response
     * @param requestDispatcherOptions the options for the request dispatcher
     * @return the character response from the include call to request dispatcher
     * @see "org.apache.sling.scripting.jsp.taglib.IncludeTagHandler"
     */
    protected String dispatch(Resource resource, String path, final SlingHttpServletRequest slingHttpServletRequest, final SlingHttpServletResponse slingHttpServletResponse, final RequestDispatcherOptions requestDispatcherOptions) {

        // ensure the path (if set) is absolute and normalized
        if (path != null) {
            if (!path.startsWith("/")) {
                path = slingHttpServletRequest.getResource().getPath() + "/" + path;
            }
            path = ResourceUtil.normalize(path);
        }

        // check the resource
        if (resource == null) {
            if (path == null) {
                // neither resource nor path is defined, use current resource
                resource = slingHttpServletRequest.getResource();
            } else {
                // check whether the path (would) resolve, else SyntheticRes.
                final String resourceType = requestDispatcherOptions.getForceResourceType();
                final Resource tmp = slingHttpServletRequest.getResourceResolver().resolve(path);
                if (tmp == null && resourceType != null) {
                    resource = new SyntheticResource(slingHttpServletRequest.getResourceResolver(), path, resourceType); // TODO DispatcherSyntheticResource?
                    // remove resource type overwrite as synthetic resource is correctly typed as requested
                    requestDispatcherOptions.remove(RequestDispatcherOptions.OPT_FORCE_RESOURCE_TYPE);
                }
            }
        }

        try {
            // create a dispatcher for the resource or path
            final RequestDispatcher dispatcher;
            if (resource != null) {
                dispatcher = slingHttpServletRequest.getRequestDispatcher(resource, requestDispatcherOptions);
            } else {
                dispatcher = slingHttpServletRequest.getRequestDispatcher(path, requestDispatcherOptions);
            }

            if (dispatcher != null) {
                try {
                    final CaptureResponseWrapper wrapper = new CaptureResponseWrapper(slingHttpServletResponse);
                    dispatcher.include(slingHttpServletRequest, wrapper);
                    if (!wrapper.isBinaryResponse()) {
                        return wrapper.getCapturedCharacterResponse();
                    }
                } catch (ServletException e) {
                    logger.error(e.getMessage(), e);
                }
            } else {
                logger.error("no request dispatcher: unable to include {}/'{}'", resource, path);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

}
