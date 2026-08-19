function settings_page()
  local enabled = luna.storage.get("enabled") ~= "0"
  local domains = luna.storage.get("domains") or ""
  return luna.ui.page({
    title = luna.i18n.t("title", "Focus list"),
    sections = {
      luna.ui.section(luna.i18n.t("section", "List"), {
        luna.ui.note({
          text = luna.i18n.t("hint", "One domain per line. Only these destinations get a plugin-owned rule."),
        }),
        luna.ui.switch({
          id = "enabled",
          title = luna.i18n.t("use_list", "Use this list"),
          body = luna.i18n.t("use_list_body", "When off, the plugin removes its own rules."),
          value = enabled,
        }),
        luna.ui.text({
          id = "domains",
          title = luna.i18n.t("domains", "Domains"),
          value = domains,
          placeholder = "example.com",
          multiline = true,
        }),
        luna.ui.button({
          id = "apply",
          title = luna.i18n.t("save", "Save to Rules"),
        }),
      }),
    },
  })
end
