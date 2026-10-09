package com.pml.identity.auth.delivery;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Captures every log event at every level and fails the test when a secret appears in any of them. */
public final class LogWatch implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level previous = root.getLevel();

    public LogWatch() {
        root.setLevel(Level.TRACE);
        appender.start();
        root.addAppender(appender);
    }

    /** Everything logged so far, rendered: message, arguments and the whole throwable chain. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            // Netty and Lettuce dump the raw wire at TRACE (every Redis command and every byte of
            // every HTTP body). That is a logger an operator never enables in production, and
            // what the application itself logs is what this test is about.
            if (event.getLoggerName().startsWith("io.netty") || event.getLoggerName().startsWith("io.lettuce")) {
                continue;
            }
            StringBuilder line = new StringBuilder(event.getFormattedMessage());
            IThrowableProxy proxy = event.getThrowableProxy();
            while (proxy != null) {
                line.append(' ').append(proxy.getClassName()).append(' ').append(proxy.getMessage());
                proxy = proxy.getCause();
            }
            event.getMDCPropertyMap().values().forEach(v -> line.append(' ').append(v));
            lines.add(line.toString());
        }
        return lines;
    }

    public void assertNoneContains(String... secrets) {
        for (String line : lines()) {
            for (String secret : secrets) {
                if (secret != null && !secret.isBlank() && line.contains(secret)) {
                    throw new AssertionError("a log line contains a secret (length " + secret.length() + "): "
                            + line.replace(secret, "<SECRET>"));
                }
            }
        }
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        root.setLevel(previous);
    }
}
