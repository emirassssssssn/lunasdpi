# Lunas DPI eklentileri

Lunas DPI, Lua ile yazılmış ZIP paketlerini cihaz üzerinde, **sıkı bir sanal alanda** çalıştırır. Eklentiler uygulamanın yapabildiği yararlı işleri genişletir; paket enjekte edemez, HTTPS çözemez, başka uygulamaları okuyamaz, kabuk çalıştıramaz.

## Paket ağacı

Klasörü ZIP’leyin. `manifest.json` arşivin kökünde veya tek bir üst klasörün içinde olabilir (`my-plugin/manifest.json`).

```
my-plugin/
  manifest.json          zorunlu
  main.lua               zorunlu giriş
  settings.lua           isteğe bağlı ayar sayfası
  modules/               luna require("ad") → modules/ad.lua
    helper.lua
  locale/
    en.json
    tr.json
  assets/
    icon.svg             veya icon.png (en fazla 256 KB PNG, 1024 px)
  README.md
```

İzin verilen uzantılar: `lua`, `json`, `svg`, `png`, `md`, `txt`. En fazla 64 dosya, 2 MB ZIP, her Lua kaynağı 128 KB. Lua **bytecode** (`\x1bLua`) reddedilir.

## manifest.json

```json
{
  "id": "community.focus.list",
  "name": "Focus list",
  "author": "Sizin adınız",
  "version": "1.0.0",
  "description": "Kısa açıklama (en fazla 280 karakter).",
  "api_level": 1,
  "min_app_version": "1.0.0",
  "main": "main.lua",
  "settings": "settings.lua",
  "icon": "assets/icon.svg",
  "homepage": "https://github.com/you/your-plugin",
  "permissions": ["storage", "ui.settings", "rules.write"]
}
```

- `id` küçük harf ters-alan adı. `com.lunasdev.*` ve `luna` rezerve.
- `homepage` yalnızca `https://github.com/…` olabilir. Kod indirilmez; Ayarlar’da bağlantı gösterilir.
- `api_level` şu an **1** olmak zorunda.

Kurulum: **Ayarlar → Eklentiler → ZIP içe aktar**. İnceleme ekranından kurun, sonra eklentiyi **açın** (izinler o anda verilir). Ayar sayfası varsa ayrıntıda **Eklenti ayarlarını aç** görünür.

## İzinler

| Anahtar | Ne verir | Ne vermez |
| --- | --- | --- |
| `storage` | Eklentiye özel 64 anahtar, 32 KB değer | Diğer eklentilerin deposu, uygulama ayarları |
| `ui.settings` | `settings_page()` ile uygulama içi ayar ekranı | Rastgele Compose / WebView |
| `rules.read` | Yalnızca `p:{id}:` önekli kurallar | Discord veya sizin diğer kurallarınız |
| `rules.write` | En fazla 16 kendi kuralı, kural başına 32 alan adı | Yerleşik kuralların üzerine yazmak |
| `vpn.read` | Koruma açık mı, kaba sayaçlar | Paket içeriği, DNS cevapları |
| `vpn.control` | Yerel korumayı başlat / durdur (15 sn’de bir) | Uzak VPN, TUN’a yazmak |
| `notify` | Kısa bildirim (30 sn / eklenti, saatte 8) | Tam ekran, rastgele kanal |
| `hosts.write` | Koruma açıkken yerel DNS hosts ezmesi (IPv4, eklenti başına 256 ad) | Sistem `/etc/hosts`, IPv6, TUN aralığı `10.7.0.0/24` |
| `app.read` | Kaba koruma ayarları (mod, DNS, MTU) | Uygulama listesi, diğer eklentiler, ayar yazmak |

`rules.write` otomatik olarak okumayı da ekler. `settings.lua` varsa `ui.settings` eklenir.

## Luna API (`luna`)

discord.js benzeri bir **modül sistemi**: `luna.Client` büyük istemci sınıfı (~240 yöntem), yöneticiler (`luna.rules`, `luna.hosts`, `luna.vpn`), olaylar, `Collection`, builder’lar ve stdlib. 200’den fazla çağrılabilir işlev vardır. Her Lua çağrısı **1,5 saniye** ile sınırlıdır. `io`, `os`, `debug`, `package`, `load`, `dofile`, `java`, `luajava`, `string.dump` yoktur. `require` yalnızca `modules/` altındadır.

