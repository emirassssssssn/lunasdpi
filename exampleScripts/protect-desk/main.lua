local client = luna.Client
local Events = luna.Events
local desk = require("desk")

client:on(Events.Ready, function()
  client.logInfo("ready " .. client.tag())
end)

client:on(Events.VpnPhase, function(phase)
  client.logInfo("vpn phase " .. tostring(phase))
  desk.onPhase(phase)
end)

function on_enable()
  desk.ensureDefaults()
  desk.applyWatch()
end

function on_disable()
  client.clearRules()
end

function on_setting_changed(id, value)
  if id == "watch_on" then
    client.storeSetBool("watch_on", value and true or false)
    desk.applyWatch()
  elseif id == "alert" then
    client.storeSetBool("alert", value and true or false)
  elseif id == "strategy" then
    client.storeSet("strategy", tostring(value))
    if desk.watchOn() then
      desk.applyWatch()
    end
  elseif id == "domains" then
    client.storeSet("domains", tostring(value))
  elseif id == "apply" then
    local result = desk.applyWatch()
    local msg = client.t("applied", "Applied") .. ": " .. tostring(result.applied or 0)
    if (result.skipped or 0) > 0 then
      msg = msg .. " · " .. client.t("skipped", "skipped") .. ": " .. tostring(result.skipped)
    end
    client.notifyInfo(client.t("title", "Pulse"), msg)
  elseif id == "start" then
    desk.control("start")
  elseif id == "stop" then
    desk.control("stop")
  end
end
