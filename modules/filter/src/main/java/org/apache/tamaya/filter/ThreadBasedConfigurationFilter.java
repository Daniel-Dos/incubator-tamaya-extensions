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
package org.apache.tamaya.filter;

import org.apache.tamaya.spi.PropertyFilter;
import org.apache.tamaya.spi.PropertyValue;
import org.osgi.service.component.annotations.Component;
import org.apache.tamaya.spi.FilterContext;

/**
 * Hereby
 * <ul>
 *     <li><b>Single</b> filters are applied only when values are explicitly accessed. This is useful, e.g. for
 *     filtering passwords into clear text variants. Nevertheless metadata keys hidden on mapProperties level must be
 *     accessible (=not filtered) when accessed as single values.</li>
 *     <li><b>Map</b> filters are applied when values are filtered as part of a full properties access.
 *     Often filtering in these cases is more commonly applied, e.g. you dont want to show up all kind of metadata.
 *     </li>
 * </ul>
 *     For both variants individual filter rules can be applied here. All filters configured are managed on a
 *     thread-local level, so this class is typically used to temporarely filter out some values. Do not forget to
 *     restore its state, when not using a thread anymore (especially important in multi-threaded environments), not
 *     doing so will createObject nasty side effects of configuration not being visisble depending on the thread
 *     active.
 */
@Component
public final class ThreadBasedConfigurationFilter implements PropertyFilter{

    static final ThreadLocal<Boolean> THREADED_METADATA_FILTERED = new ThreadLocal<Boolean>(){
        @Override
        protected Boolean initialValue() {
            return Boolean.TRUE;
        }
    };

    private static final ThreadLocal<ThreadFilterContext> THREADED_MAP_FILTERS = new ThreadLocal<ThreadFilterContext>(){
        @Override
        protected ThreadFilterContext initialValue() {
            return new ThreadFilterContext();
        }
    };

    private static final ThreadLocal<ThreadFilterContext> THREADED_VALUE_FILTERS = new ThreadLocal<ThreadFilterContext>(){
        @Override
        protected ThreadFilterContext initialValue() {
            return new ThreadFilterContext();
        }
    };

    /**
     * Flag if metadata entries (starting with an '_') are filtered out on when accessing multiple properties, default
     * is {@code true}.
     * @return true, if metadata entries (starting with an '_') are to be filtered.
     */
    public static boolean isMetadataFiltered(){
        return THREADED_METADATA_FILTERED.get();
    }

    /**
     * Seactivates metadata filtering also on global mapProperties access for this thread.
     * @see #cleanupFilterContext()
     * @param filtered true,to enable metadata filtering (default).
     */
    public static void setMetadataFiltered(boolean filtered){
        THREADED_METADATA_FILTERED.set(filtered);
    }

    /**
     * Access the filtering configuration that is used on the current thread for
     * filtering single property values accessed.
     *
     * @return the filtering config, never null.
     */
    public static ThreadFilterContext getSingleValueFilterContext(){
        return THREADED_VALUE_FILTERS.get();
    }

    /**
     * Access the filtering configuration that is used used on the current thread
     * for filtering configuration properties accessed as full
     * mapProperties.
     * @return the filtering config, never null.
     */
    public static ThreadFilterContext getMapFilterContext(){
        return THREADED_MAP_FILTERS.get();
    }

    /**
     * Removes all programmable filters active on the current thread.
     */
    public static void cleanupFilterContext(){
        THREADED_MAP_FILTERS.get().clearFilters();
        THREADED_VALUE_FILTERS.get().clearFilters();
        THREADED_METADATA_FILTERED.set(true);
    }

    @Override
    public PropertyValue filterProperty(PropertyValue valueToBeFiltered, FilterContext context) {
        if(context.isSinglePropertyScoped()){
            for(PropertyFilter pred: THREADED_VALUE_FILTERS.get().getFilters()){
                valueToBeFiltered = pred.filterProperty(valueToBeFiltered, context);
            }
        }else{
            for(PropertyFilter pred: THREADED_MAP_FILTERS.get().getFilters()){
                valueToBeFiltered = pred.filterProperty(valueToBeFiltered, context);
            }
        }
        return valueToBeFiltered;
    }
}
