/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import vkurse.core.domain.UuidV7;

/**
 * Sends telemetry to Kafka without ever making the caller wait. A request only adds to its minute's counts and, when it
 * is worth keeping whole, puts one event in a bounded queue. One thread of its own turns the queue and the closed
 * minutes into messages, in batches. It reaches Kafka lazily and tries again after {@value #RETRY_SECONDS} seconds, so
 * a broker that is down never stops the service from starting; what can't be sent is dropped and counted.
 *
 * <p>Messages are JSON, keyed by the node's id, on {@value #HTTP_TOPIC} (windows and samples), {@value #NODE_TOPIC}
 * and {@value #JOBS_TOPIC} (job runs and outside calls).
 */
public class KafkaTelemetry implements Telemetry, AutoCloseable {
    public static final String HTTP_TOPIC = "telemetry.http";
    public static final String NODE_TOPIC = "telemetry.node";
    public static final String JOBS_TOPIC = "telemetry.jobs";

    static final long RETRY_SECONDS = 30;

    private static final Logger log = LoggerFactory.getLogger(KafkaTelemetry.class);
    private static final int BATCH = 500;
    private static final Duration REPORT_EVERY = Duration.ofMinutes(5);

    private final TelemetryProperties properties;
    private final String bootstrapServers;
    private final Source source;
    private final Clock clock;
    private final NodeSampler nodeSampler;
    private final HttpWindows windows = new HttpWindows();
    private final BlockingQueue<TelemetryEvent> queue;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final Thread sender;

    private volatile boolean running = true;
    private @Nullable Producer<String, String> producer;
    private long nextProducerAttempt;
    private long nextNodeSample;
    private long nextReport;
    private long reportedLost;

    public KafkaTelemetry(TelemetryProperties properties, Source source, Clock clock, DataSource dataSource) {
        this.properties = properties;
        this.bootstrapServers = Objects.requireNonNull(
                Objects.requireNonNull(properties.kafka()).bootstrapServers());
        this.source = source;
        this.clock = clock;
        this.nodeSampler = new NodeSampler(dataSource);
        this.queue = new ArrayBlockingQueue<>(properties.queueSize());
        this.nextNodeSample = System.nanoTime();
        this.nextReport = System.nanoTime() + REPORT_EVERY.toNanos();
        this.sender = Thread.ofPlatform().name("telemetry-sender").daemon().start(this::run);
    }

    @Override
    public void request(
            String requestId,
            String method,
            String route,
            String path,
            int status,
            long millis,
            @Nullable String userId) {
        try {
            var now = clock.instant();
            windows.add(now, method, route, status, millis);
            HttpSample.Reason reason = null;
            if (status >= 500) {
                reason = HttpSample.Reason.ERROR;
            } else if (millis >= properties.slowMillis()) {
                reason = HttpSample.Reason.SLOW;
            } else if (ThreadLocalRandom.current().nextDouble() < properties.sampleRate()) {
                reason = HttpSample.Reason.RANDOM;
            }
            if (reason != null) {
                offer(new HttpSample(
                        UuidV7.at(now), now, source, requestId, method, route, path, status, millis, userId, reason));
            }
        } catch (RuntimeException e) {
            dropped.incrementAndGet();
        }
    }

    @Override
    public void job(String job, long millis, @Nullable Throwable failure) {
        try {
            var now = clock.instant();
            offer(new JobRun(UuidV7.at(now), now.minusMillis(millis), source, job, millis, name(failure)));
        } catch (RuntimeException e) {
            dropped.incrementAndGet();
        }
    }

    @Override
    public void outsideCall(
            String client, String method, URI uri, int status, long millis, @Nullable Throwable failure) {
        try {
            var now = clock.instant();
            offer(new OutsideCall(
                    UuidV7.at(now),
                    now.minusMillis(millis),
                    source,
                    client,
                    method,
                    uri.getHost() == null ? "" : uri.getHost(),
                    uri.getPath() == null ? "" : uri.getPath(),
                    status,
                    millis,
                    name(failure)));
        } catch (RuntimeException e) {
            dropped.incrementAndGet();
        }
    }

    private void offer(TelemetryEvent event) {
        if (!queue.offer(event)) {
            dropped.incrementAndGet();
        }
    }

