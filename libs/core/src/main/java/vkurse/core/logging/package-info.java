/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
/**
 * What every service logs without writing a line for it: each HTTP request it serves ({@code HttpLogFilter}), each
 * HTTP call it makes ({@code HttpClients}) and each scheduled job ({@code JobLog}), with secrets masked
 * ({@code LogMasker}).
 */
@NullMarked
package vkurse.core.logging;

import org.jspecify.annotations.NullMarked;
