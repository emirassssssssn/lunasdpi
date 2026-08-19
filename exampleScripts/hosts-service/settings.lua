local client = luna.Client
local service = require("service")

function settings_page()
  local on = service.enabled()
  local preview = service.preview()
  local protection = preview.live
  local used = preview.count / preview.max
  return luna.PageBuilder.new()
    :setTitle(client.t("title", "Hosts file"))
    :setDescription(client.t("page_desc", "Classic /etc/hosts overlay for the local DNS engine."))
    :addSection(client.t("status", "Status"), {
      luna.ui.alert({
        text = protection
          and client.t("vpn_on", "Protection is on. Hosts answers are live.")
          or client.t("vpn_off", "Start protection on the Home screen. Hosts only apply while the local VPN is up. This plugin does not start it."),
        tone = protection and "success" or "warning",
      }),
      luna.ui.badge({
        text = protection and client.t("live", "Live") or client.t("idle", "Idle"),
        tone = protection and "success" or "warning",
      }),
      luna.ui.kv({
        label = client.t("protection", "Protection"),
        value = protection and client.t("on", "On") or client.t("off", "Off"),
      }),
      luna.ui.kv({
        label = client.t("mappings", "Mappings"),
        value = tostring(preview.count) .. " / " .. tostring(preview.max),
      }),
      luna.ui.progress({
        title = client.t("capacity", "Capacity"),
        value = used,
      }),
    })
    :addSection(client.t("section", "Hosts"), {
      luna.ui.note({
        text = client.t("hint", "Same format as /etc/hosts: IPv4 then hostname. One mapping per line. # starts a comment. IPv6 lines are ignored."),
      }),
      luna.ui.heading({ text = client.t("example", "Example"), level = 2 }),
      luna.ui.code({ text = service.exampleText() }),
      luna.ui.switch({
        id = "enabled",
        title = client.t("use_file", "Use this hosts file"),
        body = client.t("use_file_body", "When off, this plugin clears its DNS overrides."),
        value = on,
      }),
      luna.ui.textarea({
        id = "hosts",
        title = client.t("hosts", "Hosts"),
        value = service.text(),
        placeholder = "xx.xx.xx.xx growtopia1.com",
      }),
      luna.ui.button({
        id = "apply",
        title = client.t("save", "Apply"),
      }),
    })
    :build()
end
