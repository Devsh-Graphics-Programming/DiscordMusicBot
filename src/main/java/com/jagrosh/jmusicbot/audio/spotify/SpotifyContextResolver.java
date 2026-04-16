package com.jagrosh.jmusicbot.audio.spotify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public final class SpotifyContextResolver
{
    private static final Pattern ITEM_COUNT_PATTERN = Pattern.compile("(\\d[\\d,]*)\\s+(items|songs)", Pattern.CASE_INSENSITIVE);

    private SpotifyContextResolver()
    {
    }

    public static SpotifyContextSnapshot resolve(String spotifyUri) throws IOException
    {
        try
        {
            SpotifyContextSnapshot snapshot = SpotifyPartnerContextResolver.resolve(spotifyUri);
            if(snapshot != null && !snapshot.getEntries().isEmpty())
                return snapshot;
        }
        catch(IOException ignore)
        {
        }
        return resolvePublicPage(spotifyUri);
    }

    private static SpotifyContextSnapshot resolvePublicPage(String spotifyUri) throws IOException
    {
        SpotifyUrl parsed = SpotifyUrl.parse(spotifyUri);
        if(parsed == null)
            return null;

        String url = "https://open.spotify.com/" + parsed.getType() + "/" + parsed.getId();
        Document document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();

        String title = content(document, "meta[property=og:title]");
        String description = content(document, "meta[property=og:description]");
        int totalItems = parseItemCount(description);

        List<SpotifyContextEntry> entries = new ArrayList<>();
        Set<String> seenUris = new LinkedHashSet<>();
        Elements rows = document.select("[data-testid=track-row]");
        for(Element row : rows)
        {
            Element trackLink = row.selectFirst("a[href^=/track/]");
            Element titleElement = row.selectFirst("[data-encore-id=listRowTitle]");
            if(trackLink == null || titleElement == null)
                continue;

            String trackId = trackLink.attr("href").replace("/track/", "").trim();
            if(trackId.isEmpty())
                continue;

            String uri = "spotify:track:" + trackId;
            if(!seenUris.add(uri))
                continue;

            List<String> artists = row.select("[data-testid=internal-artist-link] a").eachText();
            entries.add(new SpotifyContextEntry(uri, titleElement.text().trim(), String.join(", ", artists)));
        }

        return new SpotifyContextSnapshot(parsed.getType(), title, url, totalItems, entries);
    }

    private static String content(Document document, String selector)
    {
        Element element = document.selectFirst(selector);
        return element == null ? null : element.attr("content").trim();
    }

    private static int parseItemCount(String description)
    {
        if(description == null || description.isBlank())
            return 0;
        Matcher matcher = ITEM_COUNT_PATTERN.matcher(description);
        if(!matcher.find())
            return 0;
        return Integer.parseInt(matcher.group(1).replace(",", ""));
    }
}
