package com.jagrosh.jmusicbot.audio.spotify;

import java.util.Collections;
import java.util.List;

public class SpotifyContextSnapshot
{
    private final String type;
    private final String title;
    private final String url;
    private final int totalItems;
    private final List<SpotifyContextEntry> entries;

    public SpotifyContextSnapshot(String type, String title, String url, int totalItems, List<SpotifyContextEntry> entries)
    {
        this.type = type;
        this.title = title;
        this.url = url;
        this.totalItems = totalItems;
        this.entries = entries == null ? Collections.emptyList() : List.copyOf(entries);
    }

    public String getType()
    {
        return type;
    }

    public String getTitle()
    {
        return title;
    }

    public String getUrl()
    {
        return url;
    }

    public int getTotalItems()
    {
        return totalItems;
    }

    public List<SpotifyContextEntry> getEntries()
    {
        return entries;
    }

    public boolean isPartial()
    {
        return totalItems > 0 && entries.size() < totalItems;
    }
}