Bu API **Lunas DPI’yi** genişletir (kurallar, hosts, ayar UI, sayaçlar, depo, kaba koruma ayarları). Uygulamada olmayan bir özelliği eklentiyle doldurmak = bu yüzeyin izin verdiği işler. TUN’a yazmak, TLS çözmek, kabuk, rastgele HTTP veya başka uygulamaları okumak **yoktur**.

| Modül | Rol |
| --- | --- |
| `luna.Client` / `luna.client` | Tek büyük sınıf: kimlik, olaylar, kurallar, hosts, VPN, depo, format, doğrulama, UI, REST |
| `luna.user` / `luna.User` | Bu eklentinin kimliği |
| `luna.permissions` / `luna.Intents` | Verilen izinler, `has`, `bitfield` |
| `luna.IntentsBitField` | `from` / `resolve` / `has` |
| `luna.Events` | `Ready`, `VpnPhase` sabitleri |
| `luna.events` | `on` / `once` / `off` — `ready`, `vpnPhase` |
| `luna.storage` | `get/set/has/keys/getJSON/incr` … |
| `luna.rules` | Kendi kuralları: `create`, `cache.get`, kural nesnesinde `edit/enable/delete` |
| `luna.hosts` | Hosts overlay: `set_text`, `add`, `resolve`, `to_text` |
| `luna.vpn` | Durum, sayaçlar, `requestStart` / `requestStop` |
| `luna.app` | Sürüm; `app.read` ile `config()` / `mode()` / `mtu()` |
| `luna.ui` | Ayar DSL |
| `luna.PageBuilder` `luna.EmbedBuilder` `luna.RuleBuilder` `luna.HostsBuilder` | discord.js tarzı zincir builder’lar |
| `luna.REST` / `client.rest` | Yerel `get/put/post/patch/delete` (`vpn`, `rules`, `hosts`, `storage`, `app`) |
| `luna.Collection` | `Collection.new()` harita (`set/get/filter/map/ensure/reduce`) |
| `luna.string` `luna.table` `luna.json` `luna.time` `luna.color` `luna.domain` `luna.ipv4` `luna.hash` `luna.semver` `luna.path` `luna.util` `luna.fmt` | Saf yardımcılar |

```lua
local client = luna.Client

client:on(luna.Events.Ready, function()
  client.logInfo("ready " .. client.tag())
end)

client:on(luna.Events.VpnPhase, function(phase)
  if phase == "connected" then
    client.notifyInfo("Hosts", "Protection is on")
  end
end)

local col = luna.Collection.new()
col:set("growtopia1.com", "10.0.0.2")
```

`luna.clock.setTimeout(ms, fn)` / `setInterval` en fazla 4 zamanlayıcı, en az 2 sn. `client.setTimeout` aynı kapı.

### `luna.Client` (büyük sınıf)

Nokta veya iki nokta üst üste: `client.trim(" x ")` ve `client:trim(" x ")` çalışır.

Örnekler:

```lua
client.createRule({ id = "focus", name = "Focus", domains = { "example.com" } })
client.setHostsText("192.168.1.10 growtopia1.com")
client.storeSet("on", "1")
client.formatBytes(client.vpnBytesIn())
client.rest.get("vpn")
client.rest.put("hosts", raw_text)
```

Eksik bir uygulama özelliğini doldurmak için düşünülmüş kapılar: kural profilleri, hosts overlay, ayar dashboard’u, VPN sayaçları, bildirim, depo, i18n, doğrulama. Paket enjekte etmek veya başka uygulamayı okumak için kapı yoktur.

### `luna.ui` (`ui.settings`)

### `luna.ui` (`ui.settings`)

`settings_page()` bir sayfa tablosu döndürmelidir:

```lua
function settings_page()
  return luna.EmbedBuilder.new()
    :setTitle("Başlık")
    :setDescription("Sayfa açıklaması")
    :addAlert("Uyarı", "warning")
    :addCode("192.168.1.10 host")
    :addToggle("on", "Açık", true)
    :addTextarea("hosts", "Hosts", "")
    :addButton("save", "Kaydet")
    :build()
end
```

veya düşük seviye DSL:

