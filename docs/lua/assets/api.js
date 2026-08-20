(() => {
  const p = (name, type, desc, optional) => ({ name, type, desc, optional: !!optional });
  const r = (type, desc) => ({ type, desc });
  const m = (name, sig, desc, extra = {}) => ({ kind: "method", name, sig, desc, ...extra });
  const prop = (name, type, desc, extra = {}) => ({ kind: "prop", name, type, desc, ...extra });

  const lua = (src) => src.replace(/^\n/, "").replace(/\n$/, "");

  window.LUNA_DOCS = {
    package: "luna",
    apiLevel: 2,
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
              <tr><td><code>luna.fs</code> / <code>luna.assets</code></td><td><a href="#Fs">Fs</a></td><td>Read files shipped in the ZIP (<code>txt</code>, <code>json</code>, <code>md</code>, <code>lua</code>, <code>svg</code>, <code>csv</code>).</td></tr>
              <tr><td><code>luna.debug</code> / <code>luna.dev</code></td><td><a href="#Debug">Debug</a></td><td>Inspect, snapshot, reload the VM.</td></tr>
              <tr><td><code>luna.schema</code></td><td><a href="#Schema">Schema</a></td><td>Validate tables before writes.</td></tr>
              <tr><td><code>luna.sdk</code></td><td>table</td><td>Index of SDK types: List, Set, Store, URL, FormBuilder, …</td></tr>
              <tr><td><code>luna.kit</code> / <code>luna.forge</code></td><td>table</td><td>Build-your-own toolbox: machines, dashboards, schedules, validators, …</td></tr>
              <tr><td><code>luna.features</code></td><td>table</td><td>Booleans for api_level 2 capabilities.</td></tr>
              <tr><td><code>luna.API_LEVEL</code></td><td>number</td><td>Currently <code>2</code>. Same as <code>luna.version</code>.</td></tr>
            </tbody>
          </table>
        `,
      },
      {
        id: "api-2",
        title: "api_level 2",
        html: `
          <p class="lead">This app speaks <strong>api_level 2</strong> as a developer kit. Plugins that still declare <code>api_level: 1</code> load as before. Declare <code>2</code> when you use the new surface.</p>
          <h2 class="section-title">What is new</h2>
          <table>
            <thead><tr><th>Feature</th><th>Why it exists</th></tr></thead>
            <tbody>
              <tr><td><a href="#Fs"><code>luna.fs</code></a></td><td>Ship host lists and JSON next to Lua instead of stuffing them into <code>main.lua</code>.</td></tr>
              <tr><td><code>luna.hosts.load_file</code> / <code>merge</code></td><td>Apply or union packaged hosts files.</td></tr>
              <tr><td><a href="#Debug"><code>luna.debug</code></a></td><td>Inspect values, assert, time a function, snapshot runtime caps, reload the VM.</td></tr>
              <tr><td><a href="#Schema"><code>luna.schema</code></a></td><td>Validate tables before writing rules or hosts.</td></tr>
              <tr><td><code>luna.ui.reload()</code></td><td>Rebuild the open settings page after a timer or button.</td></tr>
              <tr><td><code>enabled = false</code> on controls</td><td>Grey out switches, fields, and buttons.</td></tr>
              <tr><td><code>Events.SettingChanged</code> / <code>Error</code></td><td>Bus events plus optional <code>on_error(message)</code>.</td></tr>
              <tr><td><code>Events.VpnConnected</code> / <code>VpnDisconnected</code></td><td>Convenience events on top of <code>vpnPhase</code>.</td></tr>
              <tr><td>Rate-limit reads</td><td><code>notify.allowed()</code>, <code>vpn.can_control()</code>, <code>clock.remaining()</code>.</td></tr>
              <tr><td>Batch writes</td><td><code>rules.create_many</code>, <code>storage.mset</code> / <code>mget</code>.</td></tr>
              <tr><td>Host tooling</td><td>Plugin detail: reload VM without re-importing the ZIP, always-on log, clear log.</td></tr>
              <tr><td>i18n vars</td><td><code>t("hi", "Hello {name}", { name = x })</code>.</td></tr>
              <tr><td>Higher caps</td><td>32 rules, 8 timers, 1s–120s, 96 storage keys, 32 modules, 12 UI sections. ZIP may include <code>.csv</code>.</td></tr>
              <tr><td>SDK types</td><td><a href="#List"><code>List</code></a>, <a href="#Set"><code>Set</code></a>, <a href="#Store"><code>Store</code></a>, <a href="#URL"><code>URL</code></a>, <a href="#FormBuilder"><code>FormBuilder</code></a>, and the rest under <a href="#sdk">SDK</a>. Also <code>luna.string.camel</code>, <code>luna.table.reduce</code>, <code>luna.systems</code>.</td></tr>
            </tbody>
          </table>
          <h2 class="section-title">Example</h2>
          <div class="code"><pre><span class="kw">local</span> client = luna.Client

<span class="kw">function</span> on_enable()
  client.logInfo(luna.debug.inspect(client.snapshot()))
  <span class="kw">if</span> client:hasPermission(<span class="st">"hosts.write"</span>) <span class="kw">then</span>
    client.loadHostsFile(<span class="st">"lists/block.txt"</span>)
  <span class="kw">end</span>
<span class="kw">end</span>

<span class="kw">function</span> on_error(message)
  client.logError(message)
<span class="kw">end</span>

client:on(luna.Events.SettingChanged, <span class="kw">function</span>(id, value)
  client.logInfo(id .. <span class="st">" = "</span> .. tostring(value))
  client.reloadSettings()
<span class="kw">end</span>)</pre></div>
          <div class="callout">
            <h4>Still forbidden</h4>
            <p>No network fetch, no TUN writes, no TLS, no shell, no reading other apps. <code>luna.fs</code> only sees files inside this plugin's unpacked ZIP. <code>luna.debug.reload()</code> restarts this plugin's VM on the device; it does not pull code from GitHub.</p>
          </div>
        `,
      },
      {
        id: "sdk",
        title: "SDK types",
        html: `
          <p class="lead">api_level 2 ships a local SDK: collections, URL/query parse, templates, stores, metrics, and form builders. Everything stays inside the sandbox. There is still no HTTP client, TUN write, TLS, shell, or Java.</p>
          <h2 class="section-title">Where to find them</h2>
          <p>Types live on <code>luna</code>, <code>luna.sdk</code>, and <code>luna.Client</code>. Constructors on Client are colon-safe: <code>client:newList()</code>, <code>client:form("Focus")</code>.</p>
          <table>
            <thead><tr><th>Group</th><th>Types</th></tr></thead>
            <tbody>
              <tr><td>Collections</td><td><code>List</code> <code>Set</code> <code>Queue</code> <code>Stack</code> <code>LRU</code> <code>RingBuffer</code> <code>Collection</code></td></tr>
              <tr><td>Net (parse only)</td><td><code>URL</code> <code>Query</code> <code>DomainSet</code> <code>CidrSet</code> <code>Matcher</code></td></tr>
              <tr><td>Text</td><td><code>Template</code> <code>Buffer</code> <code>Diff</code> <code>Fuzzy</code> <code>Csv</code> <code>Glob</code> <code>Interval</code></td></tr>
              <tr><td>Reactive</td><td><code>Store</code> <code>Dict</code> <code>Signal</code> <code>State</code> <code>Flags</code> <code>Memo</code> <code>Registry</code></td></tr>
              <tr><td>Ops</td><td><code>Logger</code> <code>Metrics</code> <code>Histogram</code> <code>Stopwatch</code> <code>Random</code> <code>Result</code> <code>Optional</code> <code>Range</code> <code>BitSet</code></td></tr>
              <tr><td>UI builders</td><td><code>FormBuilder</code> <code>WizardBuilder</code> <code>TableBuilder</code> <code>Dashboard</code> <code>SchemaForm</code></td></tr>
              <tr><td>Kit (build your own system)</td><td><code>Machine</code> <code>Pipeline</code> <code>Router</code> <code>Actions</code> <code>Config</code> <code>Expr</code> <code>TableQuery</code> <code>Bus</code> <code>Policy</code> <code>Catalog</code> — see <a href="#kit">Kit</a></td></tr>
              <tr><td>Forge (systems the app does not ship)</td><td><code>Schedule</code> <code>Ruleset</code> <code>Validator</code> <code>Bloom</code> <code>Circuit</code> <code>Health</code> <code>Ledger</code> <code>Workflow</code> — see <a href="#forge">Forge</a></td></tr>
              <tr><td>Meta</td><td><code>Enums</code> <code>Constants</code> <code>systems</code> <code>luna.kit</code> <code>luna.forge</code></td></tr>
            </tbody>
          </table>
          <h2 class="section-title">Example</h2>
          <div class="code"><pre><span class="kw">local</span> client = luna.Client
<span class="kw">local</span> hosts = luna.DomainSet.new()
hosts:add(<span class="st">"*.example.com"</span>)

<span class="kw">function</span> settings_page()
  <span class="kw">return</span> client:form(<span class="st">"Focus"</span>)
    :note({ text = <span class="st">"Local lists only"</span> })
    :build()
<span class="kw">end</span></pre></div>
          <div class="callout">
            <h4>Caps</h4>
            <p>Collections stop at 256 items. Stores keep 64 keys. Signals keep 8 listeners. <code>Interval.parse</code> clamps to 1–120 seconds (timer range). URL parse does not fetch.</p>
          </div>
        `,
      },
      {
        id: "kit",
        title: "Build your own system",
        html: `
          <p class="lead">The host is a DPI app. Plugins invent the rest: blocklists, dashboards, wizards, quotas, search, feature flags. <code>luna.kit</code> is the toolbox so you do not wait for a missing app feature.</p>
          <h2 class="section-title">Map an idea to a type</h2>
          <table>
            <thead><tr><th>You want…</th><th>Start here</th></tr></thead>
            <tbody>
              <tr><td>States (idle → running → done)</td><td><a href="#Machine"><code>Machine</code></a></td></tr>
              <tr><td>Middleware / transform chain</td><td><a href="#Pipeline"><code>Pipeline</code></a></td></tr>
              <tr><td>Undo / redo</td><td><a href="#History"><code>History</code></a></td></tr>
              <tr><td>TTL memory</td><td><a href="#Cache"><code>Cache</code></a></td></tr>
              <tr><td>Local routes <code>/hosts/:id</code></td><td><a href="#Router"><code>Router</code></a></td></tr>
              <tr><td>Named commands</td><td><a href="#Actions"><code>Actions</code></a></td></tr>
              <tr><td>Typed defaults + optional persist</td><td><a href="#Config"><code>Config</code></a></td></tr>
              <tr><td>Safe <code>n &gt; 10 and on</code></td><td><a href="#Expr"><code>Expr</code></a> (no Lua eval, no network)</td></tr>
              <tr><td>Filter / sort / page rows</td><td><a href="#TableQuery"><code>TableQuery</code></a>, <a href="#Paginator"><code>Paginator</code></a></td></tr>
              <tr><td>In-plugin pub/sub</td><td><a href="#Bus"><code>Bus</code></a>, <a href="#Channel"><code>Channel</code></a></td></tr>
              <tr><td>Allow / deny hosts</td><td><a href="#Policy"><code>Policy</code></a>, <a href="#DomainSet"><code>DomainSet</code></a></td></tr>
              <tr><td>Status screen</td><td><a href="#Dashboard"><code>Dashboard</code></a> (<code>stat</code>, <code>list_item</code>, <code>empty</code>, <code>chips</code>)</td></tr>
              <tr><td>Settings from a schema table</td><td><a href="#SchemaForm"><code>SchemaForm</code></a></td></tr>
              <tr><td>Search a catalog</td><td><a href="#SearchIndex"><code>SearchIndex</code></a>, <a href="#Catalog"><code>Catalog</code></a></td></tr>
              <tr><td>Rate / quota / once</td><td><a href="#RateLimit"><code>RateLimit</code></a>, <a href="#Quota"><code>Quota</code></a>, <a href="#Once"><code>Once</code></a>, <a href="#Throttle"><code>Throttle</code></a></td></tr>
              <tr><td>First-match rules</td><td><a href="#Matchbook"><code>Matchbook</code></a></td></tr>
              <tr><td>Nested JSON fields</td><td><a href="#JsonPath"><code>JsonPath</code></a>, <a href="#Deep"><code>Deep</code></a></td></tr>
              <tr><td>Time windows / work hours / cron-lite</td><td><a href="#Schedule"><code>Schedule</code></a></td></tr>
              <tr><td>Compose glob + domain + CIDR matchers</td><td><a href="#Ruleset"><code>Ruleset</code></a></td></tr>
              <tr><td>Form field errors</td><td><a href="#Validator"><code>Validator</code></a></td></tr>
              <tr><td>Huge membership without storing every host</td><td><a href="#Bloom"><code>Bloom</code></a></td></tr>
              <tr><td>Degrade after N failures</td><td><a href="#Circuit"><code>Circuit</code></a></td></tr>
              <tr><td>Credits / budgets / A-B pick</td><td><a href="#Ledger"><code>Ledger</code></a>, <a href="#Weighted"><code>Weighted</code></a>, <a href="#Ranker"><code>Ranker</code></a></td></tr>
              <tr><td>Onboarding steps / presets / board</td><td><a href="#Workflow"><code>Workflow</code></a>, <a href="#Preset"><code>Preset</code></a>, <a href="#Kanban"><code>Kanban</code></a></td></tr>
            </tbody>
          </table>
          <p>Still forbidden: HTTP fetch, TUN, TLS, shell, Java, other apps. If the idea needs those, it cannot ship as a plugin.</p>
          <h2 class="section-title">Example: local “mode machine” + dashboard</h2>
          <div class="code"><pre><span class="kw">local</span> m = luna.Machine.new(<span class="st">"idle"</span>)
m:on(<span class="st">"idle"</span>, <span class="st">"start"</span>, <span class="st">"on"</span>)
m:on(<span class="st">"on"</span>, <span class="st">"stop"</span>, <span class="st">"idle"</span>)

<span class="kw">function</span> settings_page()
  <span class="kw">return</span> luna.Dashboard.new(<span class="st">"Focus"</span>)
    :stat({ label = <span class="st">"State"</span>, value = m:state() })
    :empty({ text = <span class="st">"No hosts yet"</span>, hint = <span class="st">"Import a list from the ZIP"</span> })
    :build()
<span class="kw">end</span></pre></div>
        `,
      },
      {
        id: "forge",
        title: "Invent a system",
        html: `
          <p class="lead">The host will never ship every idea. <code>luna.forge</code> (also on <code>luna.kit</code> and <code>Client</code>) is the layer for features that do not exist in the app: schedules, validators, credit ledgers, kanban, bloom filters, circuit breakers, wizards.</p>
          <h2 class="section-title">If you can name it, start here</h2>
          <table>
            <thead><tr><th>The thought</th><th>API</th></tr></thead>
            <tbody>
              <tr><td>Only active 09:00–18:00 weekdays</td><td><a href="#Schedule"><code>Schedule.window(9, 18, {1,2,3,4,5})</code></a> or <code>Schedule.cron("0 9-17 * * 1-5")</code></td></tr>
              <tr><td>My own block engine (glob + domain + CIDR)</td><td><a href="#Ruleset"><code>Ruleset</code></a></td></tr>
              <tr><td>Settings form with field errors</td><td><a href="#Validator"><code>Validator</code></a> + <a href="#FormBuilder"><code>FormBuilder</code></a></td></tr>
              <tr><td>Million-ish “have I seen this host?”</td><td><a href="#Bloom"><code>Bloom</code></a> (probabilistic, in-memory)</td></tr>
              <tr><td>Stop applying rules after repeated failures</td><td><a href="#Circuit"><code>Circuit</code></a></td></tr>
              <tr><td>Status / SLO screen</td><td><a href="#Health"><code>Health</code></a> + <a href="#Dashboard"><code>Dashboard</code></a> (<code>stat</code>, <code>score</code>, <code>status</code>, <code>steps</code>)</td></tr>
              <tr><td>Credits, quotas, points</td><td><a href="#Ledger"><code>Ledger</code></a>, <a href="#Quota"><code>Quota</code></a>, <a href="#Scorecard"><code>Scorecard</code></a></td></tr>
              <tr><td>A/B or lottery between lists</td><td><a href="#Weighted"><code>Weighted</code></a></td></tr>
              <tr><td>Rank hosts by a score you invent</td><td><a href="#Ranker"><code>Ranker</code></a>, <a href="#Facets"><code>Facets</code></a></td></tr>
              <tr><td>Named configs the user can switch</td><td><a href="#Preset"><code>Preset</code></a></td></tr>
              <tr><td>Multi-step setup</td><td><a href="#Workflow"><code>Workflow</code></a> + <code>luna.ui.steps</code></td></tr>
              <tr><td>Storage schema v1 → v2</td><td><a href="#Migration"><code>Migration</code></a></td></tr>
              <tr><td>Parse INI / hosts / CSV in Lua</td><td><a href="#Ini"><code>Ini</code></a>, <a href="#Tokens"><code>Tokens</code></a>, <a href="#Csv"><code>Csv</code></a></td></tr>
              <tr><td>Sparkline or search highlight</td><td><a href="#Spark"><code>Spark</code></a>, <a href="#Highlight"><code>Highlight</code></a></td></tr>
              <tr><td>Cluster / merge duplicate names</td><td><a href="#UnionFind"><code>UnionFind</code></a></td></tr>
              <tr><td>Board of cards</td><td><a href="#Kanban"><code>Kanban</code></a> (<code>to_ui()</code> → list items)</td></tr>
              <tr><td>JSON pointer <code>/a/b</code></td><td><a href="#JsonPtr"><code>JsonPtr</code></a></td></tr>
              <tr><td>Heartbeat / every N seconds</td><td><a href="#Watchdog"><code>Watchdog</code></a>, <a href="#Recur"><code>Recur</code></a></td></tr>
            </tbody>
          </table>
          <p>UI nodes the app does render for you: <code>fold</code>, <code>steps</code>, <code>timeline</code>, <code>score</code>, <code>compare</code>, <code>faq</code>, <code>status</code> (plus the older <code>stat</code> / <code>list_item</code> / <code>empty</code> / <code>chips</code> / <code>quote</code>). Interactive controls stay switch/text/select/slider/button.</p>
          <h2 class="section-title">Example: weekday filter + ruleset + dashboard</h2>
          <div class="code"><pre><span class="kw">local</span> client = luna.Client
<span class="kw">local</span> hours = luna.Schedule.window(<span class="nu">9</span>, <span class="nu">18</span>, {<span class="nu">1</span>,<span class="nu">2</span>,<span class="nu">3</span>,<span class="nu">4</span>,<span class="nu">5</span>})
<span class="kw">local</span> set = luna.Ruleset.new()
set:glob(<span class="st">"*.ads.example"</span>)

<span class="kw">function</span> settings_page()
  <span class="kw">local</span> on = hours:active()
  <span class="kw">return</span> client:dashboard(<span class="st">"Focus hours"</span>)
    :status({ text = <span class="st">"Window"</span>, tone = on <span class="kw">and</span> <span class="st">"success"</span> <span class="kw">or</span> <span class="st">"warn"</span>, detail = on <span class="kw">and</span> <span class="st">"active"</span> <span class="kw">or</span> <span class="st">"idle"</span> })
    :steps({ labels = { <span class="st">"Hours"</span>, <span class="st">"Rules"</span>, <span class="st">"Live"</span> }, current = on <span class="kw">and</span> <span class="nu">3</span> <span class="kw">or</span> <span class="nu">1</span> })
    :fold({ title = <span class="st">"How it works"</span>, body = <span class="st">"This plugin invented the schedule. The app has no focus-hours feature."</span> })
    :build()
<span class="kw">end</span></pre></div>
          <div class="callout">
            <h4>Closed doors stay closed</h4>
            <p>Forge cannot fetch URLs, write TUN, intercept TLS, run shell, or read other apps. Build local systems. Persist with <code>storage</code> if the user granted it.</p>
          </div>
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
              <tr><td>Allowed extensions</td><td><code>lua</code>, <code>json</code>, <code>svg</code>, <code>png</code>, <code>md</code>, <code>txt</code>, <code>csv</code></td></tr>
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
          <p class="lead"><code>manifest.json</code> is the plugin's identity. <code>api_level</code> must be <strong>1</strong> or <strong>2</strong>. This app speaks 2; older plugins stay on 1.</p>
          <div class="code"><pre>{
  "id": "community.focus.list",
  "name": "Focus list",
  "author": "Your name",
  "version": "1.0.0",
  "description": "Short description (max 280 characters).",
  "api_level": 2,
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
              <tr><td><code>storage</code></td><td>96 keys, 32 KB per value, plugin-private</td><td>Other plugins, app settings</td></tr>
              <tr><td><code>ui.settings</code></td><td><code>settings_page()</code> in-app screen</td><td>Arbitrary Compose / WebView</td></tr>
              <tr><td><code>rules.read</code></td><td>Rules prefixed <code>p:{id}:</code></td><td>Discord / user rules</td></tr>
              <tr><td><code>rules.write</code></td><td>Up to 32 own rules, 32 domains each</td><td>Overwriting built-in rules</td></tr>
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
              <tr><td><code>on_error(message)</code></td><td>Optional. A later hook threw; the plugin stays loaded unless it was <code>on_enable</code></td></tr>
            </tbody>
          </table>
          <p>Also on the event bus: <code>ready</code>, <code>vpnPhase</code>, <code>settingChanged</code>, <code>vpnConnected</code>, <code>vpnDisconnected</code>.</p>
          <p>Plugin-owned rules are removed when the plugin is disabled or uninstalled. Hosts overlay is cleared the same way.</p>
          <h2 class="section-title">Timers</h2>
          <p>At most <strong>8</strong> timers. Delay is clamped to <strong>1s–120s</strong>. Use <code>client.setTimeout</code> / <code>setInterval</code> or <code>luna.clock</code>.</p>
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
              <tr><td>GET</td><td><code>/fs</code>, <code>/fs/{path}</code> or <code>/assets/…</code></td><td>list packaged files / read text</td></tr>
              <tr><td>GET</td><td><code>/debug</code> or <code>/dev</code></td><td>runtime snapshot</td></tr>
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
          prop("apiLevel", "number", "Always `2` for this app."),
          prop("pluginId", "string", "Same as `id`."),
          prop("readyAt", "number", "Unix seconds when this Client table was built."),
          prop("isReady", "boolean", "Always `true` after load."),
          prop("application", "table", "`{ name = \"Lunas DPI\", version, id = \"com.lunasdev.lunasdpi\" }`."),
          prop("options", "table", "Sandbox caps: `max_rules` 32, `max_hosts` 256, `max_timers` 8, `min_timer_ms` 1000, `max_timer_ms` 120000, `max_storage_keys` 96, `max_storage_chars` 32768, `sandbox` true."),
          prop("capabilities", "table", "Same as `luna.features`."),
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
          prop("fs", "Fs", "Packaged files. Alias `luna.assets`."),
          prop("sdk", "table", "Index of SDK constructor tables (`luna.sdk.List`, …)."),
          prop("systems", "table", "Capability flags. `network` / `tun` / `tls` / `shell` / `java` are always false."),
          prop("List", "List", "Array collection type."),
          prop("Set", "Set", "Unique string set."),
          prop("Store", "Store", "In-memory reactive map."),
          prop("FormBuilder", "FormBuilder", "Single-section settings builder."),
          prop("Enums", "table", "VpnPhase, LogLevel, Tone, RuleKind, SettingKind."),
          prop("Constants", "table", "API_LEVEL, MAX_RULES, MAX_TIMERS, …"),
        ],
        sections: [
          {
            title: "Events",
            members: [
              m("on", "on(event, listener)", "Subscribe. See Events for names.", {
                params: [p("event", "string", "Event name."), p("listener", "function", "Callback. `vpnPhase` receives the phase string; `settingChanged` receives id and value.")],
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
              m("createRule", "createRule(payload)", "Create a plugin-owned rule. Ids are prefixed `p:{pluginId}:`. Name `Discord` is reserved. Max 32 rules, 32 domains.", {
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
              m("loadHostsFile", "loadHostsFile(path)", "Read a packaged hosts file via `luna.fs` and apply it. Same result as `setHostsText`.", {
                perm: "hosts.write",
                params: [p("path", "string", "Relative path inside the ZIP, e.g. `lists/block.txt`.")],
                returns: r("table", "`{ applied, skipped, errors }`"),
              }),
            ],
          },
          {
            title: "Package files",
            members: [
              m("readFile", "readFile(path)", "UTF-8 text of a packaged file, or nil.", { params: [p("path", "string", "Relative path.")], returns: r("string|nil", ""), aliases: ["readAsset"] }),
              m("fileExists", "fileExists(path)", "Whether a readable packaged file exists.", { params: [p("path", "string", "")], returns: r("boolean", ""), aliases: ["assetExists"] }),
              m("listFiles", "listFiles(dir?)", "Relative paths under `dir` (or the package root).", { params: [p("dir", "string", "Folder, or omit for the whole ZIP.", true)], returns: r("string[]", ""), aliases: ["listAssets"] }),
              m("readJsonFile", "readJsonFile(path)", "Decode a packaged JSON object or array.", { params: [p("path", "string", "")], returns: r("table", "") }),
              m("readLines", "readLines(path)", "Array of lines (max 2048).", { params: [p("path", "string", "")], returns: r("string[]", "") }),
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
              m("setTimeout", "setTimeout(ms, fn)", "One-shot timer. Delay clamped 1000–120000. Max 8 timers.", { params: [p("ms", "number", ""), p("fn", "function", "")], returns: r("number", "Timer id."), aliases: ["after"] }),
              m("setInterval", "setInterval(ms, fn)", "Repeating timer. Default delay 5000 if omitted at the clock layer.", { params: [p("ms", "number", ""), p("fn", "function", "")], returns: r("number", "Timer id.") }),
              m("clearTimeout", "clearTimeout(id)", "Cancel a timer.", { params: [p("id", "number", "")], aliases: ["clearInterval"] }),
              m("t", "t(key, fallback?, vars?)", "Translate from `locale/{lang}.json`. Optional `vars` replaces `{name}` / `%{name}`. Result max 400 chars.", { params: [p("key", "string", ""), p("fallback", "string", "Defaults to the key.", true), p("vars", "table", "`{ name = \"Luna\" }`.", true)], returns: r("string", ""), aliases: ["translate"] }),
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
              m("reloadSettings", "reloadSettings()", "Rebuild the open settings page. Ignored while `settings_page()` is running.", { returns: r("boolean", ""), aliases: ["refreshSettings"] }),
              m("snapshot", "snapshot()", "Runtime debug snapshot (`luna.debug.snapshot`).", { returns: r("table", "") }),
              m("reloadPlugin", "reloadPlugin()", "Restart this plugin's VM.", { returns: r("boolean", "") }),
              m("inspect", "inspect(value)", "Pretty-print a value.", { params: [p("value", "any", "")], returns: r("string", "") }),
              m("collection", "collection()", "`Collection.new()`.", { returns: r("Collection", "") }),
              m("collectionFrom", "collectionFrom(rows)", "`Collection.from(rows)`.", { params: [p("rows", "table", "{ key, value } or { k, v } pairs")], returns: r("Collection", "") }),
              m("newList", "newList()", "Empty `List`.", { returns: r("List", "") }),
              m("listFrom", "listFrom(rows)", "`List.from(array)`.", { params: [p("rows", "table", "1-based array")], returns: r("List", "") }),
              m("newSet", "newSet()", "Empty `Set`.", { returns: r("Set", "") }),
              m("setFrom", "setFrom(rows)", "`Set.from(array)`.", { params: [p("rows", "table", "")], returns: r("Set", "") }),
              m("newStore", "newStore()", "Empty reactive `Store`.", { returns: r("Store", "") }),
              m("newFlags", "newFlags()", "In-memory feature flags.", { returns: r("Flags", "") }),
              m("newLogger", "newLogger(prefix?)", "Prefixed logger.", { params: [p("prefix", "string", "", true)], returns: r("Logger", "") }),
              m("newMetrics", "newMetrics()", "Counters and gauges.", { returns: r("Metrics", "") }),
              m("form", "form(title?)", "`FormBuilder.new(title)`.", { params: [p("title", "string", "", true)], returns: r("FormBuilder", "") }),
              m("wizard", "wizard(title?)", "`WizardBuilder.new(title)`.", { params: [p("title", "string", "", true)], returns: r("WizardBuilder", "") }),
              m("tableView", "tableView(title?)", "`TableBuilder.new(title)`.", { params: [p("title", "string", "", true)], returns: r("TableBuilder", "") }),
              m("parseUrl", "parseUrl(raw)", "Parse a URL. Does not fetch.", { params: [p("raw", "string", "")], returns: r("URL", "") }),
              m("template", "template(src, vars)", "Substitute `{name}` / `%{name}` / `{{name}}`.", { params: [p("src", "string", ""), p("vars", "table", "")], returns: r("string", "") }),
              m("fuzzy", "fuzzy(a, b)", "Similarity 0–1.", { params: [p("a", "string", ""), p("b", "string", "")], returns: r("number", "") }),
              m("interval", "interval(text)", "Parse `5s` / `2m` into seconds, clamped 1–120.", { params: [p("text", "string", "")], returns: r("number", "") }),
              m("csv", "csv(text)", "Parse CSV into rows.", { params: [p("text", "string", "")], returns: r("string[][]", "") }),
              m("glob", "glob(pattern, value)", "Case-insensitive `*` match.", { params: [p("pattern", "string", ""), p("value", "string", "")], returns: r("boolean", "") }),
              m("circuit", "circuit()", "`Circuit.new()`.", { returns: r("Circuit", "") }),
              m("bloom", "bloom(bits?)", "`Bloom.new(bits)`.", { params: [p("bits", "number", "", true)], returns: r("Bloom", "") }),
              m("validator", "validator()", "`Validator.new()`.", { returns: r("Validator", "") }),
              m("ruleset", "ruleset()", "`Ruleset.new()`.", { returns: r("Ruleset", "") }),
              m("health", "health()", "`Health.new()`.", { returns: r("Health", "") }),
              m("ledger", "ledger()", "`Ledger.new()`.", { returns: r("Ledger", "") }),
              m("workflow", "workflow(names)", "`Workflow.new({ \"setup\", \"done\" })`.", { params: [p("names", "string[]", "")], returns: r("Workflow", "") }),
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
          prop("api_level", "function", "Number 2. Also `apiLevel()`."),
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
          prop("SettingChanged", "string", "`\"settingChanged\"`. Aliases: `SETTING_CHANGED`."),
          prop("VpnConnected", "string", "`\"vpnConnected\"`. Aliases: `VPN_CONNECTED`."),
          prop("VpnDisconnected", "string", "`\"vpnDisconnected\"`. Aliases: `VPN_DISCONNECTED`."),
          prop("Error", "string", "`\"error\"`. Aliases: `ERROR`."),
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
              m("SettingChanged", "Events.SettingChanged", "Fired after a settings control changes. Listener receives `id, value`.", {
                kind: "prop",
              }),
              m("VpnConnected", "Events.VpnConnected", "Fired when phase becomes `connected`.", { kind: "prop" }),
              m("VpnDisconnected", "Events.VpnDisconnected", "Fired when phase becomes `disconnected`.", { kind: "prop" }),
              m("Error", "Events.Error", "Fired when a hook throws after load. Listener receives the message string.", { kind: "prop" }),
            ],
          },
        ],
      },
      {
        id: "EventEmitter",
        name: "EventEmitter",
        kind: "class",
        summary: "luna.events — Node-style emitter used by Client:on / once / off. Extra constants READY, VPN_PHASE, SETTING_CHANGED, VPN_CONNECTED, VPN_DISCONNECTED live here too.",
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
        summary: "luna.storage — private string map, 96 keys. Requires storage. Aliases: getJSON/setJSON/getNumber/setNumber/getBool/setBool, delete = remove, length = size.",
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
              m("create", "create(payload)", "Insert. Throws if you already have 32 rules.", { perm: "rules.write", params: [p("payload", "RulePayload", "")], returns: r("Rule", "") }),
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
              m("load_file", "load_file(path)", "Read packaged hosts text and apply. Alias `loadFile`.", { perm: "hosts.write", params: [p("path", "string", "")], returns: r("table", "") }),
            ],
          },
        ],
      },
      {
        id: "Fs",
        name: "Fs",
        kind: "class",
        summary: "luna.fs (alias luna.assets) — read files from this plugin's unpacked ZIP. No disk escape, no PNG/binary. Max 128 KB per file.",
        construct: "Not constructed. Use luna.fs or client.readFile.",
        constructExample: "local text = luna.fs.read(\"lists/block.txt\")\nlocal names = luna.fs.list(\"lists\")",
        sections: [
          {
            title: "Methods",
            members: [
              m("read", "read(path)", "UTF-8 text or nil if missing. Throws on unsafe paths or files over 128 KB.", { params: [p("path", "string", "Relative, e.g. `lists/hosts.txt`.")], returns: r("string|nil", ""), aliases: ["load", "readText"] }),
              m("exists", "exists(path)", "Readable packaged file?", { params: [p("path", "string", "")], returns: r("boolean", "") }),
              m("list", "list(dir?)", "Relative paths. Empty/`nil` lists the whole package (readable types only).", { params: [p("dir", "string", "Folder.", true)], returns: r("string[]", "") }),
              m("lines", "lines(path)", "Up to 2048 lines. Throws if the file is missing.", { params: [p("path", "string", "")], returns: r("string[]", "") }),
              m("json", "json(path)", "Decode object or array. Throws if missing or not JSON.", { params: [p("path", "string", "")], returns: r("table", ""), aliases: ["readJSON"] }),
            ],
          },
        ],
      },
      {
        id: "Debug",
        name: "Debug",
        kind: "class",
        summary: "luna.debug (alias luna.dev) — inspect values, assert, time callbacks, dump a runtime snapshot, reload this plugin's VM. Also client.inspect / snapshot / reloadPlugin.",
        construct: "Not constructed.",
        constructExample: "print(luna.debug.inspect({ host = \"a.com\", n = 2 }))\nluna.debug.reload()",
        sections: [
          {
            title: "Methods",
            members: [
              m("inspect", "inspect(value)", "Pretty string, depth 3, truncated.", { params: [p("value", "any", "")], returns: r("string", ""), aliases: ["dump"] }),
              m("assert", "assert(cond, message?)", "Throws if cond is false.", { params: [p("cond", "any", ""), p("message", "string", "", true)] }),
              m("expect", "expect(value, type)", "Throws unless value matches type (`string`, `number`, `boolean`, `table`, `function`, `ipv4`, `domain`, `any`).", { params: [p("value", "any", ""), p("type", "string", "")] }),
              m("fail", "fail(message?)", "Throw.", { params: [p("message", "string", "", true)] }),
              m("time", "time(fn)", "Call fn and return milliseconds.", { params: [p("fn", "function", "")], returns: r("number", "") }),
              m("snapshot", "snapshot()", "id, caps, timers, rule/host counts, notify/vpn cooldowns, grants.", { returns: r("table", "") }),
              m("reload", "reload()", "Restart this plugin's VM after the current call returns. Same as the Reload button.", { returns: r("boolean", "") }),
              m("log", "log(value)", "Write inspect(value) at debug level.", { params: [p("value", "any", "")] }),
            ],
          },
        ],
      },
      {
        id: "Schema",
        name: "Schema",
        kind: "class",
        summary: "luna.schema — validate tables before writing. Optional fields use a `?` prefix (`?string`).",
        constructExample: "luna.schema.check(row, { host = \"domain\", ip = \"ipv4\", note = \"?string\" })",
        sections: [
          {
            title: "Methods",
            members: [
              m("check", "check(value, spec)", "Throws with a path if a required field is missing or mistyped.", { params: [p("value", "table", ""), p("spec", "table", "field → type")], returns: r("boolean", "") }),
              m("is", "is(value, spec)", "Same check, returns boolean instead of throwing.", { params: [p("value", "table", ""), p("spec", "table", "")], returns: r("boolean", "") }),
              m("type", "type(value, name)", "Single-value type test.", { params: [p("value", "any", ""), p("name", "string", "")], returns: r("boolean", "") }),
              m("domain", "domain(text)", "Valid domain pattern?", { params: [p("text", "string", "")], returns: r("boolean", "") }),
              m("ipv4", "ipv4(text)", "Parseable IPv4?", { params: [p("text", "string", "")], returns: r("boolean", "") }),
              m("hosts_line", "hosts_line(line)", "Throws if a non-empty hosts line is invalid.", { params: [p("line", "string", "")] }),
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
              m("can_control", "can_control()", "Whether start/stop would not hit the 15s throttle.", { perm: "vpn.control", returns: r("boolean", ""), aliases: ["canControl"] }),
              m("control_cooldown_ms", "control_cooldown_ms()", "Milliseconds until control is allowed.", { perm: "vpn.control", returns: r("number", "") }),
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
              m("api_level", "api_level()", "2.", { returns: r("number", "") }),
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
              m("show", "show(title, text)", "Post a status notification. Returns false if rate-limited.", { perm: "notify", params: [p("title", "string", "Max 40."), p("text", "string", "Max 120.")], returns: r("boolean", ""), aliases: ["info", "success", "warn", "error"] }),
              m("allowed", "allowed()", "Whether a notify would be delivered now.", { perm: "notify", returns: r("boolean", ""), aliases: ["canShow"] }),
              m("cooldown_ms", "cooldown_ms()", "Milliseconds until the 30s gap opens.", { perm: "notify", returns: r("number", "") }),
              m("remaining", "remaining()", "Notifies left this hour (max 8).", { perm: "notify", returns: r("number", "") }),
            ],
          },
        ],
      },
      {
        id: "Clock",
        name: "Clock",
        kind: "class",
        summary: "luna.clock — wall clock plus the host timer pool (max 8, 1s–120s).",
        sections: [
          {
            title: "Methods",
            members: [
              m("now", "now()", "Unix seconds.", { returns: r("number", "") }),
              m("now_ms", "now_ms()", "Unix ms.", { returns: r("number", "") }),
              m("iso", "iso()", "ISO instant.", { returns: r("string", "") }),
              m("setTimeout", "setTimeout(ms, fn)", "One-shot. Default ms 2000, clamped 1000–120000.", { params: [p("ms", "number", ""), p("fn", "function", "")], returns: r("number", "id"), aliases: ["after"] }),
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
        summary: "luna.i18n — strings from locale/en.json and locale/{lang}.json. Lookups plus `{name}` interpolation, truncated to 400 characters.",
        sections: [
          {
            title: "Methods",
            members: [
              m("t", "t(key, fallback?, vars?)", "Translate. `vars` replaces `{name}` and `%{name}`.", { params: [p("key", "string", ""), p("fallback", "string", "", true), p("vars", "table", "", true)], returns: r("string", ""), aliases: ["translate"] }),
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
        id: "List",
        name: "List",
        kind: "class",
        summary: "Ordered array, max 256. Colon-safe instance methods. luna.List.new() / from(array) / of(...) / empty().",
        construct: "List.new(array?) or List.of(a, b, …) or client:newList().",
        constructExample: "local xs = luna.List.of(\"a\", \"b\")\nxs:push(\"c\")\nprint(xs:join(\",\"))",
        sections: [
          {
            title: "Instance",
            members: [
              m("size", "size()", "Length.", { returns: r("number", ""), aliases: ["length"] }),
              m("get", "get(index)", "1-based value.", { params: [p("index", "number", "")], returns: r("any", ""), aliases: ["at"] }),
              m("push", "push(value)", "Append.", { params: [p("value", "any", "")], returns: r("List", "self"), aliases: ["add", "append"] }),
              m("pop", "pop()", "Remove last.", { returns: r("any", "") }),
              m("shift", "shift()", "Remove first.", { returns: r("any", "") }),
              m("map", "map(fn)", "New list.", { params: [p("fn", "function", "")], returns: r("List", "") }),
              m("filter", "filter(fn)", "New list.", { params: [p("fn", "function", "")], returns: r("List", "") }),
              m("group_by", "group_by(fn)", "Table of Lists keyed by `fn(item)`.", { params: [p("fn", "function", "")], returns: r("table", "") }),
              m("partition", "partition(fn)", "`{ passList, failList }`.", { params: [p("fn", "function", "")], returns: r("table", "") }),
              m("shuffle", "shuffle()", "In place.", { returns: r("List", "self") }),
              m("join", "join(sep?)", "String join.", { params: [p("sep", "string", "", true)], returns: r("string", "") }),
              m("to_table", "to_table()", "Plain Lua array.", { returns: r("table", ""), aliases: ["toJSON"] }),
            ],
          },
        ],
      },
      {
        id: "Set",
        name: "Set",
        kind: "class",
        summary: "Unique strings, max 256. union / intersect / difference.",
        construct: "Set.new(array?) or Set.from(array) or client:newSet().",
        constructExample: "local s = luna.Set.of(\"a\", \"b\")\nprint(s:has(\"a\"))",
        sections: [
          {
            title: "Instance",
            members: [
              m("add", "add(value)", "Insert.", { params: [p("value", "string", "")], returns: r("Set", "self") }),
              m("has", "has(value)", "Membership.", { params: [p("value", "string", "")], returns: r("boolean", ""), aliases: ["contains"] }),
              m("remove", "remove(value)", "Delete.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
              m("union", "union(other)", "New set.", { params: [p("other", "Set|table", "")], returns: r("Set", "") }),
              m("to_table", "to_table()", "Array of values.", { returns: r("string[]", ""), aliases: ["toJSON", "values"] }),
            ],
          },
        ],
      },
      {
        id: "Queue",
        name: "Queue",
        kind: "class",
        summary: "FIFO queue, max 256.",
        construct: "Queue.new()",
        constructExample: "local q = luna.Queue.new()\nq:enqueue(\"a\")\nprint(q:dequeue())",
        sections: [{ title: "Instance", members: [
          m("enqueue", "enqueue(value)", "Push back.", { params: [p("value", "any", "")], returns: r("Queue", "self"), aliases: ["push"] }),
          m("dequeue", "dequeue()", "Pop front.", { returns: r("any", ""), aliases: ["pop"] }),
          m("peek", "peek()", "Front or nil.", { returns: r("any", "") }),
        ]}],
      },
      {
        id: "Stack",
        name: "Stack",
        kind: "class",
        summary: "LIFO stack, max 256.",
        construct: "Stack.new()",
        constructExample: "local st = luna.Stack.new()\nst:push(1)\nprint(st:pop())",
        sections: [{ title: "Instance", members: [
          m("push", "push(value)", "Push.", { params: [p("value", "any", "")], returns: r("Stack", "self") }),
          m("pop", "pop()", "Pop.", { returns: r("any", "") }),
          m("peek", "peek()", "Top or nil.", { returns: r("any", "") }),
        ]}],
      },
      {
        id: "LRU",
        name: "LRU",
        kind: "class",
        summary: "Least-recently-used map. Capacity 1–256, default 32.",
        construct: "LRU.new(capacity?)",
        constructExample: "local cache = luna.LRU.new(8)\ncache:put(\"host\", true)",
        sections: [{ title: "Instance", members: [
          m("get", "get(key)", "Value and touch.", { params: [p("key", "string", "")], returns: r("any", "") }),
          m("put", "put(key, value)", "Insert.", { params: [p("key", "string", ""), p("value", "any", "")], returns: r("LRU", "self"), aliases: ["set"] }),
          m("has", "has(key)", "Membership.", { params: [p("key", "string", "")], returns: r("boolean", "") }),
        ]}],
      },
      {
        id: "DomainSet",
        name: "DomainSet",
        kind: "class",
        summary: "Normalized domain patterns. test() honors `*.example.com`. Alias Matcher.",
        construct: "DomainSet.new(array?) / Matcher.new(array?)",
        constructExample: "local d = luna.DomainSet.new()\nd:add(\"*.example.com\")\nprint(d:test(\"a.example.com\"))",
        sections: [{ title: "Instance", members: [
          m("add", "add(pattern)", "Normalize + insert.", { params: [p("pattern", "string", "")], returns: r("DomainSet", "self") }),
          m("test", "test(host)", "Exact or wildcard match.", { params: [p("host", "string", "")], returns: r("boolean", ""), aliases: ["matches", "contains"] }),
        ]}],
      },
      {
        id: "CidrSet",
        name: "CidrSet",
        kind: "class",
        summary: "IPv4 CIDR membership. Parse only — does not change routing.",
        construct: "CidrSet.new()",
        constructExample: "local c = luna.CidrSet.new()\nc:add(\"10.0.0.0/8\")\nprint(c:contains(\"10.1.2.3\"))",
        sections: [{ title: "Instance", members: [
          m("add", "add(cidr)", "`a.b.c.d/bits`.", { params: [p("cidr", "string", "")], returns: r("CidrSet", "self") }),
          m("contains", "contains(ip)", "IPv4 in any range.", { params: [p("ip", "string", "")], returns: r("boolean", ""), aliases: ["test"] }),
        ]}],
      },
      {
        id: "URL",
        name: "URL",
        kind: "class",
        summary: "Parse https? URLs. Does not fetch. github.com is flagged; homepage rules still apply for UI links.",
        construct: "URL.parse(raw). Also URL.host / scheme / path / query / github as static helpers.",
        constructExample: "local u = luna.URL.parse(\"https://github.com/you/repo?tab=readme\")\nprint(u.host, u:ok(), u:is_github())",
        props: [
          prop("host", "string", "Hostname."),
          prop("scheme", "string", "`https` / `http` / empty."),
          prop("path", "string", "Path starting with `/`."),
          prop("query", "table", "Decoded query map."),
          prop("fragment", "string", "Hash without `#`."),
          prop("valid", "boolean", "Looks like a host + allowed scheme."),
          prop("github", "boolean", "Host is github.com."),
        ],
        sections: [{ title: "Instance", members: [
          m("ok", "ok()", "valid flag.", { returns: r("boolean", "") }),
          m("is_github", "is_github()", "github.com host.", { returns: r("boolean", "") }),
          m("https", "https()", "scheme == https.", { returns: r("boolean", "") }),
        ]}],
      },
      {
        id: "Query",
        name: "Query",
        kind: "class",
        summary: "Query-string parse/build. No network.",
        construct: "Query.parse(raw) / Query.build(map)",
        constructExample: "luna.Query.parse(\"a=1&b=2\")",
        sections: [{ title: "Static", members: [
          m("parse", "parse(raw)", "Map of decoded pairs (max 32).", { static: true, params: [p("raw", "string", "")], returns: r("table", "") }),
          m("build", "build(map)", "`k=v` joined with `&`.", { static: true, params: [p("map", "table", "")], returns: r("string", "") }),
        ]}],
      },
      {
        id: "Template",
        name: "Template",
        kind: "class",
        summary: "Substitute `{name}`, `%{name}`, and `{{name}}`. Max 4000 chars.",
        construct: "Template.render(src, vars) or Template.compile(src):render(vars)",
        constructExample: "luna.Template.render(\"hi {name}\", { name = \"Luna\" })",
        sections: [{ title: "Static", members: [
          m("render", "render(src, vars)", "One-shot.", { static: true, params: [p("src", "string", ""), p("vars", "table", "")], returns: r("string", "") }),
          m("compile", "compile(src)", "Reusable `{ render(vars), source }`.", { static: true, params: [p("src", "string", "")], returns: r("table", "") }),
        ]}],
      },
      {
        id: "Buffer",
        name: "Buffer",
        kind: "class",
        summary: "String builder, cap 8192 chars.",
        construct: "Buffer.new(seed?)",
        constructExample: "local b = luna.Buffer.new()\nb:writeln(\"line\")\nprint(b:tostring())",
        sections: [{ title: "Instance", members: [
          m("write", "write(text)", "Append.", { params: [p("text", "string", "")], returns: r("Buffer", "self") }),
          m("writeln", "writeln(text)", "Append + newline.", { params: [p("text", "string", "")], returns: r("Buffer", "self") }),
          m("tostring", "tostring()", "Current text.", { returns: r("string", ""), aliases: ["toJSON"] }),
        ]}],
      },
      {
        id: "Diff",
        name: "Diff",
        kind: "class",
        summary: "Line-set diff. Not a patch engine.",
        construct: "Diff.changed / equal / lines",
        sections: [{ title: "Static", members: [
          m("changed", "changed(a, b)", "String inequality.", { static: true, params: [p("a", "string", ""), p("b", "string", "")], returns: r("boolean", "") }),
          m("lines", "lines(a, b)", "`{ added, removed, same }`.", { static: true, params: [p("a", "string", ""), p("b", "string", "")], returns: r("table", "") }),
        ]}],
      },
      {
        id: "Fuzzy",
        name: "Fuzzy",
        kind: "class",
        summary: "Levenshtein helpers, strings truncated to 64 chars.",
        construct: "Fuzzy.ratio(a, b) / levenshtein / similar / suggest",
        constructExample: "luna.Fuzzy.suggest(\"exmple\", { \"example.com\", \"other.net\" })",
        sections: [{ title: "Static", members: [
          m("ratio", "ratio(a, b)", "0–1 similarity.", { static: true, params: [p("a", "string", ""), p("b", "string", "")], returns: r("number", "") }),
          m("levenshtein", "levenshtein(a, b)", "Edit distance.", { static: true, params: [p("a", "string", ""), p("b", "string", "")], returns: r("number", "") }),
          m("suggest", "suggest(needle, list)", "Top 8 `{ value, score }` above 0.2.", { static: true, params: [p("needle", "string", ""), p("list", "table", "")], returns: r("table[]", "") }),
        ]}],
      },
      {
        id: "Random",
        name: "Random",
        kind: "class",
        summary: "Local RNG. Not cryptographic.",
        construct: "Random.int / bool / pick / id / shuffle / sample",
        sections: [{ title: "Static", members: [
          m("int", "int(lo, hi)", "Inclusive range.", { static: true, params: [p("lo", "number", ""), p("hi", "number", "")], returns: r("number", "") }),
          m("pick", "pick(list)", "Random element.", { static: true, params: [p("list", "table", "")], returns: r("any", "") }),
          m("id", "id()", "12-char id.", { static: true, returns: r("string", "") }),
        ]}],
      },
      {
        id: "Store",
        name: "Store",
        kind: "class",
        summary: "In-memory map, 64 keys, 8 subscribers. Not plugin storage. Alias Dict.",
        construct: "Store.new() or client:newStore()",
        constructExample: "local st = luna.Store.new()\nst:subscribe(function(ev) end)\nst:set(\"on\", true)",
        sections: [{ title: "Instance", members: [
          m("get", "get(key)", "Value or nil.", { params: [p("key", "string", "")], returns: r("any", "") }),
          m("set", "set(key, value)", "Write + notify.", { params: [p("key", "string", ""), p("value", "any", "")], returns: r("Store", "self") }),
          m("subscribe", "subscribe(fn)", "`fn({ key, value })`.", { params: [p("fn", "function", "")], returns: r("Store", "self") }),
          m("merge", "merge(table)", "Copy keys in.", { params: [p("table", "table", "")], returns: r("Store", "self") }),
        ]}],
      },
      {
        id: "Signal",
        name: "Signal",
        kind: "class",
        summary: "Tiny pub/sub, max 8 listeners. Separate from luna.events.",
        construct: "Signal.new()",
        sections: [{ title: "Instance", members: [
          m("on", "on(fn)", "Subscribe.", { params: [p("fn", "function", "")], returns: r("Signal", "self") }),
          m("emit", "emit(payload)", "Call listeners.", { params: [p("payload", "any", "")], returns: r("Signal", "self") }),
          m("off", "off()", "Drop all listeners.", { returns: r("Signal", "self") }),
        ]}],
      },
      {
        id: "State",
        name: "State",
        kind: "class",
        summary: "Single boxed value with subscribers.",
        construct: "State.new(initial)",
        sections: [{ title: "Instance", members: [
          m("get", "get()", "Current value.", { returns: r("any", "") }),
          m("set", "set(next)", "Replace + notify.", { params: [p("next", "any", "")], returns: r("State", "self") }),
          m("subscribe", "subscribe(fn)", "Listener.", { params: [p("fn", "function", "")], returns: r("State", "self") }),
        ]}],
      },
      {
        id: "Flags",
        name: "Flags",
        kind: "class",
        summary: "Boolean flags in memory. persist / from_storage need the storage permission; missing permission is a no-op.",
        construct: "Flags.new() or client:newFlags()",
        sections: [{ title: "Instance", members: [
          m("get", "get(key)", "Truthy?", { params: [p("key", "string", "")], returns: r("boolean", "") }),
          m("set", "set(key, on)", "Assign.", { params: [p("key", "string", ""), p("on", "boolean", "")], returns: r("Flags", "self") }),
          m("toggle", "toggle(key)", "Flip and return new value.", { params: [p("key", "string", "")], returns: r("boolean", "") }),
          m("persist", "persist(key?)", "Write enabled keys as CSV. Default storage key `flags`.", { perm: "storage", params: [p("key", "string", "", true)], returns: r("Flags", "self") }),
        ]}],
      },
      {
        id: "Logger",
        name: "Logger",
        kind: "class",
        summary: "Prefixed luna.log wrapper.",
        construct: "Logger.new(prefix?) or client:newLogger(prefix)",
        sections: [{ title: "Instance", members: [
          m("info", "info(message)", "info.", { params: [p("message", "string", "")] }),
          m("warn", "warn(message)", "warn.", { params: [p("message", "string", "")] }),
          m("error", "error(message)", "error.", { params: [p("message", "string", "")] }),
          m("debug", "debug(message)", "debug.", { params: [p("message", "string", "")] }),
        ]}],
      },
      {
        id: "Metrics",
        name: "Metrics",
        kind: "class",
        summary: "Named counters and gauges. In-memory only.",
        construct: "Metrics.new() or client:newMetrics()",
        sections: [{ title: "Instance", members: [
          m("inc", "inc(name, by?)", "Add (default 1).", { params: [p("name", "string", ""), p("by", "number", "", true)], returns: r("Metrics", "self") }),
          m("gauge", "gauge(name, value)", "Set gauge.", { params: [p("name", "string", ""), p("value", "number", "")], returns: r("Metrics", "self") }),
          m("snapshot", "snapshot()", "`{ counters, gauges }`.", { returns: r("table", ""), aliases: ["toJSON"] }),
        ]}],
      },
      {
        id: "Histogram",
        name: "Histogram",
        kind: "class",
        summary: "Sample list, max 256. mean / min / max.",
        construct: "Histogram.new()",
        sections: [{ title: "Instance", members: [
          m("observe", "observe(n)", "Record.", { params: [p("n", "number", "")], returns: r("Histogram", "self") }),
          m("mean", "mean()", "Average or 0.", { returns: r("number", "") }),
        ]}],
      },
      {
        id: "Result",
        name: "Result",
        kind: "class",
        summary: "ok / err box. unwrap returns nil on err (does not throw).",
        construct: "Result.ok(value) / Result.err(message) / Result.from(value)",
        sections: [{ title: "Instance", members: [
          m("is_ok", "is_ok()", "Success?", { returns: r("boolean", "") }),
          m("unwrap", "unwrap()", "Value or nil.", { returns: r("any", "") }),
          m("unwrap_or", "unwrap_or(fallback)", "Value or fallback.", { params: [p("fallback", "any", "")], returns: r("any", "") }),
          m("map", "map(fn)", "Map ok value.", { params: [p("fn", "function", "")], returns: r("Result", "") }),
        ]}],
      },
      {
        id: "Optional",
        name: "Optional",
        kind: "class",
        summary: "Nil-safe box.",
        construct: "Optional.of(value) / Optional.none()",
        sections: [{ title: "Instance", members: [
          m("present", "present()", "Not nil.", { returns: r("boolean", "") }),
          m("or_else", "or_else(fallback)", "Value or fallback.", { params: [p("fallback", "any", "")], returns: r("any", "") }),
        ]}],
      },
      {
        id: "Range",
        name: "Range",
        kind: "class",
        summary: "Inclusive integer range. to_table capped at 256.",
        construct: "Range.new(start, finish)",
        sections: [{ title: "Instance", members: [
          m("contains", "contains(n)", "Inside inclusive bounds.", { params: [p("n", "number", "")], returns: r("boolean", "") }),
          m("clamp", "clamp(n)", "Clamp to range.", { params: [p("n", "number", "")], returns: r("number", "") }),
        ]}],
      },
      {
        id: "BitSet",
        name: "BitSet",
        kind: "class",
        summary: "64 bits, indices 0–63.",
        construct: "BitSet.new()",
        sections: [{ title: "Instance", members: [
          m("set", "set(i)", "Turn on.", { params: [p("i", "number", "")], returns: r("BitSet", "self") }),
          m("test", "test(i)", "On?", { params: [p("i", "number", "")], returns: r("boolean", "") }),
        ]}],
      },
      {
        id: "Interval",
        name: "Interval",
        kind: "class",
        summary: "Parse `5`, `5s`, `2m`, `1h`, `500ms` into timer seconds. Always clamped to 1–120. Not cron.",
        construct: "Interval.parse(text) / ms / valid",
        constructExample: "luna.clock.after(luna.Interval.parse(\"15s\") * 1000, fn)",
        sections: [{ title: "Static", members: [
          m("parse", "parse(text)", "Seconds 1–120, or 0 if invalid.", { static: true, params: [p("text", "string", "")], returns: r("number", "") }),
          m("valid", "valid(text)", "Recognized duration?", { static: true, params: [p("text", "string", "")], returns: r("boolean", "") }),
        ]}],
      },
      {
        id: "Csv",
        name: "Csv",
        kind: "class",
        summary: "Naive CSV split (comma, 200 lines, 16 columns). Not RFC 4180 quotes.",
        construct: "Csv.parse / stringify / row",
        sections: [{ title: "Static", members: [
          m("parse", "parse(text)", "Rows of cells.", { static: true, params: [p("text", "string", "")], returns: r("string[][]", "") }),
          m("stringify", "stringify(rows)", "Join with commas.", { static: true, params: [p("rows", "table", "")], returns: r("string", "") }),
        ]}],
      },
      {
        id: "Stopwatch",
        name: "Stopwatch",
        kind: "class",
        summary: "Elapsed time from construction or reset().",
        construct: "Stopwatch.new()",
        sections: [{ title: "Instance", members: [
          m("elapsed_ms", "elapsed_ms()", "Milliseconds.", { returns: r("number", "") }),
          m("reset", "reset()", "Restart.", { returns: r("Stopwatch", "self") }),
        ]}],
      },
      {
        id: "Memo",
        name: "Memo",
        kind: "class",
        summary: "Compute-once cache, 64 keys.",
        construct: "Memo.new()",
        sections: [{ title: "Instance", members: [
          m("compute", "compute(key, fn)", "Return cached or `fn(key)`.", { params: [p("key", "string", ""), p("fn", "function", "")], returns: r("any", "") }),
          m("get", "get(key)", "Cached or nil.", { params: [p("key", "string", "")], returns: r("any", "") }),
        ]}],
      },
      {
        id: "Registry",
        name: "Registry",
        kind: "class",
        summary: "Named lookup of values/functions, max 256.",
        construct: "Registry.new()",
        sections: [{ title: "Instance", members: [
          m("register", "register(name, value)", "Store.", { params: [p("name", "string", ""), p("value", "any", "")], returns: r("Registry", "self") }),
          m("get", "get(name)", "Lookup.", { params: [p("name", "string", "")], returns: r("any", "") }),
        ]}],
      },
      {
        id: "RingBuffer",
        name: "RingBuffer",
        kind: "class",
        summary: "Fixed-capacity FIFO. Oldest dropped.",
        construct: "RingBuffer.new(capacity?)",
        sections: [{ title: "Instance", members: [
          m("push", "push(value)", "Append, drop oldest if full.", { params: [p("value", "any", "")], returns: r("RingBuffer", "self") }),
          m("to_table", "to_table()", "Oldest first.", { returns: r("table", "") }),
        ]}],
      },
      {
        id: "Glob",
        name: "Glob",
        kind: "class",
        summary: "Case-insensitive `*` glob. Same rules as luna.string.matches.",
        construct: "Glob.match(pattern, value) / filter(list, pattern)",
        sections: [{ title: "Static", members: [
          m("match", "match(pattern, value)", "Full-string glob.", { static: true, params: [p("pattern", "string", ""), p("value", "string", "")], returns: r("boolean", "") }),
          m("filter", "filter(list, pattern)", "Keep matches.", { static: true, params: [p("list", "table", ""), p("pattern", "string", "")], returns: r("string[]", "") }),
        ]}],
      },
      {
        id: "FormBuilder",
        name: "FormBuilder",
        kind: "class",
        summary: "Single-section settings page. Methods wrap luna.ui.* and emit a Page. Max items = MAX_UI_ITEMS.",
        construct: "FormBuilder.new(title) or client:form(title)",
        constructExample: lua(`function settings_page()
  return luna.FormBuilder.new("Focus")
    :note({ text = "Local only" })
    :switch({ id = "on", title = "Enable", default = true })
    :build()
end`),
        sections: [{ title: "Instance", members: [
          m("note", "note(spec)", "Add a note.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("switch", "switch(spec)", "Add a switch.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("text", "text(spec)", "Add a text field.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("button", "button(spec)", "Add a button.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("add", "add(node)", "Append a ready UI node.", { params: [p("node", "table", "")], returns: r("FormBuilder", "self") }),
          m("stat", "stat(spec)", "`{ label, value, hint?, tone? }`.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("list_item", "list_item(spec)", "`{ title, body?, trailing?, tone? }`. Alias `item`.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self"), aliases: ["item"] }),
          m("empty", "empty(spec)", "Empty-state block.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("chips", "chips(spec)", "Display tags `{ labels = { … } }`. Not a select.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("quote", "quote(spec)", "`{ text, cite? }`.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("fold", "fold(spec)", "Collapsible `{ title, body, open? }`.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("steps", "steps(spec)", "`{ labels, current }`.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("timeline", "timeline(spec)", "`{ events = { … } }`.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("score", "score(spec)", "`{ label, value, max? }`.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("faq", "faq(spec)", "`{ q, a }` or `{ question, answer }`.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("status", "status(spec)", "`{ text, tone, detail? }`.", { params: [p("spec", "table", "")], returns: r("FormBuilder", "self") }),
          m("build", "build()", "Page with one section.", { returns: r("Page", "") }),
        ]}],
      },
      {
        id: "WizardBuilder",
        name: "WizardBuilder",
        kind: "class",
        summary: "Multiple FormBuilder steps become page sections (host still shows one screen).",
        construct: "WizardBuilder.new(title) or client:wizard(title)",
        constructExample: "luna.WizardBuilder.new(\"Setup\"):step(\"Hosts\", form):build()",
        sections: [{ title: "Instance", members: [
          m("step", "step(name, form)", "Append a section from a FormBuilder or page table.", { params: [p("name", "string", ""), p("form", "FormBuilder|table", "")], returns: r("WizardBuilder", "self") }),
          m("build", "build()", "Page.", { returns: r("Page", "") }),
        ]}],
      },
      {
        id: "TableBuilder",
        name: "TableBuilder",
        kind: "class",
        summary: "Key/value rows as ui.kv items.",
        construct: "TableBuilder.new(title) or client:tableView(title)",
        sections: [{ title: "Instance", members: [
          m("row", "row(key, value)", "Append a kv row.", { params: [p("key", "string", ""), p("value", "string", "")], returns: r("TableBuilder", "self"), aliases: ["add"] }),
          m("build", "build()", "Page.", { returns: r("Page", "") }),
        ]}],
      },
      {
        id: "Machine",
        name: "Machine",
        kind: "class",
        summary: "Finite state machine. Transitions are (from, event) → to. Max 256 edges.",
        construct: "Machine.new(initial?)",
        constructExample: "local m = luna.Machine.new(\"idle\")\nm:on(\"idle\", \"start\", \"on\")\nm:send(\"start\")",
        sections: [{ title: "Instance", members: [
          m("on", "on(from, event, to)", "Add an edge.", { params: [p("from", "string", ""), p("event", "string", ""), p("to", "string", "")], returns: r("Machine", "self") }),
          m("send", "send(event)", "Fire if allowed.", { params: [p("event", "string", "")], returns: r("boolean", "") }),
          m("state", "state()", "Current state.", { returns: r("string", "") }),
          m("can", "can(event)", "Whether this event is valid now. `from = \"*\"` matches any state.", { params: [p("event", "string", "")], returns: r("boolean", "") }),
          m("history", "history()", "Previous states (max 16).", { returns: r("string[]", "") }),
        ]}],
      },
      {
        id: "Pipeline",
        name: "Pipeline",
        kind: "class",
        summary: "Run a value through up to 32 functions.",
        construct: "Pipeline.new()",
        sections: [{ title: "Instance", members: [
          m("use", "use(fn)", "Append a step `fn(acc) → acc`.", { params: [p("fn", "function", "")], returns: r("Pipeline", "self") }),
          m("tap", "tap(fn)", "Observe without changing the value.", { params: [p("fn", "function", "")], returns: r("Pipeline", "self") }),
          m("run", "run(input)", "Fold left.", { params: [p("input", "any", "")], returns: r("any", "") }),
        ]}],
      },
      {
        id: "History",
        name: "History",
        kind: "class",
        summary: "Undo / redo stack.",
        construct: "History.new(capacity?)",
        sections: [{ title: "Instance", members: [
          m("push", "push(value)", "Commit a snapshot.", { params: [p("value", "any", "")], returns: r("History", "self") }),
          m("undo", "undo()", "Previous value.", { returns: r("any", "") }),
          m("redo", "redo()", "Next value.", { returns: r("any", "") }),
        ]}],
      },
      {
        id: "Cache",
        name: "Cache",
        kind: "class",
        summary: "In-memory map with optional TTL milliseconds. 64 keys. Not plugin storage.",
        construct: "Cache.new()",
        sections: [{ title: "Instance", members: [
          m("set", "set(key, value, ttl_ms?)", "ttl 0 = forever.", { params: [p("key", "string", ""), p("value", "any", ""), p("ttl_ms", "number", "", true)], returns: r("Cache", "self") }),
          m("get", "get(key)", "Value or nil if expired.", { params: [p("key", "string", "")], returns: r("any", "") }),
        ]}],
      },
      {
        id: "Router",
        name: "Router",
        kind: "class",
        summary: "Match `/hosts/:id` style paths. Does not perform HTTP.",
        construct: "Router.new()",
        constructExample: "local r = luna.Router.new()\nr:add(\"/hosts/:name\", function(p) return p.name end)\nprint(r:match(\"/hosts/a\"))",
        sections: [{ title: "Instance", members: [
          m("add", "add(pattern, fn)", "`fn({ params })`.", { params: [p("pattern", "string", ""), p("fn", "function", "")], returns: r("Router", "self") }),
          m("match", "match(path)", "First hit or nil.", { params: [p("path", "string", "")], returns: r("any", "") }),
        ]}],
      },
      {
        id: "Actions",
        name: "Actions",
        kind: "class",
        summary: "Named local commands.",
        construct: "Actions.new() or client:actions()",
        sections: [{ title: "Instance", members: [
          m("add", "add(name, fn)", "Register.", { params: [p("name", "string", ""), p("fn", "function", "")], returns: r("Actions", "self") }),
          m("run", "run(name, arg?)", "Call or nil.", { params: [p("name", "string", ""), p("arg", "any", "", true)], returns: r("any", "") }),
        ]}],
      },
      {
        id: "Config",
        name: "Config",
        kind: "class",
        summary: "Defaults map. persist/load need storage; missing permission is a no-op.",
        construct: "Config.new(defaults?) or client:config(defaults)",
        sections: [{ title: "Instance", members: [
          m("get", "get(key)", "Value.", { params: [p("key", "string", "")], returns: r("any", "") }),
          m("set", "set(key, value)", "Write.", { params: [p("key", "string", ""), p("value", "any", "")], returns: r("Config", "self") }),
          m("persist", "persist(key?)", "CSV-ish dump to storage.", { perm: "storage", params: [p("key", "string", "", true)], returns: r("Config", "self") }),
        ]}],
      },
      {
        id: "Expr",
        name: "Expr",
        kind: "class",
        summary: "Tiny arithmetic / boolean language. Numbers, env names, + - * / %, comparisons, and/or/not. No Lua, no strings, no calls.",
        construct: "Expr.eval(src, env?)",
        constructExample: "luna.Expr.eval(\"n > 10 and on\", { n = 12, on = 1 })",
        sections: [{ title: "Static", members: [
          m("eval", "eval(src, env?)", "Number (booleans as 0/1).", { static: true, params: [p("src", "string", ""), p("env", "table", "", true)], returns: r("number", "") }),
          m("bool", "bool(src, env?)", "Non-zero.", { static: true, params: [p("src", "string", ""), p("env", "table", "", true)], returns: r("boolean", "") }),
          m("valid", "valid(src)", "Parses?", { static: true, params: [p("src", "string", "")], returns: r("boolean", "") }),
        ]}],
      },
      {
        id: "TableQuery",
        name: "TableQuery",
        kind: "class",
        summary: "Chain where / map / sort / limit / skip over an array of tables.",
        construct: "TableQuery.from(rows)",
        sections: [{ title: "Instance", members: [
          m("where", "where(fn)", "Keep rows.", { params: [p("fn", "function", "")], returns: r("TableQuery", "self") }),
          m("sort", "sort(key)", "By field string.", { params: [p("key", "string", "")], returns: r("TableQuery", "self") }),
          m("to_table", "to_table()", "Materialize.", { returns: r("table", "") }),
        ]}],
      },
      {
        id: "Paginator",
        name: "Paginator",
        kind: "class",
        summary: "Slice an array into pages.",
        construct: "Paginator.page(list, page, size)",
        sections: [{ title: "Static", members: [
          m("page", "page(list, page, size)", "`{ items, page, pages, total, has_next, has_prev }`.", { static: true, params: [p("list", "table", ""), p("page", "number", ""), p("size", "number", "")], returns: r("table", "") }),
        ]}],
      },
      {
        id: "Dashboard",
        name: "Dashboard",
        kind: "class",
        summary: "Status page: stats, rows, empty, chips, alerts. Emits a Page.",
        construct: "Dashboard.new(title) or client:dashboard(title)",
        constructExample: "luna.Dashboard.new(\"Status\"):stat({ label = \"Rules\", value = \"3\" }):build()",
        sections: [{ title: "Instance", members: [
          m("stat", "stat(spec)", "Big number.", { params: [p("spec", "table", "")], returns: r("Dashboard", "self") }),
          m("item", "item(spec)", "List row.", { params: [p("spec", "table", "")], returns: r("Dashboard", "self") }),
          m("empty", "empty(spec)", "Empty state.", { params: [p("spec", "table", "")], returns: r("Dashboard", "self") }),
          m("steps", "steps(spec)", "Wizard bar.", { params: [p("spec", "table", "")], returns: r("Dashboard", "self") }),
          m("fold", "fold(spec)", "Collapsible help.", { params: [p("spec", "table", "")], returns: r("Dashboard", "self") }),
          m("score", "score(spec)", "0–1 or 0–max meter.", { params: [p("spec", "table", "")], returns: r("Dashboard", "self") }),
          m("status", "status(spec)", "Tone pill + detail.", { params: [p("spec", "table", "")], returns: r("Dashboard", "self") }),
          m("build", "build()", "Page.", { returns: r("Page", "") }),
        ]}],
      },
      {
        id: "SchemaForm",
        name: "SchemaForm",
        kind: "class",
        summary: "Build a settings page from `{ id = { type, title, … } }`. `type` is a luna.ui factory name.",
        construct: "SchemaForm.new(title, schema) or client:schemaForm(title, schema)",
        sections: [{ title: "Instance", members: [
          m("build", "build()", "Page.", { returns: r("Page", "") }),
        ]}],
      },
      {
        id: "Policy",
        name: "Policy",
        kind: "class",
        summary: "Allow-list + deny-list for hosts. Empty allow-list means allow all except deny.",
        construct: "Policy.new()",
        sections: [{ title: "Instance", members: [
          m("allow", "allow(pattern)", "Permit.", { params: [p("pattern", "string", "")], returns: r("Policy", "self") }),
          m("deny", "deny(pattern)", "Block.", { params: [p("pattern", "string", "")], returns: r("Policy", "self") }),
          m("test", "test(host)", "Allowed?", { params: [p("host", "string", "")], returns: r("boolean", "") }),
        ]}],
      },
      {
        id: "Bus",
        name: "Bus",
        kind: "class",
        summary: "In-plugin topics. Not luna.events and not other plugins.",
        construct: "Bus.new() or client:bus()",
        sections: [{ title: "Instance", members: [
          m("on", "on(topic, fn)", "Subscribe.", { params: [p("topic", "string", ""), p("fn", "function", "")], returns: r("Bus", "self") }),
          m("emit", "emit(topic, payload)", "Publish.", { params: [p("topic", "string", ""), p("payload", "any", "")], returns: r("Bus", "self") }),
        ]}],
      },
      {
        id: "JsonPath",
        name: "JsonPath",
        kind: "class",
        summary: "Dot paths `a.b.0`. Not JSONPath RFC.",
        construct: "JsonPath.get / set / has",
        sections: [{ title: "Static", members: [
          m("get", "get(root, path)", "Walk or nil.", { static: true, params: [p("root", "table", ""), p("path", "string", "")], returns: r("any", "") }),
          m("set", "set(root, path, value)", "Create intermediate tables.", { static: true, params: [p("root", "table", ""), p("path", "string", ""), p("value", "any", "")], returns: r("table", "") }),
        ]}],
      },
      {
        id: "SearchIndex",
        name: "SearchIndex",
        kind: "class",
        summary: "Substring search over id + body.",
        construct: "SearchIndex.new()",
        sections: [{ title: "Instance", members: [
          m("add", "add(id, body)", "Index.", { params: [p("id", "string", ""), p("body", "string", "")], returns: r("SearchIndex", "self") }),
          m("search", "search(needle)", "Matching ids.", { params: [p("needle", "string", "")], returns: r("string[]", "") }),
        ]}],
      },
      {
        id: "Matchbook",
        name: "Matchbook",
        kind: "class",
        summary: "First matching predicate wins. Predicates: function, glob string, or equality.",
        construct: "Matchbook.new()",
        sections: [{ title: "Instance", members: [
          m("when", "when(pred, action)", "Append a rule.", { params: [p("pred", "function|string", ""), p("action", "any", "")], returns: r("Matchbook", "self") }),
          m("match", "match(value)", "Action result or nil.", { params: [p("value", "any", "")], returns: r("any", "") }),
        ]}],
      },
      {
        id: "RateLimit",
        name: "RateLimit",
        kind: "class",
        summary: "Sliding window per key.",
        construct: "RateLimit.new(n, window_ms)",
        sections: [{ title: "Instance", members: [
          m("allow", "allow(key)", "Consume one token.", { params: [p("key", "string", "")], returns: r("boolean", "") }),
          m("remaining", "remaining(key)", "Tokens left.", { params: [p("key", "string", "")], returns: r("number", "") }),
        ]}],
      },
      {
        id: "Checklist",
        name: "Checklist",
        kind: "class",
        summary: "Todo items with progress. to_ui() emits list_item nodes.",
        construct: "Checklist.new() or client:checklist()",
        sections: [{ title: "Instance", members: [
          m("add", "add(text)", "Append unchecked.", { params: [p("text", "string", "")], returns: r("Checklist", "self") }),
          m("toggle", "toggle(index)", "1-based.", { params: [p("index", "number", "")], returns: r("Checklist", "self") }),
          m("progress", "progress()", "0–1.", { returns: r("number", "") }),
        ]}],
      },
      {
        id: "Schedule",
        name: "Schedule",
        kind: "class",
        summary: "Device-local clock helpers. Weekday is ISO 1=Mon … 7=Sun. Does not fire timers by itself — pair with clock.setInterval and check :active() / cron().",
        construct: "Schedule.now() / window(fromHour, toHour, weekdays?) / cron(expr)",
        constructExample: "local w = luna.Schedule.window(9, 18, {1,2,3,4,5})\nif w:active() then … end",
        sections: [{ title: "Static", members: [
          m("now", "now()", "`{ hour, minute, weekday, weekend, day, month }`.", { static: true, returns: r("table", "") }),
          m("between", "between(from, to)", "Current hour in [from, to).", { static: true, params: [p("from", "number", ""), p("to", "number", "")], returns: r("boolean", "") }),
          m("weekdays", "weekdays()", "Mon–Fri.", { static: true, returns: r("boolean", "") }),
          m("weekend", "weekend()", "Sat–Sun.", { static: true, returns: r("boolean", "") }),
          m("cron", "cron(expr)", "5-field cron (`m h dom mon dow`). `1-5` = weekdays. No network.", { static: true, params: [p("expr", "string", "")], returns: r("boolean", "") }),
          m("window", "window(from, to, days?)", "Object with `:active()`. Empty days = every day.", { static: true, params: [p("from", "number", ""), p("to", "number", ""), p("days", "number[]", "", true)], returns: r("Schedule", "") }),
        ]}],
      },
      {
        id: "Ruleset",
        name: "Ruleset",
        kind: "class",
        summary: "Ordered local matcher: glob, domain (wildcards), prefix, exact, CIDR. First hit wins. Not the app rule engine — your own.",
        construct: "Ruleset.new() or client:ruleset()",
        sections: [{ title: "Instance", members: [
          m("glob", "glob(pattern)", "Add `*.ads.com`.", { params: [p("pattern", "string", "")], returns: r("Ruleset", "self") }),
          m("domain", "domain(pattern)", "Host or `*.example.com`.", { params: [p("pattern", "string", "")], returns: r("Ruleset", "self") }),
          m("cidr", "cidr(spec)", "`10.0.0.0/8`.", { params: [p("spec", "string", "")], returns: r("Ruleset", "self") }),
          m("test", "test(value)", "Any rule matches?", { params: [p("value", "string", "")], returns: r("boolean", "") }),
          m("why", "why(value)", "`kind:pattern` or empty.", { params: [p("value", "string", "")], returns: r("string", "") }),
        ]}],
      },
      {
        id: "Validator",
        name: "Validator",
        kind: "class",
        summary: "Field rules for a table. Returns `{ ok, errors = { field = message } }`. No Lua eval.",
        construct: "Validator.new() or client:validator()",
        sections: [{ title: "Instance", members: [
          m("required", "required(field)", "Non-blank.", { params: [p("field", "string", "")], returns: r("Validator", "self") }),
          m("domain", "domain(field)", "Valid host pattern.", { params: [p("field", "string", "")], returns: r("Validator", "self") }),
          m("min", "min(field, n)", "Numeric minimum.", { params: [p("field", "string", ""), p("n", "number", "")], returns: r("Validator", "self") }),
          m("one_of", "one_of(field, list)", "Enum.", { params: [p("field", "string", ""), p("list", "string[]", "")], returns: r("Validator", "self") }),
          m("run", "run(table)", "Validate.", { params: [p("table", "table", "")], returns: r("table", "") }),
        ]}],
      },
      {
        id: "Bloom",
        name: "Bloom",
        kind: "class",
        summary: "Probabilistic set. False positives possible; false negatives are not. In-memory, max 8192 bits.",
        construct: "Bloom.new(bits?) or client:bloom(bits?)",
        sections: [{ title: "Instance", members: [
          m("add", "add(value)", "Insert.", { params: [p("value", "string", "")], returns: r("Bloom", "self") }),
          m("has", "has(value)", "Maybe present.", { params: [p("value", "string", "")], returns: r("boolean", "") }),
        ]}],
      },
      {
        id: "Circuit",
        name: "Circuit",
        kind: "class",
        summary: "closed → open after N fails, then half after cooldown_ms. Local only.",
        construct: "Circuit.new(threshold?, cooldown_ms?) or client:circuit()",
        sections: [{ title: "Instance", members: [
          m("allow", "allow()", "False while open.", { returns: r("boolean", "") }),
          m("fail", "fail()", "Count a failure.", { returns: r("Circuit", "self") }),
          m("success", "success()", "Reset.", { returns: r("Circuit", "self") }),
          m("state", "state()", "`closed` / `open` / `half`.", { returns: r("string", "") }),
        ]}],
      },
      {
        id: "Health",
        name: "Health",
        kind: "class",
        summary: "Named probes for a status page.",
        construct: "Health.new() or client:health()",
        sections: [{ title: "Instance", members: [
          m("ok", "ok(name, pass, detail?)", "Record a check.", { params: [p("name", "string", ""), p("pass", "boolean", ""), p("detail", "string", "", true)], returns: r("Health", "self") }),
          m("all", "all()", "Every check passed (and at least one exists).", { returns: r("boolean", "") }),
          m("snapshot", "snapshot()", "`{ name, ok, detail }[]`.", { returns: r("table[]", "") }),
          m("worst", "worst()", "`success` / `danger` / `info`.", { returns: r("string", "") }),
        ]}],
      },
      {
        id: "Ledger",
        name: "Ledger",
        kind: "class",
        summary: "Integer credits per account. debit fails if insufficient.",
        construct: "Ledger.new() or client:ledger()",
        sections: [{ title: "Instance", members: [
          m("credit", "credit(account, n)", "Add.", { params: [p("account", "string", ""), p("n", "number", "")], returns: r("number", "balance") }),
          m("debit", "debit(account, n)", "Subtract or false.", { params: [p("account", "string", ""), p("n", "number", "")], returns: r("boolean", "") }),
          m("balance", "balance(account)", "Current.", { params: [p("account", "string", "")], returns: r("number", "") }),
        ]}],
      },
      {
        id: "Weighted",
        name: "Weighted",
        kind: "class",
        summary: "Weighted random pick. For A/B between lists or modes.",
        construct: "Weighted.new()",
        sections: [{ title: "Instance", members: [
          m("add", "add(value, weight)", "Weight ≥ 1.", { params: [p("value", "any", ""), p("weight", "number", "")], returns: r("Weighted", "self") }),
          m("pick", "pick()", "One value.", { returns: r("any", "") }),
        ]}],
      },
      {
        id: "Ranker",
        name: "Ranker",
        kind: "class",
        summary: "Score ids and take top-N.",
        construct: "Ranker.new()",
        sections: [{ title: "Instance", members: [
          m("set", "set(id, score)", "Assign.", { params: [p("id", "string", ""), p("score", "number", "")], returns: r("Ranker", "self") }),
          m("bump", "bump(id, delta?)", "Add to score.", { params: [p("id", "string", ""), p("delta", "number", "", true)], returns: r("Ranker", "self") }),
          m("top", "top(n?)", "`{ id, score }[]`.", { params: [p("n", "number", "", true)], returns: r("table[]", "") }),
        ]}],
      },
      {
        id: "Workflow",
        name: "Workflow",
        kind: "class",
        summary: "Named setup steps. Pair with luna.ui.steps for the bar.",
        construct: "Workflow.new(names) or client:workflow(names)",
        sections: [{ title: "Instance", members: [
          m("next", "next()", "Advance, return name.", { returns: r("string", "") }),
          m("prev", "prev()", "Back.", { returns: r("string", "") }),
          m("name", "name()", "Current.", { returns: r("string", "") }),
          m("index", "index()", "1-based.", { returns: r("number", "") }),
        ]}],
      },
      {
        id: "Preset",
        name: "Preset",
        kind: "class",
        summary: "Named snapshots of string maps (work / home configs).",
        construct: "Preset.new()",
        sections: [{ title: "Instance", members: [
          m("save", "save(name, table)", "Store.", { params: [p("name", "string", ""), p("table", "table", "")], returns: r("Preset", "self") }),
          m("load", "load(name)", "Map or nil.", { params: [p("name", "string", "")], returns: r("table", "") }),
        ]}],
      },
      {
        id: "Tokens",
        name: "Tokens",
        kind: "class",
        summary: "Split text into words, lines, CSV cells, or host patterns.",
        construct: "Tokens.words(text) — static.",
        sections: [{ title: "Static", members: [
          m("words", "words(text)", "Whitespace.", { static: true, params: [p("text", "string", "")], returns: r("string[]", "") }),
          m("lines", "lines(text)", "Non-empty lines.", { static: true, params: [p("text", "string", "")], returns: r("string[]", "") }),
          m("hosts", "hosts(text)", "Valid domain tokens from hosts-file text.", { static: true, params: [p("text", "string", "")], returns: r("string[]", "") }),
        ]}],
      },
      {
        id: "Spark",
        name: "Spark",
        kind: "class",
        summary: "Unicode sparkline from a number array.",
        construct: "Spark.of({1,3,8})",
        sections: [{ title: "Static", members: [
          m("of", "of(numbers)", "`▁▂▃…` string.", { static: true, params: [p("numbers", "number[]", "")], returns: r("string", "") }),
        ]}],
      },
      {
        id: "Ini",
        name: "Ini",
        kind: "class",
        summary: "Tiny INI parser. Sections become nested tables.",
        construct: "Ini.parse(text)",
        sections: [{ title: "Static", members: [
          m("parse", "parse(text)", "Table.", { static: true, params: [p("text", "string", "")], returns: r("table", "") }),
          m("get", "get(root, path)", "`mod.on` style.", { static: true, params: [p("root", "table", ""), p("path", "string", "")], returns: r("any", "") }),
        ]}],
      },
      {
        id: "JsonPtr",
        name: "JsonPtr",
        kind: "class",
        summary: "JSON Pointer `/a/b/0`. Sibling of JsonPath (dot paths).",
        construct: "JsonPtr.get(root, path)",
        sections: [{ title: "Static", members: [
          m("get", "get(root, path)", "Walk.", { static: true, params: [p("root", "table", ""), p("path", "string", "")], returns: r("any", "") }),
          m("set", "set(root, path, value)", "Create intermediates.", { static: true, params: [p("root", "table", ""), p("path", "string", ""), p("value", "any", "")], returns: r("table", "") }),
        ]}],
      },
      {
        id: "Kanban",
        name: "Kanban",
        kind: "class",
        summary: "Named columns of cards. to_ui() emits list_item nodes.",
        construct: "Kanban.new() or client:kanban()",
        sections: [{ title: "Instance", members: [
          m("column", "column(name)", "Ensure a column.", { params: [p("name", "string", "")], returns: r("Kanban", "self") }),
          m("card", "card(column, title)", "Add a card.", { params: [p("column", "string", ""), p("title", "string", "")], returns: r("Kanban", "self") }),
          m("to_ui", "to_ui()", "list_item array.", { returns: r("table[]", "") }),
        ]}],
      },
      {
        id: "Migration",
        name: "Migration",
        kind: "class",
        summary: "Run versioned functions once. Store the returned version in plugin storage.",
        construct: "Migration.new()",
        sections: [{ title: "Instance", members: [
          m("step", "step(version, fn)", "Register.", { params: [p("version", "number", ""), p("fn", "function", "")], returns: r("Migration", "self") }),
          m("run", "run(current)", "Returns new version.", { params: [p("current", "number", "")], returns: r("number", "") }),
        ]}],
      },
      {
        id: "UnionFind",
        name: "UnionFind",
        kind: "class",
        summary: "Disjoint sets. Cluster duplicate names or merge groups.",
        construct: "UnionFind.new()",
        sections: [{ title: "Instance", members: [
          m("union", "union(a, b)", "Merge.", { params: [p("a", "string", ""), p("b", "string", "")], returns: r("UnionFind", "self") }),
          m("same", "same(a, b)", "Same root?", { params: [p("a", "string", ""), p("b", "string", "")], returns: r("boolean", "") }),
          m("find", "find(a)", "Root id.", { params: [p("a", "string", "")], returns: r("string", "") }),
        ]}],
      },
      {
        id: "Watchdog",
        name: "Watchdog",
        kind: "class",
        summary: "Ping + TTL. alive() is false after ttl_ms without ping.",
        construct: "Watchdog.new(ttl_ms?)",
        sections: [{ title: "Instance", members: [
          m("ping", "ping()", "Touch.", { returns: r("Watchdog", "self") }),
          m("alive", "alive()", "Within TTL?", { returns: r("boolean", "") }),
        ]}],
      },
      {
        id: "Recur",
        name: "Recur",
        kind: "class",
        summary: "due() is true at most once per N seconds. Pair with a timer; does not schedule by itself.",
        construct: "Recur.new(seconds)",
        sections: [{ title: "Instance", members: [
          m("due", "due()", "True when the interval elapsed (first call too).", { returns: r("boolean", "") }),
          m("reset", "reset()", "Clear last fire.", { returns: r("Recur", "self") }),
        ]}],
      },
      {
        id: "Highlight",
        name: "Highlight",
        kind: "class",
        summary: "Wrap needle with « » for display in notes/code.",
        construct: "Highlight.wrap(text, needle)",
        sections: [{ title: "Static", members: [
          m("wrap", "wrap(text, needle)", "Case-insensitive replace.", { static: true, params: [p("text", "string", ""), p("needle", "string", "")], returns: r("string", "") }),
        ]}],
      },
      {
        id: "Plural",
        name: "Plural",
        kind: "class",
        summary: "English one/many. For locale strings prefer luna.i18n.",
        construct: "Plural.en(n, one, many)",
        sections: [{ title: "Static", members: [
          m("en", "en(n, one, many)", "`item` vs `items`.", { static: true, params: [p("n", "number", ""), p("one", "string", ""), p("many", "string", "")], returns: r("string", "") }),
          m("join", "join(n, one, many)", "`3 items`.", { static: true, params: [p("n", "number", ""), p("one", "string", ""), p("many", "string", "")], returns: r("string", "") }),
        ]}],
      },
      {
        id: "Facets",
        name: "Facets",
        kind: "class",
        summary: "Count and filter rows by a string field.",
        construct: "Facets.new()",
        sections: [{ title: "Instance", members: [
          m("add", "add(row)", "Append a string map.", { params: [p("row", "table", "")], returns: r("Facets", "self") }),
          m("count", "count(field)", "`{ value = n }`.", { params: [p("field", "string", "")], returns: r("table", "") }),
          m("where", "where(field, value)", "Matching rows.", { params: [p("field", "string", ""), p("value", "string", "")], returns: r("table[]", "") }),
        ]}],
      },
      {
        id: "Scorecard",
        name: "Scorecard",
        kind: "class",
        summary: "Named 0–1 scores. score() is the mean. to_ui() emits score nodes.",
        construct: "Scorecard.new() or client:scorecard()",
        sections: [{ title: "Instance", members: [
          m("add", "add(name, value)", "Clamp 0–1.", { params: [p("name", "string", ""), p("value", "number", "")], returns: r("Scorecard", "self") }),
          m("score", "score()", "Average.", { returns: r("number", "") }),
          m("to_ui", "to_ui()", "score widgets.", { returns: r("table[]", "") }),
        ]}],
      },
      {
        id: "Sample",
        name: "Sample",
        kind: "class",
        summary: "Reservoir sample of a stream.",
        construct: "Sample.new(capacity?)",
        sections: [{ title: "Instance", members: [
          m("offer", "offer(value)", "Maybe keep.", { params: [p("value", "any", "")], returns: r("Sample", "self") }),
          m("values", "values()", "Current sample.", { returns: r("table", "") }),
        ]}],
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
        summary: "luna.ui — low-level settings DSL. settings_page() must return a page. Links must be https://github.com/… . Many aliases exist (toggle=switch, hosts=textarea, markdown=note, fold=details, steps=stepper, …).",
        notes: `<div class="callout"><h4>Control types</h4><p>Interactive: <code>switch</code>, <code>checkbox</code>, <code>text</code>, <code>textarea</code>, <code>number</code>, <code>select</code>, <code>slider</code>, <code>button</code>. Display: <code>note</code>, <code>heading</code>, <code>divider</code>, <code>spacer</code>, <code>badge</code>, <code>code</code>, <code>alert</code>, <code>kv</code>, <code>progress</code>, <code>link</code>, <code>stat</code>, <code>list_item</code>, <code>empty</code>, <code>chips</code>, <code>quote</code>, <code>fold</code>, <code>steps</code>, <code>timeline</code>, <code>score</code>, <code>compare</code>, <code>faq</code>, <code>status</code>. <code>enabled = false</code> greys interactive controls. Max 12 sections, 64 items.</p></div>`,
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
              m("stat", "stat(spec)", "`{ label, value, hint?, tone? }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["metric", "tile"] }),
              m("list_item", "list_item(spec)", "`{ title, body?, trailing?, tone? }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["listitem", "cell"] }),
              m("empty", "empty(spec)", "`{ text, hint? }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["placeholder"] }),
              m("chips", "chips(spec)", "Read-only tags. `{ labels }` or `{ items }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["tags", "pills"] }),
              m("quote", "quote(spec)", "`{ text, cite? }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["blockquote"] }),
              m("fold", "fold(spec)", "Collapsible `{ title, body, open? }`. Aliases `details`, `accordion`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["details", "accordion"] }),
              m("steps", "steps(spec)", "`{ labels, current }` wizard bar.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["stepper"] }),
              m("timeline", "timeline(spec)", "`{ events }` or `{ items }`.", { params: [p("spec", "table", "")], returns: r("table", "") }),
              m("score", "score(spec)", "`{ label, value, max? }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["rating", "gauge"] }),
              m("compare", "compare(spec)", "`{ left, right, left_label?, right_label? }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["vs"] }),
              m("faq", "faq(spec)", "`{ q, a }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["qa"] }),
              m("status", "status(spec)", "`{ text, tone, detail? }`.", { params: [p("spec", "table", "")], returns: r("table", ""), aliases: ["pill"] }),
              m("reload", "reload()", "Ask the host to rebuild the open settings page. Alias `refresh`.", { returns: r("boolean", ""), aliases: ["refresh"] }),
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
              <tr><td><code>luna.string</code></td><td>trim, slug, camel/snake/kebab/pascal, interpolate, levenshtein, similarity, ellipsis, matches (glob)</td></tr>
              <tr><td><code>luna.table</code></td><td>map, filter, reduce, every/some, zip, flatten, chunk, sum/average, shuffle, partition</td></tr>
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
      {
        id: "Systems",
        name: "Systems",
        kind: "typedef",
        summary: "luna.systems — which SDK subsystems exist. Closed doors (network, tun, tls, shell, java) are always false.",
        props: [
          prop("collections", "boolean", "List/Set/Queue/…"),
          prop("net_parse", "boolean", "URL/Query/CidrSet parse-only."),
          prop("text", "boolean", "Template/Fuzzy/Csv/Glob."),
          prop("reactive", "boolean", "Store/Signal/State/Flags."),
          prop("metrics", "boolean", "Metrics/Histogram/Stopwatch."),
          prop("forms", "boolean", "FormBuilder/WizardBuilder/TableBuilder."),
          prop("debug", "boolean", "luna.debug."),
          prop("schema", "boolean", "luna.schema."),
          prop("fs", "boolean", "Packaged ZIP reads."),
          prop("kit", "boolean", "Machine/Dashboard/…"),
          prop("forge", "boolean", "Schedule/Ruleset/Validator/…"),
          prop("schedule", "boolean", "Device-local windows / cron-lite."),
          prop("network", "boolean", "Always false."),
          prop("tun", "boolean", "Always false."),
          prop("tls", "boolean", "Always false."),
          prop("shell", "boolean", "Always false."),
          prop("java", "boolean", "Always false."),
        ],
        sections: [],
      },
      {
        id: "UrlParts",
        name: "UrlParts",
        kind: "typedef",
        summary: "Fields on a parsed URL object. Parse only; no fetch.",
        props: [
          prop("raw", "string", "Original, truncated."),
          prop("scheme", "string", "`https` / `http` / empty."),
          prop("host", "string", "Hostname."),
          prop("path", "string", "Path."),
          prop("query", "table", "Decoded map."),
          prop("fragment", "string", "Fragment."),
          prop("valid", "boolean", "Looks usable."),
          prop("github", "boolean", "Host is github.com."),
        ],
        sections: [],
      },
      {
        id: "StoreEvent",
        name: "StoreEvent",
        kind: "typedef",
        summary: "Payload passed to Store subscribers.",
        props: [
          prop("key", "string", "Changed key."),
          prop("value", "any", "New value (omit on delete)."),
          prop("deleted", "boolean", "True when the key was removed."),
        ],
        sections: [],
      },
      {
        id: "MetricsSnapshot",
        name: "MetricsSnapshot",
        kind: "typedef",
        summary: "metrics:snapshot() / toJSON().",
        props: [
          prop("counters", "table", "Name → number."),
          prop("gauges", "table", "Name → number."),
        ],
        sections: [],
      },
      {
        id: "SuggestHit",
        name: "SuggestHit",
        kind: "typedef",
        summary: "One Fuzzy.suggest row.",
        props: [
          prop("value", "string", "Candidate."),
          prop("score", "number", "0–1 similarity."),
        ],
        sections: [],
      },
      {
        id: "DiffLines",
        name: "DiffLines",
        kind: "typedef",
        summary: "Diff.lines() result. Line-set, not a unified patch.",
        props: [
          prop("added", "string[]", "Lines only in b."),
          prop("removed", "string[]", "Lines only in a."),
          prop("same", "number", "Shared line count."),
        ],
        sections: [],
      },
      {
        id: "SdkConstants",
        name: "SdkConstants",
        kind: "typedef",
        summary: "luna.Constants — numeric caps mirrored from the host.",
        props: [
          prop("API_LEVEL", "number", "`2`."),
          prop("MAX_RULES", "number", "`32`."),
          prop("MAX_TIMERS", "number", "`8`."),
          prop("MAX_STORAGE_KEYS", "number", "`96`."),
          prop("MAX_UI_SECTIONS", "number", "`12`."),
          prop("MAX_UI_ITEMS", "number", "`64`."),
          prop("MAX_COLLECTION", "number", "`256`."),
          prop("MAX_LISTENERS", "number", "`8`."),
          prop("GITHUB_HOST", "string", "`github.com`."),
        ],
        sections: [],
      },
    ],
  };
})();
