package com.jagrosh.jmusicbot.audio.spotify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class SpotifyClient
{
    private final HttpClient httpClient;
    private final URI baseUri;

    public SpotifyClient(String baseUrl)
    {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
    }

    public SpotifyStatus getStatus() throws IOException
    {
        JSONObject json = sendJson("status", "GET", null);
        JSONObject trackJson = json.optJSONObject("track");
        SpotifyTrackDetails track = null;
        if(trackJson != null)
            track = parseTrack(trackJson);
        return new SpotifyStatus(
                json.optString("username", null),
                json.optString("device_id", null),
                json.optString("device_name", null),
                json.optString("play_origin", null),
                json.optBoolean("stopped", false),
                json.optBoolean("paused", false),
                json.optBoolean("buffering", false),
                json.optInt("volume", 100),
                json.optInt("volume_steps", 100),
                json.optBoolean("repeat_context", false),
                json.optBoolean("repeat_track", false),
                json.optBoolean("shuffle_context", false),
                track
        );
    }

    public void play(String spotifyUri) throws IOException
    {
        sendNoContent("player/play", new JSONObject().put("uri", spotifyUri));
    }

    public void resume() throws IOException
    {
        sendNoContent("player/resume", null);
    }

    public void pause() throws IOException
    {
        sendNoContent("player/pause", null);
    }

    public void next() throws IOException
    {
        sendNoContent("player/next", null);
    }

    public void seek(long positionMs) throws IOException
    {
        sendNoContent("player/seek", new JSONObject().put("position", positionMs).put("relative", false));
    }

    public void setVolume(int volume) throws IOException
    {
        sendNoContent("player/volume", new JSONObject().put("volume", volume).put("relative", false));
    }

    public void setRepeatContext(boolean repeat) throws IOException
    {
        sendNoContent("player/repeat_context", new JSONObject().put("repeat_context", repeat));
    }

    public void setRepeatTrack(boolean repeat) throws IOException
    {
        sendNoContent("player/repeat_track", new JSONObject().put("repeat_track", repeat));
    }

    public void setShuffleContext(boolean shuffle) throws IOException
    {
        sendNoContent("player/shuffle_context", new JSONObject().put("shuffle_context", shuffle));
    }

    private void sendNoContent(String path, JSONObject body) throws IOException
    {
        send(path, "POST", body);
    }

    private JSONObject sendJson(String path, String method, JSONObject body) throws IOException
    {
        HttpResponse<String> response = send(path, method, body);
        String bodyText = response.body();
        if(bodyText == null || bodyText.isBlank())
            return new JSONObject();
        return new JSONObject(bodyText);
    }

    private HttpResponse<String> send(String path, String method, JSONObject body) throws IOException
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10));
        if("POST".equals(method))
        {
            builder.POST(body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            builder.header("Content-Type", "application/json");
        }
        else
        {
            builder.GET();
        }

        try
        {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("Spotify backend returned HTTP " + response.statusCode() + " for " + path + ": " + response.body());
            return response;
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while talking to Spotify backend", ex);
        }
    }

    private static SpotifyTrackDetails parseTrack(JSONObject json)
    {
        JSONArray artistsJson = json.optJSONArray("artist_names");
        List<String> artists = new ArrayList<>();
        if(artistsJson != null)
        {
            for(int i = 0; i < artistsJson.length(); i++)
                artists.add(artistsJson.optString(i));
        }
        return new SpotifyTrackDetails(
                json.optString("uri", null),
                json.optString("name", null),
                artists,
                json.optString("album_name", null),
                json.optString("album_cover_url", null),
                json.optLong("duration", 0L)
        );
    }
}
