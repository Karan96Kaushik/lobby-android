# Lobby

Lobby is an Android media browser. It opens a chosen site inside a locked-down WebView, plays that site’s own video player, and can save a finished HLS video for offline playback when the user turns downloads on for that site. The same app runs on phones, tablets, and Fire TV.

Package: `com.bayonetbarontech.lobby`. Version 0.9.1 (`versionCode` 1). Minimum Android 8.0 (API 26). Target and compile SDK 36.

## What the app does

- Lets the user pick a media source and stay on that source’s allowed hosts.
- Remembers the last page for each source and restores it on the next visit.
- Plays video in the site’s player. Fullscreen is the site’s fullscreen surface, drawn over the page.
- Optionally captures an HLS (`.m3u8`) playlist while a video is playing and downloads it for offline use.
- Plays a completed download in the app’s own player (Media3 ExoPlayer). Live playback never uses that player.
- Pairs a phone with a Fire TV (or another Lobby device) on the local network so the phone can drive the browser and the page player.
- Blocks pop-ups, known ad hosts, file downloads, and camera, microphone, location, and notification permission prompts.

Lobby is not a general web browser. A main-frame navigation that leaves the current source’s allowed hosts is refused. Subresources may load from other hosts unless that host is on the block list or the URL looks like an installer or archive.

## Sources

A source is a named home URL plus the hosts the browser may navigate to. Built-in sources ship with offline downloads off. The user can turn that switch on per source. Custom sources can be added, edited only by removing and adding again, and deleted. Built-in sources cannot be removed.

| Id | Name | Home | Allowed hosts |
| --- | --- | --- | --- |
| `archive` | Internet Archive | `https://archive.org/details/movies` | `archive.org` |

A host matches an allowed host when it is that host or a subdomain of it. `www.` is ignored when comparing hosts.

Adding a source:

- Name is optional. If it is blank, the host is used as the name.
- The address must be an `http` or `https` URL with a real host (a dot, no spaces, letters, digits, dots, and hyphens only). A missing scheme becomes `https://`.
- The stored home URL is always `https`, including path and query.
- The address is rejected when the host is on the ad-host block list, the host is already covered by an existing source, or the host is not a plausible domain.
- The custom source’s allowed host is the canonical host (`www.` removed).
- Offline downloads default to off unless the add-source switch is on.

The selected source id is stored in SharedPreferences (`lobby`). Custom sources and per-source download overrides are stored there too. Built-in download switches are overrides; they do not change the defaults in code until the user flips the switch.

No built-in source has a Fire TV entry script. The entry hook still exists and runs once, only on a first-party page, if a source id is added to it later.

## Phone and tablet

The app opens straight into the browser for the last selected source.

The top bar shows:

- The current source. Tapping it opens the source list.
- Back, forward, stop or reload, and home (the source’s home URL).
- Save, when offline downloads are on for this source and an HLS candidate has been seen. Save starts a download of that candidate.
- Downloads, Remote, Protection, and hide-controls.

A thin progress bar runs while the page loads. Pull to refresh reloads the page. A short notice appears for events such as a blocked popup, a blocked download, a connected remote, or a cleared session, then disappears after about 2.2 seconds.

If the main frame fails to load and no page URL is known yet, an overlay offers Home and Try Again. Messages cover being offline, a timeout, a rejected TLS handshake, and a generic load failure. `ERROR_UNKNOWN` is ignored.

Hiding the bar leaves a corner chip with the source logo and name. Tapping it shows the bar again.

Back goes to the previous page when one exists. Sheets and the remote screen close first.

### Source sheet

Lists built-in sources, then sources the user added. Tapping a row opens it and closes the sheet. Each row has an offline-download switch. A long-press toggles that switch. Custom rows can be deleted. Deleting the source that is open switches to the first remaining source and drops that source’s saved page.

Turning downloads on for the source that is already open reloads the page so the page script can start watching for playlists. Turning it off clears the current HLS candidate.

### Protection sheet

States that the browser is locked to the current source’s host, and lists the active limits: pop-ups stay closed, the page cannot leave that host, installers and file downloads are blocked, camera, microphone, and location stay off, known ad hosts are blocked, and offline save runs only when that site’s switch is on.

It shows two session counters: pop-ups stopped and downloads stopped. Those counters reset when the process restarts. They are not persisted.

Clear browsing data removes cookies, the WebView cache, history, web storage, and every saved page. The current source’s home then loads. A confirmation dialog precedes the clear. The sheet also warns against entering passwords or payment details.

