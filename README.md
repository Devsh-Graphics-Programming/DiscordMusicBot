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
2. This fork supports direct Spotify playback through a local `go-librespot` backend.
3. Spotify support is per-bot and can be enabled or disabled independently at startup.
4. If two bots use the same Spotify account, they will compete for the same Spotify Connect session.
5. For parallel Spotify playback on two bots, use two separate Spotify accounts.
6. If you want reliable playback for blocked or age-restricted videos, you should use a dedicated burner Google account.
7. Do not use your main Google account.
8. If the burner account cannot play a video in the normal YouTube browser UI, the bot will usually not be able to play it either.

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

## Spotify Direct Playback

This fork can play Spotify tracks, albums, and playlists directly without YouTube conversion.

### What this does

1. Spotify URLs are resolved as Spotify content, not mapped to YouTube.
2. Playback is done through a local `go-librespot` backend plus an `ffmpeg` bridge to Discord voice.
3. The feature is enabled per bot instance, not globally for the whole host.

### What this does not do

1. It does not use the official Spotify Web API for full audio playback.
2. It does not allow two bots on the same Spotify account to play different things at the same time.
3. It does not bypass normal Spotify account limits or Spotify Connect session rules.

### Requirements

1. Spotify playback is optional and disabled by default.
2. It requires the Docker image or runtime environment to start the bundled `go-librespot` sidecar.
3. It requires a Spotify Premium account for playback.
4. The bot instance must have persistent writable storage under its data directory.

### Integration overview

For one bot instance with Spotify enabled, the runtime does the following:
1. starts the Java bot process
2. starts a local `go-librespot` daemon
3. creates a local PCM pipe under the bot data directory
4. authenticates the selected Spotify account
5. uses that backend for Spotify URLs while keeping normal lavaplayer behavior for non-Spotify URLs

### Per-bot enable or disable

Spotify is controlled per bot instance through environment variables.

Example:

```env
SPOTIFY_ENABLED=true
SPOTIFY_DEVICE_NAME=devshmusic-test1-spotify
SPOTIFY_CALLBACK_PORT=0
```

If `SPOTIFY_ENABLED=false` or unset:
1. the Spotify sidecar is not started
2. direct Spotify playback is disabled for that bot
3. the bot continues to work as a normal YouTube/lavaplayer bot

This means you can run:
1. one bot with Spotify enabled
2. another bot with Spotify disabled
3. both at the same time on the same host

### Minimal bot env for Spotify

The smallest useful setup in `bot.env` is:

```env
BOT_TOKEN=replace_with_discord_bot_token
BOT_OWNER=replace_with_discord_owner_id
SPOTIFY_ENABLED=true
SPOTIFY_DEVICE_NAME=devshmusic-spotify-1
SPOTIFY_CALLBACK_PORT=0
```

Notes:
1. `SPOTIFY_DEVICE_NAME` should be unique per bot.
2. `SPOTIFY_CALLBACK_PORT=0` lets the backend choose a free local callback port automatically.
3. If Spotify is disabled for a bot, you can omit all Spotify variables entirely.

### First-time Spotify setup from scratch

When `SPOTIFY_ENABLED=true` and no prior Spotify state exists:
1. pick the bot instance that should get Spotify support
2. set `SPOTIFY_ENABLED=true` in that bot's `bot.env`
3. make sure the instance has its own persistent data directory
4. start or restart that bot container
5. watch the logs for the Spotify authorization URL
6. open the URL in a browser
7. log into the Spotify Premium account intended for that bot
8. finish the callback flow
9. let the bot finish initialization
10. test a Spotify track, album, or playlist URL on Discord

After that, restarts should reuse the stored Spotify session state.

### Where Spotify state is stored

For a bot instance running with `JMUSICBOT_HOME=/data`, the Spotify runtime stores:
1. Spotify auth and daemon state under `/data/spotify`
2. the PCM pipe under `/data/spotify.pipe`

This means:
1. deleting the instance data directory will force a fresh Spotify login
2. moving the instance to another host requires moving the bot's data volume as well

### Recommended deployment patterns

#### One bot with Spotify, one bot without Spotify

This is the cleanest production split.

Example:
1. `bot-a`: `SPOTIFY_ENABLED=true`
2. `bot-b`: `SPOTIFY_ENABLED=false`

Result:
1. `bot-a` can handle Spotify URLs and normal YouTube URLs
2. `bot-b` stays a normal non-Spotify bot
3. only one container pays the operational cost of the Spotify sidecar

#### Two bots with Spotify enabled

This is valid only if you understand the account model.

1. If both bots use the same Spotify account, they will fight over one Spotify Connect session.
2. If you want both bots to play Spotify independently, use two separate Spotify accounts.
3. Give each bot a unique `SPOTIFY_DEVICE_NAME`.

### Multiple bots and account isolation

1. Two bots can both have Spotify enabled.
2. If both bots use the same Spotify account, they will fight over one Spotify Connect session.
3. If you need two bots to play different Spotify content at the same time, use two separate Spotify accounts.

### Production checklist

Before enabling Spotify on a production bot:
1. verify the bot can still play a normal YouTube URL
2. verify the bot can play a Spotify track URL
3. verify the bot can play a Spotify playlist URL
4. verify `skip`, `pause`, `resume`, `seek`, `volume`, and `queue`
5. confirm the instance data directory is persistent
6. confirm no second bot is using the same Spotify account unless that is intentional

### Troubleshooting

#### The bot ignores Spotify URLs

Check:
1. `SPOTIFY_ENABLED=true` is actually present in that bot's runtime env
2. the bot instance was restarted after editing env
3. the Spotify sidecar started successfully in logs

#### The bot asks for Spotify login every restart

Check:
1. the bot is using persistent storage
2. `/data/spotify` is not being discarded between restarts
3. the container is not mounting a fresh empty instance directory

#### Two Spotify bots keep interrupting each other

Cause:
1. both bots are logged into the same Spotify account

Fix:
1. use one Spotify-enabled bot and one non-Spotify bot
2. or use two separate Spotify accounts

#### Spotify works but YouTube stops working

This should not happen by design. The fork keeps Spotify and lavaplayer on separate playback paths.
If this happens:
1. test a plain YouTube URL on the same bot
2. check bot logs for the failure path
3. verify the bot is not stuck in an active Spotify session that should first be stopped

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
