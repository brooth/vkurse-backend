/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
/**
 * What every service tells {@code monitoring}: how long its requests, jobs and outside calls took, and how its node
 * is doing. A service calls the {@code Telemetry} port; the events are the contract with {@code monitoring}, which
 * reads them from Kafka.
 */
@NullMarked
package vkurse.core.telemetry;

import org.jspecify.annotations.NullMarked;
