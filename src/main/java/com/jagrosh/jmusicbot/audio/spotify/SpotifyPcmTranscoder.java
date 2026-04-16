package com.jagrosh.jmusicbot.audio.spotify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class SpotifyPcmTranscoder
{
    private static final int FRAME_SIZE = 3840;
    private static final int MAX_FRAMES = 64;

    private final String ffmpegPath;
    private final String pipePath;
    private final BlockingDeque<byte[]> frames = new LinkedBlockingDeque<>(MAX_FRAMES);

    private volatile Process process;
    private volatile Thread readerThread;

    public SpotifyPcmTranscoder(String ffmpegPath, String pipePath)
    {
        this.ffmpegPath = ffmpegPath;
        this.pipePath = pipePath;
    }

    public synchronized void ensureStarted() throws IOException
    {
        if(process != null && process.isAlive())
            return;
        stop();
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-nostdin");
        command.add("-f");
        command.add("s16le");
        command.add("-ar");
        command.add("44100");
        command.add("-ac");
        command.add("2");
        command.add("-i");
        command.add(pipePath);
        command.add("-f");
        command.add("s16be");
        command.add("-ar");
        command.add("48000");
        command.add("-ac");
        command.add("2");
        command.add("pipe:1");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = processBuilder.start();
        readerThread = new Thread(this::readLoop, "spotify-ffmpeg-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public boolean canProvide()
    {
        return !frames.isEmpty();
    }

    public ByteBuffer provide()
    {
        byte[] frame = frames.pollFirst();
        return frame == null ? null : ByteBuffer.wrap(frame);
    }

    public synchronized void stop()
    {
        frames.clear();
        if(process != null)
        {
            process.destroy();
            process = null;
        }
        if(readerThread != null)
        {
            readerThread.interrupt();
            readerThread = null;
        }
    }

    private void readLoop()
    {
        byte[] remainder = new byte[0];
        byte[] buffer = new byte[8192];
        try(InputStream inputStream = process.getInputStream())
        {
            int read;
            while((read = inputStream.read(buffer)) >= 0)
            {
                if(read == 0)
                    continue;
                byte[] combined = new byte[remainder.length + read];
                System.arraycopy(remainder, 0, combined, 0, remainder.length);
                System.arraycopy(buffer, 0, combined, remainder.length, read);

                int offset = 0;
                while(combined.length - offset >= FRAME_SIZE)
                {
                    byte[] frame = new byte[FRAME_SIZE];
                    System.arraycopy(combined, offset, frame, 0, FRAME_SIZE);
                    if(!offerFrame(frame))
                        return;
                    offset += FRAME_SIZE;
                }

                int remaining = combined.length - offset;
                remainder = new byte[remaining];
                System.arraycopy(combined, offset, remainder, 0, remaining);
            }
        }
        catch(IOException ignore)
        {
        }
    }

    private boolean offerFrame(byte[] frame)
    {
        try
        {
            frames.putLast(frame);
            return true;
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