### Remote sheet

See [Remote control](#remote-control). On a phone this is a full-screen control surface over the browser.

## Playback

While a site is open, video plays in the page. Lobby does not replace the site player with ExoPlayer.

Injected page script, installed at document start when the WebView supports it and again on each page start:

- Finds the page’s `<video>` element, including inside the fullscreen element and open shadow roots.
- Maps remote and keyboard commands onto that element: play/pause, mute, seek back and forward by 5 or 10 seconds, and fullscreen enter or exit.
- Keeps D-pad focus on the player shell while a video is the thing being controlled, so the Fire TV remote does not wander into controls hidden under the video.

Fullscreen uses `WebChromeClient.onShowCustomView`. The site’s fullscreen view is added to the activity decor and focused. Back or Escape leaves fullscreen. While it is showing, D-pad and select are delivered as remote commands into the page (`up`, `down`, `left`, `right`, `select`) instead of moving focus in the toolbar underneath. Media keys (play, pause, media fast-forward and rewind, volume mute) are also forwarded.

JavaScript dialogs (`alert`, `confirm`, `prompt`) are cancelled. Geolocation prompts are denied. `PermissionRequest` is denied. `window.open` for a first-party URL navigates the same WebView; anything else is counted as a blocked popup. Links and forms that target a new window are intercepted the same way. New WebView windows are not kept: a first-party popup URL is loaded in the main WebView, and any other popup is blocked and the temporary WebView is destroyed.

Media playback does not require a user gesture (`mediaPlaybackRequiresUserGesture` is false), so a remote play command can start the site player.

## Offline downloads

Offline save is off until the user enables it for that source. The page script reports playlist URLs only when that flag is on. The WebView’s own download listener always counts as a blocked download; files are not saved through the system download manager.

### Finding a stream

When downloads are enabled, the page script reports `.m3u8` URLs it sees from the video element (`currentSrc`, `src`, `<source>`), performance resource entries, and related hooks. Only `http` and `https` URLs are kept. A variant playlist does not replace a master playlist already captured for the page. Any other newly reported URL, including a master, replaces the current candidate. The candidate’s title follows the page title. Navigating, reloading, going home, or changing source clears it.

The toolbar Save button and the Downloads sheet both start a download of the current candidate. If downloads are on but no candidate exists, Lobby records a failed item (“No downloadable HLS stream was found”) and shows “No stream found on this page”. A download already queued, running, or paused for the same playlist URL is not started again.

### What can be saved

The downloader fetches the playlist and inspects it:

- The body must be an HLS playlist (`#EXTM3U`).
- A master playlist (`#EXT-X-STREAM-INF`) is reduced to one variant. The user’s quality cap keeps variants at or under that height, then picks the highest bandwidth. “Best” has no height cap. If every variant is taller than the cap, the shortest one is used. Nested masters are followed once; a third master fails.
- A media playlist must contain `#EXT-X-ENDLIST`. A playlist without it is treated as live and is not downloaded.
- Widevine, FairPlay, `SAMPLE-AES`, and `skd://` key URIs are rejected as DRM. A clear playlist can be saved. An AES-128 playlist (`#EXT-X-KEY` with an `http` or `https` key URI) is saved only after each key URL answers with a non-empty body. That check uses the same user agent, referer, and cookies as the playlist request. A key that cannot be fetched is treated as DRM.
- HTTP 403 is “access denied”. HTTP 401, 404, and 410 are an expired or missing link, and the user is told to retry from the page. Any other non-success status fails as a generic server error.
- Low disk space fails the item instead of filling the device.

Segments are downloaded on a foreground data-sync service with an ongoing notification (“Downloading N%”). The notification permission is requested on Android 13 and later when the user taps Save. The service is not exported.

Quality choices, stored with the download library: Best, 1080p, 720p, 480p. The choice applies to downloads started after it is set.

### Library

Each item stores an id, title, source, original playlist URL, page URL, resolved media URL, page origin, the WebView user agent used for requests, state, progress, byte count, created time, and an error message plus failure kind.

States: queued, downloading, paused, completed, failed, needs re-download.

Failure kinds and what the user can do:

| Kind | Retry the stored URL | Retry from the page |
| --- | --- | --- |
| Live stream | no | no |
| DRM | no | no |
| Expired link, HTTP 403, no candidate | no | yes, when a page URL was stored |
| No disk space, network lost, other | yes | no |

Opening the Downloads sheet checks the cache. A completed item whose files are gone becomes “needs re-download”. A download that was queued or running when the app died becomes “network lost”. If the files are present again, a needs-re-download item returns to completed.

Pause and resume apply to an in-progress item. Delete removes the row and its cached files. Play is available only for a completed item.

“Retry from page” switches to that item’s source if needed and loads the saved page, or the source home if the page is no longer first-party. The notice asks the user to play the video so a fresh link can be captured.

### Offline player

Play opens a black full-screen `PlayerView` backed by ExoPlayer. The media source reads from the on-disk HLS cache for that item, falling back to the original URL. Playback starts immediately. Close, or Back, leaves the player and releases it. This player is only for saved items. It is available on Fire TV through the same Downloads sheet once the user is inside a source.

Downloads persist in app storage and survive process death. Cache lives under the app files directory (`hls-cache`).

## Remote control

Two Lobby installs on the same LAN can pair. Fire TV starts hosting as soon as the home screen is shown. A phone can host from the remote screen or connect to a host it discovers.

Discovery uses UDP. The host announces its name, a stable device id, and the TCP port it is accepting on. The controller scans, lists peers, and prunes ones that stop announcing. A Wi-Fi multicast lock is held while discovery needs it.

Pairing uses a 4-digit PIN. The host shows the PIN. The controller enters it. If nobody is paired yet, five wrong PINs in a row rotate the host PIN. Regenerating the PIN drops the current connection, clears the paired roster, and issues a new PIN. The host PIN is remembered so the same TV does not change PIN on every launch.

After the PIN matches, the controller has a TCP session. One controller is in control at a time. Another paired device can request to take control. Paired devices are remembered (device id, name, and whether that device is the controller).

Commands the controller can send:

- D-pad: up, down, left, right, select
- Browser: back, forward, home, reload, stop
- Player: play, mute, fullscreen, exit fullscreen, seek ±5 seconds, seek ±10 seconds
- Page: page up, page down
- Chrome: hide bar, show bar
- A URL. The host loads it if it normalizes to `http` or `https`, is not a blocked ad host, and does not have a dangerous file extension. If the host belongs to a known source, that source becomes selected and remote-only hosts are cleared. Otherwise the host is added as a temporary navigation host for this visit so the locked browser can stay on that site until the user picks a real source again.

The phone remote screen is a touch pad plus those transport controls, a URL field, and the pairing flow (scan, pick a peer, enter PIN, linked state). On Fire TV the remote page is a D-pad list: allow hosting, pick a peer, enter a PIN, and a linked view. When a peer connects, Fire TV closes that page, shows a short “connected” line, and announces the peer once the user is inside the browser.

Device name comes from the system device name, cleaned for display. A random device id is stored in `lobby_remote` preferences.

## Fire TV

Lobby treats a device as Fire TV when it has the `amazon.hardware.fire_tv` feature, or the manufacturer is Amazon and the model starts with `AFT`.

On Fire TV the app opens on a home screen instead of the browser.

- A carousel of every source. Left and right change the selection. Select opens that source in the browser.
- A settings button (down from the carousel, or the gear). Settings can toggle offline downloads for the selected source, add a source, remove a custom source, and open the TV remote-pairing page.
- Hosting starts automatically. A successful pair shows the peer name for about five seconds.

Inside a source, the browser is the same as on a phone, with a wider toolbar and a horizontally scrolling tool row. The remote PIN line is hidden once a peer is connected, and also while the bar is hidden. Back from the first page of a source returns to the Fire TV home and saves the session. Back still leaves fullscreen, closes sheets, and closes the remote screen before that.

Opening a source from the home screen arms the one-shot entry script described under Sources.

Touch is not required (`android.hardware.touchscreen` is optional). Leanback is optional. The launcher intent is the standard `MAIN` / `LAUNCHER` category.

## Navigation policy

For a main-frame request, `about:` is allowed. `http` and `https` are allowed only when the host is first-party for the current source, plus any temporary host added by a remote URL. Anything else is blocked and counted as a popup when it was a main-frame navigation.

For a subresource, schemes other than `http`, `https`, `about`, `blob`, and `data` are blocked. A host on the ad and popup block list is intercepted and replaced with an empty response. That list covers common ad networks (for example pop ads, ExoClick, DoubleClick, Taboola, Outbrain, Criteo, Amazon ad system, and similar).

These path extensions are treated as dangerous and are never loaded or downloaded: `apk`, `ipa`, `exe`, `dmg`, `pkg`, `msi`, `bat`, `cmd`, `scr`, `zip`, `rar`, `7z`, `iso`, `torrent`, `mobileconfig`, `deb`, `rpm`, `jar`, `dll`, `bin`, `vbs`, `ps1`, `sh`, `crt`. A main-frame hit is blocked as a download. A subresource hit is emptied and counted as a blocked download.

Cleartext traffic is disabled for the application (`usesCleartextTraffic` is false). Third-party cookies are accepted so site players can load. Mixed content is never allowed. File and content URL access are off. Geolocation in WebSettings is off. Safe browsing is on from Android 8. Multiple windows are supported only so popup attempts can be caught and folded into the main WebView. JavaScript may not open windows on its own (`javaScriptCanOpenWindowsAutomatically` is false).

The page script also stubs `alert`, `prompt`, `confirm`, and `showModalDialog`, denies geolocation, forces notification permission to denied, and reports camera, microphone, geolocation, and notification permission queries as denied.

A session URL is saved only when it is a first-party `http` or `https` page without a dangerous extension.

## Sessions and storage

| Data | Where |
| --- | --- |
| Selected source, custom sources, download-switch overrides | SharedPreferences `lobby` |
| Last URL and title per source | `files/sessions/<sourceId>.json` |
| Download library | app files, managed by `HlsDownloadManager` |
| HLS segments and keys | `files/hls-cache` |
| Remote device id, host PIN, paired roster | SharedPreferences `lobby_remote` |
| Cookies and site storage | WebView cookie store and web storage |

Backup is disabled (`allowBackup` is false).

A session file is written when the page location changes to a keepable URL, when the user leaves the source, when the activity stops, and when the controller is cleared. Source ids used as file names must be short and made of letters, digits, and hyphens.

## Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` for browsing and downloads.
- `CHANGE_WIFI_MULTICAST_STATE` for LAN discovery.
- `POST_NOTIFICATIONS` for the download notification on Android 13 and later.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` for the download service.

No camera, microphone, location, or storage permission is requested. Offline video stays in app-private storage.

## Technical stack

- Kotlin, Jetpack Compose (Material 3), Activity Compose, ViewModel.
- A single `MainActivity` hosts `BrowserScreen`. Configuration changes (orientation, screen size, keyboard, UI mode) are handled by the activity so the WebView is not recreated for them.
- One `WebView` inside a `SwipeRefreshLayout`, created by `createLobbyWebView`.
- AndroidX WebKit for document-start JavaScript.
- Media3 ExoPlayer 1.5.1 with the HLS module, used for offline playback and HLS download.
- Coroutines for downloads, notices, and the remote sockets.

Release builds do not minify.

## Layout

```
app/src/main/java/com/bayonetbarontech/lobby/
  MainActivity.kt          activity, key dispatch into Fire TV and fullscreen
  LobbyApp.kt              process-wide download manager and remote hub
  BrowserController.kt     source, navigation, session, HLS candidate
  model/Models.kt          source, playlist role, download item
  data/SourceLibrary.kt    built-in and custom sources
  data/SessionStore.kt     per-source last page
  data/DownloadStore.kt    persisted download rows
  policy/SitePolicy.kt     host lock, ad hosts, dangerous extensions
  web/LobbyWebView.kt      WebView, request policy, fullscreen
  web/PageScripts.kt       hardening, remote keys, HLS reporting
  web/SourceBehaviors.kt   one-shot Fire TV entry actions
  ui/BrowserScreen.kt      toolbar, sheets host, offline player
  ui/FireTvHome.kt         carousel and Fire TV settings
  ui/FireTvRemotePage.kt   D-pad pairing on the TV
  ui/RemoteScreen.kt       phone remote
  ui/Sheets.kt             sources, protection, downloads
  remote/RemoteHub.kt      discovery, PIN, command channel
  download/HlsDownloadManager.kt
  download/PlaylistInspector.kt
```

## Behavior that is easy to misread

- The site player is the player for anything still on the network. ExoPlayer runs only after the user plays a completed download.
- The Save button does not download the WebView. It downloads the HLS playlist the page script reported.
- A variant does not overwrite a master already captured. Saving the stream means saving one chosen rendition, not every rendition.
- Live streams and DRM streams are detected and refused. They are not downloaded with encryption stripped.
- Fire TV does not have a separate player path. It has a different home screen, D-pad handling, and automatic remote hosting.