    private static @Nullable String name(@Nullable Throwable failure) {
        if (failure == null) {
            return null;
        }
        var cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private void run() {
        while (running) {
            try {
                var batch = new ArrayList<TelemetryEvent>();
                var first = queue.poll(1, TimeUnit.SECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, BATCH);
                }
                cycle(batch, false);
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                log.warn("Telemetry could not send a batch, it goes on", e);
            }
        }
    }

    private void cycle(List<TelemetryEvent> batch, boolean stopping) {
        var now = clock.instant();
        batch.addAll(windows.closed(now, source, stopping));
        if (System.nanoTime() - nextNodeSample >= 0) {
            batch.add(nodeSampler.sample(now, source, dropped.get()));
            nextNodeSample = System.nanoTime() + properties.nodeInterval().toNanos();
        }
        send(batch);
        report();
    }

    private void send(List<TelemetryEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }
        var producer = producer();
        if (producer == null) {
            dropped.addAndGet(batch.size());
            return;
        }
        for (var event : batch) {
            try {
                producer.send(
                        new ProducerRecord<>(topic(event), source.nodeId(), json.writeValueAsString(event)),
                        (metadata, error) -> (error == null ? sent : failed).incrementAndGet());
            } catch (RuntimeException e) {
                // A message too large, a producer closed, a serialization error: this event only.
                failed.incrementAndGet();
            }
        }
    }

    private static String topic(TelemetryEvent event) {
        return switch (event) {
            case HttpWindow _ -> HTTP_TOPIC;
            case HttpSample _ -> HTTP_TOPIC;
            case NodeSample _ -> NODE_TOPIC;
            case JobRun _ -> JOBS_TOPIC;
            case OutsideCall _ -> JOBS_TOPIC;
        };
    }

    /** The producer, made on first use; null while Kafka can't be reached, with a new try every few seconds. */
    private @Nullable Producer<String, String> producer() {
        if (producer != null) {
            return producer;
        }
        if (System.nanoTime() - nextProducerAttempt < 0) {
            return null;
        }
        try {
            var config = new Properties();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            config.put(ProducerConfig.CLIENT_ID_CONFIG, source.nodeId());
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            // Losing a few events is fine, a slow request is not: never wait long for the broker.
            config.put(ProducerConfig.ACKS_CONFIG, "1");
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false");
            config.put(ProducerConfig.LINGER_MS_CONFIG, "100");
            config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "2000");
            config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
            config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "20000");
            // A broker that is down is the client's warning every backoff: ask it less often.
            config.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, "1000");
            config.put(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, "30000");
            producer = new KafkaProducer<>(config);
            log.info("Telemetry created its Kafka producer for {}", bootstrapServers);
        } catch (RuntimeException e) {
            nextProducerAttempt = System.nanoTime() + TimeUnit.SECONDS.toNanos(RETRY_SECONDS);
            log.warn(
                    "Telemetry could not create its Kafka producer for {}, it tries again in {}s",
                    bootstrapServers,
                    RETRY_SECONDS,
                    e);
        }
        return producer;
    }

    /** One line every few minutes when events were lost: a full queue, or Kafka that did not take them. */
    private void report() {
        if (System.nanoTime() - nextReport < 0) {
            return;
        }
        nextReport = System.nanoTime() + REPORT_EVERY.toNanos();
        var lost = dropped.get() + failed.get();
        if (lost > reportedLost) {
            log.warn(
                    "Telemetry lost {} events in the last {} minutes ({} dropped before sending, {} not delivered), "
                            + "{} sent since start",
                    lost - reportedLost,
                    REPORT_EVERY.toMinutes(),
                    dropped.get(),
                    failed.get(),
                    sent.get());
            reportedLost = lost;
        }
    }

    /** Sends what is left, the minutes still open included, and stops. */
    @Override
    public void close() {
        running = false;
        sender.interrupt();
        try {
            sender.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            var rest = new ArrayList<TelemetryEvent>();
            queue.drainTo(rest);
            cycle(rest, true);
            if (producer != null) {
                producer.close(Duration.ofSeconds(2));
            }
        } catch (RuntimeException e) {
            log.warn("Telemetry did not send everything before it stopped", e);
        }
    }
}
