<img align="right" src="https://i.imgur.com/zrE80HY.png" height="200" width="200">

# JMusicBot

[![Downloads](https://img.shields.io/github/downloads/jagrosh/MusicBot/total.svg)](https://github.com/jagrosh/MusicBot/releases/latest)
[![Stars](https://img.shields.io/github/stars/jagrosh/MusicBot.svg)](https://github.com/jagrosh/MusicBot/stargazers)
[![Release](https://img.shields.io/github/release/jagrosh/MusicBot.svg)](https://github.com/jagrosh/MusicBot/releases/latest)
[![License](https://img.shields.io/github/license/jagrosh/MusicBot.svg)](https://github.com/jagrosh/MusicBot/blob/master/LICENSE)
[![Discord](https://discordapp.com/api/guilds/147698382092238848/widget.png)](https://discord.gg/0p9LSGoRLu6Pet0k)<br>
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/jagrosh/MusicBot/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/jagrosh/MusicBot/tree/master)
[![Build and Test](https://github.com/jagrosh/MusicBot/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/jagrosh/MusicBot/actions/workflows/build-and-test.yml)
[![CodeFactor](https://www.codefactor.io/repository/github/jagrosh/musicbot/badge)](https://www.codefactor.io/repository/github/jagrosh/musicbot)

## DevSH Fork Notes

This repository is a maintained fork focused on keeping YouTube playback working after upstream stalled.

Important differences from the original project:
1. This fork supports YouTube OAuth playback fallback.
2. If you want reliable playback for blocked or age-restricted videos, you should use a dedicated burner Google account.
3. Do not use your main Google account.
4. If the burner account cannot play a video in the normal YouTube browser UI, the bot will usually not be able to play it either.

## YouTube OAuth Setup For This Fork

If you only care about unrestricted videos, the bot may still work without OAuth.
If you want restricted videos, age-gated videos, or better resilience against `This video requires login`, follow the full setup below.

### What you need

1. A Discord bot token as usual.
2. A separate Google burner account just for this bot.
3. That Google account must be able to play the target video directly on YouTube in a browser.

### Google account preparation

1. Create or pick a burner Google account.
2. Set the birthday on that account to 18+.
3. Complete any Google or YouTube age verification required for that account.
4. Open YouTube in a normal browser while signed into that burner account.
5. Confirm that the exact age-restricted video plays in the browser UI.

If step 5 fails, the bot is not ready yet. Fix the account first.

### Config values

This fork adds the following config keys:

```conf
ytoauth = true
ytoauthrefreshtoken = "YT_OAUTH_REFRESH_TOKEN_HERE"
```

When using OAuth, do not use a `poToken` at the same time.
Leave these at their placeholders:

```conf
ytpotoken = "PO_TOKEN_HERE"
ytvisitordata = "VISITOR_DATA_HERE"
```

### First-time OAuth setup

1. Open your `config.txt`.
2. Set `ytoauth = true`.
3. Leave `ytoauthrefreshtoken = "YT_OAUTH_REFRESH_TOKEN_HERE"` for the first run.
4. Make sure `ytpotoken` and `ytvisitordata` are still placeholders.
5. Start the bot.
6. Watch the bot logs.
7. The bot will print a message telling you to go to `https://www.google.com/device` and enter a code.
8. Open that URL in a browser.
9. Sign in with the burner Google account.
10. Enter the device code shown in the bot logs.
11. Approve the connection.
12. Wait for the bot logs to print the refresh token.

The log line will look similar to this:

```text
OAUTH INTEGRATION: Token retrieved successfully. Store your refresh token as this can be reused. (your_refresh_token_here)
```

### Persisting the refresh token

1. Stop the bot.
2. Open `config.txt`.
3. Replace the placeholder with the real refresh token:

```conf
ytoauth = true
ytoauthrefreshtoken = "paste_the_refresh_token_here"
```

4. Save the file.
5. Start the bot again.
6. Check the logs for:

```text
YouTube access token refreshed successfully
```

At that point the token is persisted and future restarts should not require the device flow again unless Google invalidates the token.

### Verifying playback

1. Join a voice channel.
2. Use the bot to play a normal YouTube video.
3. Use the bot to play an age-restricted YouTube video.
4. If the browser account can play the video but the bot cannot, check the bot logs for the current playability reason.

### Troubleshooting

1. `This video requires login`
   This usually means OAuth is not enabled, the refresh token is missing, the refresh token is invalid, or the account is not sufficiently verified.
2. `Sign in to confirm your age`
   The burner account still does not pass YouTube age verification for that video.
3. The video works in Discord for some links but not others
   The failing video may be private, region-blocked, removed, or age-gated beyond what the account currently allows.
4. The device flow appears again after restart
   The refresh token was not copied into `config.txt` correctly or Google invalidated it.

## Local Docker Multi-Bot Test

This fork can be tested locally with multiple isolated bot containers.

Recommended local layout:
1. One container per bot token.
2. One data directory per bot under `docker/instances/<bot-name>`.
3. One generated compose file for all active local test bots.

Quick start:
1. Create a bot scaffold:
   `powershell -ExecutionPolicy Bypass -File scripts/add-bot.ps1 devshmusic-test1`
2. Copy or edit `docker/instances/devshmusic-test1/bot.env`.
3. Repeat for more bots.
4. Regenerate compose:
   `powershell -ExecutionPolicy Bypass -File scripts/render-compose.ps1`
5. Start all bots:
   `docker compose -f docker-compose.generated.yml up -d --build`

Container-specific behavior:
1. `BOT_TOKEN` and `BOT_OWNER` can be provided from `bot.env`.
2. Relative state paths are resolved from `JMUSICBOT_HOME`, which defaults to `/data` in the container.
3. Each bot keeps its own `config.txt`, `serversettings.json`, and `Playlists/` under its own instance directory.

## GHCR Container Publishing

GitHub Actions now builds the Docker image from this repository and publishes it to GHCR.

Published image:
1. `ghcr.io/devsh-graphics-programming/discordmusicbot:latest` from `master`
2. `ghcr.io/devsh-graphics-programming/discordmusicbot:sha-<commit>` for immutable rollbacks
3. `ghcr.io/devsh-graphics-programming/discordmusicbot:<version>` for matching Git tags

Recommended deployment flow:
1. Build and verify locally first.
2. Push to `master` when ready.
3. On the target host run `docker compose pull`.
4. Then run `docker compose up -d`.

That lets consumers update by pulling a new image instead of rebuilding the jar on the server.

A cross-platform Discord music bot with a clean interface, and that is easy to set up and run yourself!

[![Setup](http://i.imgur.com/VvXYp5j.png)](https://jmusicbot.com/setup)

## Features
  * Easy to run (just make sure Java is installed, and run!)
  * Fast loading of songs
  * No external keys needed (besides a Discord Bot token)
  * Smooth playback
  * Server-specific setup for the "DJ" role that can moderate the music
  * Clean and beautiful menus
  * Supports many sites, including Youtube, Soundcloud, and more
  * Supports many online radio/streams
  * Supports local files
  * Playlist support (both web/youtube, and local)

## Supported sources and formats
JMusicBot supports all sources and formats supported by [lavaplayer](https://github.com/sedmelluq/lavaplayer#supported-formats):
### Sources
  * YouTube
  * SoundCloud
  * Bandcamp
  * Vimeo
  * Twitch streams
  * Local files
  * HTTP URLs
### Formats
  * MP3
  * FLAC
  * WAV
  * Matroska/WebM (AAC, Opus or Vorbis codecs)
  * MP4/M4A (AAC codec)
  * OGG streams (Opus, Vorbis and FLAC codecs)
  * AAC streams
  * Stream playlists (M3U and PLS)

## Example
![Loading Example...](https://i.imgur.com/kVtTKvS.gif)

## Setup
Please see the [Setup Page](https://jmusicbot.com/setup) to run this bot yourself!

## Questions/Suggestions/Bug Reports
**Please read the [Issues List](https://github.com/jagrosh/MusicBot/issues) before suggesting a feature**. If you have a question, need troubleshooting help, or want to brainstorm a new feature, please start a [Discussion](https://github.com/jagrosh/MusicBot/discussions). If you'd like to suggest a feature or report a reproducible bug, please open an [Issue](https://github.com/jagrosh/MusicBot/issues) on this repository. If you like this bot, be sure to add a star to the libraries that make this possible: [**JDA**](https://github.com/DV8FromTheWorld/JDA) and [**lavaplayer**](https://github.com/sedmelluq/lavaplayer)!

## Editing
This bot (and the source code here) might not be easy to edit for inexperienced programmers. The main purpose of having the source public is to show the capabilities of the libraries, to allow others to understand how the bot works, and to allow those knowledgeable about java, JDA, and Discord bot development to contribute. There are many requirements and dependencies required to edit and compile it, and there will not be support provided for people looking to make changes on their own. Instead, consider making a feature request (see the above section). If you choose to make edits, please do so in accordance with the Apache 2.0 License.
