local client = luna.Client
local ui = luna.ui
local sundial = require("sundial")

local M = {}

local function phaseLabel(phase)
  return client.t("phase_" .. tostring(phase), tostring(phase))
end

local function desiredLabel(desired)
  if desired == true then
    return client.t("want_on", "On")
  end
  if desired == false then
    return client.t("want_off", "Off")
  end
  return client.t("want_none", "—")
end

local function policyLabel(policy)
  if policy == "guard" then
    return client.t("policy_guard", "guard — on only inside the window")
  end
  return client.t("policy_quiet", "quiet — off inside the window")
end

local function actionLabel(action)
  if action == "start" then
    return client.t("action_start", "start")
  end
  if action == "stop" then
    return client.t("action_stop", "stop")
  end
  if action == "hold" then
    return client.t("action_hold", "hold")
  end
  return client.t("action_none", "none")
end

function M.build(view)
  local names = sundial.dayNames()
  local days = {}
  local i = 1
  while i <= 7 do
    days[#days + 1] = ui.checkbox({
      id = "d" .. tostring(i),
      title = names:get(tostring(i)),
      body = i == view.weekday and client.t("today_body", "Today on this device.") or "",
      value = sundial.dayOn(i),
    })
    i = i + 1
  end

  local status = {
    ui.heading({ text = client.t("now", "Now"), level = 1 }),
    ui.alert({ text = view.alert, tone = view.tone }),
  }
  if view.lastError ~= "" then
    status[#status + 1] = ui.alert({ text = view.lastError, tone = "error" })
  end
  if not view.hasControl then
    status[#status + 1] = ui.alert({
      text = client.t("need_control", "vpn.control was not granted. The clock can still show the window, but it cannot start or stop protection."),
      tone = "error",
    })
  end
  status[#status + 1] = ui.badge({
    text = view.armed and client.t("armed", "Armed") or client.t("idle_arm", "Disarmed"),
    tone = view.armed and "success" or "warning",
  })
  status[#status + 1] = ui.badge({
    text = view.inside and client.t("inside", "In window") or client.t("outside", "Outside"),
    tone = view.inside and "accent" or "info",
  })
  status[#status + 1] = ui.badge({
    text = view.live and client.t("live", "Live") or client.t("idle", "Idle"),
    tone = view.live and "success" or "warning",
  })
  if view.hold then
    status[#status + 1] = ui.badge({ text = client.t("hold_badge", "Hold"), tone = "warning" })
  end
  if view.overnight then
    status[#status + 1] = ui.badge({ text = client.t("overnight", "Overnight"), tone = "info" })
  end
  status[#status + 1] = ui.kv({ label = client.t("clock", "Clock"), value = view.clock })
  status[#status + 1] = ui.kv({ label = client.t("weekday", "Weekday"), value = names:get(tostring(view.weekday)) })
  status[#status + 1] = ui.kv({ label = client.t("window", "Window"), value = view.window })
  status[#status + 1] = ui.kv({ label = client.t("days", "Days"), value = view.days })
  status[#status + 1] = ui.kv({ label = client.t("policy", "Policy"), value = view.policy })
  status[#status + 1] = ui.kv({ label = client.t("want", "Wanted"), value = desiredLabel(view.desired) })
  status[#status + 1] = ui.kv({ label = client.t("phase", "Phase"), value = phaseLabel(view.phase) })
  status[#status + 1] = ui.kv({ label = client.t("next", "Next edge"), value = view.nextIn })
  status[#status + 1] = ui.kv({ label = client.t("last", "Last action"), value = actionLabel(view.lastAction) })
  status[#status + 1] = ui.progress({ title = view.progressTitle, value = view.progress })
  status[#status + 1] = ui.divider()
  status[#status + 1] = ui.heading({ text = client.t("journal", "Journal"), level = 2 })
  status[#status + 1] = ui.code({ text = view.journal })
  status[#status + 1] = ui.button({ id = "refresh", title = client.t("refresh", "Refresh") })

  local schedule = {
    ui.note({ text = client.t("schedule_hint", "guard keeps protection on only inside the window. quiet pauses it inside the window. Times use this phone’s clock.") }),
    ui.switch({
      id = "armed",
      title = client.t("arm", "Arm schedule"),
      body = client.t("arm_body", "When off, Sundial never calls start or stop."),
      value = view.armed,
    }),
    ui.text({
      id = "label",
      title = client.t("label", "Window name"),
      value = view.label,
      placeholder = "Sleep",
    }),
    ui.select({
      id = "policy",
      title = client.t("policy", "Policy"),
      options = { "quiet", "guard" },
      value = view.policy,
    }),
    ui.heading({ text = client.t("hours", "Hours"), level = 2 }),
    ui.slider({
      id = "start_h",
      title = client.t("start_h", "Start hour"),
      value = view.startH,
      min = 0,
      max = 23,
    }),
    ui.number({
      id = "start_m",
      title = client.t("start_m", "Start minute"),
      value = view.startM,
      min = 0,
      max = 59,
    }),
    ui.slider({
      id = "end_h",
      title = client.t("end_h", "End hour"),
      value = view.endH,
      min = 0,
      max = 23,
    }),
    ui.number({
      id = "end_m",
      title = client.t("end_m", "End minute"),
      value = view.endM,
      min = 0,
      max = 59,
    }),
    ui.kv({ label = client.t("preview", "Preview"), value = view.window }),
    ui.note({ text = policyLabel(view.policy) }),
    ui.textarea({
      id = "notes",
      title = client.t("notes", "Notes"),
      value = view.notes,
      placeholder = client.t("notes_ph", "Why this window exists"),
    }),
  }
  if not view.valid then
    schedule[#schedule + 1] = ui.alert({
      text = client.t("same_time", "Start and end match — the window has zero length."),
      tone = "error",
    })
  end

  local signals = {
    ui.switch({
      id = "notify",
      title = client.t("notify", "Notify on scheduled actions"),
      body = client.t("notify_body", "At most once every 30 seconds, eight per hour."),
      value = view.notify,
    }),
    ui.checkbox({
      id = "hold",
      title = client.t("hold", "Skip the next automatic action"),
      body = client.t("hold_body", "One shot. Useful if you need protection during tonight’s quiet window."),
      value = view.hold,
    }),
    ui.spacer(),
    ui.note({
      text = client.t("poll_note", "The clock ticks every 60 seconds (host maximum 120). Edges are not second-accurate."),
    }),
  }

  local run = {
    ui.note({
      text = client.t("run_hint", "Apply now runs the same check as the minute timer. Start/stop is limited to once every 15 seconds."),
    }),
    ui.button({ id = "apply", title = client.t("apply", "Apply now") }),
    ui.danger_button({ id = "wipe", title = client.t("wipe", "Clear journal") }),
  }

  local device = {
    ui.heading({ text = client.t("device", "This device"), level = 2 }),
    ui.kv({ label = client.t("plugin", "Plugin"), value = view.plugin }),
    ui.kv({ label = client.t("app_ver", "App"), value = view.version }),
    ui.kv({ label = client.t("locale", "Locale"), value = view.locale }),
    ui.kv({ label = client.t("mode", "Mode"), value = view.mode }),
    ui.kv({ label = "DNS", value = view.dns }),
    ui.kv({ label = "MTU", value = view.mtu }),
    ui.kv({
      label = client.t("reconnect", "App reconnect"),
      value = view.reconnect and client.t("on", "On") or client.t("off", "Off"),
    }),
    ui.kv({
      label = client.t("boot", "Start on boot"),
      value = view.boot and client.t("on", "On") or client.t("off", "Off"),
    }),
    ui.heading({ text = client.t("grants", "Granted"), level = 3 }),
    ui.code({ text = view.grants }),
  }
  if view.reconnect and view.policy == "quiet" and view.armed then
    device[#device + 1] = ui.alert({
      text = client.t("reconnect_warn", "App auto-reconnect is on. It may start protection again after Sundial pauses it. Turn that off if quiet hours should stick."),
      tone = "warning",
    })
  end
  device[#device + 1] = ui.divider()
  device[#device + 1] = ui.link({
    text = client.t("source", "Source on GitHub"),
    url = "https://github.com/emirassssssssn/lunasdpi",
  })
  device[#device + 1] = ui.note({
    text = client.t("disclaimer", "Sundial only requests the same local start/stop as the Home screen. It is not a remote VPN and does not change DNS hosts or rules."),
  })

  local dayItems = {
    ui.note({ text = client.t("days_hint", "Unchecked days are ignored. The schedule never fires on those weekdays.") }),
  }
  i = 1
  while i <= #days do
    dayItems[#dayItems + 1] = days[i]
    i = i + 1
  end

  return luna.PageBuilder.new()
    :setTitle(client.t("title", "Sundial"))
    :setDescription(client.t("page_desc", "Clock for local protection. Quiet overnight, or guard a work shift. Not a packet injector."))
    :addSection(ui.section_ex(client.t("status", "Status"), client.t("status_desc", "Live window, wanted state, and the last automatic actions."), status))
    :addSection(ui.section_ex(client.t("schedule", "Schedule"), client.t("schedule_desc", "One window per plugin. Overnight ranges (22:00 → 07:00) are allowed."), schedule))
    :addSection(ui.section_ex(client.t("week", "Week"), client.t("week_desc", "Sunday is first. Unchecked days are left alone."), dayItems))
    :addSection(ui.section_ex(client.t("signals", "Signals"), client.t("signals_desc", "Notifications and a one-shot hold."), signals))
    :addSection(ui.section_ex(client.t("run", "Run"), client.t("run_desc", "Manual tick and journal."), run))
    :addSection(ui.section_ex(client.t("device", "Device"), client.t("device_desc", "Read-only app settings that can fight this schedule."), device))
    :build()
end

return M
