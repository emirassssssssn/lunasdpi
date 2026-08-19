local client = luna.Client

local M = {}

M.DEFAULT_DOMAINS = "example.com\n*.example.com\n"
M.RULE_ID = "watch"
M.RULE_NAME = "Watch list"

local function num(value)
  if type(value) == "number" then
    return value
  end
  return 0
end

function M.ensureDefaults()
  if not client.storeHas("watch_on") then
    client.storeSetBool("watch_on", false)
  end
  if not client.storeHas("domains") then
    client.storeSet("domains", M.DEFAULT_DOMAINS)
  end
  if not client.storeHas("strategy") then
    client.storeSet("strategy", "automatic")
  end
  if not client.storeHas("alert") then
    client.storeSetBool("alert", true)
  end
end

function M.watchOn()
  return client.storeGetBool("watch_on") == true
end

function M.alertOn()
  return client.storeGetBool("alert") ~= false
end

function M.strategy()
  local value = client.storeGet("strategy") or "automatic"
  if value == "balanced" or value == "aggressive" or value == "basic" then
    return value
  end
  return "automatic"
end

function M.domainsText()
  return client.storeGet("domains") or M.DEFAULT_DOMAINS
end

function M.parseDomains(raw)
  local accepted = {}
  local skipped = 0
  for line in string.gmatch(raw or "", "[^\r\n]+") do
    local text = client.trim(line)
    if text ~= "" and text:sub(1, 1) ~= "#" then
      if client.isDomainPattern(text) then
        accepted[#accepted + 1] = client.normalizeDomain(text)
      else
        skipped = skipped + 1
      end
    end
  end
  if #accepted > 32 then
    skipped = skipped + (#accepted - 32)
    while #accepted > 32 do
      accepted[#accepted] = nil
    end
  end
  return accepted, skipped
end

function M.snapshot()
  local snap = client.vpnSnapshot() or {}
  local live = client.vpnActive()
  local processed = num(snap.packets_processed)
  local dropped = num(snap.packets_dropped)
  local dropRatio = 0
  if processed > 0 then
    dropRatio = dropped / processed
  end
  return {
    live = live,
    phase = snap.phase or client.vpnPhase(),
    uptime = num(snap.uptime_seconds),
    bytesIn = num(snap.bytes_in),
    bytesOut = num(snap.bytes_out),
    packets = processed,
    dropped = dropped,
    dns = num(snap.dns_queries),
    strategy = snap.strategy or client.appMode(),
    engine = snap.engine_alive == true,
    tun = snap.tun_active == true,
    dropRatio = dropRatio,
    mode = client.appMode(),
    dnsMode = client.appDnsMode(),
    mtu = client.appMtu(),
    lastError = client.storeGet("last_error") or "",
  }
end

function M.applyWatch()
  if not M.watchOn() then
    client.clearRules()
    client.logInfo("Watch list cleared")
    return { applied = 0, skipped = 0 }
  end
  local list, skipped = M.parseDomains(M.domainsText())
  if #list == 0 then
    client.clearRules()
    client.logInfo("Watch list empty")
    return { applied = 0, skipped = skipped }
  end
  local body = luna.RuleBuilder.new()
    :setId(M.RULE_ID)
    :setName(M.RULE_NAME)
    :setEnabled(true)
    :setStrategy(M.strategy())
    :setDomains(list)
    :build()
  client.upsertRule(body)
  client.logInfo("Watch list applied (" .. tostring(#list) .. " domains)")
  return { applied = #list, skipped = skipped }
end

function M.control(action)
  local ok, err = pcall(function()
    if action == "start" then
      client.vpnStart()
    else
      client.vpnStop()
    end
  end)
  if ok then
    client.storeSet("last_error", "")
    return true
  end
  client.storeSet("last_error", tostring(err))
  client.logWarn(tostring(err))
  return false
end

function M.onPhase(phase)
  if not M.alertOn() then
    return
  end
  if phase == "disconnected" then
    client.notifyWarn(client.t("title", "Pulse"), client.t("alert_down", "Protection stopped."))
  elseif phase == "error" then
    client.notifyError(client.t("title", "Pulse"), client.t("alert_error", "Protection hit an error."))
  end
end

return M
