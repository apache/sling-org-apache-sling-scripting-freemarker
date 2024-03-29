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
package org.apache.sling.scripting.freemarker.it.tests;

import javax.inject.Inject;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sling.scripting.freemarker.it.app.Ranked1Configuration;
import org.apache.sling.scripting.freemarker.it.app.Ranked2Configuration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.ops4j.pax.exam.CoreOptions.options;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FreemarkerScriptEngineFactoryIT extends FreemarkerTestSupport {

    @Inject
    @Filter("(name=bar)")
    private freemarker.template.Configuration configuration;

    @Configuration
    public Option[] configuration() {
        return options(
            baseConfiguration(),
            buildBundleWithBnd(
                Ranked1Configuration.class,
                Ranked2Configuration.class
            )
        );
    }

    @Test
    public void testScriptEngineFactory() {
        assertThat(scriptEngineFactory, notNullValue());
    }

    @Test
    public void testScriptEngineFactoryEngineName() {
        assertThat(scriptEngineFactory.getEngineName(), is("Apache Sling Scripting FreeMarker"));
    }

    @Test
    public void testScriptEngineFactoryLanguageName() {
        assertThat(scriptEngineFactory.getLanguageName(), is("FreeMarker"));
    }

    @Test
    public void testScriptEngineFactoryLanguageVersion() {
        assertThat(scriptEngineFactory.getLanguageVersion(), startsWith("2.3."));
    }

    @Test
    public void testScriptEngineFactoryNames() {
        assertThat(scriptEngineFactory.getNames(), hasItem("freemarker"));
    }

    @Test
    public void testConfiguration() throws IllegalAccessException {
        final Object configuration = FieldUtils.readDeclaredField(scriptEngineFactory, "configuration", true);
        assertThat(configuration, sameInstance(this.configuration));
        assertThat(configuration.getClass().getName(), is("org.apache.sling.scripting.freemarker.it.app.Ranked2Configuration"));
    }

}
