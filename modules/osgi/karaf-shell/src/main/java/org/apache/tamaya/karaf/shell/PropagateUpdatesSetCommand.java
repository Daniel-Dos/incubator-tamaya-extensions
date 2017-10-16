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
package org.apache.tamaya.karaf.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.tamaya.osgi.commands.ConfigCommands;
import org.apache.tamaya.osgi.commands.TamayaConfigService;

import java.io.IOException;

@Command(scope = "tamaya", name = "tm_propagate_updates_set",
        description="Configure if Tamaya is automatically triggering OSGI config updates, when according " +
                "Tamaya configuration changes.")
@Service
public class PropagateUpdatesSetCommand implements Action{

    @Reference
    private TamayaConfigService configPlugin;

    @Argument(index = 0, name = "enabled", description = "Set to true to enable Tamaya's updating trigger.",
            required = true, multiValued = false)
    boolean enabled;

    @Override
    public Object execute() throws IOException {
        return(ConfigCommands.setAutoUpdateEnabled(configPlugin, enabled));
    }

}