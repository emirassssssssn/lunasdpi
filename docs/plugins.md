# Lunas DPI eklentileri

Lua API belgesi (discord.js tarzı, arama ve sınıf sayfaları):

**https://emirassssssssn.github.io/lunasdpi/**

Kaynak ağaç: [`docs/lua/`](lua/). GitHub’daki HTML dosyası tıklanınca kaynak kod görünür; asıl belge GitHub Pages’tedir.

Uygulama bir DPI koruma uygulamasıdır. Eklenti, **üründe hazır gelmeyen** bir sistemi Lua ile paketler: kendi zaman penceresi, kendi eşleştirme motoru, kendi pano, kendi sihirbazı. Host paket enjekte etmez, HTTPS çözmez, kabuk açmaz.

## Ne işe yarar?

- Kural paketi (`rules.write`)
- Yerel hosts ezmesi (`hosts.write`)
- Ayar sayfası (`ui.settings`) — host’un çizdiği düğümlerle
- Sayaç, depolama, bildirim
- ZIP içindeki listeler (`luna.fs`)

Ne işe **yaramaz:** TUN yazmak, rastgele HTTP, TLS/MITM, `io`/`os`/`java`, başka uygulamaların verisi, panoya yazmak.

## Paket

ZIP kökünde veya bir klasör altında:

```text
my-plugin/
  manifest.json          gerekli
  main.lua               gerekli giriş
  settings.lua           isteğe bağlı ayar sayfası
  modules/               require("ad") → modules/ad.lua
  locale/en.json
  locale/tr.json
  assets/icon.svg
  lists/block.txt        luna.fs.read / client.loadHostsFile
  README.md
```

`manifest.json` örneği:

```json
{
  "id": "community.focus.hours",
  "name": "Focus hours",
  "author": "you",
  "version": "1.0.0",
  "api_level": 2,
  "main": "main.lua",
  "permissions": ["ui.settings", "storage", "hosts.write"]
}
```

`api_level` **1** veya **2**. Uygulama 2 konuşur. 2: `luna.fs`, `luna.debug`, `luna.schema`, `luna.sdk`, `luna.kit`, `luna.forge`, daha yüksek limitler, CSV.

Kurulum: **Ayarlar → Eklentiler → ZIP içe aktar**, sonra eklentiyi açın. İzinler o anda verilir.

## Limitler

| Limit | Değer |
| --- | --- |
| ZIP / uncompressed | 2 MB |
| Dosya | 64 |
| Lua kaynak | 128 KB, metin — bytecode yok |
| Uzantılar | `lua`, `json`, `svg`, `png`, `md`, `txt`, `csv` |
| Kurulu eklenti | 24 |
| Aynı anda açık | 8 |
| Kurallar / eklenti | 32 |
| Zamanlayıcı | 8, 1s–120s |
| Depolama anahtarı | 96 |
| Ayar bölümü / öğe | 12 / 64 |
| Lua çağrı bütçesi | 1,5 sn |

`require("ad")` yalnızca `modules/ad.lua`. Nokta, eğik çizgi, `..` yok. `io` / `os` / Lua `debug` / `package` / `load` / `java` yok. `luna.debug` host modülüdür.

## İzinler

`storage`, `ui.settings`, `rules.read`, `rules.write`, `vpn.read`, `vpn.control`, `notify`, `hosts.write`, `app.read`

Eksik izin `Permission denied: …` fırlatır.

## Ayar UI’si

`settings_page()` bir `page` döner. Host bunları çizer:

**Etkileşimli:** `switch`, `checkbox`, `text`, `textarea`, `number`, `select`, `slider`, `button`  
**Görsel:** `note`, `heading`, `divider`, `spacer`, `badge`, `code`, `alert`, `kv`, `progress`, `link` (yalnızca `https://github.com/…`), `stat`, `list_item`, `empty`, `chips`, `quote`, `fold`, `steps`, `timeline`, `score`, `compare`, `faq`, `status`

