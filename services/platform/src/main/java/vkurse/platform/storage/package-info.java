/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

/**
 * Files: who uploaded what and where it is kept. The bytes live in Supabase Storage and never pass through this
 * service: a client uploads and downloads with links this module signs. The module knows nothing of schools or users
 * beyond their ids; the module that owns a file decides who may upload, see and delete it.
 */
@NullMarked
@ApplicationModule(displayName = "Storage")
package vkurse.platform.storage;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
