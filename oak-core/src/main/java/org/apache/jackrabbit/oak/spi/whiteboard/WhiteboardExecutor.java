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
package org.apache.jackrabbit.oak.spi.whiteboard;

import java.util.concurrent.Executor;

/**
 * Dynamic {@link Executor} based on the available whiteboard services.
 * The {@link #execute(Runnable)} method passes the given command to the
 * first available executor service. Alternatively the command is run
 * in the calling thread if no executor services are available.
 */
public class WhiteboardExecutor extends AbstractServiceTracker<Executor>
        implements Executor {

    public WhiteboardExecutor() {
        super(Executor.class);
    }

    //----------------------------------------------------------< Executor >--

    @Override
    public void execute(Runnable command) {
        for (Executor executor : getServices()) {
            // use the first executor to run the command
            executor.execute(command);
            return;
        }
        // no executor services available, so use the current thread instead
        command.run();
    }

}
