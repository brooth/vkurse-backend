/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vkurse.platform.storage.StorageProperties;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.storage.domain.FileStorage;
import vkurse.platform.storage.domain.StoredFile;
import vkurse.platform.storage.domain.StoredObject;

/**
 * Keeps files in Supabase Storage, through its REST API with the service role key. Public files go to one bucket and
 * the rest to another; both are created at startup, with the size limit and the content types of the purposes they
 * hold, so storage itself refuses an upload that breaks them. If storage can't be reached then, startup fails.
 */
@Component
class SupabaseFileStorage implements FileStorage, ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SupabaseFileStorage.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    // Storage answers every error with HTTP 400 and says what happened in the body's `code`.
    private static final String OBJECT_NOT_FOUND = "NoSuchKey";
    private static final String BUCKET_EXISTS = "BucketAlreadyExists";

    private final RestClient client;
    private final StorageProperties properties;
    private final String publicBase;

    SupabaseFileStorage(StorageProperties properties) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(CONNECT_TIMEOUT);
        requests.setReadTimeout(READ_TIMEOUT);
        this.client = RestClient.builder()
                .baseUrl(withoutTrailingSlash(properties.url()))
                .requestFactory(requests)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                // The Supabase gateway, where there is one, wants the key in this header too.
                .defaultHeader("apikey", properties.serviceKey())
                .build();
        this.properties = properties;
        this.publicBase = withoutTrailingSlash(properties.publicUrl());
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureBucket(properties.publicBucket(), true);
        ensureBucket(properties.privateBucket(), false);
    }

    @Override
    public URI signUpload(StoredFile file) {
        var signed = client.post()
                .uri("/object/upload/sign/" + location(file))
                .retrieve()
                .body(SignedUpload.class);
        if (signed == null || signed.url() == null) {
            throw new IllegalStateException("Storage returned no upload link");
        }
        return URI.create(publicBase + signed.url());
    }

    @Override
    public URI signDownload(StoredFile file, Duration ttl) {
        var signed = client.post()
                .uri("/object/sign/" + location(file))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignRequest(ttl.toSeconds()))
                .retrieve()
                .body(SignedDownload.class);
        if (signed == null || signed.signedUrl() == null) {
            throw new IllegalStateException("Storage returned no download link");
        }
        // `download` makes storage send the file as an attachment under that name, so a browser saves it.
        var name = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(publicBase + signed.signedUrl() + "&download=" + name);
    }

    @Override
    public URI publicUrl(StoredFile file) {
        if (!file.getPurpose().isPublic()) {
            throw new IllegalArgumentException("File " + file.getId() + " is not public");
        }
        return URI.create(publicBase + "/object/public/" + location(file));
    }

    @Override
    public Optional<StoredObject> stat(StoredFile file) {
        try {
            var info = client.get()
                    .uri("/object/info/" + location(file))
                    .retrieve()
                    .body(ObjectInfo.class);
            if (info == null || info.size() == null || info.contentType() == null) {
                throw new IllegalStateException("Storage returned no size or type for file " + file.getId());
            }
            return Optional.of(new StoredObject(info.size(), info.contentType()));
        } catch (RestClientResponseException e) {
            if (OBJECT_NOT_FOUND.equals(code(e))) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(Collection<StoredFile> files) {
        files.stream()
                .collect(Collectors.groupingBy(
                        file -> bucket(file.getPurpose().isPublic()),
                        Collectors.mapping(StoredFile::getPath, Collectors.toList())))
                .forEach((bucket, paths) -> client.method(HttpMethod.DELETE)
                        .uri("/object/" + bucket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new DeleteRequest(paths))
                        .retrieve()
                        .toBodilessEntity());
    }

    /** Creates the bucket, or brings an existing one to the limits of today's purposes. */
    private void ensureBucket(String name, boolean isPublic) {
        var settings =
                new Bucket(name, name, isPublic, FilePurpose.maxSize(isPublic), FilePurpose.contentTypes(isPublic));
        try {
            client.post()
                    .uri("/bucket")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(settings)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Created storage bucket {}", name);
        } catch (RestClientResponseException e) {
            if (!BUCKET_EXISTS.equals(code(e))) {
                throw e;
            }
            client.put()
                    .uri("/bucket/" + name)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(settings)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    /** A path has only letters, digits, hyphens, dots and slashes (see {@code FilePurpose}), so it needs no encoding. */
    private String location(StoredFile file) {
        return bucket(file.getPurpose().isPublic()) + "/" + file.getPath();
    }

    private String bucket(boolean isPublic) {
        return isPublic ? properties.publicBucket() : properties.privateBucket();
    }

    private static @Nullable String code(RestClientResponseException e) {
        try {
            var error = e.getResponseBodyAs(StorageError.class);
            return error == null ? null : error.code();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static String withoutTrailingSlash(URI url) {
        var text = url.toString();
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }

    private record Bucket(
            String id,
            String name,
            @JsonProperty("public") boolean isPublic,
            @JsonProperty("file_size_limit") long fileSizeLimit,
            @JsonProperty("allowed_mime_types") Set<String> allowedMimeTypes) {}

    private record SignedUpload(@Nullable String url) {}

    private record SignRequest(long expiresIn) {}

    private record SignedDownload(
            @JsonProperty("signedURL") @Nullable String signedUrl) {}

    private record ObjectInfo(
            @Nullable Long size,
            @JsonProperty("content_type") @Nullable String contentType) {}

    private record DeleteRequest(List<String> prefixes) {}

    private record StorageError(@Nullable String code) {}
}
