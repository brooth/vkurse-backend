/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

/**
 * Who sent an event.
 *
 * @param service the service's name ({@code vkurse-platform})
 * @param nodeId the instance ({@code vkurse.node-id})
 * @param version the build, so a release can be compared with the one before
 */
public record Source(String service, String nodeId, String version) {}
