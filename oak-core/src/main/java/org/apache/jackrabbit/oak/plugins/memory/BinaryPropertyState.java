/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.memory;

import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.api.Type;

public class BinaryPropertyState extends SinglePropertyState {
    private final Blob value;

    protected BinaryPropertyState(String name, Blob value) {
        super(name);
        this.value = value;
    }

    @Override
    public String getString() {
        // TODO find a better way to represent string values with Blobs
        return value.toString();
    }

    @Override
    protected Blob getBlob() {
        return value;
    }

    @Override
    public long size() {
        return value.length();
    }

    @Override
    public Type<?> getType() {
        return Type.BINARY;
    }
}
