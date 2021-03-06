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

package com.moilioncircle.redis.rdb.cli.cmd;

import java.io.File;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moilioncircle.redis.rdb.cli.api.sink.ParserService;
import com.moilioncircle.redis.rdb.cli.api.sink.SinkService;
import com.moilioncircle.redis.rdb.cli.api.sink.listener.AsyncEventListener;
import com.moilioncircle.redis.rdb.cli.conf.Configure;
import com.moilioncircle.redis.rdb.cli.ext.CliRedisReplicator;
import com.moilioncircle.redis.rdb.cli.util.ProgressBar;
import com.moilioncircle.redis.rdb.cli.util.XThreadFactory;
import com.moilioncircle.redis.replicator.Replicator;
import com.moilioncircle.redis.replicator.Replicators;
import com.moilioncircle.redis.replicator.event.PreRdbSyncEvent;

/**
 * @author Baoyi Chen
 */
public class RetCommand extends AbstractCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(RetCommand.class);

    private static final Option HELP = Option.builder("h").longOpt("help").required(false).hasArg(false).desc("ret usage.").build();
    private static final Option VERSION = Option.builder("v").longOpt("version").required(false).hasArg(false).desc("ret version.").build();
    private static final Option SOURCE = Option.builder("s").longOpt("source").required(false).hasArg().argName("source").type(String.class).desc("<source> eg:\n redis://host:port?authPassword=foobar").build();
    private static final Option CONFIG = Option.builder("c").longOpt("config").required(false).hasArg().argName("file").type(File.class).desc("external config file, if not specified, default value is null").build();
    private static final Option NAME = Option.builder("n").longOpt("name").required(false).hasArg().argName("sink").type(String.class).desc("sink service name, registered sink service: example").build();
    private static final Option PARSER = Option.builder("p").longOpt("parser").required(false).hasArg().argName("parser").type(String.class).desc("parser service name, registered parser service: default, dump. if not specified, default value is default").build();
    private static final String HEADER = "ret -s <source> [-c <file>] [-p <parser>] -n <sink>";
    private static final String EXAMPLE = "\nexamples:\n ret -s redis://127.0.0.1:6379 -c ./config.conf -n example\n ret -s redis://127.0.0.1:6379 -c ./config.conf -p dump -n example\n";

    private RetCommand() {
        addOption(HELP);
        addOption(VERSION);
        addOption(SOURCE);
        addOption(CONFIG);
        addOption(NAME);
        addOption(PARSER);
    }

    @Override
    @SuppressWarnings("all")
    protected void doExecute(CommandLine line) throws Exception {
        if (line.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(HEADER, "\noptions:", options, EXAMPLE);
        } else if (line.hasOption("version")) {
            writeLine(version());
        } else {
            StringBuilder sb = new StringBuilder();

            if (!line.hasOption("source")) {
                sb.append("-s ");
            }

            if (!line.hasOption("name")) {
                sb.append("-n ");
            }

            if (sb.length() > 0) {
                writeError("Missing required options: " + sb.toString() + ". Try `ret -h` for more information.");
                return;
            }

            File conf = line.getOption("config");
            String sink = line.getOption("name");
            String source = line.getOption("source");
            String parser = line.getOption("parser", "default");
            source = normalize(source, null, "Invalid options: -s. Try `ret -h` for more information.");

            SinkService sinkService = loadSinkService(sink, conf);
            ParserService parserService = loadParseService(parser, conf);

            Configure configure = Configure.bind();
            try (ProgressBar bar = new ProgressBar(-1)) {
                Replicator r = new CliRedisReplicator(source, configure);
                r.setRdbVisitor(parserService.getRdbVisitor(r));
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    Replicators.closeQuietly(r);
                }));
                r.addExceptionListener((rep, tx, e) -> {
                    throw new RuntimeException(tx.getMessage(), tx);
                });
                r.addEventListener((rep, event) -> {
                    if (event instanceof PreRdbSyncEvent)
                        rep.addRawByteListener(b -> bar.react(b.length));
                });
                r.addEventListener(new AsyncEventListener(sinkService, r, configure.getMigrateThreads(), new XThreadFactory("sync-worker")));
                parserService.wrap(r).open();
            }
        }
    }

    private SinkService loadSinkService(String sink, File config) throws Exception {
        ServiceLoader<SinkService> loader = ServiceLoader.load(SinkService.class);

        SinkService service = null;
        Iterator<SinkService> it = loader.iterator();
        while (it.hasNext()) {
            SinkService temp = it.next();
            if (temp.sink().equals(sink)) {
                service = temp;
                break;
            }
        }

        if (service == null) {
            writeError("Failed to load sink service. Try `ret -h` for more information.");
            return null;
        }
        logger.info("loaded sink service {}", service.getClass());
        service.init(config);
        return service;
    }

    private ParserService loadParseService(String parser, File config) throws Exception {
        ServiceLoader<ParserService> loader = ServiceLoader.load(ParserService.class);

        ParserService service = null;
        Iterator<ParserService> it = loader.iterator();
        while (it.hasNext()) {
            ParserService temp = it.next();
            if (temp.parser().equals(parser)) {
                service = temp;
                break;
            }
        }

        if (service == null) {
            writeError("Failed to load parser service. Try `ret -h` for more information.");
            return null;
        }
        logger.info("loaded parser service {}", service.getClass());
        service.init(config);
        return service;
    }

    @Override
    public String name() {
        return "ret";
    }

    public static void run(String[] args) throws Exception {
        RetCommand command = new RetCommand();
        command.execute(args);
    }
}
