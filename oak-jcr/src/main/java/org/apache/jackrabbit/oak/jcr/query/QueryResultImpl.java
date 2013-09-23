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
package org.apache.jackrabbit.oak.jcr.query;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.CheckForNull;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.QueryResult;
import javax.jcr.query.RowIterator;

import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.apache.jackrabbit.commons.iterator.RowIteratorAdapter;
import org.apache.jackrabbit.oak.api.PropertyValue;
import org.apache.jackrabbit.oak.api.Result;
import org.apache.jackrabbit.oak.api.ResultRow;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.jcr.session.NodeImpl;
import org.apache.jackrabbit.oak.jcr.session.SessionContext;
import org.apache.jackrabbit.oak.jcr.delegate.NodeDelegate;
import org.apache.jackrabbit.oak.jcr.delegate.SessionDelegate;
import org.apache.jackrabbit.oak.plugins.value.ValueFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The implementation of the corresponding JCR interface.
 */
public class QueryResultImpl implements QueryResult {

    static final Logger LOG = LoggerFactory.getLogger(QueryResultImpl.class);

    /**
     * The minimum number of rows / nodes to pre-fetch.
     */ 
    private static final int PREFETCH_MIN = 20;

    /**
     * The maximum number of rows / nodes to pre-fetch.
     */
    private static final int PREFETCH_MAX = 100;

    /**
     * The maximum number of milliseconds to prefetch rows / nodes.
     */
    private static final int PREFETCH_TIMEOUT = 100;

    final Result result;

    private final SessionContext sessionContext;
    private final SessionDelegate sessionDelegate;
    private final String pathFilter;
    
    public QueryResultImpl(SessionContext sessionContext, Result result) {
        this.sessionContext = sessionContext;
        this.sessionDelegate = sessionContext.getSessionDelegate();
        this.result = result;

        // TODO the path currently contains the workspace name
        // TODO filter in oak-core once we support workspaces there
        pathFilter = "/";
    }

    @Override
    public String[] getColumnNames() throws RepositoryException {
        return result.getColumnNames();
    }

    @Override
    public String[] getSelectorNames() {
        return result.getSelectorNames();
    }

    boolean includeRow(String path) {
        if (path == null) {
            // a row without path (explain,...)
            return true;
        }
        if (PathUtils.isAncestor(pathFilter, path) || pathFilter.equals(path)) {
            // a row within this workspace
            return true;
        }
        return false;
    }

    @Override
    public RowIterator getRows() throws RepositoryException {
        Iterator<RowImpl> rowIterator = new Iterator<RowImpl>() {

            private final Iterator<? extends ResultRow> it = result.getRows().iterator();
            private final String pathSelector;
            private RowImpl current;

            {
                String[] columnSelectorNames = result.getColumnSelectorNames();
                if (columnSelectorNames.length == 1) {
                    pathSelector = columnSelectorNames[0];
                } else {
                    pathSelector = null;
                }
                fetch();
            }

            private void fetch() {
                current = null;
                while (it.hasNext()) {
                    ResultRow r = it.next();
                    boolean include = true;
                    for (String s : getSelectorNames()) {
                        String path = r.getPath(s);
                        if (!includeRow(path)) {
                            include = false;
                            break;
                        }
                    }
                    if (include) {
                        current = new RowImpl(QueryResultImpl.this, r, pathSelector);
                        return;
                    }
                }
            }

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public RowImpl next() {
                if (current == null) {
                    throw new NoSuchElementException();
                }
                RowImpl r = current;
                fetch();
                return r;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
        final PrefetchIterator<RowImpl> prefIt = new  PrefetchIterator<RowImpl>(
                rowIterator, 
                PREFETCH_MIN, PREFETCH_TIMEOUT, PREFETCH_MAX, 
                result.getSize());
        return new RowIteratorAdapter(prefIt) {
            @Override
            public long getSize() {
                return prefIt.size();
            }
        };
    }

    @CheckForNull
    NodeImpl<? extends NodeDelegate> getNode(String path) throws RepositoryException {
        if (path == null) {
            return null;
        }
        NodeDelegate d = sessionDelegate.getNode(path);
        return d == null ? null : NodeImpl.createNode(d, sessionContext);
    }

    String getLocalPath(String path) {
        if (path == null) {
            return null;
        }
        return PathUtils.concat("/", PathUtils.relativize(pathFilter, path));
    }

    @Override
    public NodeIterator getNodes() throws RepositoryException {
        String[] columnSelectorNames = result.getColumnSelectorNames();
        if (columnSelectorNames.length != 1) {
            throw new RepositoryException("Query contains more than one selector: " +
                    Arrays.toString(columnSelectorNames));
        }
        final String selectorName = columnSelectorNames[0];
        if (selectorName == null) {
            throw new RepositoryException("Query does not contain a selector: " +
                    Arrays.toString(columnSelectorNames));
        }
        Iterator<NodeImpl<? extends NodeDelegate>> nodeIterator = new Iterator<NodeImpl<? extends NodeDelegate>>() {

            private final Iterator<? extends ResultRow> it = result.getRows().iterator();
            private NodeImpl<? extends NodeDelegate> current;

            {
                fetch();
            }

            private void fetch() {
                current = null;
                while (it.hasNext()) {
                    ResultRow r = it.next();
                    String path = r.getPath(selectorName);
                    if (includeRow(path)) {
                        try {
                            current = getNode(getLocalPath(path));
                            break;
                        } catch (RepositoryException e) {
                            LOG.warn("Unable to fetch result node for path " + path, e);
                        }
                    }
                }
            }

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public NodeImpl<? extends NodeDelegate> next() {
                if (current == null) {
                    throw new NoSuchElementException();
                }
                NodeImpl<? extends NodeDelegate> n = current;
                fetch();
                return n;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
        final PrefetchIterator<NodeImpl<? extends NodeDelegate>> prefIt = new  PrefetchIterator<NodeImpl<? extends NodeDelegate>>(
                nodeIterator, 
                PREFETCH_MIN, PREFETCH_TIMEOUT, PREFETCH_MAX, 
                result.getSize());
        return new NodeIteratorAdapter(prefIt) {
            @Override
            public long getSize() {
                return prefIt.size();
            }
        };
    }

    Value createValue(PropertyValue value) {
        if (value == null) {
            return null;
        }
        return ValueFactoryImpl.createValue(value, sessionContext);
    }

}