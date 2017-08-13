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
package org.apache.tamaya.events.internal;

import org.apache.tamaya.events.FrozenConfiguration;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultConfigChangeObserverTest {
    private DefaultConfigChangeObserver sut = new DefaultConfigChangeObserver();

    @Test
    public void enableMonitoringCalledWithTrueEnablesMonitoring() {
        assertThat(sut.isMonitoring()).isFalse();

        sut.enableMonitoring(true);

        assertThat(sut.isMonitoring()).isTrue();
    }

    @Test
    public void enableMonitoringCalledWithFalseDisablesMonitoring() {
        assertThat(sut.isMonitoring()).isFalse();

        sut.enableMonitoring(true);

        assertThat(sut.isMonitoring()).isTrue();

        sut.enableMonitoring(false);

        assertThat(sut.isMonitoring()).isFalse();
    }

    @Test
    public void lastConfigIsSetByTheFirstCheckForChangesInTheConfiguration() {
        DefaultConfigChangeObserver observer = new DefaultConfigChangeObserver();

        assertThat(observer.getLastConfig()).describedAs("There must be no last configuration after creation.")
                                            .isNull();

        observer.checkConfigurationUpdate();

        assertThat(observer.getLastConfig()).describedAs("After the firt check last configuration must be set.")
                                            .isNotNull();
    }

    @Test
    public void lastConfigIsUpdatedByASubSequentCheckForChangesInTheConfigration() {
        DefaultConfigChangeObserver observer = new DefaultConfigChangeObserver();

        observer.checkConfigurationUpdate();

        FrozenConfiguration config1 = observer.getLastConfig();
        observer.checkConfigurationUpdate();
        FrozenConfiguration config2 = observer.getLastConfig();

        assertThat(config1).describedAs("After the firt check last configuration must be set.")
                                            .isNotEqualTo(config2);
    }
}