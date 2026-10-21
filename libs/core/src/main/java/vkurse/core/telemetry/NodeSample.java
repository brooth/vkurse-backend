/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import java.time.Instant;
import java.util.UUID;

/**
 * How one node is doing. A value that can't be read is -1.
 *
 * @param processCpu the share of the CPUs this process uses, 0 to 1
 * @param systemCpu the share of the CPUs the whole machine uses, 0 to 1
 * @param gcMillis time spent in garbage collection since the last sample
 * @param poolActive connections in use
 * @param poolIdle connections waiting
 * @param poolPending threads waiting for a connection
 * @param poolMax the size limit of the pool
 * @param droppedEvents events this node lost since it started (a full queue or an unreachable Kafka)
 */
public record NodeSample(
        UUID id,
        Instant at,
        Source source,
        int cpus,
        double processCpu,
        double systemCpu,
        long heapUsedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        long gcMillis,
        int threads,
        int poolActive,
        int poolIdle,
        int poolPending,
        int poolMax,
        long uptimeMillis,
        long droppedEvents)
        implements TelemetryEvent {}
