local domains = require("domains")

local function apply()
  local raw = luna.storage.get("domains") or ""
  local enabled = luna.storage.get("enabled") ~= "0"
  luna.rules.clear()
  local list = domains.parse(raw)
  if enabled and #list > 0 then
    luna.rules.upsert({
      id = "focus",
      name = "Focus list",
      enabled = true,
      strategy = "automatic",
      domains = list,
    })
    luna.log.info("Focus list applied (" .. tostring(#list) .. " domains)")
  else
    luna.log.info("Focus list cleared")
  end
end

function on_enable()
  if luna.storage.get("enabled") == nil then
    luna.storage.set("enabled", "1")
  end
  apply()
end

function on_disable()
  luna.rules.clear()
end

function on_setting_changed(id, value)
  if id == "enabled" then
    luna.storage.set("enabled", value and "1" or "0")
    apply()
  elseif id == "domains" then
    luna.storage.set("domains", tostring(value))
  elseif id == "apply" then
    apply()
    luna.notify.show("Focus list", luna.i18n.t("applied", "Rules updated"))
  end
end
