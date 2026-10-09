/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Supabase Storage settings ({@code vkurse.storage.*}). A missing value stops startup.
 *
 * @param url base URL of the Storage API as this service reaches it ({@code https://…/storage/v1} behind the Supabase
 *     gateway, the server's root without it)
 * @param publicUrl base URL of the Storage API as a browser reaches it: upload, download and public links start with it
 * @param serviceKey Supabase service role key. It can do anything in storage: never send it to a client
 * @param publicBucket bucket for files anyone may read (logos, pictures). Created at startup. A bucket must not be
 *     named after a word in the API's paths ({@code public}, {@code sign}, {@code info}, {@code upload},
 *     {@code authenticated}): storage would then misread the path
 * @param privateBucket bucket for files read only with a signed link (school files). Created at startup
 */
@Validated
@ConfigurationProperties("vkurse.storage")
public record StorageProperties(
        @NotNull URI url,
        @NotNull URI publicUrl,
        @NotBlank String serviceKey,
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]*") String publicBucket,
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]*") String privateBucket) {}
