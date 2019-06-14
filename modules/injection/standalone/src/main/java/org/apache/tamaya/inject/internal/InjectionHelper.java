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
package org.apache.tamaya.inject.internal;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tamaya.ConfigException;
import org.apache.tamaya.Configuration;
import org.apache.tamaya.TypeLiteral;
import org.apache.tamaya.events.ConfigEventManager;
import org.apache.tamaya.events.spi.BaseConfigEvent;
import org.apache.tamaya.inject.api.Config;
import org.apache.tamaya.inject.spi.InjectionUtils;
import org.apache.tamaya.inject.api.WithPropertyConverter;
import org.apache.tamaya.inject.spi.ConfiguredType;
import org.apache.tamaya.resolver.spi.ExpressionEvaluator;
import org.apache.tamaya.spi.ConfigurationContext;
import org.apache.tamaya.spi.ConversionContext;
import org.apache.tamaya.spi.PropertyConverter;
import org.apache.tamaya.spi.PropertyValue;
import org.apache.tamaya.spi.ServiceContextManager;

/**
 * Utility class containing several aspects used in this module.
 */
@SuppressWarnings("unchecked")
final class InjectionHelper {

    private static final Logger LOG = Logger.getLogger(InjectionHelper.class.getName());

    private static final boolean RESOLUTION_MODULE_LOADED = checkResolutionModuleLoaded();

    private static final boolean EVENTS_AVAILABLE = checkForEvents();

    private static boolean checkForEvents() {
        try{
            Class.forName("org.apache.tamaya.events.FrozenConfiguration");
            LOG.info("Detected tamaya-events, will trigger ConfigEvents...");
            return true;
        } catch(Exception e){
            LOG.info("tamaya-events not installed, will not trigger any ConfigEvents...");
            return false;
        }
    }

    private static boolean checkResolutionModuleLoaded() {
        try {
            Class.forName("org.apache.tamaya.resolver.internal.DefaultExpressionEvaluator");
            LOG.info("Detected tamaya-resolver...");
            return true;
        } catch (ClassNotFoundException e) {
            LOG.finest("tamaya-resolver not installed.");
            return false;
        }
    }

    private InjectionHelper() {
    }

    /**
     * Internally evaluated the current valid configuration keys based on the given annotations present.
     * @param method the method
     * @return the keys to be returned, or null.
     */
    public static String getConfigValue(Method method, Configuration config) {
        return getConfigValue(method, null, config);
    }

    /**
     * Internally evaluated the current valid configuration keys based on the given annotations present.
     * @param method the method
     * @param retKey the array to return the key found, or null.
     * @return the keys to be returned, or null.
     */
    public static String getConfigValue(Method method, String[] retKey, Configuration config) {
        return getConfigValueInternal(method, retKey, config);
    }

    /**
     * Internally evaluated the current valid configuration keys based on the given annotations present.
     * @param field the field
     * @return the keys to be returned, or null.
     */
    public static String getConfigValue(Field field, Configuration config) {
        return getConfigValue(field, null, config);
    }

    /**
     * Internally evaluated the current valid configuration keys based on the given annotations present.
     * @param field the field
     * @param retKey the array to return the key found, or null.
     * @return the keys to be returned, or null.
     */
    public static String getConfigValue(Field field, String[] retKey, Configuration config) {
        return getConfigValueInternal(field, retKey, config);
    }

    /**
     * Internally evaluated the current valid configuration keys based on the given annotations present.
     *
     * @return the keys to be returned, or null.
     */
    private static String getConfigValueInternal(AnnotatedElement element,
                                                 String[] retKey, Configuration config) {
        List<String> keys = InjectionUtils.getMemberKeys((Member) element);
        String configValue = evaluteConfigValue(keys, retKey, config);
        if (configValue == null) {
            Config configAnnot = element.getAnnotation(Config.class);
            if(configAnnot!=null && !configAnnot.defaultValue().equals(Config.UNCONFIGURED_VALUE)){
                return configAnnot.defaultValue();
            }
        }
        return configValue;
    }

    private static String evaluteConfigValue(List<String> keys, String[] retKey, Configuration config) {
        String configValue = null;
        for (String key : keys) {
            configValue = config.getOrDefault(key, null);
            if (configValue != null) {
                if(retKey!=null && retKey.length>0){
                    retKey[0] = key;
                }
                break;
            }
        }
        return configValue;
    }

    public static <T> T adaptValue(AnnotatedElement element, TypeLiteral<T> targetType, String key, String configValue) {
        // Check for adapter/filter
        T adaptedValue = null;
        WithPropertyConverter converterAnnot = element.getAnnotation(WithPropertyConverter.class);
        Class<? extends PropertyConverter<T>> converterType;
        if (converterAnnot != null) {
            converterType = (Class<? extends PropertyConverter<T>>) converterAnnot.value();
            if (!converterType.isAssignableFrom(WithPropertyConverter.class)) {
                try {
                // TODO cache here...
                    ConversionContext ctx = new ConversionContext.Builder(key,targetType)
                            .setAnnotatedElement(element).build();
                    PropertyConverter<T> converter = converterType.getConstructor()
                            .newInstance();
                    adaptedValue = converter.convert(configValue, ctx);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Failed to convert using explicit PropertyConverter on " + element +
                            ", trying default conversion.", e);
                }
            }
        }
        if (adaptedValue != null) {
            return adaptedValue;
        }
        if (String.class == targetType.getType()) {
            return (T) configValue;
        } else{
            ConfigurationContext configContext = Configuration.current().getContext();
            List<PropertyConverter<T>> converters = configContext
                    .getPropertyConverters(targetType);
            ConversionContext ctx = new ConversionContext.Builder(Configuration.current(),
                    key, targetType).setAnnotatedElement(element).build();
            for (PropertyConverter<T> converter : converters) {
                adaptedValue = converter.convert(configValue, ctx);
                if (adaptedValue != null) {
                    return adaptedValue;
                }
            }
        }
        if(configValue!=null) {
            throw new ConfigException("Non convertible property type: " + element);
        }
        return null;
    }

    /**
     * Method that allows to statically check, if the resolver module is loaded. If the module is loaded
     * value expressions are automatically forwarded to the resolver module for resolution.
     *
     * @return true, if the resolver module is on the classpath.
     */
    public static boolean isResolutionModuleLoaded() {
        return RESOLUTION_MODULE_LOADED;
    }

    /**
     * Evaluates the given expression.
     *
     * @param expression the expression, not null.
     * @return the evaluated expression.
     */
    public static String evaluateValue(String expression, ClassLoader classLoader) {
        if (!RESOLUTION_MODULE_LOADED) {
            return expression;
        }
        ExpressionEvaluator evaluator = ServiceContextManager.getServiceContext(classLoader)
                .getService(ExpressionEvaluator.class);
        if (evaluator != null) {
            return evaluator.evaluateExpression(PropertyValue.createValue("<injection>", expression), true).getValue();
        }
        return expression;
    }

    /**
     * This method distributes the configuration event, if the Tamaya event module is accessible.
     * When Tamaya events are not available, the call simply returns.
     * @param event the event to be distributed, not null.
     */
    static void sendConfigurationEvent(ConfiguredType event, ClassLoader classLoader) {
        if(EVENTS_AVAILABLE){
            ConfigEventManager.getInstance(classLoader).fireEvent(new BaseConfigEvent<ConfiguredType>(event, ConfiguredType.class) {});
        }
    }
}
