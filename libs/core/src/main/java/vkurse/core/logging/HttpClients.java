/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.logging;

import java.time.Duration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Where a client of an outside HTTP API starts, so that its calls have timeouts and are logged:
 *
 * <pre>{@code
 * this.client = httpClients.builder("yookassa", CONNECT_TIMEOUT, READ_TIMEOUT).baseUrl(url).build();
 * }</pre>
 *
 * Responses are read into memory to be logged. Don't download files with such a client.
 */
public class HttpClients {
    private final LogMasker masker;

    public HttpClients(LogMasker masker) {
        this.masker = masker;
    }

    /** @param name what the log calls this client: one lowercase word ({@code yookassa}) */
    public RestClient.Builder builder(String name, Duration connectTimeout, Duration readTimeout) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(connectTimeout);
        requests.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(requests))
                .requestInterceptor(new HttpClientLog(name, masker));
    }
}
