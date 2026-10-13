/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import vkurse.platform.course.domain.CourseErrors.UnsupportedVideoLinkException;

/**
 * A video on an outside host. It exists only if its link is one of the forms a supported host uses, so the address of
 * the player is built from an id and a key we checked, never from what an author typed.
 *
 * @param videoId the host's id of the video; for VK it is {@code {owner}_{video}}
 * @param key what lets a video that isn't public be shown: Rutube's {@code p}, VK's {@code hash}
 * @param url the link it was read from
 */
public record VideoLink(
        Provider provider, String videoId, @Nullable String key, String url) {
    public static final int MAX_LENGTH = 2000;

    private static final Pattern IFRAME_SRC =
            Pattern.compile("<iframe\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Set<String> YOUTUBE_HOSTS =
            Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "www.youtube-nocookie.com");
    private static final String YOUTUBE_SHORT_HOST = "youtu.be";
    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern YOUTUBE_PATH = Pattern.compile("/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})/?");
    private static final Pattern YOUTUBE_SHORT_PATH = Pattern.compile("/([A-Za-z0-9_-]{11})/?");

    private static final Set<String> RUTUBE_HOSTS = Set.of("rutube.ru");
    private static final Pattern RUTUBE_PATH = Pattern.compile("/(?:video(?:/private)?|play/embed)/([0-9a-f]{32})/?");
    private static final Pattern RUTUBE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private static final Set<String> VK_HOSTS = Set.of("vk.com", "vk.ru", "m.vk.com", "vkvideo.ru");
    private static final String VK_PLAYER_PATH = "/video_ext.php";
    private static final Pattern VK_PATH = Pattern.compile("/video(-?\\d{1,20})_(\\d{1,20})(?:/|$)");
    private static final Pattern VK_OPENED = Pattern.compile("video(-?\\d{1,20})_(\\d{1,20})(?:/.*)?");
    private static final Pattern VK_OWNER = Pattern.compile("-?\\d{1,20}");
    private static final Pattern VK_VIDEO = Pattern.compile("\\d{1,20}");
    private static final Pattern VK_HASH = Pattern.compile("[0-9a-f]{1,64}");

    /**
     * Reads what an author pasted: a link to the video, or the code the host gives to embed it. Of the code only the
     * address of its {@code iframe} is used.
     */
    public static VideoLink parse(@Nullable String pasted) {
        if (pasted == null || pasted.isBlank() || pasted.length() > MAX_LENGTH) {
            throw new UnsupportedVideoLinkException();
        }
        var url = pasted.strip();
        var iframe = IFRAME_SRC.matcher(url);
        if (iframe.find()) {
            url = iframe.group(1).strip().replace("&amp;", "&");
            if (url.startsWith("//")) {
                url = "https:" + url;
            }
        }
        try {
            var uri = new URI(url);
            var host = uri.getHost();
            var path = uri.getPath();
            var web = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            if (web && host != null && path != null && uri.getUserInfo() == null) {
                var link = read(host.toLowerCase(Locale.ROOT), path, query(uri), url);
                if (link != null) {
                    return link;
                }
            }
        } catch (URISyntaxException | IllegalArgumentException e) {
            // refused below
        }
        throw new UnsupportedVideoLinkException();
    }

    private static @Nullable VideoLink read(String host, String path, Map<String, String> query, String url) {
        if (host.equals(YOUTUBE_SHORT_HOST)) {
            var matcher = YOUTUBE_SHORT_PATH.matcher(path);
            return matcher.matches() ? new VideoLink(Provider.YOUTUBE, matcher.group(1), null, url) : null;
        }
        if (YOUTUBE_HOSTS.contains(host)) {
            var matcher = YOUTUBE_PATH.matcher(path);
            var id = matcher.matches() ? matcher.group(1) : path.equals("/watch") ? query.get("v") : null;
            return fits(id, YOUTUBE_ID) ? new VideoLink(Provider.YOUTUBE, id, null, url) : null;
        }
        if (RUTUBE_HOSTS.contains(host)) {
            var matcher = RUTUBE_PATH.matcher(path);
            if (!matcher.matches()) {
                return null;
            }
            // A key we can't use is a link that won't play: refuse it, don't drop the key.
            var key = query.get("p");
            return key == null || fits(key, RUTUBE_KEY)
                    ? new VideoLink(Provider.RUTUBE, matcher.group(1), key, url)
                    : null;
        }
        if (VK_HOSTS.contains(host)) {
            if (path.equals(VK_PLAYER_PATH)) {
                var owner = query.get("oid");
                var video = query.get("id");
                var hash = query.get("hash");
                return fits(owner, VK_OWNER) && fits(video, VK_VIDEO) && (hash == null || fits(hash, VK_HASH))
                        ? new VideoLink(Provider.VK, owner + "_" + video, hash, url)
                        : null;
            }
            // A video opened over a page is named in `z`, and the path is the page's.
            var opened = VK_OPENED.matcher(query.getOrDefault("z", ""));
            if (opened.matches()) {
                return new VideoLink(Provider.VK, opened.group(1) + "_" + opened.group(2), null, url);
            }
            var matcher = VK_PATH.matcher(path);
            return matcher.find()
                    ? new VideoLink(Provider.VK, matcher.group(1) + "_" + matcher.group(2), null, url)
                    : null;
        }
        return null;
    }

    private static boolean fits(@Nullable String value, Pattern pattern) {
        return value != null && pattern.matcher(value).matches();
    }

    /** The query's parameters, decoded; the first value of one that is repeated. */
    private static Map<String, String> query(URI uri) {
        var parameters = new HashMap<String, String>();
        var query = uri.getRawQuery();
        if (query == null) {
            return parameters;
        }
        for (var pair : query.split("&")) {
            var at = pair.indexOf('=');
            if (at > 0) {
                parameters.putIfAbsent(
                        URLDecoder.decode(pair.substring(0, at), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
            }
        }
        return parameters;
    }

    /** The address of the host's player for this video: what an {@code iframe} shows. */
    public String embedUrl() {
        return switch (provider) {
            case YOUTUBE -> "https://www.youtube.com/embed/" + videoId;
            case RUTUBE -> "https://rutube.ru/play/embed/" + videoId + "/" + (key == null ? "" : "?p=" + key);
            case VK -> {
                var at = videoId.indexOf('_');
                yield "https://vk.com/video_ext.php?oid=" + videoId.substring(0, at) + "&id="
                        + videoId.substring(at + 1) + "&hd=2" + (key == null ? "" : "&hash=" + key);
            }
        };
    }

    public enum Provider {
        RUTUBE,
        VK,
        YOUTUBE
    }
}
