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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestSuiteContainerX;
import org.gradle.testing.base.TestingExtension;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

public abstract class DefaultTestingExtension implements TestingExtension {
    private final TestSuiteContainerX suites;

    @Inject
    public DefaultTestingExtension() {
        this.suites = new DelegatingTestSuiteContainerX(getObjectFactory().polymorphicDomainObjectContainer(TestSuite.class));
    }

    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Override
    public TestSuiteContainerX getSuites() {
        return suites;
    }

    private static class DelegatingTestSuiteContainerX implements TestSuiteContainerX {

        private final ExtensiblePolymorphicDomainObjectContainer<TestSuite> delegate;

        public DelegatingTestSuiteContainerX(ExtensiblePolymorphicDomainObjectContainer<TestSuite> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void addLater(Provider<? extends TestSuite> provider) {
            delegate.addLater(provider);
        }

        @Override
        public void addAllLater(Provider<? extends Iterable<TestSuite>> provider) {
            delegate.addAllLater(provider);
        }

        @Override
        public <S extends TestSuite> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
            return delegate.withType(type, configureAction);
        }

        @Override
        public <S extends TestSuite> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
            return delegate.withType(type, configureClosure);
        }

        @Override
        public Action<? super TestSuite> whenObjectAdded(Action<? super TestSuite> action) {
            return delegate.whenObjectAdded(action);
        }

        @Override
        public void whenObjectAdded(Closure action) {
            delegate.whenObjectAdded(action);
        }

        @Override
        public Action<? super TestSuite> whenObjectRemoved(Action<? super TestSuite> action) {
            return delegate.whenObjectRemoved(action);
        }

        @Override
        public void whenObjectRemoved(Closure action) {
            delegate.whenObjectRemoved(action);
        }

        @Override
        public void all(Action<? super TestSuite> action) {
            delegate.all(action);
        }

        @Override
        public void all(Closure action) {
            delegate.all(action);
        }

        @Override
        public void configureEach(Action<? super TestSuite> action) {
            delegate.configureEach(action);
        }

        @Override
        public <U extends TestSuite> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
            delegate.registerFactory(type, factory);
        }

        @Override
        public <U extends TestSuite> void registerFactory(Class<U> type, Closure<? extends U> factory) {
            delegate.registerFactory(type, factory);
        }

        @Override
        public <U extends TestSuite> void registerBinding(Class<U> type, Class<? extends U> implementationType) {
            delegate.registerBinding(type, implementationType);
        }

        @Override
        public boolean add(TestSuite e) {
            return delegate.add(e);
        }

        @Override
        public boolean addAll(Collection<? extends TestSuite> c) {
            return delegate.addAll(c);
        }

        @Override
        public Namer<TestSuite> getNamer() {
            return delegate.getNamer();
        }

        @Override
        public SortedMap<String, TestSuite> getAsMap() {
            return delegate.getAsMap();
        }

        @Override
        public SortedSet<String> getNames() {
            return delegate.getNames();
        }

        @Override
        public TestSuite findByName(String name) {
            return delegate.findByName(name);
        }

        @Override
        public TestSuite getByName(String name) throws UnknownDomainObjectException {
            return delegate.getByName(name);
        }

        @Override
        public TestSuite getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
            return delegate.getByName(name, configureClosure);
        }

        @Override
        public TestSuite getByName(String name, Action<? super TestSuite> configureAction) throws UnknownDomainObjectException {
            return delegate.getByName(name, configureAction);
        }

        @Override
        public TestSuite getAt(String name) throws UnknownDomainObjectException {
            return delegate.getAt(name);
        }

        @Override
        public Rule addRule(Rule rule) {
            return delegate.addRule(rule);
        }

        @Override
        public Rule addRule(String description, Closure ruleAction) {
            return delegate.addRule(description, ruleAction);
        }

        @Override
        public Rule addRule(String description, Action<String> ruleAction) {
            return delegate.addRule(description, ruleAction);
        }

        @Override
        public List<Rule> getRules() {
            return delegate.getRules();
        }

        @Override
        public NamedDomainObjectProvider<TestSuite> named(String name) throws UnknownDomainObjectException {
            return delegate.named(name);
        }

        @Override
        public NamedDomainObjectProvider<TestSuite> named(String name, Action<? super TestSuite> configurationAction) throws UnknownDomainObjectException {
            return delegate.named(name, configurationAction);
        }

        @Override
        public <S extends TestSuite> NamedDomainObjectProvider<S> named(String name, Class<S> type) throws UnknownDomainObjectException {
            return delegate.named(name, type);
        }

        @Override
        public <S extends TestSuite> NamedDomainObjectProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownDomainObjectException {
            return delegate.named(name, type, configurationAction);
        }

        @Override
        public NamedDomainObjectCollectionSchema getCollectionSchema() {
            return delegate.getCollectionSchema();
        }

        @Override
        public TestSuite create(String name) throws InvalidUserDataException {
            return delegate.create(name);
        }

        @Override
        public TestSuite maybeCreate(String name) {
            return delegate.maybeCreate(name);
        }

        @Override
        public TestSuite create(String name, Closure configureClosure) throws InvalidUserDataException {
            return delegate.create(name, configureClosure);
        }

        @Override
        public TestSuite create(String name, Action<? super TestSuite> configureAction) throws InvalidUserDataException {
            return delegate.create(name, configureAction);
        }

        @Override
        public NamedDomainObjectContainer<TestSuite> configure(Closure configureClosure) {
            return delegate.configure(configureClosure);
        }

        @Override
        public NamedDomainObjectProvider<TestSuite> register(String name, Action<? super TestSuite> configurationAction) throws InvalidUserDataException {
            return delegate.register(name, configurationAction);
        }

        @Override
        public NamedDomainObjectProvider<TestSuite> register(String name) throws InvalidUserDataException {
            return delegate.register(name);
        }

        @Override
        public <S extends TestSuite> NamedDomainObjectSet<S> withType(Class<S> type) {
            return delegate.withType(type);
        }

        @Override
        public NamedDomainObjectSet<TestSuite> matching(Spec<? super TestSuite> spec) {
            return delegate.matching(spec);
        }

        @Override
        public NamedDomainObjectSet<TestSuite> matching(Closure spec) {
            return delegate.matching(spec);
        }

        @Override
        public Set<TestSuite> findAll(Closure spec) {
            return delegate.findAll(spec);
        }

        @Override
        public <U extends TestSuite> U create(String name, Class<U> type) throws InvalidUserDataException {
            return delegate.create(name, type);
        }

        @Override
        public <U extends TestSuite> U maybeCreate(String name, Class<U> type) throws InvalidUserDataException {
            return delegate.maybeCreate(name, type);
        }

        @Override
        public <U extends TestSuite> U create(String name, Class<U> type, Action<? super U> configuration) throws InvalidUserDataException {
            return delegate.create(name, type, configuration);
        }

        @Override
        public <U extends TestSuite> NamedDomainObjectContainer<U> containerWithType(Class<U> type) {
            return delegate.containerWithType(type);
        }

        @Override
        public <U extends TestSuite> NamedDomainObjectProvider<U> register(String name, Class<U> type, Action<? super U> configurationAction) throws InvalidUserDataException {
            return delegate.register(name, type, configurationAction);
        }

        @Override
        public <U extends TestSuite> NamedDomainObjectProvider<U> register(String name, Class<U> type) throws InvalidUserDataException {
            return delegate.register(name, type);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public Iterator<TestSuite> iterator() {
            return delegate.iterator();
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean remove(Object o) {
            return delegate.remove(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return delegate.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return delegate.retainAll(c);
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }
}
