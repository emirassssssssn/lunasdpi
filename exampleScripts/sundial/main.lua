local client = luna.Client
local Events = luna.Events
local sundial = require("sundial")

client:on(Events.Ready, function()
  client.logInfo("ready " .. client.tag())
end)

client:on(Events.VpnPhase, function(phase)
  client.logInfo("vpn phase " .. tostring(phase))
  sundial.tick("phase")
end)

function on_enable()
  sundial.ensureDefaults()
  sundial.startClock()
  sundial.tick("enable")
end

function on_disable()
  sundial.stopClock()
end

function on_setting_changed(id, value)
  if id == "armed" then
    client.storeSetBool("armed", value and true or false)
    sundial.tick("armed")
  elseif id == "notify" then
    client.storeSetBool("notify", value and true or false)
  elseif id == "hold" then
    client.storeSetBool("hold", value and true or false)
  elseif id == "policy" then
    local policy = tostring(value)
    if policy ~= "guard" then
      policy = "quiet"
    end
    client.storeSet("policy", policy)
    sundial.tick("policy")
  elseif id == "label" then
    client.storeSet("label", tostring(value))
  elseif id == "notes" then
    client.storeSet("notes", tostring(value))
  elseif id == "start_h" then
    client.storeSetNumber("start_h", tonumber(value) or 23)
  elseif id == "start_m" then
    client.storeSetNumber("start_m", tonumber(value) or 0)
  elseif id == "end_h" then
    client.storeSetNumber("end_h", tonumber(value) or 7)
  elseif id == "end_m" then
    client.storeSetNumber("end_m", tonumber(value) or 0)
  elseif id == "apply" then
    sundial.tick("manual")
  elseif id == "wipe" then
    sundial.clearJournal()
  elseif id == "refresh" then
    return
  else
    local day = string.match(tostring(id), "^d([1-7])$")
    if day then
      client.storeSetBool("d" .. day, value and true or false)
    end
  end
end
