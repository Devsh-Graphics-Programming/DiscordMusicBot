package com.jagrosh.jmusicbot.audio.spotify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class SpotifyPartnerContextResolver
{
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final URI PATHFINDER_URI = URI.create("https://api-partner.spotify.com/pathfinder/v1/query");
    private static final String PLAYLIST_QUERY_HASH = "908a5597b4d0af0489a9ad6a2d41bc3b416ff47c0884016d92bbd6822d0eb6d8";
    private static final String ALBUM_QUERY_HASH = "ce390dbf7ca6b61a23aec210619e1094fe9d23d7f101ff773ce1146f84d4dd10";
    private static final String TRACK_QUERY_HASH = "cc31bfe16d74df1e9f6f880a908bb3880674deca34c8b67576ecbf8246e967ba";
    private static final int PLAYLIST_PAGE_SIZE = 100;

    private static final SpotifyPartnerTokenProvider TOKEN_PROVIDER = new SpotifyPartnerTokenProvider();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SpotifyPartnerContextResolver()
    {
    }

    static SpotifyContextSnapshot resolve(String spotifyUri) throws IOException
    {
        SpotifyUrl parsed = SpotifyUrl.parse(spotifyUri);
        if(parsed == null)
            return null;

        switch(parsed.getType())
        {
            case "playlist":
                return resolvePlaylist(parsed);
            case "album":
                return resolveAlbum(parsed);
            case "track":
                return resolveTrack(parsed);
            default:
                return null;
        }
    }

    private static SpotifyContextSnapshot resolvePlaylist(SpotifyUrl parsed) throws IOException
    {
        String uri = parsed.toUri();
        String title = null;
        int totalItems = 0;
        List<SpotifyContextEntry> entries = new ArrayList<>();
        Set<String> seenUris = new LinkedHashSet<>();

        for(int offset = 0; ; offset += PLAYLIST_PAGE_SIZE)
        {
            JSONObject response = query("queryPlaylist",
                    new JSONObject()
                            .put("uri", uri)
                            .put("offset", offset)
                            .put("limit", PLAYLIST_PAGE_SIZE),
                    PLAYLIST_QUERY_HASH);
            JSONObject playlist = response.optJSONObject("data");
            playlist = playlist == null ? null : playlist.optJSONObject("playlistV2");
            if(playlist == null || isGraphError(playlist))
                break;

            title = firstNonBlank(title, trimToNull(playlist.optString("name", null)));
            JSONObject content = playlist.optJSONObject("content");
            if(content == null)
                break;

            totalItems = Math.max(totalItems, content.optInt("totalCount", totalItems));
            JSONArray items = content.optJSONArray("items");
            int before = entries.size();
            appendPlaylistItems(items, offset, entries, seenUris);
            if(items == null || items.isEmpty() || entries.size() == before || (totalItems > 0 && entries.size() >= totalItems))
                break;
        }

        return entries.isEmpty() && totalItems == 0
                ? null
                : new SpotifyContextSnapshot(parsed.getType(), title, toOpenSpotifyUrl(uri), totalItems, entries);
    }

    private static SpotifyContextSnapshot resolveAlbum(SpotifyUrl parsed) throws IOException
    {
        String uri = parsed.toUri();
        String title = null;
        int totalItems = 0;
        int offset = 0;
        List<SpotifyContextEntry> entries = new ArrayList<>();
        Set<String> seenUris = new LinkedHashSet<>();

        while(true)
        {
            JSONObject response = query("queryAlbum",
                    new JSONObject()
                            .put("uri", uri)
                            .put("offset", offset),
                    ALBUM_QUERY_HASH);
            JSONObject album = response.optJSONObject("data");
            album = album == null ? null : album.optJSONObject("albumUnion");
            if(album == null || isGraphError(album))
                break;

            title = firstNonBlank(title, trimToNull(album.optString("name", null)));
            JSONObject tracks = album.optJSONObject("tracksV2");
            if(tracks == null)
                break;

            totalItems = Math.max(totalItems, tracks.optInt("totalCount", totalItems));
            JSONArray items = tracks.optJSONArray("items");
            int before = entries.size();
            appendAlbumItems(items, entries, seenUris);
            int pageSize = items == null ? 0 : items.length();
            if(pageSize == 0 || entries.size() == before || (totalItems > 0 && entries.size() >= totalItems))
                break;
            offset += pageSize;
        }

        return entries.isEmpty() && totalItems == 0
                ? null
                : new SpotifyContextSnapshot(parsed.getType(), title, toOpenSpotifyUrl(uri), totalItems, entries);
    }

    private static SpotifyContextSnapshot resolveTrack(SpotifyUrl parsed) throws IOException
    {
        String uri = parsed.toUri();
        JSONObject response = query("queryTrack",
                new JSONObject().put("uri", uri),
                TRACK_QUERY_HASH);
        JSONObject track = response.optJSONObject("data");
        track = track == null ? null : track.optJSONObject("trackUnion");
        SpotifyContextEntry entry = parseTrack(track);
        if(entry == null)
            return null;
        return new SpotifyContextSnapshot(parsed.getType(), entry.getTitle(), toOpenSpotifyUrl(uri), 1, List.of(entry));
    }

    private static JSONObject query(String operationName, JSONObject variables, String hash) throws IOException
    {
        IOException last = null;
        for(int attempt = 0; attempt < 2; attempt++)
        {
            String accessToken = TOKEN_PROVIDER.getAccessToken();
            HttpRequest request = HttpRequest.newBuilder(PATHFINDER_URI)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", USER_AGENT)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(new JSONObject()
                            .put("operationName", operationName)
                            .put("variables", variables)
                            .put("extensions", new JSONObject()
                                    .put("persistedQuery", new JSONObject()
                                            .put("version", 1)
                                            .put("sha256Hash", hash)))
                            .toString(), StandardCharsets.UTF_8))
                    .build();
            try
            {
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if(response.statusCode() == 401 || response.statusCode() == 403)
                {
                    TOKEN_PROVIDER.invalidate();
                    last = new IOException("Spotify pathfinder authorization failed with HTTP " + response.statusCode() + ".");
                    continue;
                }
                if(response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new IOException("Spotify pathfinder returned HTTP " + response.statusCode() + ": " + response.body());
                return new JSONObject(response.body());
            }
            catch(InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while querying Spotify pathfinder.", ex);
            }
        }
        throw last == null ? new IOException("Spotify pathfinder authorization failed.") : last;
    }

    private static void appendPlaylistItems(JSONArray items, int offset, List<SpotifyContextEntry> entries, Set<String> seenUris)
    {
        if(items == null)
            return;
        for(int i = 0; i < items.length(); i++)
        {
            JSONObject item = items.optJSONObject(i);
            JSONObject wrapper = item == null ? null : item.optJSONObject("itemV2");
            JSONObject data = wrapper == null ? null : wrapper.optJSONObject("data");
            SpotifyContextEntry entry = parsePlayable(wrapper, data, offset + i);
            if(entry != null && seenUris.add(entry.getUri()))
                entries.add(entry);
        }
    }

    private static void appendAlbumItems(JSONArray items, List<SpotifyContextEntry> entries, Set<String> seenUris)
    {
        if(items == null)
            return;
        for(int i = 0; i < items.length(); i++)
        {
            JSONObject item = items.optJSONObject(i);
            JSONObject track = item == null ? null : item.optJSONObject("track");
            SpotifyContextEntry entry = parseTrack(track);
            if(entry != null && seenUris.add(entry.getUri()))
                entries.add(entry);
        }
    }

    private static SpotifyContextEntry parsePlayable(JSONObject wrapper, JSONObject data, int absoluteIndex)
    {
        String wrapperType = wrapper == null ? null : trimToNull(wrapper.optString("__typename", null));
        if("LocalTrackResponseWrapper".equals(wrapperType))
            return new SpotifyContextEntry("spotify:local:" + absoluteIndex, "Local track", "Unavailable via public Spotify metadata");
        if(data == null)
            return null;
        String typename = trimToNull(data.optString("__typename", null));
        if("Track".equals(typename))
            return parseTrack(data);
        if("Episode".equals(typename))
            return parseEpisode(data);
        return null;
    }

    private static SpotifyContextEntry parseTrack(JSONObject track)
    {
        if(track == null || !"Track".equals(track.optString("__typename", "Track")))
            return null;
        String uri = trimToNull(track.optString("uri", null));
        String title = trimToNull(track.optString("name", null));
        String artists = parseArtistNames(track.optJSONObject("artists"));
        return uri == null || title == null ? null : new SpotifyContextEntry(uri, title, artists);
    }

    private static SpotifyContextEntry parseEpisode(JSONObject episode)
    {
        String uri = trimToNull(episode.optString("uri", null));
        String title = trimToNull(episode.optString("name", null));
        String publisher = null;
        JSONObject podcast = episode.optJSONObject("podcastV2");
        if(podcast != null)
            publisher = trimToNull(podcast.optString("name", null));
        return uri == null || title == null ? null : new SpotifyContextEntry(uri, title, publisher);
    }

    private static String parseArtistNames(JSONObject artists)
    {
        if(artists == null)
            return null;
        JSONArray items = artists.optJSONArray("items");
        if(items == null || items.isEmpty())
            return null;
        List<String> names = new ArrayList<>();
        for(int i = 0; i < items.length(); i++)
        {
            JSONObject artist = items.optJSONObject(i);
            JSONObject profile = artist == null ? null : artist.optJSONObject("profile");
            String name = profile == null ? null : trimToNull(profile.optString("name", null));
            if(name != null)
                names.add(name);
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private static String toOpenSpotifyUrl(String spotifyUri)
    {
        if(spotifyUri == null || !spotifyUri.startsWith("spotify:"))
            return spotifyUri;
        String[] parts = spotifyUri.split(":");
        return parts.length == 3 ? "https://open.spotify.com/" + parts[1] + "/" + parts[2] : spotifyUri;
    }

    private static boolean isGraphError(JSONObject data)
    {
        String typename = trimToNull(data.optString("__typename", null));
        return "NotFound".equals(typename) || "GenericError".equals(typename);
    }

    private static String firstNonBlank(String current, String candidate)
    {
        return current != null && !current.isBlank() ? current : candidate;
    }

    private static String trimToNull(String value)
    {
        if(value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
