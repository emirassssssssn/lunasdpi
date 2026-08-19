local client = luna.Client

local M = {}
local intervalId = nil

local POLL_MS = 60000
local CONTROL_GAP = 15
local JOURNAL_MAX = 8

local function num(value, fallback)
  if type(value) == "number" then
    return value
  end
  return fallback or 0
end

local function clamp(value, lo, hi)
  value = math.floor(num(value, lo))
  if value < lo then
    return lo
  end
  if value > hi then
    return hi
  end
  return value
end

local function pad2(n)
  n = clamp(n, 0, 99)
  if n < 10 then
    return "0" .. tostring(n)
  end
  return tostring(n)
end

local function boolOr(key, fallback)
  local value = client.storeGetBool(key)
  if value == nil then
    return fallback
  end
  return value == true
end

function M.ensureDefaults()
  if not client.storeHas("armed") then
    client.storeSetBool("armed", false)
  end
  if not client.storeHas("policy") then
    client.storeSet("policy", "quiet")
  end
  if not client.storeHas("label") then
    client.storeSet("label", "Sleep")
  end
  if not client.storeHas("start_h") then
    client.storeSetNumber("start_h", 23)
  end
  if not client.storeHas("start_m") then
    client.storeSetNumber("start_m", 0)
  end
  if not client.storeHas("end_h") then
    client.storeSetNumber("end_h", 7)
  end
  if not client.storeHas("end_m") then
    client.storeSetNumber("end_m", 0)
  end
  if not client.storeHas("notify") then
    client.storeSetBool("notify", true)
  end
  if not client.storeHas("hold") then
    client.storeSetBool("hold", false)
  end
  if not client.storeHas("notes") then
    client.storeSet("notes", "")
  end
  local i = 1
  while i <= 7 do
    local key = "d" .. tostring(i)
    if not client.storeHas(key) then
      client.storeSetBool(key, true)
    end
    i = i + 1
  end
end

function M.armed()
  return boolOr("armed", false)
end

function M.notifyOn()
  return boolOr("notify", true)
end

function M.holdOn()
  return boolOr("hold", false)
end

function M.policy()
  local value = client.storeGet("policy") or "quiet"
  if value == "guard" then
    return "guard"
  end
  return "quiet"
end

function M.label()
  local raw = client.trim(client.storeGet("label") or "Sleep")
  if raw == "" then
    return "Sleep"
  end
  return client.truncate(raw, 24)
end

function M.notes()
  return client.storeGet("notes") or ""
end

function M.startH()
  return clamp(client.storeGetNumber("start_h"), 0, 23)
end

function M.startM()
  return clamp(client.storeGetNumber("start_m"), 0, 59)
end

function M.endH()
  return clamp(client.storeGetNumber("end_h"), 0, 23)
end

function M.endM()
  return clamp(client.storeGetNumber("end_m"), 0, 59)
end

function M.dayOn(n)
  return boolOr("d" .. tostring(n), true)
end

function M.clockMinutes()
  return client.hour() * 60 + luna.time.minute()
end

function M.startMinutes()
  return M.startH() * 60 + M.startM()
end

function M.endMinutes()
  return M.endH() * 60 + M.endM()
end

function M.formatHm(h, m)
  return pad2(h) .. ":" .. pad2(m)
end

function M.windowText()
  return M.formatHm(M.startH(), M.startM()) .. " → " .. M.formatHm(M.endH(), M.endM())
end

function M.spanMinutes(fromM, toM)
  local span = toM - fromM
  if span <= 0 then
    span = span + 24 * 60
  end
  return span
end

function M.inWindow(nowM)
  local startM = M.startMinutes()
  local endM = M.endMinutes()
  if startM == endM then
    return false
  end
  if startM < endM then
    return nowM >= startM and nowM < endM
  end
  return nowM >= startM or nowM < endM
end

function M.overnight()
  return M.startMinutes() > M.endMinutes()
end

function M.todayOn()
  return M.dayOn(client.weekday())
end

function M.windowValid()
  return M.startMinutes() ~= M.endMinutes()
end

function M.desiredOn(nowM)
  if not M.windowValid() then
    return nil
  end
  local inside = M.inWindow(nowM)
  if M.policy() == "guard" then
    return inside
  end
  return not inside
end

function M.minutesUntil(target, nowM)
  return M.spanMinutes(nowM, target)
end

