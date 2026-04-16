package com.jagrosh.jmusicbot.audio.spotify;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SpotifyTrackDetails
{
    private final String uri;
    private final String name;
    private final List<String> artistNames;
    private final String albumName;
    private final String albumCoverUrl;
    private final long durationMs;

    public SpotifyTrackDetails(String uri, String name, List<String> artistNames, String albumName, String albumCoverUrl, long durationMs)
    {
        this.uri = uri;
        this.name = name;
        this.artistNames = artistNames == null ? Collections.emptyList() : List.copyOf(artistNames);
        this.albumName = albumName;
        this.albumCoverUrl = albumCoverUrl;
        this.durationMs = durationMs;
    }

    public String getUri()
    {
        return uri;
    }

    public String getName()
    {
        return name;
    }

    public List<String> getArtistNames()
    {
        return artistNames;
    }

    public String getArtistsDisplay()
    {
        return String.join(", ", artistNames);
    }

    public String getAlbumName()
    {
        return albumName;
    }

    public String getAlbumCoverUrl()
    {
        return albumCoverUrl;
    }

    public long getDurationMs()
    {
        return durationMs;
    }

    @Override
    public boolean equals(Object o)
    {
        if(this == o)
            return true;
        if(!(o instanceof SpotifyTrackDetails))
            return false;
        SpotifyTrackDetails that = (SpotifyTrackDetails) o;
        return Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(uri);
    }
}
