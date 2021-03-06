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
package org.apache.tamaya.consul;

import org.apache.tamaya.spi.PropertyValue;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by atsticks on 07.01.16.
 */
public class ConsulPropertySourceTest {

    private final ConsulPropertySource propertySource = new ConsulPropertySource();

    @BeforeClass
    public static void setup(){
        System.setProperty("consul.urls", "http://127.0.0.1:8300");
    }

    @Test
    public void testGetOrdinal() throws Exception {
        assertThat(1000).isEqualTo(propertySource.getOrdinal());
    }

    @Test
    public void testGetDefaultOrdinal() throws Exception {
        assertThat(1000).isEqualTo(propertySource.getDefaultOrdinal());
    }

    @Test
    public void testGetName() throws Exception {
        assertThat("consul").isEqualTo(propertySource.getName());
    }

    @Test
    public void testGet() throws Exception {
        Map<String,PropertyValue> props = propertySource.getProperties();
        for(Map.Entry<String,PropertyValue> en:props.entrySet()){
            assertThat(propertySource.get(en.getKey())).isNotNull();
        }
    }

    @Test
    public void testGetProperties() throws Exception {
        Map<String,PropertyValue> props = propertySource.getProperties();
        assertThat(props).isNotNull();
    }

}
