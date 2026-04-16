/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyPlayback;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyContextSnapshot;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyStatus;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyTrackDetails;
import com.jagrosh.jmusicbot.audio.spotify.SpotifyUrl;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.LoggerFactory;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class AudioHandler extends AudioEventAdapter implements AudioSendHandler 
{
    public final static String PLAY_EMOJI  = "\u25B6"; // ▶
    public final static String PAUSE_EMOJI = "\u23F8"; // ⏸
    public final static String STOP_EMOJI  = "\u23F9"; // ⏹


    private final List<AudioTrack> defaultQueue = new LinkedList<>();
    private final Set<String> votes = new HashSet<>();
    
    private final PlayerManager manager;
    private final AudioPlayer audioPlayer;
    private final long guildId;
    private final SpotifyPlayback spotifyPlayback;
    
    private AudioFrame lastFrame;
    private AbstractQueue<QueuedTrack> queue;
    private String lastTrackTitle;

    protected AudioHandler(PlayerManager manager, Guild guild, AudioPlayer player)
    {
        this.manager = manager;
        this.audioPlayer = player;
        this.guildId = guild.getIdLong();
        this.spotifyPlayback = manager.getBot().getConfig().useSpotify() ? new SpotifyPlayback(manager.getBot().getConfig()) : null;

        this.setQueueType(manager.getBot().getSettingsManager().getSettings(guildId).getQueueType());
    }

    public void setQueueType(QueueType type)
    {
        queue = type.createInstance(queue);
    }

    public int addTrackToFront(QueuedTrack qtrack)
    {
        if(isSpotifyActive())
            throw new IllegalStateException("Spotify playback does not support local queue operations yet.");
        if(audioPlayer.getPlayingTrack()==null)
        {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        }
        else
        {
            queue.addAt(0, qtrack);
            return 0;
        }
    }
    
    public int addTrack(QueuedTrack qtrack)
    {
        if(isSpotifyActive())
            throw new IllegalStateException("Spotify playback does not support local queue operations yet.");
        if(audioPlayer.getPlayingTrack()==null)
        {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        }
        else
            return queue.add(qtrack);
    }
    
    public AbstractQueue<QueuedTrack> getQueue()
    {
        return queue;
    }
    
    public void stopAndClear()
    {
        queue.clear();
        defaultQueue.clear();
        audioPlayer.stopTrack();
        if(spotifyPlayback != null)
        {
            try
            {
                spotifyPlayback.stop();
            }
            catch(IOException ex)
            {
                LoggerFactory.getLogger("AudioHandler").warn("Failed to stop Spotify playback", ex);
            }
        }
        updateTrackTitle(null);
    }
    
    public boolean isMusicPlaying(JDA jda)
    {
        if(isSpotifyActive())
            return guild(jda).getSelfMember().getVoiceState().inAudioChannel() && getSpotifyTrack() != null;
        return guild(jda).getSelfMember().getVoiceState().inAudioChannel() && audioPlayer.getPlayingTrack()!=null;
    }
    
    public Set<String> getVotes()
    {
        return votes;
    }
    
    public AudioPlayer getPlayer()
    {
        return audioPlayer;
    }
    
    public RequestMetadata getRequestMetadata()
    {
        if(isSpotifyActive())
            return spotifyPlayback.getRequestMetadata();
        if(audioPlayer.getPlayingTrack() == null)
            return RequestMetadata.EMPTY;
        RequestMetadata rm = audioPlayer.getPlayingTrack().getUserData(RequestMetadata.class);
        return rm == null ? RequestMetadata.EMPTY : rm;
    }
    
    public boolean playFromDefault()
    {
        if(isSpotifyActive())
            return false;
        if(!defaultQueue.isEmpty())
        {
            audioPlayer.playTrack(defaultQueue.remove(0));
            return true;
        }
        Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
        if(settings==null || settings.getDefaultPlaylist()==null)
            return false;
        
        Playlist pl = manager.getBot().getPlaylistLoader().getPlaylist(settings.getDefaultPlaylist());
        if(pl==null || pl.getItems().isEmpty())
            return false;
        pl.loadTracks(manager, (at) -> 
        {
            if(audioPlayer.getPlayingTrack()==null)
                audioPlayer.playTrack(at);
            else
                defaultQueue.add(at);
        }, () -> 
        {
            if(pl.getTracks().isEmpty() && !manager.getBot().getConfig().getStay())
                manager.getBot().closeAudioConnection(guildId);
        });
        return true;
    }
    
    // Audio Events
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) 
    {
        RepeatMode repeatMode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
        // if the track ended normally, and we're in repeat mode, re-add it to the queue
        if(endReason==AudioTrackEndReason.FINISHED && repeatMode != RepeatMode.OFF)
        {
            QueuedTrack clone = new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class));
            if(repeatMode == RepeatMode.ALL)
                queue.add(clone);
            else
                queue.addAt(0, clone);
        }
        
        if(queue.isEmpty())
        {
            if(!playFromDefault())
            {
                manager.getBot().getNowplayingHandler().onTrackUpdate(null);
                if(!manager.getBot().getConfig().getStay())
                    manager.getBot().closeAudioConnection(guildId);
                // unpause, in the case when the player was paused and the track has been skipped.
                // this is to prevent the player being paused next time it's being used.
                player.setPaused(false);
            }
        }
        else
        {
            QueuedTrack qt = queue.pull();
            player.playTrack(qt.getTrack());
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        LoggerFactory.getLogger("AudioHandler").error("Track " + track.getIdentifier() + " has failed to play", exception);
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) 
    {
        votes.clear();
        updateTrackTitle(track == null ? null : track.getInfo().title);
    }

    
    // Formatting
    public MessageCreateData getNowPlaying(JDA jda)
    {
        if(isSpotifyActive())
            return getSpotifyNowPlaying(jda);
        if(isMusicPlaying(jda))
        {
            Guild guild = guild(jda);
            AudioTrack track = audioPlayer.getPlayingTrack();
            MessageCreateBuilder mb = new MessageCreateBuilder();
            mb.setContent(FormatUtil.filter(manager.getBot().getConfig().getSuccess()+" **Now Playing in "+guild.getSelfMember().getVoiceState().getChannel().getAsMention()+"...**"));
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(guild.getSelfMember().getColor());
            RequestMetadata rm = getRequestMetadata();
            if(rm.getOwner() != 0L)
            {
                User u = guild.getJDA().getUserById(rm.user.id);
                if(u==null)
                    eb.setAuthor(FormatUtil.formatUsername(rm.user), null, rm.user.avatar);
                else
                    eb.setAuthor(FormatUtil.formatUsername(u), null, u.getEffectiveAvatarUrl());
            }

            try 
            {
                eb.setTitle(track.getInfo().title, track.getInfo().uri);
            }
            catch(Exception e) 
            {
                eb.setTitle(track.getInfo().title);
            }

            if(track instanceof YoutubeAudioTrack && manager.getBot().getConfig().useNPImages())
            {
                eb.setThumbnail("https://img.youtube.com/vi/"+track.getIdentifier()+"/mqdefault.jpg");
            }
            
            if(track.getInfo().author != null && !track.getInfo().author.isEmpty())
                eb.setFooter("Source: " + track.getInfo().author, null);

            double progress = (double)audioPlayer.getPlayingTrack().getPosition()/track.getDuration();
            eb.setDescription(getStatusEmoji()
                    + " "+FormatUtil.progressBar(progress)
                    + " `[" + TimeUtil.formatTime(track.getPosition()) + "/" + TimeUtil.formatTime(track.getDuration()) + "]` "
                    + FormatUtil.volumeIcon(audioPlayer.getVolume()));
            
            return mb.setEmbeds(eb.build()).build();
        }
        else return null;
    }
    
    public MessageCreateData getNoMusicPlaying(JDA jda)
    {
        Guild guild = guild(jda);
        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(manager.getBot().getConfig().getSuccess()+" **Now Playing...**"))
                .setEmbeds(new EmbedBuilder()
                .setTitle("No music playing")
                .setDescription(STOP_EMOJI+" "+FormatUtil.progressBar(-1)+" "+FormatUtil.volumeIcon(getVolume()))
                .setColor(guild.getSelfMember().getColor())
                .build()).build();
    }

    public String getStatusEmoji()
    {
        return isPaused() ? PAUSE_EMOJI : PLAY_EMOJI;
    }
    
    // Audio Send Handler methods
    /*@Override
    public boolean canProvide() 
    {
        if (lastFrame == null)
            lastFrame = audioPlayer.provide();

        return lastFrame != null;
    }

    @Override
    public byte[] provide20MsAudio() 
    {
        if (lastFrame == null) 
            lastFrame = audioPlayer.provide();

        byte[] data = lastFrame != null ? lastFrame.getData() : null;
        lastFrame = null;

        return data;
    }*/
    
    @Override
    public boolean canProvide() 
    {
        if(isSpotifyActive())
            return spotifyPlayback.canProvide();
        lastFrame = audioPlayer.provide();
        return lastFrame != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() 
    {
        if(isSpotifyActive())
            return spotifyPlayback.provide();
        return ByteBuffer.wrap(lastFrame.getData());
    }

    @Override
    public boolean isOpus() 
    {
        return !isSpotifyActive();
    }

    public boolean isPaused()
    {
        return isSpotifyActive() ? spotifyPlayback.isPaused() : audioPlayer.isPaused();
    }

    public int getVolume()
    {
        return isSpotifyActive() ? spotifyPlayback.getVolume() : audioPlayer.getVolume();
    }

    public void setVolume(int volume) throws IOException
    {
        if(isSpotifyActive())
            spotifyPlayback.setVolume(volume);
        else
            audioPlayer.setVolume(volume);
    }

    public RepeatMode setRepeatMode(RepeatMode mode) throws IOException
    {
        if(isSpotifyActive())
            return spotifyPlayback.setRepeatMode(mode);
        return mode;
    }

    public RepeatMode getRepeatMode()
    {
        return isSpotifyActive() ? spotifyPlayback.currentRepeatMode() : null;
    }

    public boolean setShuffleContext(boolean shuffle) throws IOException
    {
        return isSpotifyActive() && spotifyPlayback.setShuffleContext(shuffle);
    }

    public boolean isSeekable()
    {
        return isSpotifyActive() ? getSpotifyTrack() != null : audioPlayer.getPlayingTrack() != null && audioPlayer.getPlayingTrack().isSeekable();
    }

    public long getPosition()
    {
        return isSpotifyActive() ? spotifyPlayback.getPosition() : audioPlayer.getPlayingTrack().getPosition();
    }

    public long getDuration()
    {
        return isSpotifyActive() ? spotifyPlayback.getDuration() : audioPlayer.getPlayingTrack().getDuration();
    }

    public String getCurrentTitle()
    {
        if(isSpotifyActive())
        {
            SpotifyTrackDetails track = getSpotifyTrack();
            return track == null ? null : track.getName();
        }
        return audioPlayer.getPlayingTrack() == null ? null : audioPlayer.getPlayingTrack().getInfo().title;
    }

    public String getCurrentUri()
    {
        if(isSpotifyActive())
        {
            SpotifyTrackDetails track = getSpotifyTrack();
            return track == null ? spotifyPlayback.getRequestedUri() : track.getUri();
        }
        return audioPlayer.getPlayingTrack() == null ? null : audioPlayer.getPlayingTrack().getInfo().uri;
    }

    public void pausePlayback() throws IOException
    {
        if(isSpotifyActive())
            spotifyPlayback.pause();
        else
            audioPlayer.setPaused(true);
    }

    public void resumePlayback() throws IOException
    {
        if(isSpotifyActive())
            spotifyPlayback.resume();
        else
            audioPlayer.setPaused(false);
    }

    public void skipCurrent() throws IOException
    {
        if(isSpotifyActive())
            spotifyPlayback.next();
        else
            audioPlayer.stopTrack();
    }

    public void seekTo(long position) throws IOException
    {
        if(isSpotifyActive())
            spotifyPlayback.seek(position);
        else if(audioPlayer.getPlayingTrack() != null)
            audioPlayer.getPlayingTrack().setPosition(position);
    }

    public boolean isSpotifyEnabled()
    {
        return spotifyPlayback != null;
    }

    public boolean isSpotifyActive()
    {
        return spotifyPlayback != null && spotifyPlayback.isActive();
    }

    public boolean isSpotifyInput(String input)
    {
        return spotifyPlayback != null && SpotifyUrl.parse(input) != null;
    }

    public boolean hasActiveTrack()
    {
        return isSpotifyActive() || audioPlayer.getPlayingTrack() != null;
    }

    public boolean hasManagedQueue()
    {
        return !isSpotifyActive();
    }

    public SpotifyTrackDetails startSpotify(String input, RequestMetadata metadata) throws IOException
    {
        SpotifyUrl parsed = SpotifyUrl.parse(input);
        if(parsed == null)
            throw new IOException("Unsupported Spotify URL.");
        queue.clear();
        defaultQueue.clear();
        audioPlayer.stopTrack();
        votes.clear();
        SpotifyTrackDetails track = spotifyPlayback.play(parsed.toUri(), metadata);
        updateTrackTitle(track == null ? "Spotify" : track.getName());
        return track;
    }

    public String getSpotifyRequestedUri()
    {
        return spotifyPlayback == null ? null : spotifyPlayback.getRequestedUri();
    }

    public SpotifyStatus getSpotifyStatus()
    {
        return spotifyPlayback == null ? null : spotifyPlayback.getStatus();
    }

    public List<SpotifyTrackDetails> getSpotifyRecentTracks()
    {
        return spotifyPlayback == null ? new ArrayList<>() : spotifyPlayback.getRecentTracks();
    }

    public String getSpotifyRequestedUrl()
    {
        String uri = getSpotifyRequestedUri();
        return uri == null ? null : toOpenSpotifyUrl(uri);
    }

    public SpotifyContextSnapshot getSpotifyContextSnapshot()
    {
        return spotifyPlayback == null ? null : spotifyPlayback.getContextSnapshot();
    }

    public void destroy()
    {
        audioPlayer.destroy();
        if(spotifyPlayback != null)
            spotifyPlayback.shutdown();
    }
    
    
    // Private methods
    private Guild guild(JDA jda)
    {
        return jda.getGuildById(guildId);
    }

    private SpotifyTrackDetails getSpotifyTrack()
    {
        return spotifyPlayback == null ? null : spotifyPlayback.getTrack();
    }

    private MessageCreateData getSpotifyNowPlaying(JDA jda)
    {
        SpotifyTrackDetails track = getSpotifyTrack();
        if(track == null)
            return null;
        updateTrackTitle(track.getName());

        Guild guild = guild(jda);
        MessageCreateBuilder mb = new MessageCreateBuilder();
        mb.setContent(FormatUtil.filter(manager.getBot().getConfig().getSuccess()+" **Now Playing in "+guild.getSelfMember().getVoiceState().getChannel().getAsMention()+"...**"));
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(guild.getSelfMember().getColor());

        RequestMetadata rm = getRequestMetadata();
        if(rm.getOwner() != 0L)
        {
            User u = guild.getJDA().getUserById(rm.user.id);
            if(u==null)
                eb.setAuthor(FormatUtil.formatUsername(rm.user), null, rm.user.avatar);
            else
                eb.setAuthor(FormatUtil.formatUsername(u), null, u.getEffectiveAvatarUrl());
        }

        eb.setTitle(track.getName(), toOpenSpotifyUrl(track.getUri()));
        if(track.getAlbumCoverUrl() != null && !track.getAlbumCoverUrl().isBlank())
            eb.setThumbnail(track.getAlbumCoverUrl());
        if(track.getArtistsDisplay() != null && !track.getArtistsDisplay().isBlank())
            eb.setFooter("Source: " + track.getArtistsDisplay(), null);

        double progress = track.getDurationMs() <= 0 ? -1D : (double)getPosition() / track.getDurationMs();
        eb.setDescription(getStatusEmoji()
                + " " + FormatUtil.progressBar(progress)
                + " `[" + TimeUtil.formatTime(getPosition()) + "/" + TimeUtil.formatTime(track.getDurationMs()) + "]` "
                + FormatUtil.volumeIcon(getVolume()));
        return mb.setEmbeds(eb.build()).build();
    }

    private void updateTrackTitle(String title)
    {
        if((lastTrackTitle == null && title == null) || (lastTrackTitle != null && lastTrackTitle.equals(title)))
            return;
        lastTrackTitle = title;
        manager.getBot().getNowplayingHandler().onTrackUpdate(title);
    }

    private String toOpenSpotifyUrl(String spotifyUri)
    {
        if(spotifyUri == null || !spotifyUri.startsWith("spotify:"))
            return spotifyUri;
        String[] parts = spotifyUri.split(":");
        return parts.length == 3 ? "https://open.spotify.com/" + parts[1] + "/" + parts[2] : spotifyUri;
    }
}
