package com.bayonetbarontech.lobby.web

import org.json.JSONArray
import org.json.JSONObject

/**
 * Behaviors that belong to one media source.
 *
 * [onFireTvEntry] runs once, on the first page loaded after that source is
 * opened from the Fire TV home screen. Later pages in the same visit do not
 * run it again. Add another source by putting its id in the map.
 */
object SourceBehaviors {
    private val onFireTvEntry: Map<String, List<EntryAction>> = emptyMap()

    fun fireTvEntryScript(sourceId: String): String? {
        val actions = onFireTvEntry[sourceId].orEmpty()
        if (actions.isEmpty()) return null
        val payload = JSONArray()
        actions.forEach { action ->
            when (action) {
                is EntryAction.ClickButton -> payload.put(
                    JSONObject()
                        .put("type", "clickButton")
                        .put("text", action.text)
                )
            }
        }
        return runner.replace("__ACTIONS__", payload.toString())
    }

    private val runner = """
        (function () {
          var actions = __ACTIONS__;
          var start = location.href;
          var tries = 0;
          var pending = actions.slice();
          function norm(value) {
            return String(value || "").replace(/\s+/g, " ").trim();
          }
          function label(el) {
            return norm(el.innerText || el.textContent || el.value || (el.getAttribute && el.getAttribute("aria-label")) || "");
          }
          function clickMatch(root, text) {
            if (!root || !root.querySelectorAll) return false;
            var nodes = root.querySelectorAll("button, a, [role='button'], input[type='button'], input[type='submit']");
            for (var i = 0; i < nodes.length; i++) {
              if (label(nodes[i]) !== text) continue;
              try { nodes[i].click(); } catch (e) { return false; }
              return true;
            }
            var all = root.querySelectorAll("*");
            for (var j = 0; j < all.length; j++) {
              if (all[j].shadowRoot && clickMatch(all[j].shadowRoot, text)) return true;
            }
            return false;
          }
          function run() {
            var left = [];
            for (var i = 0; i < pending.length; i++) {
              var action = pending[i];
              if (action.type === "clickButton" && !clickMatch(document, norm(action.text))) left.push(action);
            }
            pending = left;
            return pending.length === 0;
          }
          if (run()) return;
          var timer = setInterval(function () {
            tries++;
            if (location.href !== start || tries > 40) {
              clearInterval(timer);
              return;
            }
            if (run()) clearInterval(timer);
          }, 200);
        })();
    """.trimIndent()
}

sealed interface EntryAction {
    data class ClickButton(val text: String) : EntryAction
}
