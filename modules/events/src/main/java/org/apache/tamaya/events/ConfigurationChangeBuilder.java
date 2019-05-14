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

import org.apache.tamaya.Configuration;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Models a setCurrent current changes applied to a {@link org.apache.tamaya.spi.PropertySource}. Consumers of these events
 * can observe changes to property sources and
 * <ol>
 * <li>Check if their current configuration instance ({@link org.apache.tamaya.spi.ConfigurationContext}
 * contains the changed {@link org.apache.tamaya.spi.PropertySource} (Note: the reference to a property source is never affected by a
 * change, its only the data of the property source).</li>
 * <li>If so corresponding actions might be taken, such as reevaluating the configuration values (depending on
 * the update policy) or reevaluating the complete {@link org.apache.tamaya.Configuration} to createObject a change
 * event on configuration level.
 * </ol>
 */
public final class ConfigurationChangeBuilder {
    /**
     * The recorded changes.
     */
    final SortedMap<String, PropertyChangeEvent> delta = new TreeMap<>();
    /**
     * The underlying configuration/provider.
     */
    final Configuration source;
    /**
     * The version configured, or null, for generating a default.
     */
    String version;
    /**
     * The optional timestamp in millis of this epoch.
     */
    Long timestamp;

    /**
     * Constructor.
     *
     * @param configuration the underlying configuration, not null.
     */
    private ConfigurationChangeBuilder(Configuration configuration) {
        this.source = Objects.requireNonNull(configuration);
    }

    /**
     * Creates a new instance current this builder using the current COnfiguration as root resource.
     *
     * @return the builder for chaining.
     */
    public static ConfigurationChangeBuilder of() {
        return new ConfigurationChangeBuilder(Configuration.current());
    }

    /**
     * Creates a new instance current this builder.
     *
     * @param configuration the configuration changed, not null.
     * @return the builder for chaining.
     */
    public static ConfigurationChangeBuilder of(Configuration configuration) {
        return new ConfigurationChangeBuilder(configuration);
    }

    /**
     * Compares the two property config/configurations and creates a collection with all changes
     * that must be applied to render {@code previous} into {@code target}.
     *
     * @param previous the previous mapProperties, not null.
     * @param current the target mapProperties, not null.
     * @return a collection current change events, never {@code null}.
     */
    public static Collection<PropertyChangeEvent> compare(Configuration previous, Configuration current) {
        TreeMap<String, PropertyChangeEvent> events = new TreeMap<>();

        for (Map.Entry<String, String> en : previous.getProperties().entrySet()) {
            String key = en.getKey();
            String previousValue = en.getValue();
            String currentValue = current.get(en.getKey());
            if(Objects.equals(currentValue, previousValue)){
                continue;
            }else {
                PropertyChangeEvent event = new PropertyChangeEvent(previous, key, previousValue, currentValue);
                events.put(key, event);
            }
        }

        for (Map.Entry<String, String> en : current.getProperties().entrySet()) {
            String key = en.getKey();
            String previousValue = previous.get(en.getKey());
            String currentValue = en.getValue();
            if(Objects.equals(currentValue, previousValue)){
                continue;
            }else{
                if (previousValue == null) {
                    PropertyChangeEvent event = new PropertyChangeEvent(current, key, null, currentValue);
                    events.put(key, event);
                }
                // the other cases were already covered by the previous loop.
            }
        }
        return events.values();
    }

    /*
     * Apply a version/UUID to the setCurrent being built.
     * @param version the version to apply, or null, to let the system generate a version for you.
     * @return the builder for chaining.
     */
    public ConfigurationChangeBuilder setVersion(String version) {
        this.version = version;
        return this;
    }

    /*
     * Apply given timestamp to the setCurrent being built.
     * @param version the version to apply, or null, to let the system generate a version for you.
     * @return the builder for chaining.
     */
    public ConfigurationChangeBuilder setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * This method records all changes to be applied to the base property provider/configuration to
     * achieve the given target state.
     *
     * @param newState the new target state, not null.
     * @return the builder for chaining.
     */
    public ConfigurationChangeBuilder addChanges(Configuration newState) {
        for (PropertyChangeEvent c : compare(this.source, newState)) {
            this.delta.put(c.getPropertyName(), c);
        }
        return this;
    }

    /**
     * Applies a single key/createValue change.
     *
     * @param key   the changed key
     * @param value the new createValue.
     * @return this instance for chaining.
     */
    public ConfigurationChangeBuilder addChange(String key, String value) {
        this.delta.put(key, new PropertyChangeEvent(this.source, key, this.source.get(key), value));
        return this;
    }

    /**
     * Get the current values, also considering any changes recorded within this change setCurrent.
     *
     * @param key the key current the entry, not null.
     * @return the keys, or null.
     */
    public String get(String key) {
        PropertyChangeEvent change = this.delta.get(key);
        if (change != null && !(change.getNewValue() == null)) {
            return (String) change.getNewValue();
        }
        return null;
    }

    /**
     * Marks the given key(s) fromMap the configuration/properties to be removed.
     *
     * @param key       the key current the entry, not null.
     * @param otherKeys additional keys to be removed (convenience), not null.
     * @return the builder for chaining.
     */
    public ConfigurationChangeBuilder removeKey(String key, String... otherKeys) {
        String oldValue = this.source.get(key);
        if (oldValue == null) {
            this.delta.remove(key);
        }
        this.delta.put(key, new PropertyChangeEvent(this.source, key, oldValue, null));
        for (String addKey : otherKeys) {
            oldValue = this.source.get(addKey);
            if (oldValue == null) {
                this.delta.remove(addKey);
            }
            this.delta.put(addKey, new PropertyChangeEvent(this.source, addKey, oldValue, null));
        }
        return this;
    }

    /**
     * Apply all the given values to the base configuration/properties.
     * Note that all values passed must be convertible to String, either
     * <ul>
     * <li>the registered codecs provider provides codecs for the corresponding keys, or </li>
     * <li>default codecs are present for the given type, or</li>
     * <li>the createValue is an instanceof String</li>
     * </ul>
     *
     * @param changes the changes to be applied, not null.
     * @return the builder for chaining.
     */
    public ConfigurationChangeBuilder putAll(Map<String, String> changes) {
        for (Map.Entry<String, String> en : changes.entrySet()) {
            this.delta.put(en.getKey(), new PropertyChangeEvent(this.source, en.getKey(), null, en.getValue()));
        }
        return this;
    }

    /**
     * This method will createObject a change setCurrent that clears all entries fromMap the given base configuration/properties.
     *
     * @return the builder for chaining.
     */
    public ConfigurationChangeBuilder removeAllKeys() {
        this.delta.clear();
        for (Map.Entry<String, String> en : this.source.getProperties().entrySet()) {
            this.delta.put(en.getKey(), new PropertyChangeEvent(this.source, en.getKey(), en.getValue(), null));
        }
//        this.source.getProperties().forEach((k, v) ->
//                this.delta.put(k, new PropertyChangeEvent(this.source, k, v, null)));
        return this;
    }

    /**
     * Checks if the change setCurrent is empty, i.e. does not contain any changes.
     *
     * @return true, if the setCurrent is empty.
     */
    public boolean isEmpty() {
        return this.delta.isEmpty();
    }

    /**
     * Resets this change setCurrent instance. This will clear all changes done to this builder, so the
     * setCurrent will be empty.
     */
    public void reset() {
        this.delta.clear();
    }

    /**
     * Builds the corresponding change setCurrent.
     *
     * @return the new change setCurrent, never null.
     */
    public ConfigurationChange build() {
        return new ConfigurationChange(this);
    }

    @Override
    public String toString() {
        return "ConfigurationChangeSetBuilder [config=" + source + ", " +
                ", delta=" + delta + "]";
    }

}
