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
package org.apache.tamaya.cdi.extra;

import org.apache.tamaya.Configuration;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;

/**
 * CDI Extension that can be used to veto on beans by configuring the fully qualified class names (as regex expression)
 * under {@code javax.enterprise.inject.vetoed}. Multiple expression can be added as comma separated values.
 */
public class ConfiguredVetoExtension implements Extension {

    public void observesBean(@Observes ProcessAnnotatedType<?> type) {
        String vetoedTypesVal = Configuration.current().get("javax.enterprise.inject.vetoed");
        String[] vetoedTypes = vetoedTypesVal.split(",");
        for (String typeExpr : vetoedTypes) {
            String typeExprTrimmed = typeExpr.trim();
            if (type.getAnnotatedType().getJavaClass().getName().matches(typeExprTrimmed)) {
                type.veto();
            }
        }
    }

}
