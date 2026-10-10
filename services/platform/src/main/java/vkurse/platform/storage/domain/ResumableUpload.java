/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import java.net.URI;

/**
 * How a client uploads a big file in pieces, with the tus protocol, and resumes after a break. Every request to
 * {@code endpoint} carries {@code signature} in the {@code x-signature} header; the upload's metadata names
 * {@code bucketName}, {@code objectName} and the file's {@code contentType}.
 */
public record ResumableUpload(URI endpoint, String signature, String bucketName, String objectName) {}
