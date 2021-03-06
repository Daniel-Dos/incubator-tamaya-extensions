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
package org.apache.tamaya.format;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link org.apache.tamaya.format.ConfigurationFormats}.
 */
public class ConfigurationFormatsTest {

    @org.junit.Test
    public void testGetFormats() throws Exception {
        List<ConfigurationFormat> formats = ConfigurationFormats.getInstance().getFormats();
        assertThat(formats).isNotNull();
        assertThat(formats).hasSize(3);
    }

    @org.junit.Test
    public void testReadConfigurationData() throws Exception {
        List<ConfigurationFormat> formats = ConfigurationFormats.getInstance().getFormats(getClass().getResource("/Test.ini"));
        assertThat(formats).isNotNull();
        assertThat(formats).hasSize(1);
        formats = ConfigurationFormats.getInstance().getFormats(getClass().getResource("/Test.properties"));
        assertThat(formats).isNotNull();
        assertThat(formats).hasSize(1);

    }

    @org.junit.Test
    public void testReadConfigurationData_URL() throws Exception {
        ConfigurationData data = ConfigurationFormats.getInstance().readConfigurationData(
                getClass().getResource("/Test.ini"));
        assertThat(data).isNotNull();
        data = ConfigurationFormats.getInstance().readConfigurationData(getClass().getResource("/Test.properties"));
        assertThat(data).isNotNull();
    }

    @org.junit.Test
    public void testReadConfigurationData_URL_ConfiguratonFormat() throws Exception {
        ConfigurationData data = ConfigurationFormats.getInstance().readConfigurationData(
                getClass().getResource("/Test.ini"),
                ConfigurationFormats.getInstance().getFormats("ini"));
        assertThat(data).isNotNull();
    }

    @org.junit.Test
    public void testReadConfigurationData_URL_CollectionOfConfiguratonFormat() throws Exception {
        List<ConfigurationFormat> formats = new ArrayList<>();
        formats.add(ConfigurationFormats.getInstance().getFormats("ini").get(0));
        ConfigurationData data = ConfigurationFormats.getInstance().readConfigurationData(
                getClass().getResource("/Test.ini"),
                formats);
        assertThat(data).isNotNull();
    }

    @org.junit.Test
    public void testReadConfigurationData_CollectionOfURL_CollectionOfConfiguratonFormat() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(getClass().getResource("/Test.ini"));
        List<ConfigurationFormat> formats = new ArrayList<>();
        formats.add(ConfigurationFormats.getInstance().getFormats("ini").get(0));
        Collection<ConfigurationData> data = ConfigurationFormats.getInstance().readConfigurationData(
                urls,
                formats);
        assertThat(data).isNotNull();
        assertThat(data).hasSize(1);
    }

    @org.junit.Test
    public void testReadConfigurationData_CollectionOfURL_ConfiguratonFormat() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(getClass().getResource("/Test.ini"));
        Collection<ConfigurationData> data = ConfigurationFormats.getInstance().readConfigurationData(
                urls,
                ConfigurationFormats.getInstance().getFormats("ini").get(0));
        assertThat(data).isNotNull();
        assertThat(data).hasSize(1);
    }

    @org.junit.Test
    public void testReadConfigurationData_String_InputStream_ConfiguratonFormat() throws Exception {
        ConfigurationData data = ConfigurationFormats.getInstance().readConfigurationData(
                "Test.ini",
                getClass().getResource("/Test.ini").openStream(),
                ConfigurationFormats.getInstance().getFormats("ini"));
        assertThat(data).isNotNull();
    }

    @org.junit.Test
    public void testReadConfigurationData_String_InputStream_CollectionOfConfiguratonFormat() throws Exception {
        List<ConfigurationFormat> formats = new ArrayList<>();
        formats.add(ConfigurationFormats.getInstance().getFormats("ini").get(0));
        ConfigurationData data = ConfigurationFormats.getInstance().readConfigurationData(
                "Test.ini",
                getClass().getResource("/Test.ini").openStream(),
                formats);
        assertThat(data).isNotNull();
    }

    @org.junit.Test
    public void testReadConfigurationData2() throws Exception {
        List<ConfigurationFormat> formats = ConfigurationFormats.getInstance().getFormats();
        ConfigurationData data = ConfigurationFormats.getInstance().readConfigurationData(
                getClass().getResource("/Test.ini"),
                formats.toArray(new ConfigurationFormat[formats.size()]));
        assertThat(data).isNotNull();
        System.out.println(data);
    }
}
