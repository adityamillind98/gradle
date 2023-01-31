/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testing.base.internal;

import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestSuiteContainerX;
import org.gradle.testing.base.TestingExtension;

import javax.inject.Inject;

public abstract class DefaultTestingExtension implements TestingExtension {
    private final TestSuiteContainerX suites;

    @Inject
    public DefaultTestingExtension(ObjectFactory objectFactory, InstantiatorFactory instantiatorFactory, ServiceRegistry servicesToInject, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this.suites = objectFactory.newInstance(DefaultTestSuiteContainerX.class, instantiatorFactory, servicesToInject, collectionCallbackActionDecorator);
    }

    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Override
    public TestSuiteContainerX getSuites() {
        return suites;
    }

    public static class DefaultTestSuiteContainerX extends DefaultPolymorphicDomainObjectContainer<TestSuite> implements TestSuiteContainerX {

        @Inject
        public DefaultTestSuiteContainerX(InstantiatorFactory instantiatorFactory, ServiceRegistry servicesToInject, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
            super(TestSuite.class, instantiatorFactory.decorateLenient(), instantiatorFactory.decorateLenient(servicesToInject), collectionCallbackActionDecorator);
        }
    }

}
