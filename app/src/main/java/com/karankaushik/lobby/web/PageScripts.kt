package com.karankaushik.lobby.web

/**
 * Hardening always runs. HLS capture installs its observers only when
 * [LobbyJsBridge.downloadsEnabled] is true for the source currently selected.
 */
object PageScripts {
    fun source(): String = """
        (function () {
          function allowedHosts() {
            try { return JSON.parse(window.LobbyBridge.allowedHosts()); }
            catch (e) { return []; }
          }
          function downloadsEnabled() {
            try { return window.LobbyBridge && window.LobbyBridge.downloadsEnabled() === true; }
            catch (e) { return false; }
          }
          function postPopup(kind) {
            try { window.LobbyBridge.postPopup(kind); } catch (e) {}
          }
          function isFirstParty(host) {
            host = (host || "").toLowerCase();
            if (host.indexOf("www.") === 0) host = host.slice(4);
            var hosts = allowedHosts();
            for (var i = 0; i < hosts.length; i++) {
              var base = String(hosts[i] || "").toLowerCase();
              if (host === base || host.endsWith("." + base)) return true;
            }
            return false;
          }

          if (!window.__lobbyHardeningInstalled) {
            window.__lobbyHardeningInstalled = true;
            try { localStorage.setItem("addies", "false"); } catch (e) {}
            try {
              window.open = function (url) {
                try {
                  if (url) {
                    var dest = new URL(url, location.href);
                    if ((dest.protocol === "https:" || dest.protocol === "http:") && isFirstParty(dest.hostname)) {
                      location.assign(dest.href);
                      return null;
                    }
                  }
                } catch (e) {}
                postPopup("open");
                return null;
              };
            } catch (e) {}
            try { window.showModalDialog = function () { return null; }; } catch (e) {}
            try { window.alert = function () {}; } catch (e) {}
            try { window.prompt = function () { return null; }; } catch (e) {}
            try { window.confirm = function () { return false; }; } catch (e) {}

            document.addEventListener("click", function (event) {
              var node = event.target && event.target.closest ? event.target.closest("a[target],area[target]") : null;
              if (!node) return;
              var target = (node.getAttribute("target") || "").toLowerCase();
              if (target !== "_blank" && target !== "_new" && target !== "blank") return;
              event.preventDefault();
              event.stopPropagation();
              var href = node.href || "";
              try {
                var dest = new URL(href, location.href);
                if ((dest.protocol === "https:" || dest.protocol === "http:") && isFirstParty(dest.hostname)) {
                  location.assign(dest.href);
                  return;
                }
              } catch (e) {}
              postPopup("link");
            }, true);

            document.addEventListener("submit", function (event) {
              var form = event.target;
              if (!form || !form.getAttribute) return;
              var target = (form.getAttribute("target") || "").toLowerCase();
              if (target === "_blank" || target === "_new") {
                event.preventDefault();
                event.stopPropagation();
                postPopup("form");
              }
            }, true);

            try {
              var geo = navigator.geolocation;
              if (geo) {
                var deny = function (_ok, err) {
                  if (typeof err === "function") err({ code: 1, message: "denied", PERMISSION_DENIED: 1 });
                };
                geo.getCurrentPosition = deny;
                geo.watchPosition = function (_ok, err) { deny(null, err); return 0; };
                geo.clearWatch = function () {};
              }
            } catch (e) {}
            try {
              if (window.Notification) {
                Notification.requestPermission = function () { return Promise.resolve("denied"); };
              }
            } catch (e) {}
            try {
              if (navigator.permissions && navigator.permissions.query) {
                var query = navigator.permissions.query.bind(navigator.permissions);
                navigator.permissions.query = function (desc) {
                  var name = desc && desc.name;
                  if (name === "camera" || name === "microphone" || name === "geolocation" || name === "notifications") {
                    return Promise.resolve({ state: "denied", addEventListener: function () {}, removeEventListener: function () {} });
                  }
                  return query(desc);
                };
              }
            } catch (e) {}
          }

          if (!window.__lobbyRemoteInstalled) {
            window.__lobbyRemoteInstalled = true;
            function findLocalVideo() {
              var fs = document.fullscreenElement || document.webkitFullscreenElement;
              if (fs) {
                if ((fs.tagName || "").toUpperCase() === "VIDEO") return fs;
                if (fs.querySelector) {
                  var inside = fs.querySelector("video");
                  if (inside) return inside;
                }
              }
              var best = null;
              var bestScore = -1;
              function scan(root) {
                if (!root || !root.querySelectorAll) return;
                var vids = root.querySelectorAll("video");
                for (var i = 0; i < vids.length; i++) {
                  var v = vids[i];
                  var r = v.getBoundingClientRect ? v.getBoundingClientRect() : { width: 0, height: 0 };
                  var score = Math.max(0, r.width) * Math.max(0, r.height) + ((!v.paused && !v.ended) ? 1e9 : 0) + (v.readyState > 0 ? 1e6 : 0);
                  if (score > bestScore) { best = v; bestScore = score; }
                }
                var all = root.querySelectorAll("*");
                for (var j = 0; j < all.length; j++) {
                  if (all[j].shadowRoot) scan(all[j].shadowRoot);
                }
              }
              scan(document);
              return best;
            }
            function broadcastCommand(cmd, value) {
              var msg = { __lobbyMediaCmd: cmd, value: value };
              var frames = document.getElementsByTagName("iframe");
              for (var i = 0; i < frames.length; i++) {
                try { frames[i].contentWindow.postMessage(msg, "*"); } catch (e) {}
              }
            }
            function applyMediaCommand(cmd, value) {
              var v = findLocalVideo();
              if (v && (v.readyState > 0 || v.currentSrc || v.src)) {
                try {
                  if (cmd === "seek" && isFinite(v.currentTime)) {
                    var next = v.currentTime + Number(value || 0);
                    if (isFinite(v.duration) && v.duration > 0) {
                      next = Math.min(Math.max(0, next), Math.max(0, v.duration - 0.25));
                    } else {
                      next = Math.max(0, next);
                    }
                    v.currentTime = next;
                  } else if (cmd === "play" || (cmd === "toggle" && v.paused)) {
                    var p = v.play();
                    if (p && p.catch) p.catch(function () {});
                  } else if (cmd === "pause" || cmd === "toggle") {
                    v.pause();
                  } else if (cmd === "mute") {
                    v.muted = !v.muted;
                  }
                } catch (e) {}
              }
              broadcastCommand(cmd, value);
            }
            function parentEl(node) {
              if (!node) return null;
              if (node.parentElement) return node.parentElement;
              var root = node.getRootNode && node.getRootNode();
              if (root && root.host) return root.host;
              return null;
            }
            function playerShell(video) {
              var node = parentEl(video);
              for (var i = 0; i < 6 && node && node !== document.body && node !== document.documentElement; i++) {
                if (node.querySelector && node.querySelector("button, [role='button'], [role='slider']")) return node;
                node = parentEl(node);
              }
              return video;
            }
            function enterFullscreen() {
              var fs = document.fullscreenElement || document.webkitFullscreenElement;
              if (fs) return;
              var v = findLocalVideo();
              var target = v ? playerShell(v) : null;
              var entered = false;
              if (target) {
                try {
                  if (target.requestFullscreen) { target.requestFullscreen(); entered = true; }
                  else if (target.webkitRequestFullscreen) { target.webkitRequestFullscreen(); entered = true; }
                } catch (e) {}
              }
              if (!entered) broadcastCommand("fullscreen", 0);
            }
            function exitFullscreen() {
              var fs = document.fullscreenElement || document.webkitFullscreenElement;
              if (!fs) return;
              try {
                if (document.exitFullscreen) document.exitFullscreen();
                else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
              } catch (e) {}
              broadcastCommand("exitfs", 0);
            }
            function toggleFullscreen() {
              var fs = document.fullscreenElement || document.webkitFullscreenElement;
              if (fs) exitFullscreen();
              else enterFullscreen();
            }
            function changeVolume(delta) {
              var v = findLocalVideo();
              if (v && (v.readyState > 0 || v.currentSrc || v.src)) {
                var next = Math.min(1, Math.max(0, Number(v.volume) + Number(delta || 0)));
                v.volume = next;
                v.muted = next === 0;
              }
              broadcastCommand("volume", delta);
            }
            function revealControls(root) {
              if (!root) return;
              try {
                var box = root.getBoundingClientRect();
                root.dispatchEvent(new MouseEvent("mousemove", {
                  bubbles: true,
                  cancelable: true,
                  clientX: box.left + box.width / 2,
                  clientY: box.top + Math.max(8, box.height - 24)
                }));
              } catch (e) {}
              if (!root.querySelectorAll) return;
              var bars = root.querySelectorAll(".vjs-user-inactive, .jw-flag-user-inactive, .plyr--hide-controls");
              for (var i = 0; i < bars.length; i++) {
                bars[i].classList.remove("vjs-user-inactive", "jw-flag-user-inactive", "plyr--hide-controls");
                bars[i].classList.add("vjs-user-active", "jw-flag-user-active");
              }
            }
            function controlButtons(root) {
              var found = [];
              var selector = "button, a[href], [role='button'], [role='slider'], [role='menuitem'], input, select";
              function walk(node) {
                if (!node || !node.querySelectorAll) return;
                var nodes = node.querySelectorAll(selector);
                for (var i = 0; i < nodes.length; i++) {
                  var el = nodes[i];
                  if (el.disabled) continue;
                  if ((el.tagName || "").toUpperCase() === "VIDEO") continue;
                  var tab = el.getAttribute && el.getAttribute("tabindex");
                  if (tab === "-1") continue;
                  var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                  if (!rect || rect.width < 8 || rect.height < 8) continue;
                  var style = window.getComputedStyle(el);
                  if (style.display === "none" || style.visibility === "hidden") continue;
                  found.push(el);
                }
                var all = node.querySelectorAll("*");
                for (var j = 0; j < all.length; j++) {
                  if (all[j].shadowRoot) walk(all[j].shadowRoot);
                }
              }
              walk(root);
              return found;
            }
            function markFocused(el) {
              var previous = window.__lobbyFocused;
              if (previous && previous !== el) previous.style.outline = window.__lobbyFocusedOutline || "";
              window.__lobbyFocused = el;
              window.__lobbyFocusedOutline = el.style.outline;
              el.style.outline = "3px solid #95FF50";
              el.style.outlineOffset = "2px";
              try { el.focus(); } catch (e) {}
            }
            function moveFocus(root, dir) {
              revealControls(root);
              var items = controlButtons(root);
              if (!items.length) return false;
              var current = document.activeElement;
              var index = items.indexOf(current);
              if (index < 0 && window.__lobbyFocused && items.indexOf(window.__lobbyFocused) >= 0) {
                current = window.__lobbyFocused;
                index = items.indexOf(current);
              }
              if (index < 0) {
                markFocused(items[0]);
                return true;
              }
              var rect = current.getBoundingClientRect();
              var cx = rect.left + rect.width / 2;
              var cy = rect.top + rect.height / 2;
              var best = null;
              var bestScore = Infinity;
              for (var i = 0; i < items.length; i++) {
                var el = items[i];
                if (el === current) continue;
                var next = el.getBoundingClientRect();
                var dx = (next.left + next.width / 2) - cx;
                var dy = (next.top + next.height / 2) - cy;
                var along = dir === "left" ? -dx : dir === "right" ? dx : dir === "up" ? -dy : dy;
                var across = dir === "left" || dir === "right" ? Math.abs(dy) : Math.abs(dx);
                if (along < 8) continue;
                var score = along + across * 3;
                if (score < bestScore) { bestScore = score; best = el; }
              }
              if (!best) return true;
              markFocused(best);
              return true;
            }
            function activateFocused() {
              var el = window.__lobbyFocused || document.activeElement;
              if (!el || el === document.body || el === document.documentElement) return false;
              if ((el.tagName || "").toUpperCase() === "VIDEO") return false;
              try { el.click(); } catch (e) { return false; }
              return true;
            }
            function navigateUi(dir) {
              var fs = document.fullscreenElement || document.webkitFullscreenElement;
              if (!fs) return;
              if ((fs.tagName || "").toUpperCase() === "IFRAME") {
                broadcastCommand("nav", dir);
                return;
              }
              if (!moveFocus(fs, dir)) broadcastCommand("nav", dir);
            }
            function runRemoteKey(name) {
              if (name === "left" || name === "right" || name === "up" || name === "down") navigateUi(name);
              else if (name === "select") {
                if (!activateFocused()) broadcastCommand("activate", 0);
              }
              else if (name === "play") applyMediaCommand("toggle", 0);
              else if (name === "resume") applyMediaCommand("play", 0);
              else if (name === "pause") applyMediaCommand("pause", 0);
              else if (name === "seekback") applyMediaCommand("seek", -10);
              else if (name === "seekfwd") applyMediaCommand("seek", 10);
              else if (name === "mute") applyMediaCommand("mute", 0);
            }
            window.__lobbySeek = function (delta) { applyMediaCommand("seek", delta); };
            window.__lobbyTransport = function (mode) { applyMediaCommand(mode, 0); };
            window.__lobbyMute = function () { applyMediaCommand("mute", 0); };
            window.__lobbyFullscreen = toggleFullscreen;
            window.__lobbyEnterFullscreen = enterFullscreen;
            window.__lobbyExitFullscreen = exitFullscreen;
            window.__lobbyRemoteKey = function (name) { runRemoteKey(String(name || "")); };
            function fullscreenShortcut(e) {
              var k = e.key || "";
              var c = e.code || "";
              var kc = e.keyCode || 0;
              var exit = k === "F10" || c === "F10" || kc === 121 || kc === 140;
              var enter = k === "F11" || c === "F11" || kc === 122 || kc === 141;
              if (!exit && !enter) return;
              if (e.repeat) return;
              e.preventDefault();
              e.stopPropagation();
              if (e.type !== "keydown") return;
              if (exit) exitFullscreen();
              else enterFullscreen();
            }
            window.addEventListener("keydown", fullscreenShortcut, true);
            window.addEventListener("keyup", fullscreenShortcut, true);
            window.addEventListener("message", function (e) {
              var d = e && e.data;
              if (!d || typeof d !== "object" || !d.__lobbyMediaCmd) return;
              if (d.__lobbyMediaCmd === "fullscreen") {
                enterFullscreen();
                return;
              }
              if (d.__lobbyMediaCmd === "exitfs") {
                try {
                  if (document.exitFullscreen) document.exitFullscreen();
                  else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
                } catch (err) {}
                return;
              }
              if (d.__lobbyMediaCmd === "volume") {
                var clip = findLocalVideo();
                if (clip && (clip.readyState > 0 || clip.currentSrc || clip.src)) {
                  var level = Math.min(1, Math.max(0, Number(clip.volume) + Number(d.value || 0)));
                  clip.volume = level;
                  clip.muted = level === 0;
                }
                return;
              }
              if (d.__lobbyMediaCmd === "nav") {
                moveFocus(document.body, d.value);
                return;
              }
              if (d.__lobbyMediaCmd === "activate") {
                activateFocused();
                return;
              }
              applyMediaCommand(d.__lobbyMediaCmd, d.value);
            });

            function isEditable(el) {
              if (!el) return false;
              var tag = (el.tagName || "").toUpperCase();
              return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || !!el.isContentEditable;
            }
            function inFullscreen() {
              return !!(document.fullscreenElement || document.webkitFullscreenElement);
            }
            function onKey(e) {
              var k = e.key || "";
              var kc = e.keyCode || 0;
              if (k === "MediaRewind" || kc === 227) {
                e.preventDefault();
                e.stopImmediatePropagation();
                if (e.type === "keydown") applyMediaCommand("seek", -10);
                return;
              }
              if (k === "MediaFastForward" || kc === 228) {
                e.preventDefault();
                e.stopImmediatePropagation();
                if (e.type === "keydown") applyMediaCommand("seek", 10);
                return;
              }
              if (k === "MediaPlayPause" || kc === 179) {
                e.preventDefault();
                e.stopImmediatePropagation();
                if (e.type === "keydown" && !e.repeat) applyMediaCommand("toggle", 0);
                return;
              }
              var isArrow = k === "ArrowLeft" || k === "ArrowRight" || k === "ArrowUp" || k === "ArrowDown" || kc === 37 || kc === 38 || kc === 39 || kc === 40;
              if (isArrow && inFullscreen() && !isEditable(e.target)) {
                e.preventDefault();
                e.stopImmediatePropagation();
                if (e.type !== "keydown" || e.repeat) return;
                var dir = (k === "ArrowLeft" || kc === 37) ? "left" : (k === "ArrowRight" || kc === 39) ? "right" : (k === "ArrowUp" || kc === 38) ? "up" : "down";
                navigateUi(dir);
                return;
              }
              var isArrowH = k === "ArrowLeft" || k === "ArrowRight" || kc === 37 || kc === 39;
              if (isArrowH && !inFullscreen() && !isEditable(e.target)) {
                e.stopImmediatePropagation();
              }
            }
            window.addEventListener("keydown", onKey, true);
            window.addEventListener("keyup", onKey, true);
            window.addEventListener("keypress", onKey, true);
          }

          if (!downloadsEnabled()) return;
          if (window.__lobbyHLSCaptureInstalled) return;
          window.__lobbyHLSCaptureInstalled = true;

          var bestCandidateURL = null;
          var bestCandidateKind = "unknown";
          var lastSentURL = null;
          var lastSentTitle = null;

          function isM3U8(urlString) {
            if (!urlString || typeof urlString !== "string") return false;
            if (urlString.indexOf("blob:") === 0 || urlString.indexOf("data:") === 0) return false;
            try {
              var parsed = new URL(urlString, document.baseURI || location.href);
              if (parsed.protocol !== "https:" && parsed.protocol !== "http:") return false;
              var path = (parsed.pathname || "").toLowerCase();
              var search = (parsed.search || "").toLowerCase();
              if (path.endsWith(".m3u8") || path.indexOf(".m3u8/") !== -1) return true;
              if (/(\.m3u8)(${'$'}|[?&/#])/i.test(parsed.href)) return true;
              if (search.indexOf(".m3u8") !== -1) return true;
            } catch (e) {}
            return false;
          }
          function resolveURL(raw) {
            try { return new URL(raw, document.baseURI || location.href).href; }
            catch (e) { return null; }
          }
          function classifyPlaylistURL(urlString) {
            try {
              var parsed = new URL(urlString);
              var lower = (parsed.pathname + parsed.search).toLowerCase();
              if (/(^|[\/_\-.])(master|manifest|multivariant)([\/_\-.]|${'$'})/i.test(lower)) return "master";
              if (/(chunklist|rendition|variant|prog_index|index_[0-9]+|stream_[0-9]+|[\/_\-](240|360|480|540|720|1080|1440|2160)p?[\/_\-.]|audio|subtitles|captions)/i.test(lower)) return "variant";
            } catch (e) {}
            return "unknown";
          }
          function currentTitle() {
            try {
              if (window === window.top && document.title) return String(document.title).trim();
            } catch (e) {}
            return String(document.title || "").trim();
          }
          function reportCandidate(rawURL, originSource) {
            if (!downloadsEnabled()) return;
            if (!rawURL || typeof rawURL !== "string") return;
            var resolved = resolveURL(rawURL.trim());
            if (!resolved || !isM3U8(resolved)) return;
            var incomingKind = classifyPlaylistURL(resolved);
            if (bestCandidateURL && bestCandidateKind === "master" && incomingKind === "variant") return;
            bestCandidateURL = resolved;
            bestCandidateKind = incomingKind;
            var title = currentTitle();
            if (resolved === lastSentURL && title === lastSentTitle) return;
            lastSentURL = resolved;
            lastSentTitle = title;
            try {
              window.LobbyBridge.postHls(JSON.stringify({
                url: resolved,
                kind: incomingKind,
                source: originSource || "unknown",
                title: title,
                pageURL: String(location.href || ""),
                isMainFrame: (window === window.top)
              }));
            } catch (e) {}
          }
          function scanVideos() {
            if (!downloadsEnabled()) return;
            try {
              var videos = document.querySelectorAll("video");
              for (var i = 0; i < videos.length; i++) {
                var v = videos[i];
                if (v.currentSrc) reportCandidate(v.currentSrc, "video.currentSrc");
                if (v.src) reportCandidate(v.src, "video.src");
                var sources = v.querySelectorAll("source[src]");
                for (var j = 0; j < sources.length; j++) {
                  var s = sources[j].getAttribute("src");
                  if (s) reportCandidate(s, "video.source");
                }
              }
            } catch (e) {}
          }
          function scanPerformanceEntries(entries) {
            if (!entries || !entries.length) return;
            for (var i = 0; i < entries.length; i++) {
              var entry = entries[i];
              if (entry && entry.name) reportCandidate(entry.name, "performance");
            }
          }
          try {
            if (window.performance && typeof window.performance.getEntriesByType === "function") {
              scanPerformanceEntries(window.performance.getEntriesByType("resource"));
            }
          } catch (e) {}
          try {
            if (typeof PerformanceObserver === "function") {
              var observer = new PerformanceObserver(function (list) {
                try { scanPerformanceEntries(list.getEntries()); } catch (e) {}
              });
              try { observer.observe({ type: "resource", buffered: true }); }
              catch (e) { try { observer.observe({ entryTypes: ["resource"] }); } catch (e2) {} }
            }
          } catch (e) {}
          document.addEventListener("loadedmetadata", scanVideos, true);
          document.addEventListener("play", scanVideos, true);
          setInterval(scanVideos, 1500);
          scanVideos();
        })();
    """.trimIndent()
}
