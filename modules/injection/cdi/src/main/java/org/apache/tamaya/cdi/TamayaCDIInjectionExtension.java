/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tamaya.cdi;

import org.apache.tamaya.ConfigException;
import org.apache.tamaya.ConfigOperator;
import org.apache.tamaya.Configuration;
import org.apache.tamaya.inject.api.Config;
import org.apache.tamaya.inject.api.ConfigSection;
import org.apache.tamaya.inject.api.WithConfigOperator;
import org.apache.tamaya.inject.api.WithPropertyConverter;
import org.apache.tamaya.spi.PropertyConverter;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;


/**
 * CDI Extension module that adds injection mechanism for configuration.
 *
 * @see Config
 * @see ConfigSection
 * @see ConfigException
 */
public class TamayaCDIInjectionExtension implements Extension {

    private static final Logger LOG = Logger.getLogger(TamayaCDIInjectionExtension.class.getName());

    static final Map<Class, UnaryOperator<Configuration>> CUSTOM_OPERATORS = new ConcurrentHashMap<>();
    static final Map<Class, PropertyConverter> CUSTOM_CONVERTERS = new ConcurrentHashMap<>();

    private final Set<Type> types = new HashSet<>();
    private Bean<?> tamayaProducerBean;

    /**
     * Constructor for loading logging its load.
     */
    public TamayaCDIInjectionExtension(){
        LOG.finest("Loading Tamaya CDI Support...");
    }

    /**
     * Method that checks the configuration injection points during deployment for available configuration.
     * @param pb the bean to process.
     * @param beanManager the bean manager to notify about new injections.
     */
    public void retrieveTypes(@Observes final ProcessBean<?> pb, BeanManager beanManager) {

        final Set<InjectionPoint> ips = pb.getBean().getInjectionPoints();
        CDIConfiguredType configuredType = new CDIConfiguredType(pb.getBean().getBeanClass());

        boolean configured = false;
        for (InjectionPoint injectionPoint : ips) {
            if (injectionPoint.getAnnotated().isAnnotationPresent(Config.class)) {
                LOG.fine("Configuring: " + injectionPoint);
                final Config annotation = injectionPoint.getAnnotated().getAnnotation(Config.class);
                final ConfigSection typeAnnot = injectionPoint.getMember().getDeclaringClass().getAnnotation(ConfigSection.class);
                final List<String> keys = evaluateKeys(injectionPoint.getMember().getName(),
                        annotation!=null?annotation.key():null,
                        annotation!=null?annotation.alternateKeys():null,
                        typeAnnot!=null?typeAnnot.value():null);
                final WithConfigOperator withOperatorAnnot = injectionPoint.getAnnotated().getAnnotation(WithConfigOperator.class);
                if(withOperatorAnnot!=null){
                    tryLoadOpererator(withOperatorAnnot.value());
                }
                final WithPropertyConverter withConverterAnnot = injectionPoint.getAnnotated().getAnnotation(WithPropertyConverter.class);
                if(withConverterAnnot!=null){
                    tryLoadConverter(withConverterAnnot.value());
                }
                Type originalType = injectionPoint.getAnnotated().getBaseType();
                Type convertedType = unwrapType(originalType);
                types.add(convertedType);
                configured = true;
                LOG.finest("Enabling Tamaya CDI Configuration on bean: " + configuredType.getName());
                configuredType.addConfiguredMember(injectionPoint, keys);
            }
        }
        if(configured) {
            beanManager.fireEvent(configuredType);
        }
    }


    public void captureConvertBean(@Observes final ProcessProducerMethod<?, ?> ppm) {
        if (ppm.getAnnotated().isAnnotationPresent(Config.class)) {
            tamayaProducerBean = ppm.getBean();
        }
    }

    public void addConverter(@Observes final AfterBeanDiscovery abd, final BeanManager bm) {
        if(!types.isEmpty()&& tamayaProducerBean!=null) {
            abd.addBean(new ConverterBean(tamayaProducerBean, types));
        }
    }

