(() => {
  const p = (name, type, desc, optional) => ({ name, type, desc, optional: !!optional });
  const r = (type, desc) => ({ type, desc });
  const m = (name, sig, desc, extra = {}) => ({ kind: "method", name, sig, desc, ...extra });
  const prop = (name, type, desc, extra = {}) => ({ kind: "prop", name, type, desc, ...extra });

  const lua = (src) => src.replace(/^\n/, "").replace(/\n$/, "");

  window.LUNA_DOCS = {
    package: "luna",
    apiLevel: 1,
    guides: [
      {
        id: "welcome",
        title: "Welcome",
        html: `
          <p class="lead">The Lunas DPI plugin API is a sandboxed Lua surface. It looks a lot like discord.js: one big <a href="#Client"><code>Client</code></a>, managers, builders, events, and a local REST facade. It does <em>not</em> talk to Discord, and it cannot leave the app.</p>
          <div class="grid-2">
            <a class="card" href="#package" style="color:inherit;text-decoration:none">
              <h3>Create a package</h3>
              <p>ZIP a folder with <code>manifest.json</code> and <code>main.lua</code>, then import it from Settings → Plugins.</p>
            </a>
            <a class="card" href="#Client" style="color:inherit;text-decoration:none">
              <h3>Read the Client</h3>
              <p>Almost everything hangs off <code>luna.Client</code>. Dot or colon both work: <code>client.trim(x)</code> / <code>client:trim(x)</code>.</p>
            </a>
            <a class="card" href="#permissions" style="color:inherit;text-decoration:none">
              <h3>Ask for intents</h3>
              <p>Permissions are granted when the user enables the plugin. Missing one throws <code>Permission denied: …</code>.</p>
            </a>
            <a class="card" href="#sandbox" style="color:inherit;text-decoration:none">
              <h3>Stay in the sandbox</h3>
              <p>No <code>io</code>, <code>os</code>, Java, TUN, or arbitrary HTTP. Each Lua call has a 1.5s budget.</p>
            </a>
          </div>
          <h2 class="section-title">Minimal plugin</h2>
          <div class="code"><pre><span class="cm">-- main.lua</span>
<span class="kw">local</span> <span class="fn">client</span> = <span class="fn">luna</span>.Client

<span class="fn">client</span>:on(<span class="fn">luna</span>.Events.Ready, <span class="kw">function</span>()
  <span class="fn">client</span>.logInfo(<span class="st">"ready "</span> .. <span class="fn">client</span>.tag())
<span class="kw">end</span>)

<span class="kw">function</span> on_enable()
  <span class="fn">client</span>.logInfo(<span class="st">"enabled"</span>)
<span class="kw">end</span>

<span class="kw">function</span> on_disable()
  <span class="fn">client</span>.logInfo(<span class="st">"disabled"</span>)
<span class="kw">end</span></pre></div>
          <div class="callout">
            <h4>What this API is for</h4>
            <p>Extend Lunas DPI with rule packs, a hosts overlay, a settings page, counters, storage, and notifications. There is no door for packet injection, TLS interception, shell, or reading other apps.</p>
          </div>
          <h2 class="section-title">Global <code>luna</code></h2>
          <table>
            <thead><tr><th>Name</th><th>Kind</th><th>Description</th></tr></thead>
            <tbody>
              <tr><td><code>luna.Client</code> / <code>luna.client</code></td><td><a href="#Client">Client</a></td><td>The runtime. Already constructed.</td></tr>
              <tr><td><code>luna.User</code> / <code>luna.user</code></td><td><a href="#User">User</a></td><td>This plugin's identity.</td></tr>
              <tr><td><code>luna.Intents</code> / <code>luna.permissions</code></td><td><a href="#Permissions">Permissions</a></td><td>Granted permission flags.</td></tr>
              <tr><td><code>luna.Events</code></td><td><a href="#Events">Events</a></td><td>Event name constants.</td></tr>
              <tr><td><code>luna.events</code></td><td><a href="#EventEmitter">EventEmitter</a></td><td><code>on</code> / <code>once</code> / <code>off</code>.</td></tr>
              <tr><td><code>luna.REST</code></td><td><a href="#REST">REST</a></td><td>Local get/put/post/patch/delete.</td></tr>
              <tr><td><code>luna.API_LEVEL</code></td><td>number</td><td>Currently <code>1</code>. Same as <code>luna.version</code>.</td></tr>
            </tbody>
          </table>
        `,
      },
      {
        id: "package",
        title: "Packages",
        html: `
          <p class="lead">A plugin is a ZIP. Put <code>manifest.json</code> at the archive root or one folder down (<code>my-plugin/manifest.json</code>).</p>
          <h2 class="section-title">Tree</h2>
          <div class="code"><pre>my-plugin/
  manifest.json          required
  main.lua               required entry
  settings.lua           optional settings page
  modules/               luna require("name") → modules/name.lua
    helper.lua
  locale/
    en.json
    tr.json
  assets/
    icon.svg             or icon.png (PNG ≤ 256 KB, 1024 px)
  README.md</pre></div>
          <h2 class="section-title">Limits</h2>
          <table>
            <thead><tr><th>Limit</th><th>Value</th></tr></thead>
            <tbody>
              <tr><td>ZIP / uncompressed</td><td>2 MB</td></tr>
              <tr><td>Files</td><td>64</td></tr>
              <tr><td>Each Lua source</td><td>128 KB, text only — bytecode (<code>\\x1bLua</code>) is rejected</td></tr>
              <tr><td>Allowed extensions</td><td><code>lua</code>, <code>json</code>, <code>svg</code>, <code>png</code>, <code>md</code>, <code>txt</code></td></tr>
              <tr><td>Installed plugins</td><td>24</td></tr>
              <tr><td>Enabled at once</td><td>8</td></tr>
            </tbody>
          </table>
          <h2 class="section-title">Install</h2>
          <p><strong>Settings → Plugins → Import ZIP</strong>. Review the package, install it, then <strong>enable</strong> it. Permissions are granted at enable time. If <code>settings.lua</code> exists, the detail screen shows <em>Open plugin settings</em>.</p>
          <div class="callout warn">
            <h4>require</h4>
            <p><code>require("helper")</code> loads <code>modules/helper.lua</code> only. Dots, slashes, and <code>..</code> are invalid. Modules are cached for that VM.</p>
          </div>
        `,
      },
      {
        id: "manifest",
        title: "Manifest",
        html: `
          <p class="lead"><code>manifest.json</code> is the plugin's identity. <code>api_level</code> must be <strong>1</strong>.</p>
          <div class="code"><pre>{
  "id": "community.focus.list",
  "name": "Focus list",
  "author": "Your name",
  "version": "1.0.0",
  "description": "Short description (max 280 characters).",
  "api_level": 1,
  "min_app_version": "1.0.0",
  "main": "main.lua",
  "settings": "settings.lua",
  "icon": "assets/icon.svg",
  "homepage": "https://github.com/you/your-plugin",
  "permissions": ["storage", "ui.settings", "rules.write"]
}</pre></div>
          <table>
            <thead><tr><th>Field</th><th>Notes</th></tr></thead>
            <tbody>
              <tr><td><code>id</code></td><td>Lowercase reverse-domain. <code>com.lunasdev.*</code> and <code>luna</code> are reserved.</td></tr>
              <tr><td><code>homepage</code></td><td>Only <code>https://github.com/…</code>. The app never downloads code from it; Settings just shows the link.</td></tr>
              <tr><td><code>settings</code></td><td>If present, <code>ui.settings</code> is added automatically.</td></tr>
              <tr><td><code>permissions</code></td><td>See <a href="#permissions">Permissions</a>. <code>rules.write</code> implies <code>rules.read</code>.</td></tr>
            </tbody>
          </table>
        `,
      },
      {
        id: "permissions",
        title: "Permissions",
        html: `
          <p class="lead">Permissions are discord.js-style intents: declared in the manifest, confirmed by the user, then readable at runtime via <a href="#Permissions"><code>luna.permissions</code></a> or <code>client:hasPermission("storage")</code>.</p>
          <table>
            <thead><tr><th>Key</th><th>Grants</th><th>Does not grant</th></tr></thead>
            <tbody>
              <tr><td><code>storage</code></td><td>64 keys, 32 KB per value, plugin-private</td><td>Other plugins, app settings</td></tr>
              <tr><td><code>ui.settings</code></td><td><code>settings_page()</code> in-app screen</td><td>Arbitrary Compose / WebView</td></tr>
              <tr><td><code>rules.read</code></td><td>Rules prefixed <code>p:{id}:</code></td><td>Discord / user rules</td></tr>
              <tr><td><code>rules.write</code></td><td>Up to 16 own rules, 32 domains each</td><td>Overwriting built-in rules</td></tr>
              <tr><td><code>vpn.read</code></td><td>Phase + coarse counters</td><td>Packet payloads, DNS answers</td></tr>
              <tr><td><code>vpn.control</code></td><td>Start / stop local protection (15s throttle)</td><td>Remote VPN, writing the TUN</td></tr>
              <tr><td><code>notify</code></td><td>Short notification (30s gap, 8 / hour)</td><td>Fullscreen, custom channels</td></tr>
              <tr><td><code>hosts.write</code></td><td>Local DNS overlay while protection is on (IPv4, 256 names)</td><td>System <code>/etc/hosts</code>, IPv6, TUN range <code>10.7.0.0/24</code></td></tr>
              <tr><td><code>app.read</code></td><td>Coarse protection settings (mode, DNS, MTU)</td><td>App list, other plugins, writes</td></tr>
            </tbody>
          </table>
          <div class="callout">
            <h4>Checking at runtime</h4>
            <p>Unknown keys return false. Missing a granted permission throws from the manager, it does not silently no-op.</p>
          </div>
          <div class="code"><pre>if client:hasPermission("hosts.write") then
  client.setHostsText("127.0.0.1 blocked.example")
end</pre></div>
        `,
      },
      {
        id: "lifecycle",
        title: "Lifecycle",
        html: `
          <p class="lead">The host loads <code>main.lua</code> (and <code>settings.lua</code> if present), then calls hooks. Listeners on <a href="#Events"><code>luna.Events</code></a> fire in addition to the named functions.</p>
          <table>
            <thead><tr><th>Hook</th><th>When</th></tr></thead>
            <tbody>
              <tr><td><code>on_enable()</code></td><td>Plugin turned on, after scripts load</td></tr>
              <tr><td><code>on_disable()</code></td><td>Turned off or uninstalled</td></tr>
              <tr><td><code>settings_page()</code></td><td>Settings screen opened / refreshed — must return a page table</td></tr>
              <tr><td><code>on_setting_changed(id, value)</code></td><td>User changed a control. Switch/checkbox → boolean, text/select → string, slider/number → number, button → <code>true</code></td></tr>
              <tr><td><code>on_vpn_phase(phase)</code></td><td>Local protection phase changed (optional)</td></tr>
            </tbody>
          </table>
          <p>Plugin-owned rules are removed when the plugin is disabled or uninstalled. Hosts overlay is cleared the same way.</p>
          <h2 class="section-title">Timers</h2>
          <p>At most <strong>4</strong> timers. Delay is clamped to <strong>2s–120s</strong>. Use <code>client.setTimeout</code> / <code>setInterval</code> or <code>luna.clock</code>.</p>
        `,
      },
      {
        id: "sandbox",
        title: "Sandbox",
        html: `
          <p class="lead">The VM is LuaJ with dangerous libraries stripped. Think of it as a sealed worker, not a phone OS.</p>
          <h2 class="section-title">Removed</h2>
          <p><code>io</code>, <code>os</code>, <code>debug</code>, <code>package</code>, <code>load</code>, <code>loadstring</code>, <code>loadfile</code>, <code>dofile</code>, <code>collectgarbage</code>, <code>module</code>, <code>java</code>, <code>luajava</code>, <code>newproxy</code>, <code>string.dump</code>.</p>
          <h2 class="section-title">Kept</h2>
          <p>Base, bit32, table, string, math, coroutines. <code>print</code> forwards to <code>luna.log.info</code> (500 chars). <code>require</code> is replaced.</p>
          <h2 class="section-title">Budgets</h2>
          <table>
            <thead><tr><th>Budget</th><th>Value</th></tr></thead>
            <tbody>
              <tr><td>Each Lua call</td><td>1.5 seconds or <code>Plugin exceeded the 1.5s time budget.</code></td></tr>
              <tr><td>Log line</td><td>500 characters</td></tr>
              <tr><td>Notification</td><td>Title 40, body 120; 30s cooldown; 8 per hour</td></tr>
              <tr><td>VPN start/stop</td><td>Once per 15 seconds</td></tr>
            </tbody>
          </table>
          <div class="callout danger">
            <h4>Intentionally absent</h4>
            <p>Raw packets, TUN writes, DPI strategy mutation, TLS/MITM, <code>Runtime.exec</code>, JNI, clipboard, accessibility, fetching code from GitHub, other plugins' storage/rules/hosts.</p>
          </div>
        `,
      },
      {
        id: "rest-guide",
        title: "REST style",
        html: `
          <p class="lead"><a href="#REST"><code>luna.REST</code></a> (also <code>client.rest</code>) is a local router. Paths are not HTTP — they never leave the process.</p>
          <table>
            <thead><tr><th>Method</th><th>Path</th><th>Maps to</th></tr></thead>
            <tbody>
              <tr><td>GET</td><td><code>/me</code> or <code>/user</code></td><td>plugin identity JSON</td></tr>
              <tr><td>GET</td><td><code>/vpn</code>, <code>/vpn/snapshot</code></td><td>VPN snapshot</td></tr>
              <tr><td>GET</td><td><code>/vpn/phase</code></td><td>phase string</td></tr>
              <tr><td>GET</td><td><code>/rules</code>, <code>/rules/{id}</code></td><td>list / get</td></tr>
              <tr><td>GET</td><td><code>/hosts</code>, <code>/hosts/{host}</code></td><td>list / get</td></tr>
              <tr><td>GET</td><td><code>/app</code>, <code>/app/mode</code>, …</td><td>app config fields</td></tr>
              <tr><td>GET</td><td><code>/storage</code>, <code>/storage/{key}</code></td><td>keys / get</td></tr>
              <tr><td>GET</td><td><code>/permissions</code></td><td>granted keys</td></tr>
              <tr><td>PUT</td><td><code>/hosts</code></td><td>set table or hosts text</td></tr>
              <tr><td>PUT</td><td><code>/storage/{key}</code></td><td>set</td></tr>
              <tr><td>PUT</td><td><code>/rules</code></td><td>upsert</td></tr>
              <tr><td>POST</td><td><code>/rules</code></td><td>create</td></tr>
              <tr><td>POST</td><td><code>/hosts</code></td><td>set table</td></tr>
              <tr><td>POST</td><td><code>/notify</code></td><td><code>{ title, text }</code> or a string body</td></tr>
              <tr><td>POST</td><td><code>/vpn</code></td><td>body <code>"start"</code> / <code>"stop"</code></td></tr>
              <tr><td>PATCH</td><td><code>/rules/{id}</code></td><td>edit</td></tr>
              <tr><td>DELETE</td><td><code>/rules</code>, <code>/rules/{id}</code></td><td>clear / delete</td></tr>
              <tr><td>DELETE</td><td><code>/hosts</code>, <code>/hosts/{host}</code></td><td>clear / remove</td></tr>
              <tr><td>DELETE</td><td><code>/storage/{key}</code></td><td>remove</td></tr>
            </tbody>
          </table>
          <div class="code"><pre>client.rest.get("vpn")
client.rest.put("hosts", "127.0.0.1 example.com")
client.rest.post("vpn", "start")
client.rest.delete("rules/focus")</pre></div>
        `,
      },
    ],
    classes: [
      {
        id: "Client",
        name: "Client",
        kind: "class",
        summary: "The plugin runtime. luna.Client and luna.client are the same table — already constructed. Nested managers (user, rules, vpn, …) are copied onto it, plus ~240 convenience methods.",
        construct: "Do not construct Client. Use the global luna.Client (alias luna.client).",
        constructExample: "local client = luna.Client\nclient.logInfo(client.tag())",
        notes: `<div class="callout"><h4>Calling convention</h4><p>Methods accept both dot and colon. <code>LuaFn</code> strips an extra first argument when it is the client table, so <code>client:trim(" x ")</code> and <code>client.trim(" x ")</code> are equivalent.</p></div>`,
        props: [
          prop("id", "string", "Manifest plugin id."),
          prop("username", "string", "Plugin display name (same as displayName)."),
          prop("displayName", "string", "Plugin display name."),
          prop("author", "string", "Manifest author."),
          prop("version", "string", "Manifest version."),
          prop("tag", "string", "`Name@version`."),
          prop("locale", "string", "Device language, e.g. `en`."),
          prop("apiLevel", "number", "Always `1` for this app."),
          prop("pluginId", "string", "Same as `id`."),
          prop("readyAt", "number", "Unix seconds when this Client table was built."),
          prop("isReady", "boolean", "Always `true` after load."),
          prop("application", "table", "`{ name = \"Lunas DPI\", version, id = \"com.lunasdev.lunasdpi\" }`."),
          prop("options", "table", "Sandbox caps: `max_rules` 16, `max_hosts` 256, `max_timers` 4, `min_timer_ms` 2000, `max_timer_ms` 120000, `max_storage_keys` 64, `max_storage_chars` 32768, `sandbox` true."),
          prop("rest", "REST", "Local REST facade. Also exported as `luna.REST`."),
          prop("user", "User", "Nested identity manager."),
          prop("permissions", "Permissions", "Nested intents."),
          prop("events", "EventEmitter", "Nested event bus."),
          prop("storage", "StorageManager", "Nested key-value store."),
          prop("rules", "RulesManager", "Nested rule manager."),
          prop("hosts", "HostsManager", "Nested hosts overlay."),
          prop("vpn", "VPNManager", "Nested VPN manager."),
          prop("app", "AppManager", "Nested app-config reader."),
          prop("ui", "UI", "Settings DSL."),
          prop("notify", "NotifyManager", "Notification helpers."),
          prop("clock", "Clock", "Time and timers."),
          prop("log", "Log", "Plugin log."),
          prop("i18n", "I18n", "Locale strings from `locale/*.json`."),
        ],
        sections: [
          {
            title: "Events",
            members: [
              m("on", "on(event, listener)", "Subscribe. `event` is `ready` or `vpnPhase` (see Events).", {
                params: [p("event", "string", "Event name."), p("listener", "function", "Callback. `vpnPhase` receives the phase string.")],
                returns: r("boolean", "Always true."),
                aliases: ["addListener", "prependListener"],
                examples: [lua(`client:on(luna.Events.VpnPhase, function(phase)
  if phase == "connected" then client.notifyInfo("DPI", "Protection is on") end
end)`)],
              }),
              m("once", "once(event, listener)", "Subscribe for a single fire.", {
                params: [p("event", "string", "Event name."), p("listener", "function", "Callback.")],
                returns: r("boolean", "Always true."),
              }),
              m("off", "off(event, listener?)", "Remove one listener, or all for that event if listener is omitted.", {
                params: [p("event", "string", "Event name."), p("listener", "function", "Listener to drop.", true)],
                aliases: ["removeListener"],
              }),
              m("removeAllListeners", "removeAllListeners(event)", "Drop every listener for `event`.", {
                params: [p("event", "string", "Event name.")],
              }),
              m("listenerCount", "listenerCount(event)", "How many listeners are registered.", {
                params: [p("event", "string", "Event name.")],
                returns: r("number", "Count."),
              }),
              m("eventNames", "eventNames()", "Names that currently have listeners.", { returns: r("string[]", "Event names.") }),
              m("emit", "emit(event)", "Host-side emit of a named event (no payload). Prefer listening over emitting.", {
                params: [p("event", "string", "Event name.")],
              }),
            ],
          },
          {
            title: "Permissions",
            members: [
              m("hasPermission", "hasPermission(permission)", "Whether the user granted this key.", {
                params: [p("permission", "string", "Manifest key such as `storage`.")],
                returns: r("boolean", "False for unknown keys."),
                aliases: ["hasIntent"],
              }),
              m("missingPermission", "missingPermission(permission)", "Inverse of hasPermission. Unknown keys are missing.", {
                params: [p("permission", "string", "Manifest key.")],
                returns: r("boolean", "True if not granted."),
              }),
              m("anyPermission", "anyPermission(keys)", "True if any listed key is granted.", {
                params: [p("keys", "string[]", "Up to 16 keys.")],
                returns: r("boolean", ""),
              }),
              m("allPermissions", "allPermissions(keys)", "True if the list is non-empty and every key is granted.", {
                params: [p("keys", "string[]", "Up to 16 keys.")],
                returns: r("boolean", ""),
              }),
              m("grantedPermissions", "grantedPermissions()", "Granted keys as an array.", { returns: r("string[]", "") }),
              m("intentsBitfield", "intentsBitfield()", "Bitmask of granted PluginPermission ordinals.", { returns: r("number", "") }),
              m("permissionFlags", "permissionFlags()", "The PermissionFlagsBits table.", { returns: r("table", "String constants.") }),
            ],
          },
          {
            title: "Storage",
            members: [
              m("storeGet", "storeGet(key)", "Read a string, or nil.", { perm: "storage", params: [p("key", "string", "")], returns: r("string|nil", "") }),
              m("storeSet", "storeSet(key, value)", "Write a string. Max 32 768 characters.", { perm: "storage", params: [p("key", "string", ""), p("value", "string", "")], returns: r("boolean", "") }),
              m("storeDelete", "storeDelete(key)", "Remove a key.", { perm: "storage", params: [p("key", "string", "")] }),
              m("storeHas", "storeHas(key)", "Whether the key exists.", { perm: "storage", params: [p("key", "string", "")], returns: r("boolean", "") }),
              m("storeKeys", "storeKeys()", "All keys (max 64).", { perm: "storage", returns: r("string[]", "") }),
              m("storeSize", "storeSize()", "Key count.", { perm: "storage", returns: r("number", "") }),
              m("storeGetNumber", "storeGetNumber(key)", "Parse stored text as a number.", { perm: "storage", params: [p("key", "string", "")], returns: r("number|nil", "") }),
              m("storeSetNumber", "storeSetNumber(key, value)", "Store `tostring(value)`.", { perm: "storage", params: [p("key", "string", ""), p("value", "number", "")] }),
              m("storeGetBool", "storeGetBool(key)", "`1`/`true`/`yes` → true, `0`/`false`/`no` → false, else nil.", { perm: "storage", params: [p("key", "string", "")], returns: r("boolean|nil", "") }),
              m("storeSetBool", "storeSetBool(key, value)", "Stores `1` or `0`.", { perm: "storage", params: [p("key", "string", ""), p("value", "boolean", "")] }),
              m("storeGetJSON", "storeGetJSON(key)", "Parse a JSON object into a table.", { perm: "storage", params: [p("key", "string", "")], returns: r("table|nil", "") }),
              m("storeSetJSON", "storeSetJSON(key, value)", "Encode a table/value as JSON text.", { perm: "storage", params: [p("key", "string", ""), p("value", "any", "")] }),
              m("storeIncr", "storeIncr(key, delta?)", "Add delta (default 1) to a numeric value.", { perm: "storage", params: [p("key", "string", ""), p("delta", "number", "Default 1.", true)], returns: r("number", "New value.") }),
              m("storeClear", "storeClear()", "Delete every key for this plugin.", { perm: "storage" }),
            ],
          },
          {
            title: "Logging & notifications",
            members: [
              m("logDebug", "logDebug(message)", "Write to the plugin log at debug.", { params: [p("message", "string", "Truncated to 500 chars.")] }),
              m("logInfo", "logInfo(message)", "Info line. `print(...)` also lands here.", { params: [p("message", "string", "")] }),
              m("logWarn", "logWarn(message)", "Warning line.", { params: [p("message", "string", "")] }),
              m("logError", "logError(message)", "Error line.", { params: [p("message", "string", "")] }),
              m("logPrint", "logPrint(message)", "Alias of logInfo.", { params: [p("message", "string", "")] }),
              m("logAt", "logAt(level, message)", "Arbitrary level string.", { params: [p("level", "string", "`debug` | `info` | `warn` | `error`"), p("message", "string", "")] }),
              m("notifyShow", "notifyShow(title, text)", "Status notification. 30s gap, 8/hour, title 40 / body 120.", { perm: "notify", params: [p("title", "string", ""), p("text", "string", "")], aliases: ["notifyInfo", "notifySuccess", "notifyWarn", "notifyError"] }),
            ],
          },
          {
            title: "VPN",
            members: [
              m("vpnState", "vpnState()", "Current phase string.", { perm: "vpn.read", returns: r("string", "`disconnected` | `connecting` | `connected` | …"), aliases: ["vpnPhase"] }),
              m("vpnConnected", "vpnConnected()", "True when phase is `connected`.", { perm: "vpn.read", returns: r("boolean", ""), aliases: ["vpnActive"] }),
              m("vpnDisconnected", "vpnDisconnected()", "True when phase is `disconnected`.", { perm: "vpn.read", returns: r("boolean", "") }),
              m("vpnSnapshot", "vpnSnapshot()", "Coarse counters. Same object as vpnStats / vpnFetch.", { perm: "vpn.read", returns: r("VpnSnapshot", ""), aliases: ["vpnStats", "vpnFetch"] }),
              m("vpnUptime", "vpnUptime()", "Seconds the engine reports as up.", { perm: "vpn.read", returns: r("number", "") }),
              m("vpnAlive", "vpnAlive()", "`engine_alive` flag.", { perm: "vpn.read", returns: r("boolean", "") }),
              m("vpnTun", "vpnTun()", "`tun_active` flag.", { perm: "vpn.read", returns: r("boolean", "") }),
              m("vpnPackets", "vpnPackets()", "`packets_processed`.", { perm: "vpn.read", returns: r("number", "") }),
              m("vpnDropped", "vpnDropped()", "`packets_dropped`.", { perm: "vpn.read", returns: r("number", "") }),
              m("vpnBytesIn", "vpnBytesIn()", "Bytes in.", { perm: "vpn.read", returns: r("number", "") }),
              m("vpnBytesOut", "vpnBytesOut()", "Bytes out.", { perm: "vpn.read", returns: r("number", "") }),
              m("vpnDnsQueries", "vpnDnsQueries()", "DNS query count.", { perm: "vpn.read", returns: r("number", "") }),
              m("vpnStrategy", "vpnStrategy()", "Current DPI strategy name.", { perm: "vpn.read", returns: r("string", "") }),
              m("vpnStart", "vpnStart()", "Ask the app to start local protection. 15s throttle.", { perm: "vpn.control", aliases: ["vpnConnect"] }),
              m("vpnStop", "vpnStop()", "Ask the app to stop. 15s throttle.", { perm: "vpn.control", aliases: ["vpnDisconnect"] }),
            ],
          },
          {
            title: "Rules",
            members: [
              m("createRule", "createRule(payload)", "Create a plugin-owned rule. Ids are prefixed `p:{pluginId}:`. Name `Discord` is reserved. Max 16 rules, 32 domains.", {
                perm: "rules.write",
                params: [p("payload", "RulePayload", "id, name, enabled, strategy, domains")],
                returns: r("Rule", "Structured rule with edit/delete/enable/disable."),
                examples: [lua(`client.createRule({
  id = "focus",
  name = "Focus",
  domains = { "example.com", "*.example.com" },
  strategy = "automatic",
})`)],
              }),
              m("upsertRule", "upsertRule(payload)", "Create or replace. Returns the full rule id string.", { perm: "rules.write", params: [p("payload", "RulePayload", "")], returns: r("string", "Rule id.") }),
              m("editRule", "editRule(id, patch)", "Patch an existing own rule. Id may be the suffix.", { perm: "rules.write", params: [p("id", "string", ""), p("patch", "RulePayload", "")], returns: r("Rule", "") }),
              m("deleteRule", "deleteRule(id)", "Delete an own rule.", { perm: "rules.write", params: [p("id", "string", "Full id or local suffix.")] }),
              m("getRule", "getRule(id)", "Fetch by id or suffix.", { perm: "rules.read", params: [p("id", "string", "")], returns: r("Rule|nil", ""), aliases: ["resolveRule"] }),
              m("findRule", "findRule(nameOrId)", "Find by name (case-insensitive) or id suffix via `rules.cache.find`.", { perm: "rules.read", params: [p("nameOrId", "string", "")], returns: r("Rule|nil", "") }),
              m("listRules", "listRules()", "Array of own rules.", { perm: "rules.read", returns: r("Rule[]", ""), aliases: ["fetchRules"] }),
              m("countRules", "countRules()", "How many own rules.", { perm: "rules.read", returns: r("number", "") }),
              m("hasRule", "hasRule(id)", "Whether an own rule exists.", { perm: "rules.read", params: [p("id", "string", "")], returns: r("boolean", "") }),
              m("enableRule", "enableRule(id)", "Set enabled = true.", { perm: "rules.write", params: [p("id", "string", "Id suffix match.")] }),
              m("disableRule", "disableRule(id)", "Set enabled = false.", { perm: "rules.write", params: [p("id", "string", "")] }),
              m("clearRules", "clearRules()", "Delete every own rule.", { perm: "rules.write" }),
            ],
          },
          {
            title: "Hosts overlay",
            members: [
              m("setHostsText", "setHostsText(text)", "Parse a hosts file and replace this plugin's overlay. Comments with `#`. IPv6 lines skipped. Max 32 768 chars, 256 names.", {
                perm: "hosts.write",
                params: [p("text", "string", "`ip hostname` lines")],
                returns: r("table", "`{ applied, skipped, errors }`"),
                notes: "<p>Active only while local protection is on. Matching A queries get a synthetic answer; AAAA / HTTPS / SVCB get empty NOERROR. Does not write the system hosts file. Rejected IPs: <code>0.0.0.0/8</code>, <code>169.254.0.0/16</code>, multicast, TUN <code>10.7.0.0/24</code>. Loopback and private ranges are allowed. Wildcards like <code>*.example.com</code> are ok.</p>",
                examples: [lua(`client.setHostsText([[
192.168.1.10 growtopia1.com
192.168.1.10 growtopia2.com
]])`)],
              }),
              m("setHosts", "setHosts(entries)", "Replace overlay from an array of `{ host|hostname, ip|ipv4 }`.", { perm: "hosts.write", params: [p("entries", "HostEntry[]", "")], returns: r("number", "Applied count."), aliases: ["replaceHosts"] }),
              m("addHost", "addHost(host, ip)", "Append one mapping (still subject to the 256 cap).", { perm: "hosts.write", params: [p("host", "string", ""), p("ip", "string", "")], returns: r("number", "New size.") }),
              m("removeHost", "removeHost(host)", "Drop one hostname.", { perm: "hosts.write", params: [p("host", "string", "")] }),
              m("getHost", "getHost(host)", "Entry table or nil.", { perm: "hosts.write", params: [p("host", "string", "")], returns: r("HostEntry|nil", "") }),
              m("resolveHost", "resolveHost(host)", "IPv4 string or nil.", { perm: "hosts.write", params: [p("host", "string", "")], returns: r("string|nil", "") }),
              m("hasHost", "hasHost(host)", "Case-insensitive lookup.", { perm: "hosts.write", params: [p("host", "string", "")], returns: r("boolean", "") }),
              m("listHosts", "listHosts()", "All overlay rows.", { perm: "hosts.write", returns: r("HostEntry[]", ""), aliases: ["fetchHosts"] }),
              m("countHosts", "countHosts()", "Row count.", { perm: "hosts.write", returns: r("number", "") }),
              m("clearHosts", "clearHosts()", "Empty this plugin's overlay.", { perm: "hosts.write" }),
              m("parseHosts", "parseHosts(text)", "Parse hosts text into `{ entries, errors }`. Does not apply the overlay — use `setHostsText` for that.", { perm: "hosts.write", params: [p("text", "string", "")], returns: r("table", "") }),
              m("hostsToText", "hostsToText()", "Serialize overlay to `ip host` lines.", { perm: "hosts.write", returns: r("string", "") }),
            ],
          },
          {
            title: "App config",
            members: [
              m("appVersion", "appVersion()", "Host app version string. No extra permission.", { returns: r("string", "") }),
              m("appName", "appName()", "`Lunas DPI`.", { returns: r("string", "") }),
              m("appLocale", "appLocale()", "Device locale.", { returns: r("string", "") }),
              m("appConfig", "appConfig()", "Full coarse config map.", { perm: "app.read", returns: r("AppConfig", ""), aliases: ["appJSON"] }),
              m("appMode", "appMode()", "DPI mode (`automatic`, …).", { perm: "app.read", returns: r("string", "") }),
              m("appDnsMode", "appDnsMode()", "DNS mode.", { perm: "app.read", returns: r("string", "") }),
              m("appMtu", "appMtu()", "MTU.", { perm: "app.read", returns: r("number", "") }),
              m("appIpv6Mode", "appIpv6Mode()", "IPv6 mode (`block`, …).", { perm: "app.read", returns: r("string", "") }),
              m("appBlockQuic", "appBlockQuic()", "Whether QUIC is blocked.", { perm: "app.read", returns: r("boolean", "") }),
              m("appLogLevel", "appLogLevel()", "Numeric log level.", { perm: "app.read", returns: r("number", "") }),
              m("appPerAppMode", "appPerAppMode()", "`all` or per-app mode name.", { perm: "app.read", returns: r("string", "") }),
            ],
          },
          {
            title: "Clock & i18n",
            members: [
              m("now", "now()", "Unix seconds.", { returns: r("number", "") }),
              m("nowMs", "nowMs()", "Unix milliseconds.", { returns: r("number", "") }),
              m("isoNow", "isoNow()", "ISO-8601 instant.", { returns: r("string", "") }),
              m("setTimeout", "setTimeout(ms, fn)", "One-shot timer. Delay clamped 2000–120000. Max 4 timers.", { params: [p("ms", "number", ""), p("fn", "function", "")], returns: r("number", "Timer id."), aliases: ["after"] }),
              m("setInterval", "setInterval(ms, fn)", "Repeating timer. Default delay 5000 if omitted at the clock layer.", { params: [p("ms", "number", ""), p("fn", "function", "")], returns: r("number", "Timer id.") }),
              m("clearTimeout", "clearTimeout(id)", "Cancel a timer.", { params: [p("id", "number", "")], aliases: ["clearInterval"] }),
              m("t", "t(key, fallback?)", "Translate from `locale/{lang}.json`, max 200 chars.", { params: [p("key", "string", ""), p("fallback", "string", "Defaults to the key.", true)], returns: r("string", ""), aliases: ["translate"] }),
              m("hasTranslation", "hasTranslation(key)", "True if the key resolves to a non-empty string.", { params: [p("key", "string", "")], returns: r("boolean", "") }),
              m("language", "language()", "Two-letter language code.", { returns: r("string", "") }),
            ],
          },
          {
            title: "Format & validate",
            members: [
              m("formatBytes", "formatBytes(n)", "Human bytes (`1.2 MB`).", { params: [p("n", "number", "")], returns: r("string", "") }),
              m("formatDuration", "formatDuration(seconds)", "`h:mm:ss` / `m:ss` / `Ns`.", { params: [p("seconds", "number", "")], returns: r("string", ""), aliases: ["formatUptime"] }),
              m("formatPercent", "formatPercent(n)", "Values in 0–1 are treated as fractions.", { params: [p("n", "number", "")], returns: r("string", "") }),
              m("formatNumber", "formatNumber(n)", "tostring of the number.", { params: [p("n", "number", "")], returns: r("string", "") }),
              m("compactNumber", "compactNumber(n)", "`1.2K` / `3.4M`.", { params: [p("n", "number", "")], returns: r("string", "") }),
              m("humanJoin", "humanJoin(list, sep?)", "Join strings with a separator.", { params: [p("list", "string[]", ""), p("sep", "string", "Default `, `.", true)], returns: r("string", "") }),
              m("bulletLines", "bulletLines(list)", "Prefix each line with `•`.", { params: [p("list", "string[]", "")], returns: r("string", "") }),
              m("isDomain", "isDomain(value)", "Valid hostname, not a wildcard.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isDomainPattern", "isDomainPattern(value)", "Valid hostname or `*.domain`.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isWildcard", "isWildcard(value)", "Normalized form starts with `*.`.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isIpv4", "isIpv4(value)", "Parses as IPv4.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isAllowedIpv4", "isAllowedIpv4(value)", "Allowed as a hosts target.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isPrivateIpv4", "isPrivateIpv4(value)", "RFC1918.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isLoopbackIpv4", "isLoopbackIpv4(value)", "127.0.0.0/8.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isHexColor", "isHexColor(value)", "`#rgb` / `#rrggbb`.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isSemver", "isSemver(value)", "`major.minor.patch` with 1–3 digits each.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isHostsLine", "isHostsLine(value)", "Empty/comment or `ipv4 hostname`.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("isGithubUrl", "isGithubUrl(value)", "Passes homepage validation (`https://github.com/…`).", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("rejectDomain", "rejectDomain(value)", "Human reject reason, or nil if ok.", { params: [p("value", "string", "")], returns: r("string|nil", "") }),
            ],
          },
          {
            title: "Domain, net, hash, json, color, time",
            members: [
              m("normalizeDomain", "normalizeDomain(host)", "Lowercase / punycode normalize.", { params: [p("host", "string", "")], returns: r("string", "") }),
              m("domainMatches", "domainMatches(host, pattern)", "Exact or wildcard match.", { params: [p("host", "string", ""), p("pattern", "string", "")], returns: r("boolean", "") }),
              m("parseIpv4", "parseIpv4(ip)", "Packed integer or nil.", { params: [p("ip", "string", "")], returns: r("number|nil", "") }),
              m("formatIpv4", "formatIpv4(packed)", "Dotted quad.", { params: [p("packed", "number", "")], returns: r("string", "") }),
              m("inCidr", "inCidr(ip, net, bits)", "CIDR membership.", { params: [p("ip", "string", ""), p("net", "string", ""), p("bits", "number", "")], returns: r("boolean", "") }),
              m("classifyIpv4", "classifyIpv4(ip)", "`tun` | `loopback` | `private` | `link_local` | `multicast` | `public` | `reserved` | `invalid`.", { params: [p("ip", "string", "")], returns: r("string", "") }),
              m("sha256", "sha256(text)", "Hex SHA-256 of the string bytes.", { params: [p("text", "string", "")], returns: r("string", "") }),
              m("jsonEncode", "jsonEncode(value)", "JSON text, max 32 768 chars.", { params: [p("value", "any", "")], returns: r("string", ""), aliases: ["jsonStringify"] }),
              m("jsonDecode", "jsonDecode(text)", "Lua table / value.", { params: [p("text", "string", "")], returns: r("any", "") }),
              m("parseColor", "parseColor(hex)", "`{ r, g, b, hex }` or nil.", { params: [p("hex", "string", "")], returns: r("table|nil", "") }),
              m("mixColor", "mixColor(a, b, t)", "Lerp two hex colors.", { params: [p("a", "string", ""), p("b", "string", ""), p("t", "number", "0–1")], returns: r("string", "") }),
              m("year", "year()", "Local calendar year.", { returns: r("number", "") }),
              m("since", "since(unix)", "Seconds since that timestamp.", { params: [p("unix", "number", "")], returns: r("number", "") }),
            ],
          },
          {
            title: "UI helpers & builders",
            members: [
              m("page", "page(spec)", "Wrap a settings page table.", { perm: "ui.settings", params: [p("spec", "table", "title, description, sections")], returns: r("Page", "") }),
              m("section", "section(title, items)", "A section node.", { params: [p("title", "string", ""), p("items", "table[]", "")], returns: r("table", "") }),
              m("pageBuilder", "pageBuilder()", "New PageBuilder.", { returns: r("PageBuilder", "") }),
              m("embedBuilder", "embedBuilder()", "New EmbedBuilder (single-section page).", { returns: r("EmbedBuilder", "") }),
              m("ruleBuilder", "ruleBuilder()", "New RuleBuilder.", { returns: r("RuleBuilder", "") }),
              m("hostsBuilder", "hostsBuilder()", "New HostsBuilder.", { returns: r("HostsBuilder", "") }),
              m("collection", "collection()", "`Collection.new()`.", { returns: r("Collection", "") }),
              m("collectionFrom", "collectionFrom(rows)", "`Collection.from(rows)`.", { params: [p("rows", "table", "{ key, value } or { k, v } pairs")], returns: r("Collection", "") }),
              m("toJSON", "toJSON()", "Identity JSON of this plugin (`user.toJSON`).", { returns: r("table", "") }),
              m("guid", "guid()", "Random UUID string.", { returns: r("string", "") }),
              m("typeof", "typeof(value)", "LuaJ type name.", { params: [p("value", "any", "")], returns: r("string", "") }),
            ],
          },
        ],
      },
      {
        id: "User",
        name: "User",
        kind: "class",
        summary: "This plugin's identity. Available as luna.user and luna.User (same table).",
        construct: "Not constructed. Read luna.user.",
        props: [
          prop("id", "function", "`user.id()` → manifest id."),
          prop("name", "function", "`user.name()` → plugin name."),
          prop("author", "function", "Author string."),
          prop("version", "function", "Version string."),
          prop("tag", "function", "`Name@version`."),
          prop("locale", "function", "Device language."),
          prop("api_level", "function", "Number 1. Also `apiLevel()`."),
        ],
        sections: [
          {
            title: "Methods",
            members: [
              m("toJSON", "toJSON()", " `{ id, name, author, version }`.", { returns: r("table", "") }),
              m("hasPermission", "hasPermission(key)", "Same check as the permissions manager.", { params: [p("key", "string", "")], returns: r("boolean", ""), aliases: ["has_permission"] }),
              m("permissions", "permissions()", "Returns the live Permissions table.", { returns: r("Permissions", "") }),
            ],
          },
        ],
      },
      {
        id: "Permissions",
        name: "Permissions",
        kind: "class",
        summary: "Granted intents. luna.permissions and luna.Intents are the same table. Each manifest key is also a boolean field (permissions.storage, permissions['ui.settings']).",
        construct: "Not constructed.",
        sections: [
          {
            title: "Methods",
            members: [
              m("has", "has(key)", "Granted?", { params: [p("key", "string", "")], returns: r("boolean", "") }),
              m("missing", "missing(key)", "Not granted (unknown → true).", { params: [p("key", "string", "")], returns: r("boolean", "") }),
              m("any", "any(keys)", "Any of up to 16 keys.", { params: [p("keys", "string[]", "")], returns: r("boolean", "") }),
              m("all", "all(keys)", "All of a non-empty list.", { params: [p("keys", "string[]", "")], returns: r("boolean", "") }),
              m("toArray", "toArray()", "Granted keys.", { returns: r("string[]", "") }),
              m("bitfield", "bitfield()", "Ordinal bitmask.", { returns: r("number", "") }),
            ],
          },
        ],
      },
      {
        id: "IntentsBitField",
        name: "IntentsBitField",
        kind: "class",
        summary: "Bitfield helper around PluginPermission ordinals. luna.IntentsBitField.Flags aliases PermissionFlagsBits.",
        construct: "IntentsBitField.from(bits) or IntentsBitField.resolve() (current grants).",
        constructExample: "local bits = luna.IntentsBitField.resolve()\nif bits:has('storage') then end",
        sections: [
          {
            title: "Static",
            members: [
              m("from", "from(bits)", "Wrap an integer bitfield.", { static: true, params: [p("bits", "number", "")], returns: r("IntentsBitField", "") }),
              m("resolve", "resolve()", "Bitfield of permissions granted to this plugin.", { static: true, returns: r("IntentsBitField", "") }),
            ],
          },
          {
            title: "Instance",
            members: [
              m("bitfield", "bitfield()", "Underlying integer.", { returns: r("number", "") }),
              m("has", "has(key)", "Whether that permission bit is set.", { params: [p("key", "string", "")], returns: r("boolean", "") }),
              m("missing", "missing(key)", "Inverse.", { params: [p("key", "string", "")], returns: r("boolean", "") }),
              m("toArray", "toArray()", "Granted keys encoded in this field.", { returns: r("string[]", "") }),
              m("equals", "equals(other)", "Compare to an integer.", { params: [p("other", "number", "")], returns: r("boolean", "") }),
            ],
          },
        ],
      },
      {
        id: "Events",
        name: "Events",
        kind: "class",
        summary: "String constants for the event bus. Prefer these over magic strings.",
        construct: "Constants table — not constructed.",
        props: [
          prop("Ready", "string", "`\"ready\"`. Aliases: `ClientReady`, `READY`."),
          prop("VpnPhase", "string", "`\"vpnPhase\"`. Aliases: `VPN_PHASE`."),
        ],
        sections: [
          {
            title: "Usage",
            members: [
              m("Ready", "Events.Ready", "Fired after the plugin VM is up. No payload.", {
                kind: "prop",
                examples: [lua(`client:on(luna.Events.Ready, function()
  client.logInfo("hello")
end)`)],
              }),
              m("VpnPhase", "Events.VpnPhase", "Fired when local protection phase changes. Listener receives the phase string.", {
                kind: "prop",
                examples: [lua(`client:on(luna.Events.VpnPhase, function(phase)
  client.logInfo(phase)
end)`)],
              }),
            ],
          },
        ],
      },
      {
        id: "EventEmitter",
        name: "EventEmitter",
        kind: "class",
        summary: "luna.events — Node-style emitter used by Client:on / once / off. Extra constants READY and VPN_PHASE live here too.",
        sections: [
          {
            title: "Methods",
            members: [
              m("on", "on(name, fn)", "Add a listener. `fn` must be a function.", { params: [p("name", "string", ""), p("fn", "function", "")] }),
              m("once", "once(name, fn)", "Fire once.", { params: [p("name", "string", ""), p("fn", "function", "")] }),
              m("off", "off(name, fn?)", "Remove one or all listeners for `name`.", { params: [p("name", "string", ""), p("fn", "function", "", true)] }),
              m("removeAllListeners", "removeAllListeners(name)", "Clear an event.", { params: [p("name", "string", "")] }),
              m("listenerCount", "listenerCount(name)", "Count.", { params: [p("name", "string", "")], returns: r("number", "") }),
              m("eventNames", "eventNames()", "Active event names.", { returns: r("string[]", "") }),
            ],
          },
        ],
      },
      {
        id: "StorageManager",
        name: "StorageManager",
        kind: "class",
        summary: "luna.storage — private string map. Requires storage. Aliases: getJSON/setJSON/getNumber/setNumber/getBool/setBool, delete = remove, length = size.",
        sections: [
          {
            title: "Methods",
            members: [
              m("get", "get(key)", "String or nil.", { perm: "storage", params: [p("key", "string", "")], returns: r("string|nil", "") }),
              m("set", "set(key, value)", "Store `tostring(value)`. Throws if over 32 768 chars.", { perm: "storage", params: [p("key", "string", ""), p("value", "any", "")] }),
              m("remove", "remove(key)", "Delete.", { perm: "storage", params: [p("key", "string", "")], aliases: ["delete"] }),
              m("has", "has(key)", "Exists?", { perm: "storage", params: [p("key", "string", "")], returns: r("boolean", "") }),
              m("keys", "keys()", "Key list.", { perm: "storage", returns: r("string[]", "") }),
              m("size", "size()", "Count.", { perm: "storage", returns: r("number", ""), aliases: ["length"] }),
              m("get_json", "get_json(key)", "JSON object → table.", { perm: "storage", params: [p("key", "string", "")], returns: r("table|nil", "") }),
              m("set_json", "set_json(key, value)", "Encode and store.", { perm: "storage", params: [p("key", "string", ""), p("value", "any", "")] }),
              m("incr", "incr(key, delta?)", "Numeric increment.", { perm: "storage", params: [p("key", "string", ""), p("delta", "number", "", true)], returns: r("number", "") }),
              m("clear", "clear()", "Wipe plugin storage.", { perm: "storage" }),
            ],
          },
        ],
      },
      {
        id: "RulesManager",
        name: "RulesManager",
        kind: "class",
        summary: "luna.rules — only rules whose id starts with p:{pluginId}:. cache.get / cache.find / cache.array / cache.size mirror discord.js Collection-ish accessors. Rule objects include edit, delete, enable, disable, toJSON.",
        sections: [
          {
            title: "Methods",
            members: [
              m("create", "create(payload)", "Insert. Throws if you already have 16 rules.", { perm: "rules.write", params: [p("payload", "RulePayload", "")], returns: r("Rule", "") }),
              m("upsert", "upsert(payload)", "Insert or replace; returns id.", { perm: "rules.write", params: [p("payload", "RulePayload", "")], returns: r("string", "") }),
              m("edit", "edit(id, patch)", "Replace fields, keep id.", { perm: "rules.write", params: [p("id", "string", ""), p("patch", "RulePayload", "")], returns: r("Rule", "") }),
              m("delete", "delete(id)", "Own rules only.", { perm: "rules.write", params: [p("id", "string", "")] }),
              m("get", "get(id)", "By full id or suffix.", { perm: "rules.read", params: [p("id", "string", "")], returns: r("Rule|nil", ""), aliases: ["resolve"] }),
              m("list", "list()", "1-based array.", { perm: "rules.read", returns: r("Rule[]", ""), aliases: ["fetch"] }),
              m("count", "count()", "Size.", { perm: "rules.read", returns: r("number", "") }),
              m("has", "has(id)", "Exists?", { perm: "rules.read", params: [p("id", "string", "")], returns: r("boolean", "") }),
              m("enable", "enable(id)", "enabled = true (suffix match).", { perm: "rules.write", params: [p("id", "string", "")] }),
              m("disable", "disable(id)", "enabled = false.", { perm: "rules.write", params: [p("id", "string", "")] }),
              m("clear", "clear()", "Delete all own rules.", { perm: "rules.write" }),
            ],
          },
        ],
      },
      {
        id: "HostsManager",
        name: "HostsManager",
        kind: "class",
        summary: "luna.hosts — per-plugin DNS overlay. Requires hosts.write even to list. Entry objects have host/hostname, ip/ipv4, delete(), toJSON().",
        sections: [
          {
            title: "Methods",
            members: [
              m("set_text", "set_text(text)", "Parse + apply. Returns applied/skipped/errors.", { perm: "hosts.write", params: [p("text", "string", "")], returns: r("table", ""), aliases: ["setText"] }),
              m("set", "set(entries)", "Replace from array.", { perm: "hosts.write", params: [p("entries", "HostEntry[]", "")], returns: r("number", "") }),
              m("add", "add(host, ip)", "Append one row.", { perm: "hosts.write", params: [p("host", "string", ""), p("ip", "string", "")], returns: r("number", "") }),
              m("remove", "remove(host)", "Drop by hostname.", { perm: "hosts.write", params: [p("host", "string", "")] }),
              m("get", "get(host)", "Entry or nil.", { perm: "hosts.write", params: [p("host", "string", "")], returns: r("HostEntry|nil", "") }),
              m("resolve", "resolve(host)", "IPv4 or nil.", { perm: "hosts.write", params: [p("host", "string", "")], returns: r("string|nil", "") }),
              m("has", "has(host)", "Lookup.", { perm: "hosts.write", params: [p("host", "string", "")], returns: r("boolean", "") }),
              m("list", "list()", "All rows.", { perm: "hosts.write", returns: r("HostEntry[]", ""), aliases: ["fetch"] }),
              m("count", "count()", "Size.", { perm: "hosts.write", returns: r("number", "") }),
              m("parse", "parse(text)", "`{ entries, errors }` without describing apply — still requires permission.", { perm: "hosts.write", params: [p("text", "string", "")], returns: r("table", "") }),
              m("to_text", "to_text()", "Serialize.", { perm: "hosts.write", returns: r("string", ""), aliases: ["toText"] }),
              m("clear", "clear()", "Empty overlay.", { perm: "hosts.write" }),
            ],
          },
        ],
      },
      {
        id: "VPNManager",
        name: "VPNManager",
        kind: "class",
        summary: "luna.vpn — local protection status and start/stop. Aliases: isActive, requestStart, requestStop, bytesIn, bytesOut, dnsQueries, start/stop/connect/disconnect.",
        sections: [
          {
            title: "Read",
            members: [
              m("state", "state()", "Phase string.", { perm: "vpn.read", returns: r("string", ""), aliases: ["phase"] }),
              m("connected", "connected()", "phase == connected.", { perm: "vpn.read", returns: r("boolean", ""), aliases: ["is_active", "isActive"] }),
              m("snapshot", "snapshot()", "Full VpnSnapshot.", { perm: "vpn.read", returns: r("VpnSnapshot", ""), aliases: ["stats", "fetch"] }),
              m("uptime", "uptime()", "uptime_seconds.", { perm: "vpn.read", returns: r("number", "") }),
              m("alive", "alive()", "engine_alive.", { perm: "vpn.read", returns: r("boolean", "") }),
              m("tun", "tun()", "tun_active.", { perm: "vpn.read", returns: r("boolean", "") }),
              m("packets", "packets()", "packets_processed.", { perm: "vpn.read", returns: r("number", "") }),
              m("dropped", "dropped()", "packets_dropped.", { perm: "vpn.read", returns: r("number", "") }),
              m("bytes_in", "bytes_in()", "bytes_in.", { perm: "vpn.read", returns: r("number", "") }),
              m("bytes_out", "bytes_out()", "bytes_out.", { perm: "vpn.read", returns: r("number", "") }),
              m("dns_queries", "dns_queries()", "dns_queries.", { perm: "vpn.read", returns: r("number", "") }),
              m("strategy", "strategy()", "Current strategy.", { perm: "vpn.read", returns: r("string", "") }),
            ],
          },
          {
            title: "Control",
            members: [
              m("request_start", "request_start()", "Start local protection. Throws if called again within 15s.", { perm: "vpn.control", aliases: ["start", "connect", "requestStart"] }),
              m("request_stop", "request_stop()", "Stop. Same throttle.", { perm: "vpn.control", aliases: ["stop", "disconnect", "requestStop"] }),
            ],
          },
        ],
      },
      {
        id: "AppManager",
        name: "AppManager",
        kind: "class",
        summary: "luna.app — host identity is free; config fields need app.read. Note: app.id() is this plugin's id, not the Android package.",
        sections: [
          {
            title: "Methods",
            members: [
              m("version", "version()", "App version.", { returns: r("string", "") }),
              m("name", "name()", "`Lunas DPI`.", { returns: r("string", "") }),
              m("locale", "locale()", "Language.", { returns: r("string", "") }),
              m("api_level", "api_level()", "1.", { returns: r("number", "") }),
              m("id", "id()", "Plugin id.", { returns: r("string", "") }),
              m("config", "config()", "AppConfig map.", { perm: "app.read", returns: r("AppConfig", ""), aliases: ["toJSON"] }),
              m("mode", "mode()", "DPI mode.", { perm: "app.read", returns: r("string", "") }),
              m("dns_mode", "dns_mode()", "DNS mode.", { perm: "app.read", returns: r("string", "") }),
              m("mtu", "mtu()", "MTU.", { perm: "app.read", returns: r("number", "") }),
              m("ipv6_mode", "ipv6_mode()", "IPv6 mode.", { perm: "app.read", returns: r("string", "") }),
              m("block_quic", "block_quic()", "QUIC blocked?", { perm: "app.read", returns: r("boolean", "") }),
              m("log_level", "log_level()", "Log level int.", { perm: "app.read", returns: r("number", "") }),
              m("per_app_mode", "per_app_mode()", "Per-app mode.", { perm: "app.read", returns: r("string", "") }),
            ],
          },
        ],
      },
      {
        id: "NotifyManager",
        name: "NotifyManager",
        kind: "class",
        summary: "luna.notify — all of show/info/success/warn/error call the same channel. Rate limited.",
        sections: [
          {
            title: "Methods",
            members: [
              m("show", "show(title, text)", "Post a status notification.", { perm: "notify", params: [p("title", "string", "Max 40."), p("text", "string", "Max 120.")], aliases: ["info", "success", "warn", "error"] }),
            ],
          },
        ],
      },
      {
        id: "Clock",
        name: "Clock",
        kind: "class",
        summary: "luna.clock — wall clock plus the host timer pool (max 4, 2s–120s).",
        sections: [
          {
            title: "Methods",
            members: [
              m("now", "now()", "Unix seconds.", { returns: r("number", "") }),
              m("now_ms", "now_ms()", "Unix ms.", { returns: r("number", "") }),
              m("iso", "iso()", "ISO instant.", { returns: r("string", "") }),
              m("setTimeout", "setTimeout(ms, fn)", "One-shot. Default ms 2000.", { params: [p("ms", "number", ""), p("fn", "function", "")], returns: r("number", "id"), aliases: ["after"] }),
              m("setInterval", "setInterval(ms, fn)", "Repeat. Default ms 5000.", { params: [p("ms", "number", ""), p("fn", "function", "")], returns: r("number", "id") }),
              m("clearTimeout", "clearTimeout(id)", "Cancel.", { params: [p("id", "number", "")], aliases: ["clearInterval"] }),
            ],
          },
        ],
      },
      {
        id: "Log",
        name: "Log",
        kind: "class",
        summary: "luna.log — plugin log sink. print() in Lua is routed to log.info.",
        sections: [
          {
            title: "Methods",
            members: [
              m("debug", "debug(message)", "Debug.", { params: [p("message", "string", "Max 500.")] }),
              m("info", "info(message)", "Info.", { params: [p("message", "string", "")] }),
              m("warn", "warn(message)", "Warn.", { params: [p("message", "string", "")] }),
              m("error", "error(message)", "Error.", { params: [p("message", "string", "")] }),
              m("log", "log(level, message)", "Custom level.", { params: [p("level", "string", ""), p("message", "string", "")] }),
              m("print", "print(message)", "Info alias.", { params: [p("message", "string", "")] }),
            ],
          },
        ],
      },
      {
        id: "I18n",
        name: "I18n",
        kind: "class",
        summary: "luna.i18n — strings from locale/en.json and locale/{lang}.json. Lookups are truncated to 200 characters.",
        sections: [
          {
            title: "Methods",
            members: [
              m("t", "t(key, fallback?)", "Translate.", { params: [p("key", "string", ""), p("fallback", "string", "", true)], returns: r("string", ""), aliases: ["translate"] }),
              m("has", "has(key)", "Non-empty translation?", { params: [p("key", "string", "")], returns: r("boolean", "") }),
              m("locale", "locale()", "Full locale string.", { returns: r("string", "") }),
              m("language", "language()", "First two letters.", { returns: r("string", "") }),
            ],
          },
        ],
      },
      {
        id: "REST",
        name: "REST",
        kind: "class",
        summary: "Local router mounted at luna.REST and client.rest. Paths are slash-separated names, case-insensitive. Unknown routes throw LuaError.",
        construct: "Already created. client.restGet(path) is rest.get, restPut(path, body), restPost, restPatch, restDelete.",
        sections: [
          {
            title: "Methods",
            members: [
              m("get", "get(path)", "Read. See the REST guide for routes.", { params: [p("path", "string", "`vpn`, `rules/focus`, `storage/key`, …")], returns: r("any", "") }),
              m("put", "put(path, body)", "Replace hosts, storage key, or upsert a rule.", { params: [p("path", "string", ""), p("body", "any", "")] }),
              m("post", "post(path, body)", "Create rules, set hosts, notify, or vpn start/stop.", { params: [p("path", "string", ""), p("body", "any", "")] }),
              m("patch", "patch(path, body)", "Only `rules/{id}`.", { params: [p("path", "string", ""), p("body", "table", "")] }),
              m("delete", "delete(path)", "rules, hosts, or storage/{key}.", { params: [p("path", "string", "")] }),
            ],
          },
        ],
      },
      {
        id: "Collection",
        name: "Collection",
        kind: "class",
        summary: "Ordered string-key map, discord.js Collection-inspired. Max 256 entries, keys truncated to 80 chars. Not persisted.",
        construct: "Collection.new() or Collection.from({ { key, value }, ... }). Also client.collection() / collectionFrom().",
        constructExample: "local col = luna.Collection.new()\ncol:set(\"growtopia1.com\", \"10.0.0.2\")\nprint(col:get(\"growtopia1.com\"))",
        sections: [
          {
            title: "Static",
            members: [
              m("new", "new()", "Empty collection.", { static: true, returns: r("Collection", "") }),
              m("from", "from(rows)", "Build from array of `{ key, value }` or `{ [1]=key, [2]=value }`.", { static: true, params: [p("rows", "table", "")], returns: r("Collection", "") }),
            ],
          },
          {
            title: "Instance",
            members: [
              m("set", "set(key, value)", "Insert. Silently ignores new keys once size is 256.", { params: [p("key", "string", ""), p("value", "any", "")], returns: r("Collection", "self") }),
              m("get", "get(key)", "Value or nil.", { params: [p("key", "string", "")], returns: r("any", "") }),
              m("has", "has(key)", "Membership.", { params: [p("key", "string", "")], returns: r("boolean", "") }),
              m("delete", "delete(key)", "Remove.", { params: [p("key", "string", "")], returns: r("Collection", "self") }),
              m("clear", "clear()", "Empty.", { returns: r("Collection", "self") }),
              m("size", "size()", "Count.", { returns: r("number", ""), aliases: ["count"] }),
              m("keys", "keys()", "Key array.", { returns: r("string[]", ""), aliases: ["keyArray"] }),
              m("values", "values()", "Value array.", { returns: r("any[]", ""), aliases: ["array"] }),
              m("first", "first()", "First value.", { returns: r("any", "") }),
              m("last", "last()", "Last value.", { returns: r("any", "") }),
              m("at", "at(index)", "1-based value.", { params: [p("index", "number", "")], returns: r("any", "") }),
              m("find", "find(fn)", "`fn(value, key)` → first match.", { params: [p("fn", "function", "")], returns: r("any", "") }),
              m("filter", "filter(fn)", "New collection.", { params: [p("fn", "function", "")], returns: r("Collection", "") }),
              m("map", "map(fn)", "Array of mapped values.", { params: [p("fn", "function", "")], returns: r("any[]", "") }),
              m("forEach", "forEach(fn)", "Iterate. Alias `each`.", { params: [p("fn", "function", "")], returns: r("Collection", "self") }),
              m("ensure", "ensure(key, value)", "Set if missing, return stored value.", { params: [p("key", "string", ""), p("value", "any", "")], returns: r("any", "") }),
              m("reduce", "reduce(fn, start)", "`fn(acc, value, key)`.", { params: [p("fn", "function", ""), p("start", "any", "")], returns: r("any", "") }),
              m("sweep", "sweep(fn)", "Delete entries where predicate is true.", { params: [p("fn", "function", "")], returns: r("Collection", "self") }),
              m("clone", "clone()", "Shallow copy.", { returns: r("Collection", "") }),
              m("sort", "sort()", "Copy sorted by key.", { returns: r("Collection", "") }),
              m("toJSON", "toJSON()", "Map of stringified values.", { returns: r("table", "") }),
            ],
          },
        ],
      },
      {
        id: "PageBuilder",
        name: "PageBuilder",
        kind: "class",
        summary: "Chainable settings page with multiple sections. luna.PageBuilder.new() or client.pageBuilder().",
        construct: "PageBuilder.new()",
        constructExample: lua(`function settings_page()
  return luna.PageBuilder.new()
    :setTitle("Focus")
    :setDescription("Block lists")
    :addSection("General", { luna.ui.note({ text = "Hello" }) })
    :addNote("Saved locally")
    :build()
end`),
        sections: [
          {
            title: "Methods",
            members: [
              m("setTitle", "setTitle(title)", "Page title.", { params: [p("title", "string", "")], returns: r("PageBuilder", "self") }),
              m("setDescription", "setDescription(text)", "Subtitle.", { params: [p("text", "string", "")], returns: r("PageBuilder", "self") }),
              m("addSection", "addSection(title, items)", "Append a section, or pass a ready section table as the only argument.", { params: [p("title", "string|table", ""), p("items", "table[]", "", true)], returns: r("PageBuilder", "self") }),
              m("addNote", "addNote(text)", "Note on the last section (creates one if needed).", { params: [p("text", "string", "")], returns: r("PageBuilder", "self") }),
              m("build", "build()", "Produce a Page table.", { returns: r("Page", ""), aliases: ["toJSON"] }),
            ],
          },
        ],
      },
      {
        id: "EmbedBuilder",
        name: "EmbedBuilder",
        kind: "class",
        summary: "Single-section settings page, discord.js EmbedBuilder-shaped. Best default for settings.lua.",
        construct: "EmbedBuilder.new()",
        constructExample: lua(`function settings_page()
  return luna.EmbedBuilder.new()
    :setTitle("Hosts")
    :setDescription("Overlay")
    :addAlert("Protection must be on", "warning")
    :addToggle("on", "Enabled", true)
    :addTextarea("hosts", "Hosts", "")
    :addButton("save", "Save")
    :build()
end`),
        sections: [
          {
            title: "Methods",
            members: [
              m("setTitle", "setTitle(title)", "Title.", { params: [p("title", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("setDescription", "setDescription(text)", "Description.", { params: [p("text", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("setSection", "setSection(title)", "Inner section heading.", { params: [p("title", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("addHeading", "addHeading(text)", "Heading control.", { params: [p("text", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("addAlert", "addAlert(text, tone?)", "Alert. tone: info | warning | danger.", { params: [p("text", "string", ""), p("tone", "string", "Default info.", true)], returns: r("EmbedBuilder", "self") }),
              m("addNote", "addNote(text)", "Note.", { params: [p("text", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("addCode", "addCode(text)", "Monospace block.", { params: [p("text", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("addField", "addField(label, value)", "Key/value row.", { params: [p("label", "string", ""), p("value", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("addDivider", "addDivider()", "Horizontal rule.", { returns: r("EmbedBuilder", "self") }),
              m("addBadge", "addBadge(text)", "Chip.", { params: [p("text", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("addProgress", "addProgress(title, value)", "Meter 0–1.", { params: [p("title", "string", ""), p("value", "number", "")], returns: r("EmbedBuilder", "self") }),
              m("addToggle", "addToggle(id, title, value)", "Switch. Changes fire `on_setting_changed`.", { params: [p("id", "string", ""), p("title", "string", ""), p("value", "boolean", "")], returns: r("EmbedBuilder", "self") }),
              m("addInput", "addInput(id, title, value)", "Single-line text.", { params: [p("id", "string", ""), p("title", "string", ""), p("value", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("addTextarea", "addTextarea(id, title, value)", "Multiline text.", { params: [p("id", "string", ""), p("title", "string", ""), p("value", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("addButton", "addButton(id, title)", "Button. Changed value is `true`.", { params: [p("id", "string", ""), p("title", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("addDangerButton", "addDangerButton(id, title)", "Destructive button.", { params: [p("id", "string", ""), p("title", "string", "")], returns: r("EmbedBuilder", "self") }),
              m("build", "build()", "Page table.", { returns: r("Page", ""), aliases: ["toJSON"] }),
            ],
          },
        ],
      },
      {
        id: "RuleBuilder",
        name: "RuleBuilder",
        kind: "class",
        summary: "Build a RulePayload for rules.create / upsert.",
        construct: "RuleBuilder.new()",
        constructExample: lua(`local rule = luna.RuleBuilder.new()
  :setId("focus")
  :setName("Focus")
  :setStrategy("automatic")
  :addDomain("example.com")
  :build()
client.createRule(rule)`),
        sections: [
          {
            title: "Methods",
            members: [
              m("setId", "setId(id)", "Local id (prefixed later).", { params: [p("id", "string", "")], returns: r("RuleBuilder", "self") }),
              m("setName", "setName(name)", "Max 40 chars. `Discord` is reserved at create time.", { params: [p("name", "string", "")], returns: r("RuleBuilder", "self") }),
              m("setEnabled", "setEnabled(enabled)", "Default true.", { params: [p("enabled", "boolean", "")], returns: r("RuleBuilder", "self") }),
              m("setStrategy", "setStrategy(strategy)", "`automatic` | `basic` | `balanced` | `aggressive` | `custom`.", { params: [p("strategy", "string", "")], returns: r("RuleBuilder", "self") }),
              m("addDomain", "addDomain(domain)", "Append one pattern.", { params: [p("domain", "string", "")], returns: r("RuleBuilder", "self") }),
              m("setDomains", "setDomains(list)", "Replace the list (max 32 at create).", { params: [p("list", "string[]", "")], returns: r("RuleBuilder", "self") }),
              m("build", "build()", "Plain payload table.", { returns: r("RulePayload", ""), aliases: ["toJSON"] }),
            ],
          },
        ],
      },
      {
        id: "HostsBuilder",
        name: "HostsBuilder",
        kind: "class",
        summary: "Build a HostEntry array or hosts text.",
        construct: "HostsBuilder.new()",
        constructExample: lua(`local hosts = luna.HostsBuilder.new()
  :add("growtopia1.com", "192.168.1.10")
  :addLine("192.168.1.10 growtopia2.com")
client.setHosts(hosts:build())`),
        sections: [
          {
            title: "Methods",
            members: [
              m("add", "add(host, ip)", "Append a row.", { params: [p("host", "string", ""), p("ip", "string", "")], returns: r("HostsBuilder", "self") }),
              m("addLine", "addLine(line)", "Parse `ip host` (ignores bad lines).", { params: [p("line", "string", "")], returns: r("HostsBuilder", "self") }),
              m("build", "build()", "Array of `{ host, ip }`.", { returns: r("HostEntry[]", ""), aliases: ["toJSON"] }),
              m("toText", "toText()", "`ip host` lines.", { returns: r("string", "") }),
            ],
          },
        ],
      },
      {
        id: "UI",
        name: "UI",
        kind: "class",
        summary: "luna.ui — low-level settings DSL. settings_page() must return a page. Max 10 sections, 32 controls each. Links must be https://github.com/… . Many aliases exist (toggle=switch, hosts=textarea, markdown=note, …).",
        notes: `<div class="callout"><h4>Control types</h4><p><code>page</code>, <code>section</code>, <code>note</code>, <code>heading</code>, <code>divider</code>, <code>spacer</code>, <code>badge</code>, <code>code</code>, <code>alert</code>, <code>kv</code>, <code>progress</code>, <code>link</code>, <code>switch</code>, <code>checkbox</code>, <code>text</code>, <code>textarea</code> (emits type <code>text</code> + multiline), <code>number</code>, <code>select</code>, <code>slider</code>, <code>button</code>, <code>danger_button</code> (button + destructive).</p></div>`,
        sections: [
          {
            title: "Factories",
            members: [
              m("page", "page(spec)", "Page node. `spec` may be a table with title/description/sections.", { params: [p("spec", "table", "")], returns: r("Page", "") }),
              m("section", "section(title, items)", "Section.", { params: [p("title", "string", ""), p("items", "table[]", "")], returns: r("table", "") }),
              m("section_ex", "section_ex(title, description, items)", "Section with description.", { params: [p("title", "string", ""), p("description", "string", ""), p("items", "table[]", "")], returns: r("table", "") }),
              m("note", "note(spec|text)", "If given a string, becomes `{ text }`.", { params: [p("spec", "table|string", "")], returns: r("table", "") }),
              m("heading", "heading(spec)", "`{ text, level? }`.", { params: [p("spec", "table|string", "")], returns: r("table", "") }),
              m("alert", "alert(spec)", "`{ text, tone }`.", { params: [p("spec", "table|string", "")], returns: r("table", "") }),
              m("switch", "switch(spec)", "`{ id, title, body?, value }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["toggle"] }),
              m("textarea", "textarea(spec)", "Multiline text field.", { params: [p("spec", "table", "")], returns: r("table", "") }),
              m("button", "button(spec)", "`{ id, title, destructive? }`.", { params: [p("spec", "table", "")], returns: r("table", "") }),
              m("link", "link(spec)", "`{ text, url }` — GitHub HTTPS only.", { params: [p("spec", "table", "")], returns: r("table", "") }),
              m("embed", "embed(spec)", "One-section page from `{ title, description, section, fields }`.", { params: [p("spec", "table", "")], returns: r("Page", "") }),
            ],
          },
        ],
      },
      {
        id: "Stdlib",
        name: "Stdlib",
        kind: "class",
        summary: "Pure helpers on luna.string (str, text), luna.table (list), luna.json, luna.time, luna.color, luna.domain, luna.ipv4 (net), luna.hash (crypto), luna.util, luna.semver, luna.path, luna.fmt. No permissions.",
        notes: `<p>Most Client format/validate methods are thin wrappers over these modules. Prefer <code>luna.domain.normalize</code> in libraries and <code>client.normalizeDomain</code> in glue code — they are the same implementation.</p>
          <table class="prop-table">
            <thead><tr><th>Module</th><th>Highlights</th></tr></thead>
            <tbody>
              <tr><td><code>luna.string</code></td><td>trim, lower, upper, slug, split, join, pad_start/end, truncate, slice, title_case, matches (glob)</td></tr>
              <tr><td><code>luna.table</code></td><td>size, keys, values, map, filter, find, unique, merge, concat, sort (string sort)</td></tr>
              <tr><td><code>luna.json</code></td><td>encode / decode / stringify / get / is_array / is_object</td></tr>
              <tr><td><code>luna.time</code></td><td>now, iso, year/month/day/hour, since, start_of_day, add_seconds</td></tr>
              <tr><td><code>luna.color</code></td><td>parse, hex, luma, lighten, darken, mix, contrast, css</td></tr>
              <tr><td><code>luna.domain</code></td><td>normalize, valid, valid_pattern, matches, labels, root, parent</td></tr>
              <tr><td><code>luna.ipv4</code></td><td>parse, format, private, loopback, in_cidr, tun_range, allowed_host</td></tr>
              <tr><td><code>luna.hash</code></td><td>sha256, hex_encode/decode, base64_encode/decode</td></tr>
              <tr><td><code>luna.semver</code></td><td>parse, compare, gt/gte/lt/lte/eq, valid</td></tr>
              <tr><td><code>luna.path</code></td><td>basename, dirname, extname, join, posix (string-only — no disk)</td></tr>
              <tr><td><code>luna.fmt</code></td><td>bytes, duration, percent, compact, bullets, join</td></tr>
              <tr><td><code>luna.util</code></td><td>guid, clamp, lerp, typeof, inspect, min/max</td></tr>
            </tbody>
          </table>`,
        sections: [
          {
            title: "Examples",
            members: [
              m("string.slug", "string.slug(text)", "URL-ish slug.", { params: [p("text", "string", "")], returns: r("string", ""), examples: [lua(`luna.string.slug("Hello World") -- hello-world`)] }),
              m("domain.matches", "domain.matches(host, pattern)", "Wildcard-aware match.", { params: [p("host", "string", ""), p("pattern", "string", "")], returns: r("boolean", ""), examples: [lua(`luna.domain.matches("a.example.com", "*.example.com")`)] }),
              m("ipv4.in_cidr", "ipv4.in_cidr(ip, net, bits)", "CIDR test.", { params: [p("ip", "string", ""), p("net", "string", ""), p("bits", "number", "")], returns: r("boolean", "") }),
              m("json.encode", "json.encode(value)", "Serialize.", { params: [p("value", "any", "")], returns: r("string", "") }),
              m("semver.compare", "semver.compare(a, b)", "Negative / zero / positive.", { params: [p("a", "string", ""), p("b", "string", "")], returns: r("number", "") }),
            ],
          },
        ],
      },
      {
        id: "RulePayload",
        name: "RulePayload",
        kind: "typedef",
        summary: "Table accepted by rules.create / upsert / edit and RuleBuilder.build().",
        props: [
          prop("id", "string", "Local id. Stored as `p:{pluginId}:{sanitized}` unless already prefixed."),
          prop("name", "string", "Max 40 characters. `Discord` is rejected."),
          prop("enabled", "boolean", "Default true."),
          prop("strategy", "string", "`automatic` (default), `basic`, `balanced`, `aggressive`, `custom`."),
          prop("domains", "string[]", "1-based array, max 32 valid patterns."),
        ],
        sections: [],
      },
      {
        id: "Rule",
        name: "Rule",
        kind: "typedef",
        summary: "Live rule object returned by the rules manager. Includes payload fields plus methods.",
        props: [
          prop("id", "string", "Full id including `p:{pluginId}:` prefix."),
          prop("name", "string", "Display name."),
          prop("enabled", "boolean", "Whether the rule is on."),
          prop("strategy", "string", "Lowercase strategy name."),
          prop("domains", "string[]", "Patterns."),
        ],
        sections: [
          {
            title: "Instance methods",
            members: [
              m("edit", "edit(patch)", "Write permission. Keeps this id.", { perm: "rules.write", params: [p("patch", "RulePayload", "")], returns: r("Rule", "") }),
              m("delete", "delete()", "Remove this rule.", { perm: "rules.write" }),
              m("enable", "enable()", "Turn on.", { perm: "rules.write" }),
              m("disable", "disable()", "Turn off.", { perm: "rules.write" }),
              m("toJSON", "toJSON()", "Plain payload clone.", { returns: r("RulePayload", "") }),
            ],
          },
        ],
      },
      {
        id: "HostEntry",
        name: "HostEntry",
        kind: "typedef",
        summary: "One overlay row. Fields are duplicated as host/hostname and ip/ipv4.",
        props: [
          prop("host", "string", "Normalized hostname or wildcard pattern."),
          prop("hostname", "string", "Same as host."),
          prop("ip", "string", "Dotted IPv4."),
          prop("ipv4", "string", "Same as ip."),
        ],
        sections: [
          {
            title: "Instance methods",
            members: [
              m("delete", "delete()", "Remove this hostname from the overlay.", { perm: "hosts.write" }),
              m("toJSON", "toJSON()", "`{ host, ip }`.", { returns: r("table", "") }),
            ],
          },
        ],
      },
      {
        id: "VpnSnapshot",
        name: "VpnSnapshot",
        kind: "typedef",
        summary: "Coarse engine counters from vpn.snapshot(). No packet payloads.",
        props: [
          prop("phase", "string", "Current phase."),
          prop("packets_processed", "number", "Processed packets."),
          prop("packets_modified", "number", "Modified packets."),
          prop("packets_dropped", "number", "Dropped packets."),
          prop("bytes_in", "number", "Bytes in."),
          prop("bytes_out", "number", "Bytes out."),
          prop("dns_queries", "number", "DNS queries."),
          prop("active_tcp", "number", "Active TCP."),
          prop("active_udp", "number", "Active UDP."),
          prop("engine_alive", "boolean", "Native engine up."),
          prop("tun_active", "boolean", "TUN up."),
          prop("uptime_seconds", "number", "Uptime."),
          prop("strategy", "string", "Current strategy."),
        ],
        sections: [],
      },
      {
        id: "AppConfig",
        name: "AppConfig",
        kind: "typedef",
        summary: "Read-only protection settings from app.config() (app.read).",
        props: [
          prop("mode", "string", "DPI mode."),
          prop("dns_mode", "string", "DNS mode."),
          prop("mtu", "number", "MTU."),
          prop("ipv6_mode", "string", "IPv6 mode."),
          prop("block_quic", "boolean", "QUIC blocked."),
          prop("log_level", "number", "Log level."),
          prop("per_app_mode", "string", "Per-app mode."),
          prop("fragment_size", "number", "Fragment size."),
          prop("tcp_fragmentation", "boolean", "TCP fragmentation."),
          prop("http_host_case", "boolean", "HTTP host-case trick."),
          prop("start_on_boot", "boolean", "Start on boot."),
          prop("auto_reconnect", "boolean", "Auto reconnect."),
        ],
        sections: [],
      },
      {
        id: "PermissionFlagsBits",
        name: "PermissionFlagsBits",
        kind: "typedef",
        summary: "String constants for every permission. luna.PermissionFlagsBits.Storage === \"storage\". Both enum names (STORAGE) and manifest keys (storage) are set. PascalCase shortcuts: Storage, UiSettings, RulesRead, RulesWrite, VpnRead, VpnControl, Notify, HostsWrite, AppRead.",
        props: [
          prop("Storage", "string", "`storage`"),
          prop("UiSettings", "string", "`ui.settings`"),
          prop("RulesRead", "string", "`rules.read`"),
          prop("RulesWrite", "string", "`rules.write`"),
          prop("VpnRead", "string", "`vpn.read`"),
          prop("VpnControl", "string", "`vpn.control`"),
          prop("Notify", "string", "`notify`"),
          prop("HostsWrite", "string", "`hosts.write`"),
          prop("AppRead", "string", "`app.read`"),
        ],
        sections: [],
      },
      {
        id: "Page",
        name: "Page",
        kind: "typedef",
        summary: "Return value of settings_page(). `{ type = \"page\", title, description, sections }` where each section is `{ type = \"section\", title, items }`.",
        props: [
          prop("type", "string", "Always `page`."),
          prop("title", "string", "Screen title."),
          prop("description", "string", "Optional subtitle."),
          prop("sections", "table[]", "Up to 10 sections."),
        ],
        sections: [],
      },
    ],
  };
})();