function M.appendJournal(line)
  local raw = client.storeGet("journal") or ""
  local rows = {}
  for row in string.gmatch(raw, "[^\r\n]+") do
    rows[#rows + 1] = row
  end
  rows[#rows + 1] = client.isoNow() .. "  " .. line
  local start = 1
  if #rows > JOURNAL_MAX then
    start = #rows - JOURNAL_MAX + 1
  end
  local kept = {}
  local i = start
  while i <= #rows do
    kept[#kept + 1] = rows[i]
    i = i + 1
  end
  client.storeSet("journal", table.concat(kept, "\n"))
end

function M.journalText()
  local raw = client.storeGet("journal") or ""
  if raw == "" then
    return client.t("journal_empty", "No scheduled actions yet.")
  end
  return raw
end

function M.clearJournal()
  client.storeSet("journal", "")
  client.storeSet("last_action", "")
  client.storeSet("last_error", "")
end

local function phaseOf(snap)
  if type(snap) == "table" and snap.phase ~= nil then
    return tostring(snap.phase)
  end
  return tostring(client.vpnPhase())
end

local function control(action)
  local now = client.now()
  local last = num(client.storeGetNumber("last_ctrl"), 0)
  if now - last < CONTROL_GAP then
    return false, "throttle"
  end
  local ok, err = pcall(function()
    if action == "start" then
      client.rest.post("vpn", "start")
    else
      client.rest.post("vpn", "stop")
    end
  end)
  client.storeSetNumber("last_ctrl", now)
  if ok then
    client.storeSet("last_error", "")
    return true, nil
  end
  client.storeSet("last_error", tostring(err))
  client.logWarn(tostring(err))
  return false, tostring(err)
end

local function announce(kind, text)
  if not M.notifyOn() then
    return
  end
  local title = client.t("title", "Sundial") .. " · " .. M.label()
  if kind == "start" then
    client.notifySuccess(title, text)
  elseif kind == "stop" then
    client.notifyWarn(title, text)
  else
    client.notifyInfo(title, text)
  end
end

function M.tick(reason)
  M.ensureDefaults()
  local nowM = M.clockMinutes()
  local phase = phaseOf(client.rest.get("vpn"))
  local active = client.vpnActive() or phase == "connected"
  local connecting = phase == "connecting" or phase == "requesting_permission"
  local desired = M.desiredOn(nowM)

  if not M.armed() then
    return { skipped = "disarmed" }
  end
  if not M.todayOn() then
    return { skipped = "day" }
  end
  if desired == nil then
    return { skipped = "invalid" }
  end

  local wouldStart = desired and not active and not connecting
  local wouldStop = (not desired) and (active or connecting)
  local wouldAct = wouldStart or wouldStop

  if M.holdOn() then
    if wouldAct then
      client.storeSetBool("hold", false)
      M.appendJournal("hold  skipped " .. reason)
      client.storeSet("last_action", "hold")
      client.logInfo("Sundial hold consumed")
    end
    return { skipped = "hold" }
  end

  if not wouldAct then
    return { skipped = "aligned" }
  end

  local action = wouldStart and "start" or "stop"
  local ok, err = control(action)
  if not ok then
    if err ~= "throttle" then
      M.appendJournal(action .. "  error")
    end
    return { ok = false, err = err }
  end

  local note = action == "start"
    and client.t("acted_start", "Requested start for this window.")
    or client.t("acted_stop", "Requested stop for this window.")
  client.storeSet("last_action", action)
  M.appendJournal(action .. "  " .. tostring(reason))
  client.logInfo("Sundial " .. action .. " (" .. tostring(reason) .. ")")
  announce(action, note)
  return { ok = true, action = action }
end

function M.startClock()
  if intervalId ~= nil then
    return
  end
  intervalId = client.setInterval(POLL_MS, function()
    M.tick("poll")
  end)
end

function M.stopClock()
  if intervalId == nil then
    return
  end
  client.clearInterval(intervalId)
  intervalId = nil
end

function M.dayNames()
  local col = luna.Collection.new()
  col:set("1", client.t("day_1", "Sun"))
  col:set("2", client.t("day_2", "Mon"))
  col:set("3", client.t("day_3", "Tue"))
  col:set("4", client.t("day_4", "Wed"))
  col:set("5", client.t("day_5", "Thu"))
  col:set("6", client.t("day_6", "Fri"))
  col:set("7", client.t("day_7", "Sat"))
  return col
end

function M.activeDaysText()
  local names = M.dayNames()
  local parts = {}
  local i = 1
  while i <= 7 do
    if M.dayOn(i) then
      parts[#parts + 1] = names:get(tostring(i))
    end
    i = i + 1
  end
  if #parts == 0 then
    return client.t("no_days", "No days selected")
  end
  if #parts == 7 then
    return client.t("every_day", "Every day")
  end
  return table.concat(parts, " · ")
end

function M.view()
  M.ensureDefaults()
  local nowM = M.clockMinutes()
  local snap = client.rest.get("vpn") or {}
  local cfg = client.appConfig() or {}
  local phase = phaseOf(snap)
  local live = client.vpnActive() or phase == "connected"
  local inside = M.inWindow(nowM)
  local desired = M.desiredOn(nowM)
  local valid = M.windowValid()
  local nextTarget = inside and M.endMinutes() or M.startMinutes()
  local untilMin = valid and M.minutesUntil(nextTarget, nowM) or 0
  local untilSec = untilMin * 60 - luna.time.second()
  if untilSec < 0 then
    untilSec = 0
  end

  local span = valid and M.spanMinutes(M.startMinutes(), M.endMinutes()) or 1
  local elapsed = 0
  if valid and inside then
    elapsed = M.spanMinutes(M.startMinutes(), nowM)
    if elapsed > span then
      elapsed = span
    end
  elseif valid then
    local gap = M.spanMinutes(M.endMinutes(), M.startMinutes())
    elapsed = M.spanMinutes(M.endMinutes(), nowM)
    span = gap
    if elapsed > span then
      elapsed = span
    end
  end
  local ratio = elapsed / span

  local tone = "info"
  if not M.armed() then
    tone = "warning"
  elseif not valid or not M.todayOn() then
    tone = "warning"
  elseif live and desired == true then
    tone = "success"
  elseif (not live) and desired == false then
    tone = "success"
  else
    tone = "warning"
  end

  local reconnect = cfg.auto_reconnect == true
  local lastError = client.storeGet("last_error") or ""
  local lastAction = client.storeGet("last_action") or ""

  local alert = client.t("disarmed_alert", "Schedule is off. Nothing is started or stopped until you arm it.")
  if M.armed() and not valid then
    alert = client.t("invalid_alert", "Start and end are the same. Widen the window before arming does anything.")
  elseif M.armed() and not M.todayOn() then
    alert = client.t("day_alert", "Today is excluded. Sundial will wait for a selected weekday.")
  elseif M.armed() and M.holdOn() then
    alert = client.t("hold_alert", "Next automatic start/stop is skipped. The hold clears after that moment.")
  elseif M.armed() and M.policy() == "quiet" then
    if inside then
      alert = client.t("quiet_in", "Inside the quiet window — protection should stay off.")
    else
      alert = client.t("quiet_out", "Outside the quiet window — protection may stay on.")
    end
  elseif M.armed() then
    if inside then
      alert = client.t("guard_in", "Inside the guard window — protection should stay on.")
    else
      alert = client.t("guard_out", "Outside the guard window — protection should stay off.")
    end
  end

  return {
    tone = tone,
    alert = alert,
    lastError = lastError,
    lastAction = lastAction,
    armed = M.armed(),
    hold = M.holdOn(),
    notify = M.notifyOn(),
    policy = M.policy(),
    label = M.label(),
    live = live,
    phase = phase,
    inside = inside,
    desired = desired,
    valid = valid,
    overnight = M.overnight(),
    todayOn = M.todayOn(),
    weekday = client.weekday(),
    clock = M.formatHm(client.hour(), luna.time.minute()),
    window = M.windowText(),
    days = M.activeDaysText(),
    nextIn = client.formatDuration(untilSec),
    progress = ratio,
    progressTitle = inside and client.t("progress_in", "Through this window") or client.t("progress_out", "Until next window"),
    journal = M.journalText(),
    startH = M.startH(),
    startM = M.startM(),
    endH = M.endH(),
    endM = M.endM(),
    reconnect = reconnect,
    boot = cfg.start_on_boot == true,
    mode = tostring(cfg.mode or client.appMode()),
    dns = tostring(cfg.dns_mode or client.appDnsMode()),
    mtu = tostring(cfg.mtu or client.appMtu()),
    version = client.appVersion(),
    locale = client.language(),
    plugin = client.tag(),
    notes = M.notes(),
    grants = M.grantsText(),
    hasControl = client.hasPermission("vpn.control") == true,
    hasNotify = client.hasPermission("notify") == true,
  }
end

function M.grantsText()
  local granted = client.grantedPermissions()
  local parts = {}
  local i = 1
  while i <= 16 do
    local item = granted[i]
    if item == nil then
      break
    end
    parts[#parts + 1] = tostring(item)
    i = i + 1
  end
  if #parts == 0 then
    return "—"
  end
  return table.concat(parts, "\n")
end

return M
