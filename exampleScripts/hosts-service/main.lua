local client = luna.Client
local Events = luna.Events
local service = require("service")

client:on(Events.Ready, function()
  client.logInfo("ready " .. client.tag())
end)

client:on(Events.VpnPhase, function(phase)
  if phase ~= "connected" then
    return
  end
  if service.enabled() then
    service.apply()
    client.logInfo("Hosts overlay reapplied; answers are live")
  end
end)

function on_enable()
  service.ensureDefaults()
  service.apply()
end

function on_disable()
  client.clearHosts()
end

function on_setting_changed(id, value)
  if id == "enabled" then
    client.storeSetBool("enabled", value and true or false)
    service.apply()
  elseif id == "hosts" then
    client.storeSet("hosts", tostring(value))
  elseif id == "apply" then
    local result = service.apply()
    local msg = client.t("applied", "Applied") .. ": " .. tostring(result.applied or 0)
    if (result.skipped or 0) > 0 then
      msg = msg .. " · " .. client.t("skipped", "skipped") .. ": " .. tostring(result.skipped)
    end
    if not result.live then
      msg = msg .. " · " .. client.t("idle", "Idle")
    end
    client.notifyInfo(client.t("title", "Hosts file"), msg)
  end
end
