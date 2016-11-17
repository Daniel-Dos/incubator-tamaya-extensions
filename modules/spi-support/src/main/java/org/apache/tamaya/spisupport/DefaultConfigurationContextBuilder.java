/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.tamaya.spisupport;

import org.apache.tamaya.TypeLiteral;
import org.apache.tamaya.spi.ConfigurationContext;
import org.apache.tamaya.spi.ConfigurationContextBuilder;
import org.apache.tamaya.spi.PropertyConverter;
import org.apache.tamaya.spi.PropertyFilter;
import org.apache.tamaya.spi.PropertySource;
import org.apache.tamaya.spi.PropertySourceProvider;
import org.apache.tamaya.spi.PropertyValueCombinationPolicy;
import org.apache.tamaya.spi.ServiceContextManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Default implementation of {@link ConfigurationContextBuilder}.
 */
public class DefaultConfigurationContextBuilder implements ConfigurationContextBuilder {

    private static final Logger LOG = Logger.getLogger(DefaultConfigurationContextBuilder.class.getName());

    public static final Comparator<PropertySource> DEFAULT_PROPERTYSOURCE_COMPARATOR = new PropertySourceComparator();
    public static final Comparator<?> DEFAULT_PROPERTYFILTER_COMPARATOR = new PriorityServiceComparator();

    List<PropertyFilter> propertyFilters = new ArrayList<>();
    List<PropertySource> propertySources = new ArrayList<>();
    PropertyValueCombinationPolicy combinationPolicy =
            PropertyValueCombinationPolicy.DEFAULT_OVERRIDING_COLLECTOR;
    Map<TypeLiteral<?>, Collection<PropertyConverter<?>>> propertyConverters = new HashMap<>();

    /**
     * Flag if the config has already been built.
     * Configuration can be built only once
     */
    private boolean built;



    /**
     * Creates a new builder instance.
     */
    public DefaultConfigurationContextBuilder() {
    }

    /**
     * Creates a new builder instance.
     */
    public DefaultConfigurationContextBuilder(ConfigurationContext context) {
        this.propertyConverters.putAll(context.getPropertyConverters());
        this.propertyFilters.addAll(context.getPropertyFilters());
        for(PropertySource ps:context.getPropertySources()) {
            addPropertySources(ps);
        }
        this.combinationPolicy = context.getPropertyValueCombinationPolicy();
    }

    /**
     * Allows to set configuration context during unit tests.
     */
    ConfigurationContextBuilder setConfigurationContext(ConfigurationContext configurationContext) {
        checkBuilderState();
        //noinspection deprecation
        this.propertyFilters.clear();
        this.propertyFilters.addAll(configurationContext.getPropertyFilters());
        this.propertySources.clear();
        for(PropertySource ps:configurationContext.getPropertySources()) {
            addPropertySources(ps);
        }
        this.propertyConverters.clear();
        this.propertyConverters.putAll(configurationContext.getPropertyConverters());
        this.combinationPolicy = configurationContext.getPropertyValueCombinationPolicy();
        return this;
    }


    @Override
    public ConfigurationContextBuilder setContext(ConfigurationContext context) {
        checkBuilderState();
        this.propertyConverters.putAll(context.getPropertyConverters());
        for(PropertySource ps:context.getPropertySources()){
            this.propertySources.add(ps);
        }
        this.propertyFilters.addAll(context.getPropertyFilters());
        this.combinationPolicy = context.getPropertyValueCombinationPolicy();
        return this;
    }

    @Override
    public ConfigurationContextBuilder addPropertySources(PropertySource... sources){
        return addPropertySources(Arrays.asList(sources));
    }

    @Override
    public ConfigurationContextBuilder addPropertySources(Collection<PropertySource> sources){
        checkBuilderState();
        for(PropertySource source:sources) {
            if (!this.propertySources.contains(source)) {
                this.propertySources.add(source);
            }
        }
        return this;
    }

    public DefaultConfigurationContextBuilder addDefaultPropertyFilters() {
        checkBuilderState();
        for(PropertyFilter pf:ServiceContextManager.getServiceContext().getServices(PropertyFilter.class)){
            addPropertyFilters(pf);
        }
        return this;
    }

