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
