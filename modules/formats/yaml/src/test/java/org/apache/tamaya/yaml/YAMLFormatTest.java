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
package org.apache.tamaya.yaml;


import org.apache.tamaya.format.ConfigurationData;
import org.apache.tamaya.spi.PropertyValue;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class YAMLFormatTest {
    private final YAMLFormat format = new YAMLFormat();

    @Test
    public void testGetName() throws Exception {
        assertThat(format.getName()).isEqualTo("yaml");
    }

    @Test
    public void testAcceptURL() throws MalformedURLException {
        assertThat(format.accepts(new URL("http://127.0.0.1/anyfile.yaml"))).isTrue();
    }

    @Test
    public void testAcceptURL_BC1() throws MalformedURLException {
        assertThat(format.accepts(new URL("http://127.0.0.1/anyfile.YAML"))).isFalse();
    }

    @Test(expected = NullPointerException.class)
    public void testAcceptURL_BC2() throws MalformedURLException {
        assertThat(format.accepts(null)).isFalse();
    }

    @Test
    public void testAcceptURL_BC3() throws MalformedURLException {
        assertThat(format.accepts(new URL("http://127.0.0.1/anyfile.docx"))).isFalse();
    }

    @Test
    public void testRead_Map() throws IOException {
        URL configURL = YAMLPropertySourceTest.class.getResource("/configs/valid/contact.yaml");
        ConfigurationData data = format.readConfiguration(configURL.toString(), configURL.openStream());
        assertThat(data).isNotNull();
        for(Map.Entry<String,String> en:data.getData().get(0).toMap().entrySet()) {
            System.out.println(en.getKey() + " -> " + en.getValue());
        }
    }

    @Test
    public void testRead_List() throws IOException {
        URL configURL = YAMLPropertySourceTest.class.getResource("/configs/valid/list.yaml");
        ConfigurationData data = format.readConfiguration(configURL.toString(), configURL.openStream());
        assertThat(data).isNotNull();
        for(Map.Entry<String,String> en:data.getData().get(0).toMap().entrySet()) {
            System.out.println(en.getKey() + " -> " + en.getValue());
        }
    }

    @Test
    public void testRead_nullValues() throws IOException {
        URL configURL = getContactYaml();
        ConfigurationData data = loadConfigurationData(configURL);
        for(PropertyValue val:data.getData()){
            if(val.getKey().equals("summary")){
                fail("Contains null yaml value");
            }
        }
    }

    private ConfigurationData loadConfigurationData(URL configURL) throws IOException {
        return format.readConfiguration(configURL.toString(), configURL.openStream());
    }

    private URL getContactYaml() {
        return YAMLPropertySourceTest.class.getResource("/configs/valid/contact.yaml");
    }

}
