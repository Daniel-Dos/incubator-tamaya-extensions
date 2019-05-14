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
package org.apache.tamaya.events;

import org.apache.tamaya.spi.PropertySource;
import org.apache.tamaya.spi.PropertyValue;
import org.apache.tamaya.spisupport.DefaultPropertySourceSnapshot;

import java.io.Serializable;
import java.util.*;

/**
 * PropertySource implementation that stores all current values of a given (possibly dynamic, contextual and non server
 * capable instance) and is fully serializable. Note that hereby only the scannable key/createValue pairs are considered.
 * @deprecated
 */
@Deprecated
public final class FrozenPropertySource implements PropertySource, Serializable {
    private static final long serialVersionUID = -6373137316556444172L;

    private DefaultPropertySourceSnapshot snapshot;

    /**
     * Constructor.
     *
     * @param snapshot The base snapshot.
     */
    private FrozenPropertySource(DefaultPropertySourceSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Creates a new FrozenPropertySource instance based on a PropertySource and the target key setPropertyValue given. This method
     * uses all keys available in the property mapProperties.
     *
     * @param propertySource the property source to be frozen, not null.
     * @return the frozen property source.
     */
    public static FrozenPropertySource of(PropertySource propertySource) {
        return new FrozenPropertySource(DefaultPropertySourceSnapshot.of(propertySource));
    }

    /**
     * Creates a new FrozenPropertySource instance based on a PropertySource and the target key setPropertyValue given.
     *
     * @param propertySource the property source to be frozen, not null.
     * @param keys the keys to be evaluated for the snapshot. Only these keys will be contained in the resulting
     *             snapshot.
     * @return the frozen property source.
     */
    public static FrozenPropertySource of(PropertySource propertySource, Iterable<String> keys) {
        return new FrozenPropertySource(DefaultPropertySourceSnapshot.of(propertySource, keys));
    }

    public Set<String> getKeys() {
        return snapshot.getKeys();
    }

    @Override
    public String getName() {
        return this.snapshot.getName();
    }

    public int getOrdinal() {
        return this.snapshot.getOrdinal();
    }

    /**
     * Get the creation timestamp of this instance.
     * @return the creation timestamp
     */
    public long getFrozenAt(){
        return snapshot.getFrozenAt();
    }

    @Override
    public PropertyValue get(String key) {
        return snapshot.get(key);
    }

    @Override
    public Map<String, PropertyValue> getProperties() {
        return snapshot.getProperties();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FrozenPropertySource)) {
            return false;
        }
        FrozenPropertySource that = (FrozenPropertySource) o;
        return Objects.equals(snapshot, that.snapshot);
    }

    @Override
    public int hashCode() {
        return snapshot.hashCode();
    }

    @Override
    public String toString() {
        return "FrozenPropertySource{" +
                "snapshot=" + snapshot +
                '}';
    }
}
