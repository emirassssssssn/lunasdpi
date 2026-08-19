# Hosts file

Example Lunas DPI plugin written against the current Lua SDK (`luna.Client`, events, `PageBuilder`, `HostsBuilder`, `Collection`, local REST). It feeds a classic `/etc/hosts` list into the local DNS engine so names like `growtopia1.com` resolve to the IPv4 you type.

Zip **this folder** (so `manifest.json` is at the archive root, or inside one top-level folder) and import it from **Settings → Plugins**.

```
hosts-service/
  manifest.json
  main.lua
  settings.lua
  modules/service.lua
  locale/en.json
  locale/tr.json
  assets/icon.svg
  README.md
```

After import:

1. Open the plugin and enable it (grants storage, settings UI, hosts overlay, protection state, notifications).
2. Open **plugin settings**. Status shows whether protection is live and how many mappings parse. The hosts box is prefilled with:

```
xx.xx.xx.xx growtopia1.com
xx.xx.xx.xx growtopia2.com
```

3. Replace `xx.xx.xx.xx` with a real IPv4 (LAN, `127.0.0.1`, or a public A record). Tap **Apply**.
4. Start **protection** on the Home screen. Hosts only answer while the local VPN is up. The plugin does not start it for you. When protection connects, the plugin reapplies the overlay.

The Lua side uses `client.rest.put("hosts", text)` / `delete("hosts")`, listens for `luna.Events.Ready` and `VpnPhase`, and builds the settings screen with `PageBuilder`. Comments (`#`) and IPv6 lines are ignored. The overlay is not written to Android’s system hosts file.
