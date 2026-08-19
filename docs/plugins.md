# Lunas DPI eklentileri

Lua API belgesi (discord.js tarzı, arama ve sınıf sayfaları):

**https://emirassssssn.github.io/lunasdpi/**

Kaynak ağaç: [`docs/lua/`](lua/). GitHub’daki HTML dosyası tıklanınca kaynak kod görünür; asıl belge GitHub Pages’tedir.

## Kısa özet

Lunas DPI, Lua ZIP paketlerini cihaz üzerinde **sıkı bir sanal alanda** çalıştırır. Eklentiler kural paketleri, hosts ezmesi, ayar sayfası, sayaç ve bildirim ekler. Paket enjekte edemez, HTTPS çözemez, kabuk çalıştıramaz, başka uygulamaları okuyamaz.

- Dil: Lua (`api_level` **1**)
- Kurulum: **Ayarlar → Eklentiler → ZIP içe aktar**, sonra eklentiyi açın (izinler o anda verilir)
- `require("ad")` yalnızca `modules/ad.lua`
- Her Lua çağrısı **1,5 sn**; `io` / `os` / `debug` / `package` / `load` / `java` yok
- İzinler: `storage`, `ui.settings`, `rules.read` / `rules.write`, `vpn.read` / `vpn.control`, `notify`, `hosts.write`, `app.read`
