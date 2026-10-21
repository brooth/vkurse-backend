/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import java.time.Instant;
import java.util.UUID;

/**
 * A fact one node reports. Every event has a UUIDv7 {@link #id()}, so a message delivered twice is stored once.
 * Events carry no bodies, addresses, tokens or query strings.
 */
public sealed interface TelemetryEvent permits HttpWindow, HttpSample, NodeSample, JobRun, OutsideCall {

    UUID id();

    /** When it happened. For a {@link HttpWindow}, when its minute began. */
    Instant at();

    Source source();
}
