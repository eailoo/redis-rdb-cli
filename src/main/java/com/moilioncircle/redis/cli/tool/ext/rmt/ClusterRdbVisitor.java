/*
 * Copyright 2018-2019 Baoyi Chen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.moilioncircle.redis.cli.tool.ext.rmt;

import com.moilioncircle.redis.cli.tool.conf.Configure;
import com.moilioncircle.redis.cli.tool.ext.AsyncEventListener;
import com.moilioncircle.redis.cli.tool.glossary.DataType;
import com.moilioncircle.redis.cli.tool.net.Endpoints;
import com.moilioncircle.redis.replicator.Configuration;
import com.moilioncircle.redis.replicator.Replicator;
import com.moilioncircle.redis.replicator.event.Event;
import com.moilioncircle.redis.replicator.event.EventListener;
import com.moilioncircle.redis.replicator.event.PostRdbSyncEvent;
import com.moilioncircle.redis.replicator.event.PreCommandSyncEvent;
import com.moilioncircle.redis.replicator.event.PreRdbSyncEvent;
import com.moilioncircle.redis.replicator.rdb.dump.datatype.DumpKeyValuePair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static com.moilioncircle.redis.cli.tool.net.Endpoints.closeQuietly;
import static com.moilioncircle.redis.replicator.Configuration.defaultSetting;
import static java.util.Collections.singletonList;

/**
 * @author Baoyi Chen
 */
public class ClusterRdbVisitor extends AbstractMigrateRdbVisitor implements EventListener {

    private final List<String> conf;
    private final Configuration configuration;
    private ThreadLocal<Endpoints> endpoints = new ThreadLocal<>();

    public ClusterRdbVisitor(Replicator replicator, Configure configure, File conf, List<String> regexs, List<DataType> types, boolean replace) throws IOException {
        super(replicator, configure, singletonList(0L), regexs, types, replace);
        this.conf = Files.readAllLines(conf.toPath());
        this.configuration = configure.merge(defaultSetting());
        this.replicator.addEventListener(new AsyncEventListener(this, replicator, configure));
    }

    @Override
    public void onEvent(Replicator replicator, Event event) {
        if (event instanceof PreRdbSyncEvent) {
            closeQuietly(this.endpoints.get());
            int pipe = configure.getMigrateBatchSize();
            this.endpoints.set(new Endpoints(conf, pipe, configuration));
        } else if (event instanceof DumpKeyValuePair) {
            retry(event, configure.getMigrateRetries());
        } else if (event instanceof PostRdbSyncEvent || event instanceof PreCommandSyncEvent) {
            this.endpoints.get().flush();
            closeQuietly(this.endpoints.get());
        }
    }

    public void retry(Event event, int times) {
        DumpKeyValuePair dkv = (DumpKeyValuePair) event;
        try {
            byte[] expire = ZERO;
            if (dkv.getExpiredMs() != null) {
                long ms = dkv.getExpiredMs() - System.currentTimeMillis();
                if (ms <= 0) return;
                expire = String.valueOf(ms).getBytes();
            }

            if (!replace) {
                endpoints.get().batch(flush, RESTORE_ASKING, dkv.getKey(), expire, dkv.getValue());
            } else {
                endpoints.get().batch(flush, RESTORE_ASKING, dkv.getKey(), expire, dkv.getValue(), REPLACE);
            }
        } catch (Throwable e) {
            times--;
            if (times >= 0) {
                this.endpoints.get().update(dkv.getKey());
                retry(event, times);
            }
        }
    }
}
