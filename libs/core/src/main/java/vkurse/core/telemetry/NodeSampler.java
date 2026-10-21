/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import com.sun.management.OperatingSystemMXBean;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.sql.SQLException;
import java.time.Instant;
import javax.sql.DataSource;
import vkurse.core.domain.UuidV7;

/** Reads how this JVM and its connection pool are doing, from the JVM's own beans. */
class NodeSampler {
    private final DataSource dataSource;
    private long lastGcMillis;

    NodeSampler(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    NodeSample sample(Instant now, Source source, long droppedEvents) {
        var os = ManagementFactory.getOperatingSystemMXBean();
        var memory = ManagementFactory.getMemoryMXBean();
        var gcMillis = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(gc -> Math.max(gc.getCollectionTime(), 0))
                .sum();
        var gcSince = gcMillis - lastGcMillis;
        lastGcMillis = gcMillis;
        var processCpu = -1d;
        var systemCpu = -1d;
        if (os instanceof OperatingSystemMXBean sun) {
            processCpu = sun.getProcessCpuLoad();
            systemCpu = sun.getCpuLoad();
        }
        var pool = pool();
        return new NodeSample(
                UuidV7.at(now),
                now,
                source,
                os.getAvailableProcessors(),
                processCpu,
                systemCpu,
                memory.getHeapMemoryUsage().getUsed(),
                memory.getHeapMemoryUsage().getMax(),
                memory.getNonHeapMemoryUsage().getUsed(),
                gcSince,
                ManagementFactory.getThreadMXBean().getThreadCount(),
                pool[0],
                pool[1],
                pool[2],
                pool[3],
                ManagementFactory.getRuntimeMXBean().getUptime(),
                droppedEvents);
    }

    /** Active, idle, pending and the limit; -1 where the pool isn't Hikari's or hasn't started. */
    private int[] pool() {
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                var hikari = dataSource.unwrap(HikariDataSource.class);
                var bean = hikari.getHikariPoolMXBean();
                if (bean != null) {
                    return new int[] {
                        bean.getActiveConnections(),
                        bean.getIdleConnections(),
                        bean.getThreadsAwaitingConnection(),
                        hikari.getMaximumPoolSize()
                    };
                }
            }
        } catch (SQLException e) {
            // Not a pool we can read; the sample says -1.
        }
        return new int[] {-1, -1, -1, -1};
    }
}