    private Type unwrapType(Type type) {
        if(type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if(rawType == Provider.class || rawType == Instance.class) {
                return ((ParameterizedType) type).getActualTypeArguments()[0];
            }
        }
        return type;
    }

    private void tryLoadOpererator(Class<? extends ConfigOperator> operatorClass) {
        Objects.requireNonNull(operatorClass);
        if(ConfigOperator.class == operatorClass){
            return;
        }
        try{
            if(!CUSTOM_OPERATORS.containsKey(operatorClass)) {
                final ConfigOperator op = operatorClass.getConstructor().newInstance();
                CUSTOM_OPERATORS.put(operatorClass, op::operate);
            }
        } catch(Exception e){
            throw new ConfigException("Custom ConfigOperator could not be loaded: " + operatorClass.getName(), e);
        }
    }

    private void tryLoadConverter(Class<? extends PropertyConverter> converterClass) {
        Objects.requireNonNull(converterClass);
        if(PropertyConverter.class == converterClass){
            return;
        }
        try{
            if(!CUSTOM_CONVERTERS.containsKey(converterClass)) {
                CUSTOM_CONVERTERS.put(converterClass, converterClass.getConstructor().newInstance());
            }
        } catch(Exception e){
            throw new ConfigException("Custom PropertyConverter could not be loaded: " + converterClass.getName(), e);
        }
    }

    /**
     * Evaluates the effective keys to be used. if no {@code keys} are defined, {@code memberName} is used.
     * The effective keys are then combined with the sections given (if any) and only, if the given keys are not
     * absolute keys (surrounded by brackets).
     * @param memberName the default member name, not null.
     * @param mainKey the main key, may be empty, or null.
     * @param alternateKeys the alternate keys, may be empty, or null.
     * @param section the default section, may be empty. May also be null.
     * @return the createList of keys to be finally used for configuration resolution in order of
     * precedence. The first keys in the createList that could be successfully resolved define the final
     * configuration value.
     */
    public static List<String> evaluateKeys(String memberName, String mainKey, String[] alternateKeys, String section) {
        List<String> effKeys = new ArrayList<>();
        if(mainKey!=null && !mainKey.trim().isEmpty()){
            effKeys.add(mainKey);
        }
        if (effKeys.isEmpty()) {
            effKeys.add(memberName);
        }
        effKeys.addAll(Arrays.asList(alternateKeys));
        ListIterator<String> iterator = effKeys.listIterator();
        while (iterator.hasNext()) {
            String next = iterator.next();
            if (next.startsWith("[") && next.endsWith("]")) {
                // absolute key, strip away brackets, take key as is
                iterator.set(next.substring(1, next.length() - 1));
            } else {
                if (section != null && !section.trim().isEmpty()) {
                    // Remove original entry, since it will be replaced with prefixed entries
                    iterator.remove();
                    // Add prefixed entries, including absolute (root) entry for "" area keys.
                    iterator.add(section + '.' + next);
                }
            }
        }
        return effKeys;
    }


    /**
     * Internally used conversion bean.
     */
    private static class ConverterBean implements Bean<Object> {

        private final Bean<Object> delegate;
        private final Set<Type> types;

        public ConverterBean(final Bean convBean, final Set<Type> types) {
            this.types = types;
            this.delegate = Objects.requireNonNull(convBean);
        }

        @Override
        public Set<Type> getTypes() {
            return types;
        }

        @Override
        public Class<?> getBeanClass() {
            return delegate.getBeanClass();
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return delegate.getInjectionPoints();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return delegate.getQualifiers();
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return delegate.getScope();
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return delegate.getStereotypes();
        }

        @Override
        public boolean isAlternative() {
            return delegate.isAlternative();
        }

        @Override
        public boolean isNullable() {
            return delegate.isNullable();
        }

        @Override
        public Object create(CreationalContext<Object> creationalContext) {
            return delegate.create(creationalContext);
        }

        @Override
        public void destroy(Object instance, CreationalContext<Object> creationalContext) {
            delegate.destroy(instance, creationalContext);
        }
    }

}
