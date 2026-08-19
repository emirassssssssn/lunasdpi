local client = luna.Client
local desk = require("desk")

local function phaseLabel(phase)
  return client.t("phase_" .. tostring(phase), tostring(phase))
end

function settings_page()
  local snap = desk.snapshot()
  local tone = "warning"
  if snap.phase == "error" then
    tone = "error"
  elseif snap.live then
    tone = "success"
  end

  local items = {}
  if snap.lastError ~= "" then
    items[#items + 1] = luna.ui.alert({ text = snap.lastError, tone = "error" })
  end
  items[#items + 1] = luna.ui.alert({
    text = snap.live and client.t("vpn_on", "Protection is on. Stats below are live.")
      or client.t("vpn_off", "Protection is off. You can request start from this page; Android’s VPN dialog still appears."),
    tone = tone,
  })
  items[#items + 1] = luna.ui.badge({
    text = snap.live and client.t("live", "Live") or client.t("idle", "Idle"),
    tone = tone,
  })
  items[#items + 1] = luna.ui.kv({ label = client.t("phase", "Phase"), value = phaseLabel(snap.phase) })
  items[#items + 1] = luna.ui.kv({ label = client.t("mode", "Mode"), value = tostring(snap.mode) })
  items[#items + 1] = luna.ui.kv({ label = client.t("dns_mode", "DNS"), value = tostring(snap.dnsMode) })
  items[#items + 1] = luna.ui.kv({ label = "MTU", value = tostring(snap.mtu) })
  items[#items + 1] = luna.ui.kv({ label = client.t("uptime", "Uptime"), value = client.formatDuration(snap.uptime) })
  items[#items + 1] = luna.ui.kv({ label = client.t("bytes_in", "Bytes in"), value = client.formatBytes(snap.bytesIn) })
  items[#items + 1] = luna.ui.kv({ label = client.t("bytes_out", "Bytes out"), value = client.formatBytes(snap.bytesOut) })
  items[#items + 1] = luna.ui.kv({ label = client.t("packets", "Packets"), value = client.compactNumber(snap.packets) })
  items[#items + 1] = luna.ui.kv({ label = client.t("dns_queries", "DNS queries"), value = client.compactNumber(snap.dns) })
  items[#items + 1] = luna.ui.progress({
    title = client.t("drop_ratio", "Drop ratio"),
    value = snap.dropRatio,
  })
  items[#items + 1] = luna.ui.button({
    id = "refresh",
    title = client.t("refresh", "Refresh"),
  })

  return luna.PageBuilder.new()
    :setTitle(client.t("title", "Pulse"))
    :setDescription(client.t("page_desc", "Protection desk for this device. Watch-list rules are owned by this plugin only."))
    :addSection(client.t("status", "Status"), items)
    :addSection(client.t("control", "Protection"), {
      luna.ui.note({
        text = client.t("control_hint", "Start/stop is rate-limited to once every 15 seconds. Notifications at most once every 30 seconds."),
      }),
      luna.ui.switch({
        id = "alert",
        title = client.t("alert", "Notify when protection stops"),
        body = client.t("alert_body", "Sends a short notice if the local VPN disconnects or errors."),
        value = desk.alertOn(),
      }),
      luna.ui.button({
        id = "start",
        title = client.t("start", "Request start"),
      }),
      luna.ui.danger_button({
        id = "stop",
        title = client.t("stop", "Request stop"),
      }),
    })
    :addSection(client.t("watch", "Watch list"), {
      luna.ui.note({
        text = client.t("watch_hint", "One domain per line. Applied as a plugin-owned rule (max 32). Open Rules to see the Plugin badge."),
      }),
      luna.ui.switch({
        id = "watch_on",
        title = client.t("watch_on", "Use watch list"),
        body = client.t("watch_on_body", "When off, this plugin removes its own rule."),
        value = desk.watchOn(),
      }),
      luna.ui.select({
        id = "strategy",
        title = client.t("strategy", "Strategy"),
        options = { "automatic", "balanced", "aggressive" },
        value = desk.strategy(),
      }),
      luna.ui.textarea({
        id = "domains",
        title = client.t("domains", "Domains"),
        value = desk.domainsText(),
        placeholder = "example.com",
      }),
      luna.ui.button({
        id = "apply",
        title = client.t("save", "Apply watch list"),
      }),
    })
    :build()
end
