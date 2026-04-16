package com.jagrosh.jmusicbot.audio.spotify;

import java.util.Objects;

public class SpotifyContextEntry
{
    private final String uri;
    private final String title;
    private final String artists;

    public SpotifyContextEntry(String uri, String title, String artists)
    {
        this.uri = uri;
        this.title = title;
        this.artists = artists;
    }

    public String getUri()
    {
        return uri;
    }

    public String getTitle()
    {
        return title;
    }

    public String getArtists()
    {
        return artists;
    }

    @Override
    public boolean equals(Object o)
    {
        if(this == o)
            return true;
        if(!(o instanceof SpotifyContextEntry))
            return false;
        SpotifyContextEntry that = (SpotifyContextEntry) o;
        return Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(uri);
    }
}
