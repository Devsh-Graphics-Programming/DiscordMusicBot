package com.jagrosh.jmusicbot.audio.spotify;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

final class SpotifyPartnerTokenProvider
{
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String PRODUCT_TYPE = "mobile-web-player";
    private static final String TOTP_VERSION = "61";
    private static final String OBFUSCATED_SECRET = ",7/*F(\"rLJ2oxaKL^f+E1xvP@N";
    private static final byte[] TOTP_SECRET = decodeSecret(OBFUSCATED_SECRET);
    private static final URI SERVER_TIME_URI = URI.create("https://open.spotify.com/api/server-time");
    private static final String TOKEN_URI_PREFIX = "https://open.spotify.com/api/token";

    private final HttpClient httpClient;

    private String accessToken;
    private long accessTokenExpiryMs;

    SpotifyPartnerTokenProvider()
    {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    synchronized String getAccessToken() throws IOException
    {
        long now = System.currentTimeMillis();
        if(accessToken != null && now + 60_000L < accessTokenExpiryMs)
            return accessToken;

        long serverTimeSeconds = fetchServerTimeSeconds();
        String uri = TOKEN_URI_PREFIX
                + "?reason=transport"
                + "&productType=" + encode(PRODUCT_TYPE)
                + "&totp=" + encode(generateTotp(now))
                + "&totpServer=" + encode(generateTotp(serverTimeSeconds * 1000L))
                + "&totpVer=" + encode(TOTP_VERSION);

        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        JSONObject json = sendJson(request);
        accessToken = trimToNull(json.optString("accessToken", null));
        accessTokenExpiryMs = json.optLong("accessTokenExpirationTimestampMs", 0L);
        if(accessToken == null || accessTokenExpiryMs <= 0L)
            throw new IOException("Spotify partner token response did not contain a usable access token.");
        return accessToken;
    }

    synchronized void invalidate()
    {
        accessToken = null;
        accessTokenExpiryMs = 0L;
    }

    private long fetchServerTimeSeconds() throws IOException
    {
        HttpRequest request = HttpRequest.newBuilder(SERVER_TIME_URI)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        JSONObject json = sendJson(request);
        long serverTime = json.optLong("serverTime", 0L);
        if(serverTime <= 0L)
            throw new IOException("Spotify server-time response did not contain a valid timestamp.");
        return serverTime;
    }

    private JSONObject sendJson(HttpRequest request) throws IOException
    {
        try
        {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("Spotify partner auth returned HTTP " + response.statusCode() + ": " + response.body());
            return new JSONObject(response.body());
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while talking to Spotify partner auth", ex);
        }
    }

    private String generateTotp(long timestampMs) throws IOException
    {
        ByteBuffer counter = ByteBuffer.allocate(Long.BYTES);
        counter.putLong((timestampMs / 1000L) / 30L);
        byte[] digest;
        try
        {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(TOTP_SECRET, "HmacSHA1"));
            digest = mac.doFinal(counter.array());
        }
        catch(GeneralSecurityException ex)
        {
            throw new IOException("Failed generating Spotify partner TOTP.", ex);
        }

        int offset = digest[digest.length - 1] & 0x0F;
        int binary = ((digest[offset] & 0x7F) << 24)
                | ((digest[offset + 1] & 0xFF) << 16)
                | ((digest[offset + 2] & 0xFF) << 8)
                | (digest[offset + 3] & 0xFF);
        return String.format("%06d", binary % 1_000_000);
    }

    private static byte[] decodeSecret(String secret)
    {
        StringBuilder decoded = new StringBuilder();
        for(int i = 0; i < secret.length(); i++)
            decoded.append(secret.charAt(i) ^ (i % 33 + 9));
        return decoded.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimToNull(String value)
    {
        if(value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
