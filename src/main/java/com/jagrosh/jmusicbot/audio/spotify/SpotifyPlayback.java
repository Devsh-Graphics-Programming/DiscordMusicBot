package com.jagrosh.jmusicbot.audio.spotify;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class SpotifyPlayback
{
    private static final int MAX_RECENT_TRACKS = 8;

    private final SpotifyClient client;
    private final SpotifyPcmTranscoder transcoder;
    private final Deque<SpotifyTrackDetails> recentTracks = new ArrayDeque<>();

    private volatile boolean active;
    private volatile SpotifyContextSnapshot contextSnapshot;
    private volatile String requestedUri;
    private volatile RequestMetadata requestMetadata = RequestMetadata.EMPTY;
    private volatile SpotifyStatus status;
    private volatile long positionMs;
    private volatile long positionAnchorNanos;
    private volatile boolean locallyPaused;

    public SpotifyPlayback(BotConfig config)
    {
        this.client = new SpotifyClient(config.getSpotifyApiUrl());
        this.transcoder = new SpotifyPcmTranscoder(config.getSpotifyFfmpegPath(), config.getSpotifyPipePath());
    }

    public SpotifyTrackDetails play(String spotifyUri, RequestMetadata metadata) throws IOException
    {
        transcoder.ensureStarted();
        client.play(spotifyUri);
        synchronized(recentTracks)
        {
            recentTracks.clear();
        }
        try
        {
            contextSnapshot = SpotifyContextResolver.resolve(spotifyUri);
        }
        catch(IOException ex)
        {
            contextSnapshot = null;
        }
        requestedUri = spotifyUri;
        requestMetadata = metadata == null ? RequestMetadata.EMPTY : metadata;
        active = true;
        locallyPaused = false;
        positionMs = 0L;
        positionAnchorNanos = System.nanoTime();
        status = waitForTrack();
        syncTrackChange(status);
        return getTrack();
    }

    public void pause() throws IOException
    {
        if(!active)
            return;
        positionMs = getPosition();
        client.pause();
        locallyPaused = true;
        refreshStatus();
    }

    public void resume() throws IOException
    {
        if(!active)
            return;
        client.resume();
        locallyPaused = false;
        positionAnchorNanos = System.nanoTime();
        refreshStatus();
    }

    public void next() throws IOException
    {
        if(!active)
            return;
        client.next();
        positionMs = 0L;
        positionAnchorNanos = System.nanoTime();
        refreshStatus();
    }

    public void seek(long position) throws IOException
    {
        if(!active)
            return;
        client.seek(position);
        positionMs = position;
        positionAnchorNanos = System.nanoTime();
        refreshStatus();
    }

    public void setVolume(int volume) throws IOException
    {
        client.setVolume(volume);
        refreshStatus();
    }

    public RepeatMode setRepeatMode(RepeatMode mode) throws IOException
    {
        if(!active)
            return RepeatMode.OFF;
        switch(mode)
        {
            case SINGLE:
                client.setRepeatContext(false);
                client.setRepeatTrack(true);
                break;
            case ALL:
                client.setRepeatTrack(false);
                client.setRepeatContext(true);
                break;
            case OFF:
            default:
                client.setRepeatTrack(false);
                client.setRepeatContext(false);
                break;
        }
        return currentRepeatMode();
    }

    public boolean setShuffleContext(boolean shuffle) throws IOException
    {
        if(!active)
            return false;
        client.setShuffleContext(shuffle);
        SpotifyStatus current = refreshStatus();
        return current != null && current.isShuffleContext();
    }

    public void stop() throws IOException
    {
        if(active)
            client.pause();
        transcoder.stop();
        active = false;
        locallyPaused = false;
        requestedUri = null;
        requestMetadata = RequestMetadata.EMPTY;
        positionMs = 0L;
        positionAnchorNanos = 0L;
        status = null;
        contextSnapshot = null;
        synchronized(recentTracks)
        {
            recentTracks.clear();
        }
    }

    public void shutdown()
    {
        try
        {
            stop();
        }
        catch(IOException ignore)
        {
        }
        transcoder.stop();
    }

    public boolean isActive()
    {
        return active;
    }

    public boolean isPaused()
    {
        if(!active)
            return false;
        SpotifyStatus current = refreshStatus();
        return locallyPaused || (current != null && current.isPaused());
    }

    public boolean canProvide()
    {
        return active && !locallyPaused && transcoder.canProvide();
    }

    public ByteBuffer provide()
    {
        return transcoder.provide();
    }

    public SpotifyStatus refreshStatus()
    {
        try
        {
            SpotifyStatus remote = client.getStatus();
            syncTrackChange(remote);
            if(remote.isStopped())
                active = false;
            this.status = remote;
            return remote;
        }
        catch(IOException ex)
        {
            return status;
        }
    }

    public SpotifyTrackDetails getTrack()
    {
        SpotifyStatus current = refreshStatus();
        return current == null ? null : current.getTrack();
    }

    public String getRequestedUri()
    {
        return requestedUri;
    }

    public SpotifyContextSnapshot getContextSnapshot()
    {
        return contextSnapshot;
    }

    public SpotifyStatus getStatus()
    {
        return refreshStatus();
    }

    public RepeatMode currentRepeatMode()
    {
        SpotifyStatus current = refreshStatus();
        if(current == null)
            return RepeatMode.OFF;
        if(current.isRepeatTrack())
            return RepeatMode.SINGLE;
        if(current.isRepeatContext())
            return RepeatMode.ALL;
        return RepeatMode.OFF;
    }

    public List<SpotifyTrackDetails> getRecentTracks()
    {
        synchronized(recentTracks)
        {
            return new ArrayList<>(recentTracks);
        }
    }

    public RequestMetadata getRequestMetadata()
    {
        return requestMetadata;
    }

    public int getVolume()
    {
        SpotifyStatus current = refreshStatus();
        return current == null ? 100 : current.getVolume();
    }

    public long getDuration()
    {
        SpotifyTrackDetails track = getTrack();
        return track == null ? 0L : track.getDurationMs();
    }

    public long getPosition()
    {
        if(!active)
            return 0L;
        long duration = getDuration();
        if(isPaused())
            return Math.min(positionMs, duration);
        long elapsed = (System.nanoTime() - positionAnchorNanos) / 1_000_000L;
        return Math.min(positionMs + elapsed, duration);
    }

    private SpotifyStatus waitForTrack() throws IOException
    {
        IOException last = null;
        for(int i = 0; i < 40; i++)
        {
            try
            {
                SpotifyStatus remote = client.getStatus();
                if(remote.getTrack() != null && !remote.isStopped())
                    return remote;
            }
            catch(IOException ex)
            {
                last = ex;
            }

            try
            {
                Thread.sleep(250L);
            }
            catch(InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for Spotify playback", ex);
            }
        }

        if(last != null)
            throw last;
        throw new IOException("Timed out waiting for Spotify playback to start");
    }

    private void syncTrackChange(SpotifyStatus remote)
    {
        if(remote == null)
            return;

        SpotifyTrackDetails previous = status == null ? null : status.getTrack();
        SpotifyTrackDetails current = remote.getTrack();
        if(current != null && (previous == null || !current.equals(previous)))
        {
            positionMs = 0L;
            positionAnchorNanos = System.nanoTime();
            synchronized(recentTracks)
            {
                SpotifyTrackDetails last = recentTracks.peekLast();
                if(last == null || !current.equals(last))
                {
                    recentTracks.addLast(current);
                    while(recentTracks.size() > MAX_RECENT_TRACKS)
                        recentTracks.removeFirst();
                }
            }
        }

        boolean remotePaused = remote.isPaused();
        if(remotePaused != locallyPaused)
        {
            if(remotePaused)
                positionMs = positionMs + ((System.nanoTime() - positionAnchorNanos) / 1_000_000L);
            else
                positionAnchorNanos = System.nanoTime();
            locallyPaused = remotePaused;
        }
    }
}
