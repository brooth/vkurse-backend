/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.net.URI;
import java.net.URISyntaxException;
import vkurse.core.error.Errors.BadRequestException;

/**
 * Where a provider sends the payer back to after paying. It must be a page of our own web app, so a payment can't be
 * used to send someone to a stranger's site.
 */
public record ReturnUrl(URI value) {
    public static final int MAX_LENGTH = 500;

    /**
     * @param webUrl the base URL of our web app
     */
    public static ReturnUrl of(String url, URI webUrl) {
        URI parsed;
        try {
            parsed = new URI(url);
        } catch (URISyntaxException e) {
            throw new BadRequestException("The return URL is not a URL");
        }
        var base = webUrl.toString();
        var prefix = base.endsWith("/") ? base : base + "/";
        if (url.length() > MAX_LENGTH || !(url.equals(base) || url.startsWith(prefix))) {
            throw new BadRequestException("The return URL must be a page of the web app");
        }
        return new ReturnUrl(parsed);
    }
}
