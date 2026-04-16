package com.jagrosh.jmusicbot.audio.spotify;

import java.net.URI;
import java.util.Locale;

public class SpotifyUrl
{
    private final String type;
    private final String id;

    private SpotifyUrl(String type, String id)
    {
        this.type = type;
        this.id = id;
    }

    public static SpotifyUrl parse(String input)
    {
        if(input == null || input.isBlank())
            return null;
        String trimmed = input.trim();
        if(trimmed.startsWith("spotify:"))
        {
            String[] parts = trimmed.split(":");
            if(parts.length == 3 && isSupportedType(parts[1]) && !parts[2].isBlank())
                return new SpotifyUrl(parts[1], parts[2]);
            return null;
        }
        try
        {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            if(host == null)
                return null;
            host = host.toLowerCase(Locale.ROOT);
            if(!host.equals("open.spotify.com") && !host.equals("play.spotify.com"))
                return null;
            String[] parts = uri.getPath().split("/");
            if(parts.length < 3)
                return null;
            String type = parts[1].toLowerCase(Locale.ROOT);
            String id = parts[2];
            return isSupportedType(type) && !id.isBlank() ? new SpotifyUrl(type, id) : null;
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    private static boolean isSupportedType(String type)
    {
        return "track".equals(type) || "album".equals(type) || "playlist".equals(type);
    }

    public String toUri()
    {
        return "spotify:" + type + ":" + id;
    }

    public String getType()
    {
        return type;
    }

    public String getId()
    {
        return id;
    }
}