`enabled = false` veya `disabled = true` etkileşimli kontrolü soldurur. `luna.ui.reload()` açık ayar sayfasını yeniden kurar.

Zincir: `client:form("Başlık")`, `client:dashboard("Durum")`, `client:wizard("Kurulum")`, `SchemaForm`.

## Uygulamada olmayan sistemi kurmak

Tipler `luna`, `luna.sdk`, `luna.kit`, `luna.forge` ve `luna.Client` üzerindedir. Client’ta `client:ruleset()` gibi colon-safe fabrikalar vardır.

| Aklınıza gelen | Başlangıç |
| --- | --- |
| Durum makinesi | `Machine` (`from = "*"` her durum) |
| Middleware zinciri | `Pipeline` (`tap` gözlemler) |
| Geri al / yinele | `History` |
| TTL bellek | `Cache` |
| `/hosts/:id` (HTTP yok) | `Router` |
| İsimli komutlar | `Actions` |
| Güvenli `n > 10 and on` | `Expr` (Lua eval yok) |
| Satır süz / sırala | `TableQuery`, `Paginator` |
| Mesai / cron-lite | `Schedule.window`, `Schedule.cron` |
| Kendi eşleştirme motoru | `Ruleset` (glob, domain, CIDR, prefix) |
| Form alanı hataları | `Validator` |
| “Bu host’u gördüm mü?” (büyük küme) | `Bloom` |
| N hatadan sonra dur | `Circuit` |
| Durum / SLO | `Health` + `Dashboard` |
| Kredi / A-B / sıralama | `Ledger`, `Weighted`, `Ranker`, `Scorecard` |
| Profiller | `Preset` |
| Sihirbaz adımları | `Workflow` + `ui.steps` |
| Depolama şema v1→v2 | `Migration` |
| INI / hosts metni | `Ini`, `Tokens.hosts`, `Csv` |
| Kanban / sparkline | `Kanban`, `Spark` |
| JSON `/a/b` | `JsonPtr` (noktalı yol: `JsonPath`) |
| Kümele | `UnionFind` |
| Kalp atışı / her N sn | `Watchdog`, `Recur` |

Kapalı kapılar (`luna.systems`): `network`, `tun`, `tls`, `shell`, `java` her zaman `false`.

Örnek (mesai + kendi kurallar + pano):

```lua
local client = luna.Client
local hours = luna.Schedule.window(9, 18, {1, 2, 3, 4, 5})
local set = luna.Ruleset.new()
set:glob("*.ads.example")

function settings_page()
  local on = hours:active()
  return client:dashboard("Focus hours")
    :status({
      text = "Window",
      tone = on and "success" or "warn",
      detail = on and "active" or "idle",
    })
    :steps({ labels = { "Hours", "Rules", "Live" }, current = on and 3 or 1 })
    :fold({
      title = "How it works",
      body = "This plugin invented the schedule. The app has no focus-hours feature.",
    })
    :build()
end
```

Haftanın günü ISO: 1 = Pazartesi … 7 = Pazar.

## Koleksiyonlar ve stdlib

`List` / `Set` / `Queue` / `Stack` / `Store` (en fazla 256 / 64 anahtar). `List:group_by`, `partition`, `shuffle`, `window`.  
`luna.string`, `luna.table`, `luna.json`, `luna.time`, `luna.color`, `luna.domain`, `luna.ipv4`, `luna.hash`, `luna.semver`, `luna.path`, `luna.fmt` — izin istemez, ağ yok.

## Geliştirme

- `luna.debug.inspect`, `snapshot`, `reload` (bu eklentinin VM’i)
- Eklenti ayrıntı ekranında yeniden yükle + günlük
- `luna.schema.check` yazmadan önce tablo doğrula
- `on_error(message)`, `Events.SettingChanged`, `Events.VpnConnected`

Tam sınıf listesi ve imzalar: **https://emirassssssssn.github.io/lunasdpi/** — özellikle [Invent a system](https://emirassssssssn.github.io/lunasdpi/#forge) ve [Build your own system](https://emirassssssssn.github.io/lunasdpi/#kit).
