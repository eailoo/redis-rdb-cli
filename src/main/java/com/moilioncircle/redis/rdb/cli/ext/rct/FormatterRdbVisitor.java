/*
 * Copyright 2016-2017 Leon Chen
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

package com.moilioncircle.redis.rdb.cli.ext.rct;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.moilioncircle.redis.rdb.cli.api.format.FormatterService;
import com.moilioncircle.redis.rdb.cli.api.format.escape.Escaper;
import com.moilioncircle.redis.rdb.cli.conf.Configure;
import com.moilioncircle.redis.rdb.cli.ext.AbstractRdbVisitor;
import com.moilioncircle.redis.rdb.cli.glossary.DataType;
import com.moilioncircle.redis.rdb.cli.util.OutputStreams;
import com.moilioncircle.redis.replicator.Replicator;
import com.moilioncircle.redis.replicator.event.Event;
import com.moilioncircle.redis.replicator.event.PreRdbSyncEvent;
import com.moilioncircle.redis.replicator.io.RedisInputStream;
import com.moilioncircle.redis.replicator.rdb.datatype.AuxField;
import com.moilioncircle.redis.replicator.rdb.datatype.ContextKeyValuePair;

/**
 * @author Baoyi Chen
 */
public class FormatterRdbVisitor extends AbstractRdbVisitor {
    
    protected FormatterService formatter;

    public FormatterRdbVisitor(Replicator replicator, Configure configure, File output, List<Long> db, List<String> regexs, List<DataType> types, Escaper escaper, FormatterService formatter) {
        super(replicator, configure, db, regexs, types);
        this.escaper = escaper;
        this.formatter = formatter;
        this.formatter.setEscaper(escaper);
        this.formatter.setProperties(configure.properties());
        replicator.addEventListener((rep, event) -> {
            if (event instanceof PreRdbSyncEvent) {
                OutputStreams.closeQuietly(this.formatter.getOutputStream());
                this.formatter.setOutputStream(OutputStreams.newBufferedOutputStream(output, configure.getBufferSize()));
            }
            this.formatter.onEvent(rep, event);
        });
        replicator.addCloseListener(rep -> OutputStreams.closeQuietly(this.formatter.getOutputStream()));
    }

    @Override
    public String applyMagic(RedisInputStream in) throws IOException {
        String magic = super.applyMagic(in);
        formatter.applyStart(in);
        return magic;
    }

    @Override
    public long applyEof(RedisInputStream in, int version) throws IOException {
        long checksum = super.applyEof(in, version);
        formatter.applyEnd(in, version, checksum);
        return checksum;
    }

    @Override
    public Event applyAux(RedisInputStream in, int version) throws IOException {
        Event event = super.applyAux(in, version);
        if (event != null && (event instanceof AuxField)) {
            AuxField aux = (AuxField) event;
            formatter.applyRedisProperty(in, version, aux.getAuxKey(), aux.getAuxValue());
        }
        return event;
    }

    @Override
    protected Event doApplyString(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyString(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyList(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyList(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplySet(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applySet(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyZSet(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyZSet(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyZSet2(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyZSet2(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyHash(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyHash(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyHashZipMap(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyHashZipMap(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyListZipList(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyListZipList(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplySetIntSet(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applySetIntSet(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyZSetZipList(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyZSetZipList(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyHashZipList(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyHashZipList(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyListQuickList(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyListQuickList(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyModule(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyModule(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyModule2(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyModule2(replicator, in, version, key, type, context);
    }

    @Override
    protected Event doApplyStreamListPacks(RedisInputStream in, int version, byte[] key, boolean contains, int type, ContextKeyValuePair context) throws IOException {
        return formatter.applyStreamListPacks(replicator, in, version, key, type, context);
    }
}
