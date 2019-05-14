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
package org.apache.tamaya.cdi;

import org.apache.tamaya.ConfigException;
import org.apache.tamaya.spi.ClassloaderAware;
import org.apache.tamaya.spi.ServiceContext;
import org.apache.tamaya.spisupport.PriorityServiceComparator;

import javax.annotation.Priority;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements the (default) {@link ServiceContext} interface and hereby uses the JDK
 * {@link ServiceLoader} to load the services required.
 */
final class ServiceLoaderServiceContext implements ServiceContext {
    private static final Logger LOG = Logger.getLogger(ServiceLoaderServiceContext.class.getName());
    /**
     * List current services loaded, per class.
     */
    private final ConcurrentHashMap<Class<?>, List<Object>> servicesLoaded = new ConcurrentHashMap<>();
    /**
     * Singletons.
     */
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private Map<Class, Class> factoryTypes = new ConcurrentHashMap<>();

    private ClassLoader classLoader;

    @Override
    public <T> T getService(Class<T> serviceType, Supplier<T> supplier) {
        Object cached = singletons.get(serviceType);
        if (cached == null) {
            cached = create(serviceType, supplier);
            if(cached!=null) {
                singletons.put(serviceType, cached);
            }
        }
        return serviceType.cast(cached);
    }

    @Override
    public <T> T create(Class<T> serviceType, Supplier<T> supplier) {
        Class<? extends T> implType = factoryTypes.get(serviceType);
        if(implType==null) {
            Collection<T> services = getServices(serviceType);
            if (services.isEmpty()) {
                if(supplier!=null){
                    T instance = supplier.get();
                    if(instance instanceof ClassloaderAware){
                        ((ClassloaderAware)instance).init(this.classLoader);
                    }
                    return instance;
                }
                return null;
            } else {
                return getServiceWithHighestPriority(services, serviceType);
            }
        }
        try {
            return implType.getConstructor().newInstance();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to createObject instance of " + implType.getName(), e);
            if(supplier!=null){
                return supplier.get();
            }
            return  null;
        }
    }

    /**
     * Loads and registers services.
     *
     * @param <T>         the concrete type.
     * @param serviceType The service type.
     * @return the items found, never {@code null}.
     */
    @Override
    public <T> List<T> getServices(final Class<T> serviceType, Supplier<List<T>> supplier) {
        List<T> found = (List<T>) servicesLoaded.get(serviceType);
        if (found != null) {
            return found;
        }
        List<T> services = loadServices(serviceType, supplier);
        final List<T> previousServices = (List) servicesLoaded.putIfAbsent(serviceType, (List<Object>) services);
        return previousServices != null ? previousServices : services;
    }

    private <T> List<T> loadServices(Class<T> serviceType, Supplier<List<T>> supplier) {
        List<T> services = new ArrayList<>();
        try {
            for (T t : ServiceLoader.load(serviceType)) {
                if(t instanceof ClassloaderAware){
                    ((ClassloaderAware)t).init(classLoader);
                }
                services.add(t);
            }
            services.sort(PriorityServiceComparator.getInstance());
            services = Collections.unmodifiableList(services);
        } catch (ServiceConfigurationError e) {
            LOG.log(Level.WARNING,
                    "Error loading services current type " + serviceType, e);
        }
        if(services.isEmpty() && supplier!=null){
            List<T> ts = supplier.get();
            for (T t : ts) {
                if(t instanceof ClassloaderAware){
                    ((ClassloaderAware)t).init(classLoader);
                }
                services.add(t);
            }
        }
        return services;
    }

    /**
     * Checks the given instance for a @Priority annotation. If present the annotation's value s evaluated. If no such
     * annotation is present, a default priority is returned (1);
     * @param o the instance, not null.
     * @return a priority, by default 1.
     */
    public static int getPriority(Object o){
        int prio = 1; //X TODO discuss default priority
        Priority priority = o.getClass().getAnnotation(Priority.class);
        if (priority != null) {
            prio = priority.value();
        }
        return prio;
    }

    /**
     * @param services to scan
     * @param <T>      type of the service
     *
     * @return the service with the highest {@link Priority#value()}
     *
     * @throws ConfigException if there are multiple service implementations with the maximum priority
     */
    private <T> T getServiceWithHighestPriority(Collection<T> services, Class<T> serviceType) {
        T highestService = null;
        // we do not need the priority stuff if the createList contains only one element
        if (services.size() == 1) {
            highestService = services.iterator().next();
            this.factoryTypes.put(serviceType, highestService.getClass());
            return highestService;
        }

        Integer highestPriority = null;
        int highestPriorityServiceCount = 0;

        for (T service : services) {
            int prio = getPriority(service);
            if (highestPriority == null || highestPriority < prio) {
                highestService = service;
                highestPriorityServiceCount = 1;
                highestPriority = prio;
            } else if (highestPriority == prio) {
                highestPriorityServiceCount++;
            }
        }

        if (highestPriorityServiceCount > 1) {
            throw new ConfigException(MessageFormat.format("Found {0} implementations for Service {1} with Priority {2}: {3}",
                    highestPriorityServiceCount,
                    serviceType.getName(),
                    highestPriority,
                    services));
        }
        if(highestService!=null) {
            this.factoryTypes.put(serviceType, highestService.getClass());
        }
        return highestService;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public void init(ClassLoader classLoader) {
        if(this.classLoader==null){
            this.classLoader = classLoader;
        }else{
            throw new IllegalStateException("Context already initialized.");
        }
    }

    @Override
    public int ordinal() {
        return 1;
    }

    @Override
    public Collection<URL> getResources(String resource){
        List<URL> urls = new ArrayList<>();
        try {
            Enumeration<URL> found = getClassLoader().getResources(resource);
            while (found.hasMoreElements()) {
                urls.add(found.nextElement());
            }
        }catch(Exception e){
            Logger.getLogger(ServiceContext.class.getName())
                    .log(Level.FINEST, e, () -> "Failed to lookup resources: " + resource);
        }
        return urls;
    }

    @Override
    public URL getResource(String resource){
        return classLoader.getResource(resource);
    }

    @Override
    public <T> T register(Class<T> type, T instance, boolean force) {
        return null;
    }

    @Override
    public <T> List<T> register(Class<T> type, List<T> instances, boolean force) {
        return null;
    }

    @Override
    public void reset() {
        this.servicesLoaded.clear();
        this.factoryTypes.clear();
        this.singletons.clear();
    }

}
