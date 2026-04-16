/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.commands.music;

import java.util.List;
import java.util.concurrent.TimeUnit;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.menu.Paginator;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyContextEntry;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyContextSnapshot;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyStatus;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyTrackDetails;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyUrl;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class QueueCmd extends MusicCommand 
{
    private final Paginator.Builder builder;
    
    public QueueCmd(Bot bot)
    {
        super(bot);
        this.name = "queue";
        this.help = "shows the current queue";
        this.arguments = "[pagenum]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
        this.botPermissions = new Permission[]{Permission.MESSAGE_ADD_REACTION,Permission.MESSAGE_EMBED_LINKS};
        builder = new Paginator.Builder()
                .setColumns(1)
                .setFinalAction(m -> {try{m.clearReactions().queue();}catch(PermissionException ignore){}})
                .setItemsPerPage(10)
                .waitOnSinglePage(false)
                .useNumberedItems(true)
                .showPageNumbers(true)
                .wrapPageEnds(true)
                .setEventWaiter(bot.getWaiter())
                .setTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        int pagenum = 1;
        try
        {
            pagenum = Integer.parseInt(event.getArgs());
        }
        catch(NumberFormatException ignore){}
        AudioHandler ah = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        if(ah.isSpotifyActive())
        {
            if(replySpotifyPaginator(event, ah, pagenum))
                return;
            event.reply(buildSpotifyQueue(event, ah));
            return;
        }
        List<QueuedTrack> list = ah.getQueue().getList();
        if(list.isEmpty())
        {
            MessageCreateData nowp = ah.getNowPlaying(event.getJDA());
            MessageCreateData nonowp = ah.getNoMusicPlaying(event.getJDA());
            MessageCreateData built = new MessageCreateBuilder()
                    .setContent(event.getClient().getWarning() + " There is no music in the queue!")
                    .setEmbeds((nowp==null ? nonowp : nowp).getEmbeds().get(0)).build();
            event.reply(built, m -> 
            {
                if(nowp!=null)
                    bot.getNowplayingHandler().setLastNPMessage(m);
            });
            return;
        }
        String[] songs = new String[list.size()];
        long total = 0;
        for(int i=0; i<list.size(); i++)
        {
            total += list.get(i).getTrack().getDuration();
            songs[i] = list.get(i).toString();
        }
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        long fintotal = total;
        builder.setText((i1,i2) -> getQueueTitle(ah, event.getClient().getSuccess(), songs.length, fintotal, settings.getRepeatMode(), settings.getQueueType()))
                .setItems(songs)
                .setUsers(event.getAuthor())
                .setColor(event.getSelfMember().getColor())
                ;
        builder.build().paginate(event.getChannel(), pagenum);
    }
    
    private String getQueueTitle(AudioHandler ah, String success, int songslength, long total, RepeatMode repeatmode, QueueType queueType)
    {
        StringBuilder sb = new StringBuilder();
        if(ah.getPlayer().getPlayingTrack()!=null)
        {
            sb.append(ah.getStatusEmoji()).append(" **")
                    .append(ah.getPlayer().getPlayingTrack().getInfo().title).append("**\n");
        }
        return FormatUtil.filter(sb.append(success).append(" Current Queue | ").append(songslength)
                .append(" entries | `").append(TimeUtil.formatTime(total)).append("` ")
                .append("| ").append(queueType.getEmoji()).append(" `").append(queueType.getUserFriendlyName()).append('`')
                .append(repeatmode.getEmoji() != null ? " | "+repeatmode.getEmoji() : "").toString());
    }

    private MessageCreateData buildSpotifyQueue(CommandEvent event, AudioHandler ah)
    {
        SpotifyStatus status = ah.getSpotifyStatus();
        SpotifyTrackDetails current = status == null ? null : status.getTrack();
        List<SpotifyTrackDetails> recent = ah.getSpotifyRecentTracks();
        String requestedUri = ah.getSpotifyRequestedUri();
        String requestedUrl = ah.getSpotifyRequestedUrl();
        SpotifyUrl parsed = SpotifyUrl.parse(requestedUri);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(event.getSelfMember().getColor())
                .setTitle(buildSpotifyQueueTitle(parsed), requestedUrl);

        if(current != null)
        {
            eb.setDescription(FormatUtil.filter(ah.getStatusEmoji()
                    + " **" + current.getName() + "**\n`["
                    + TimeUtil.formatTime(ah.getPosition()) + "/" + TimeUtil.formatTime(current.getDurationMs())
                    + "]` " + FormatUtil.volumeIcon(ah.getVolume())));
            if(current.getAlbumCoverUrl() != null && !current.getAlbumCoverUrl().isBlank())
                eb.setThumbnail(current.getAlbumCoverUrl());
            if(current.getArtistsDisplay() != null && !current.getArtistsDisplay().isBlank())
                eb.addField("Now Playing", current.getName() + "\n" + current.getArtistsDisplay(), false);
        }

        if(parsed != null)
            eb.addField("Context", formatContext(parsed, requestedUrl), false);

        if(status != null)
            eb.addField("Playback", formatPlayback(status), false);

        if(!recent.isEmpty())
            eb.addField("Recent In This Context", formatRecentTracks(recent), false);

        if(parsed != null && ("playlist".equals(parsed.getType()) || "album".equals(parsed.getType())))
        {
            eb.addField("Upcoming",
                    "Spotify controls the remaining queue inside this context.\nExact upcoming entries are not exposed by the current go-librespot API.",
                    false);
        }

        return new net.dv8tion.jda.api.utils.messages.MessageCreateBuilder()
                .setContent(FormatUtil.filter(event.getClient().getSuccess() + " Spotify Queue"))
                .setEmbeds(eb.build())
                .build();
    }

    private boolean replySpotifyPaginator(CommandEvent event, AudioHandler ah, int pagenum)
    {
        SpotifyContextSnapshot snapshot = ah.getSpotifyContextSnapshot();
        if(snapshot == null || snapshot.getEntries().isEmpty())
            return false;

        String currentUri = ah.getCurrentUri();
        String[] items = snapshot.getEntries().stream()
                .map(entry -> formatSpotifyQueueEntry(entry, currentUri))
                .toArray(String[]::new);

        builder.setText((i1, i2) -> getSpotifyQueueTitle(event, ah, snapshot))
                .setItems(items)
                .setUsers(event.getAuthor())
                .setColor(event.getSelfMember().getColor());
        builder.build().paginate(event.getChannel(), pagenum);
        return true;
    }

    private String buildSpotifyQueueTitle(SpotifyUrl parsed)
    {
        if(parsed == null)
            return "Spotify Queue";
        switch(parsed.getType())
        {
            case "playlist":
                return "Spotify Playlist Context";
            case "album":
                return "Spotify Album Context";
            case "track":
                return "Spotify Track Context";
            default:
                return "Spotify Queue";
        }
    }

    private String formatContext(SpotifyUrl parsed, String requestedUrl)
    {
        String label;
        switch(parsed.getType())
        {
            case "playlist":
                label = "Playlist";
                break;
            case "album":
                label = "Album";
                break;
            case "track":
                label = "Track";
                break;
            default:
                label = "Context";
        }
        return requestedUrl == null ? label + "\n`" + parsed.toUri() + "`" : label + "\n" + requestedUrl;
    }

    private String getSpotifyQueueTitle(CommandEvent event, AudioHandler ah, SpotifyContextSnapshot snapshot)
    {
        SpotifyStatus status = ah.getSpotifyStatus();
        SpotifyTrackDetails current = status == null ? null : status.getTrack();
        StringBuilder sb = new StringBuilder();
        if(current != null)
        {
            sb.append(ah.getStatusEmoji()).append(" **").append(current.getName()).append("**\n");
        }
        sb.append(event.getClient().getSuccess()).append(" Spotify Queue | ");
        sb.append(snapshot.getTitle() == null || snapshot.getTitle().isBlank() ? "Context" : snapshot.getTitle());
        if(snapshot.isPartial())
        {
            sb.append(" | ").append(snapshot.getEntries().size()).append(" shown");
            if(snapshot.getTotalItems() > 0)
                sb.append("/").append(snapshot.getTotalItems());
        }
        else
        {
            int count = snapshot.getTotalItems() > 0 ? snapshot.getTotalItems() : snapshot.getEntries().size();
            sb.append(" | ").append(count).append(" entries");
        }
        sb.append(" | `").append(snapshot.getType()).append('`');
        if(snapshot.isPartial())
            sb.append("\n").append(event.getClient().getWarning()).append(" Showing the first public chunk exposed by Spotify page HTML.");
        return FormatUtil.filter(sb.toString());
    }

    private String formatPlayback(SpotifyStatus status)
    {
        return "Device: `" + status.getDeviceName() + "`"
                + "\nShuffle: `" + onOff(status.isShuffleContext()) + "`"
                + "\nRepeat Context: `" + onOff(status.isRepeatContext()) + "`"
                + "\nRepeat Track: `" + onOff(status.isRepeatTrack()) + "`"
                + "\nState: `" + playbackState(status) + "`";
    }

    private String formatRecentTracks(List<SpotifyTrackDetails> recent)
    {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < recent.size(); i++)
        {
            SpotifyTrackDetails track = recent.get(i);
            if(i > 0)
                sb.append('\n');
            boolean current = i == recent.size() - 1;
            sb.append(current ? "▶ " : "• ")
                    .append(track.getName());
            String artists = track.getArtistsDisplay();
            if(artists != null && !artists.isBlank())
                sb.append(" - ").append(artists);
        }
        return sb.toString();
    }

    private String onOff(boolean value)
    {
        return value ? "on" : "off";
    }

    private String playbackState(SpotifyStatus status)
    {
        if(status.isBuffering())
            return "buffering";
        if(status.isPaused())
            return "paused";
        if(status.isStopped())
            return "stopped";
        return "playing";
    }

    private String formatSpotifyQueueEntry(SpotifyContextEntry entry, String currentUri)
    {
        StringBuilder sb = new StringBuilder();
        if(entry.getUri() != null && entry.getUri().equals(currentUri))
            sb.append("▶ ");
        String title = entry.getTitle();
        String url = toOpenSpotifyUrl(entry.getUri());
        if(url != null)
            sb.append("[**").append(title).append("**](").append(url).append(")");
        else
            sb.append(title);
        if(entry.getArtists() != null && !entry.getArtists().isBlank())
            sb.append(" - ").append(entry.getArtists());
        return sb.toString();
    }

    private String toOpenSpotifyUrl(String uri)
    {
        if(uri == null || !uri.startsWith("spotify:"))
            return null;
        String[] parts = uri.split(":");
        if(parts.length != 3)
            return null;
        switch(parts[1])
        {
            case "track":
            case "album":
            case "playlist":
            case "episode":
            case "artist":
            case "show":
                return "https://open.spotify.com/" + parts[1] + "/" + parts[2];
            default:
                return null;
        }
    }
}
