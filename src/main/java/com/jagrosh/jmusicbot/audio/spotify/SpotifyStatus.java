package com.jagrosh.jmusicbot.audio.spotify;

public class SpotifyStatus
{
    private final String username;
    private final String deviceId;
    private final String deviceName;
    private final String playOrigin;
    private final boolean stopped;
    private final boolean paused;
    private final boolean buffering;
    private final int volume;
    private final int volumeSteps;
    private final boolean repeatContext;
    private final boolean repeatTrack;
    private final boolean shuffleContext;
    private final SpotifyTrackDetails track;

    public SpotifyStatus(String username, String deviceId, String deviceName, String playOrigin,
                         boolean stopped, boolean paused, boolean buffering,
                         int volume, int volumeSteps,
                         boolean repeatContext, boolean repeatTrack, boolean shuffleContext,
                         SpotifyTrackDetails track)
    {
        this.username = username;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.playOrigin = playOrigin;
        this.stopped = stopped;
        this.paused = paused;
        this.buffering = buffering;
        this.volume = volume;
        this.volumeSteps = volumeSteps;
        this.repeatContext = repeatContext;
        this.repeatTrack = repeatTrack;
        this.shuffleContext = shuffleContext;
        this.track = track;
    }

    public String getUsername()
    {
        return username;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public String getDeviceName()
    {
        return deviceName;
    }

    public String getPlayOrigin()
    {
        return playOrigin;
    }

    public boolean isStopped()
    {
        return stopped;
    }

    public boolean isPaused()
    {
        return paused;
    }

    public boolean isBuffering()
    {
        return buffering;
    }

    public int getVolume()
    {
        return volume;
    }

    public int getVolumeSteps()
    {
        return volumeSteps;
    }

    public boolean isRepeatContext()
    {
        return repeatContext;
    }

    public boolean isRepeatTrack()
    {
        return repeatTrack;
    }

    public boolean isShuffleContext()
    {
        return shuffleContext;
    }

    public SpotifyTrackDetails getTrack()
    {
        return track;
    }
}
