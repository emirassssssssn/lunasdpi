local client = luna.Client

local M = {}

M.DEFAULT = "xx.xx.xx.xx growtopia1.com\nxx.xx.xx.xx growtopia2.com\n"

function M.maxHosts()
  local options = client.options()
  return (options and options.max_hosts) or 256
end

function M.text()
  return client.storeGet("hosts") or M.DEFAULT
end

function M.enabled()
  local value = client.storeGetBool("enabled")
  if value == nil then
    return true
  end
  return value
end

function M.ensureDefaults()
  if not client.storeHas("enabled") then
    client.storeSetBool("enabled", true)
  end
  if not client.storeHas("hosts") then
    client.storeSet("hosts", M.DEFAULT)
  end
end

function M.exampleText()
  return luna.HostsBuilder.new()
    :add("growtopia1.com", "192.168.1.10")
    :add("growtopia2.com", "192.168.1.10")
    :toText()
end

function M.preview()
  local parsed = client.parseHosts(M.text())
  local entries = (parsed and parsed.entries) or {}
  local errors = (parsed and parsed.errors) or {}
  return {
    count = luna.table.size(entries),
    skipped = luna.table.size(errors),
    live = client.vpnActive(),
    max = M.maxHosts(),
  }
end

function M.cache()
  local col = client.collection()
  local list = client.listHosts()
  local i = 1
  while list[i] do
    local row = list[i]
    col:set(row.host or row.hostname, row.ip or row.ipv4)
    i = i + 1
  end
  return col
end

function M.apply()
  if not M.enabled() then
    client.rest.delete("hosts")
    client.logInfo("Hosts overlay cleared")
    return { applied = 0, skipped = 0, live = false }
  end
  local result = client.rest.put("hosts", M.text())
  local cache = M.cache()
  client.logInfo("Hosts overlay applied (" .. tostring(cache:size()) .. " names)")
  result.live = client.vpnActive()
  return result
end

return M