    public DefaultConfigurationContextBuilder addDefaultPropertySources() {
        checkBuilderState();
        for(PropertySource ps:ServiceContextManager.getServiceContext().getServices(PropertySource.class)){
            addPropertySources(ps);
        }
        for(PropertySourceProvider pv:ServiceContextManager.getServiceContext().getServices(PropertySourceProvider.class)){
            for(PropertySource ps:pv.getPropertySources()){
                addPropertySources(ps);
            }
        }
        return this;
    }

    public DefaultConfigurationContextBuilder addDefaultPropertyConverters() {
        checkBuilderState();
        for(Map.Entry<TypeLiteral, Collection<PropertyConverter>> en:getDefaultPropertyConverters().entrySet()){
            for(PropertyConverter pc: en.getValue()) {
                addPropertyConverters(en.getKey(), pc);
            }
        }
        return this;
    }

    @Override
    public ConfigurationContextBuilder removePropertySources(PropertySource... propertySources) {
        return removePropertySources(Arrays.asList(propertySources));
    }

    @Override
    public ConfigurationContextBuilder removePropertySources(Collection<PropertySource> propertySources) {
        checkBuilderState();
        this.propertySources.removeAll(propertySources);
        return this;
    }

    private PropertySource getPropertySource(String name) {
        for(PropertySource ps:propertySources){
            if(ps.getName().equals(name)){
                return ps;
            }
        }
        throw new IllegalArgumentException("No such PropertySource: "+name);
    }

    @Override
    public List<PropertySource> getPropertySources() {
        return this.propertySources;
    }

    @Override
    public ConfigurationContextBuilder increasePriority(PropertySource propertySource) {
        checkBuilderState();
        int index = propertySources.indexOf(propertySource);
        if(index<0){
            throw new IllegalArgumentException("No such PropertySource: " + propertySource);
        }
        if(index<(propertySources.size()-1)){
            propertySources.remove(propertySource);
            propertySources.add(index+1, propertySource);
        }
        return this;
    }

    @Override
    public ConfigurationContextBuilder decreasePriority(PropertySource propertySource) {
        checkBuilderState();
        int index = propertySources.indexOf(propertySource);
        if(index<0){
            throw new IllegalArgumentException("No such PropertySource: " + propertySource);
        }
        if(index>0){
            propertySources.remove(propertySource);
            propertySources.add(index-1, propertySource);
        }
        return this;
    }

    @Override
    public ConfigurationContextBuilder highestPriority(PropertySource propertySource) {
        checkBuilderState();
        int index = propertySources.indexOf(propertySource);
        if(index<0){
            throw new IllegalArgumentException("No such PropertySource: " + propertySource);
        }
        if(index<(propertySources.size()-1)){
            propertySources.remove(propertySource);
            propertySources.add(propertySource);
        }
        return this;
    }

    @Override
    public ConfigurationContextBuilder lowestPriority(PropertySource propertySource) {
        checkBuilderState();
        int index = propertySources.indexOf(propertySource);
        if(index<0){
            throw new IllegalArgumentException("No such PropertySource: " + propertySource);
        }
        if(index>0){
            propertySources.remove(propertySource);
            propertySources.add(0, propertySource);
        }
        return this;
    }

    @Override
    public ConfigurationContextBuilder addPropertyFilters(PropertyFilter... filters){
        return addPropertyFilters(Arrays.asList(filters));
    }

    @Override
    public ConfigurationContextBuilder addPropertyFilters(Collection<PropertyFilter> filters){
        checkBuilderState();
        for(PropertyFilter f:filters) {
            if (!this.propertyFilters.contains(f)) {
                this.propertyFilters.add(f);
            }
        }
        return this;
    }

    @Override
    public ConfigurationContextBuilder removePropertyFilters(PropertyFilter... filters) {
        return removePropertyFilters(Arrays.asList(filters));
    }

    @Override
    public ConfigurationContextBuilder removePropertyFilters(Collection<PropertyFilter> filters) {
        checkBuilderState();
        this.propertyFilters.removeAll(filters);
        return this;
    }


    @Override
    public <T> ConfigurationContextBuilder removePropertyConverters(TypeLiteral<T> typeToConvert,
                                                                    PropertyConverter<T>... converters) {
        return removePropertyConverters(typeToConvert, Arrays.asList(converters));
    }