```lua
function settings_page()
  return luna.ui.page({
    title = "Başlık",
    description = "Sayfa açıklaması",
    sections = {
      luna.ui.section("Genel", {
        luna.ui.note({ text = "Açıklama" }),
        luna.ui.heading({ text = "Başlık", level = 1 }),
        luna.ui.alert({ text = "Uyarı", tone = "warning" }),
        luna.ui.code({ text = "ornek.com" }),
        luna.ui.badge({ text = "hosts", tone = "accent" }),
        luna.ui.kv({ label = "Durum", value = "açık" }),
        luna.ui.progress({ title = "Doluluk", value = 0.4 }),
        luna.ui.switch({ id = "on", title = "Açık", body = "İpucu", value = true }),
        luna.ui.checkbox({ id = "extra", title = "Ek", value = false }),
        luna.ui.textarea({ id = "hosts", title = "Hosts", value = "" }),
        luna.ui.number({ id = "n", title = "Sayı", value = 2, min = 1, max = 10 }),
        luna.ui.select({ id = "mode", title = "Mod", options = { "a", "b" }, value = "a" }),
        luna.ui.slider({ id = "n", title = "Sayı", value = 2, min = 1, max = 10 }),
        luna.ui.link({ text = "Kaynak", url = "https://github.com/you/plugin" }),
        luna.ui.button({ id = "save", title = "Kaydet", destructive = false }),
      }),
    },
  })
end

function on_setting_changed(id, value)
  -- switch/checkbox: boolean, text/select: string, slider/number: number, button: true
end
```

Kontroller: `page`, `section`, `note`, `heading`, `divider`, `spacer`, `badge`, `code`, `alert`, `kv`, `progress`, `link`, `switch`, `checkbox`, `text`, `textarea`, `number`, `select`, `slider`, `button`. En fazla 10 bölüm, bölüm başına 32 kontrol. Linkler yalnızca `https://github.com/…`.

### `luna.rules` (`rules.read` / `rules.write`)

```lua
luna.rules.upsert({
  id = "focus",          -- otomatik p:sizin.id:focus olur
  name = "Focus list",   -- "Discord" rezerve
  enabled = true,
  strategy = "automatic", -- basic | balanced | aggressive | custom
  domains = { "example.com", "*.example.com" },
})
luna.rules.list()
luna.rules.delete(id)
luna.rules.clear()
```

Kural kapatılınca veya eklenti kaldırılınca bu kurallar silinir. Kurallar ekranında **Eklenti** rozeti görünür.

### `luna.hosts` (`hosts.write`)

Koruma açıkken (yerel VPN) DNS sorguları önce bu tabloya bakılır. Eşleşen A sorgusuna sentetik cevap dönülür; AAAA / HTTPS / SVCB boş NOERROR alır. Sistem hosts dosyasına yazılmaz. Koruma kapalıyken etkisi yoktur.

```lua
luna.hosts.set_text([[
192.168.1.10 growtopia1.com
192.168.1.10 growtopia2.com
]])
-- { applied = 2, skipped = 0, errors = {} }

luna.hosts.set({
  { host = "growtopia1.com", ip = "192.168.1.10" },
})
luna.hosts.list()   -- { { host = "...", ip = "..." }, ... }
luna.hosts.parse(raw)
luna.hosts.clear()
```

`#` yorum, IPv6 satırları yok sayılır. `*.ornek.com` jokerine izin vardır. Reddedilenler: `0.0.0.0/8`, bağ-yerel `169.254.0.0/16`, çoklu yayın, `10.7.0.0/24` (TUN). `127.0.0.1` ve özel ağlar serbesttir.

### `luna.vpn` / `luna.notify`

```lua
luna.vpn.state()          -- "disconnected" | "connected" | …
luna.vpn.is_active()
luna.vpn.snapshot()       -- phase, packets_processed, packets_modified, engine_alive, tun_active, uptime_seconds
luna.vpn.request_start()
luna.vpn.request_stop()
luna.notify.show("Başlık", "Metin")
```

## Yaşam döngüsü

| İşlev | Ne zaman |
| --- | --- |
| `on_enable()` | Eklenti açılınca, `main.lua` (ve varsa `settings.lua`) yüklendikten sonra |
| `on_disable()` | Kapatılınca veya kaldırılınca |
| `settings_page()` | Ayar ekranı açılınca / yenilenince |
| `on_setting_changed(id, value)` | Kullanıcı bir kontrolü değiştirince |
| `on_vpn_phase(phase)` | Yerel koruma durumu değişince (isteğe bağlı) |

## Yasak olanlar (bilinçli olarak yok)

- Ham paket / TUN / DPI stratejisi yazmak
- TLS, sertifika, MITM
- `Runtime.exec`, JNI, Java nesneleri
- Uygulama dosya sistemi, clipboard, erişilebilirlik
- GitHub’dan kod çekmek
- Başka eklentinin kuralları, hosts girdileri veya deposu

Zararlı bir eklenti yazmak bu yüzden pratikte imkânsızdır: host bu kapıları hiç açmaz.