    @Override
    public <T> ConfigurationContextBuilder removePropertyConverters(TypeLiteral<T> typeToConvert,
                                                                    Collection<PropertyConverter<T>> converters) {
        Collection<PropertyConverter<?>> subConverters = this.propertyConverters.get(typeToConvert);
        if(subConverters!=null) {
            subConverters.removeAll(converters);
        }
        return this;
    }

    @Override
    public ConfigurationContextBuilder removePropertyConverters(TypeLiteral<?> typeToConvert) {
        this.propertyConverters.remove(typeToConvert);
        return this;
    }


    @Override
    public ConfigurationContextBuilder setPropertyValueCombinationPolicy(PropertyValueCombinationPolicy combinationPolicy){
        checkBuilderState();
        this.combinationPolicy = Objects.requireNonNull(combinationPolicy);
        return this;
    }


    @Override
    public <T> ConfigurationContextBuilder addPropertyConverters(TypeLiteral<T> type, PropertyConverter<T>... propertyConverters){
        checkBuilderState();
        Objects.requireNonNull(type);
        Objects.requireNonNull(propertyConverters);
        Collection<PropertyConverter<?>> converters = this.propertyConverters.get(type);
        if(converters==null){
            converters = new ArrayList<>();
            this.propertyConverters.put(type, converters);
        }
        for(PropertyConverter<T> propertyConverter:propertyConverters) {
            if (!converters.contains(propertyConverter)) {
                converters.add(propertyConverter);
            } else {
                LOG.warning("Converter ignored, already registered: " + propertyConverter);
            }
        }
        return this;
    }

    @Override
    public <T> ConfigurationContextBuilder addPropertyConverters(TypeLiteral<T> type, Collection<PropertyConverter<T>> propertyConverters){
        checkBuilderState();
        Objects.requireNonNull(type);
        Objects.requireNonNull(propertyConverters);
        Collection<PropertyConverter<?>> converters = this.propertyConverters.get(type);
        if(converters==null){
            converters = new ArrayList<>();
            this.propertyConverters.put(type, converters);
        }
        for(PropertyConverter<T> propertyConverter:propertyConverters) {
            if (!converters.contains(propertyConverter)) {
                converters.add(propertyConverter);
            } else {
                LOG.warning("Converter ignored, already registered: " + propertyConverter);
            }
        }
        return this;
    }

    protected ConfigurationContextBuilder loadDefaults() {
        checkBuilderState();
        this.combinationPolicy = PropertyValueCombinationPolicy.DEFAULT_OVERRIDING_COLLECTOR;
        addDefaultPropertySources();
        addDefaultPropertyFilters();
        addDefaultPropertyConverters();
        return this;
    }


    private Map<TypeLiteral, Collection<PropertyConverter>> getDefaultPropertyConverters() {
        Map<TypeLiteral, Collection<PropertyConverter>> result = new HashMap<>();
        for (PropertyConverter conv : ServiceContextManager.getServiceContext().getServices(
                PropertyConverter.class)) {
            TypeLiteral target = TypeLiteral.of(TypeLiteral.of(conv.getClass()).getType());
            Collection<PropertyConverter> convList = result.get(target);
            if (convList == null) {
                convList = new ArrayList<>();
                result.put(target, convList);
            }
            convList.add(conv);
        }
        return result;
    }


    /**
     * Builds a new configuration based on the configuration of this builder instance.
     *
     * @return a new {@link org.apache.tamaya.Configuration configuration instance},
     *         never {@code null}.
     */
    @Override
    public ConfigurationContext build() {
        checkBuilderState();
        built = true;
        return new DefaultConfigurationContext(this);
    }

    @Override
    public ConfigurationContextBuilder sortPropertyFilter(Comparator<PropertyFilter> comparator) {
        Collections.sort(propertyFilters, comparator);
        return this;
    }

    @Override
    public ConfigurationContextBuilder sortPropertySources(Comparator<PropertySource> comparator) {
        Collections.sort(propertySources, comparator);
        return this;
    }

    private void checkBuilderState() {
        if (built) {
            throw new IllegalStateException("Configuration has already been build.");
        }
    }

    @Override
    public List<PropertyFilter> getPropertyFilters() {
        return propertyFilters;
    }

    @Override
    public Map<TypeLiteral<?>, Collection<PropertyConverter<?>>> getPropertyConverter() {
        return Collections.unmodifiableMap(this.propertyConverters);
    }
}
