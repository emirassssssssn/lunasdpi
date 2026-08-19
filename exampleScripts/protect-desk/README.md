# Pulse

Lunas DPI için örnek koruma masası. Canlı durum, başlat/durdur, kopma bildirimi ve eklentiye ait bir izleme listesi.

Bu klasörü ZIP’leyin (`manifest.json` arşiv kökünde veya tek bir üst klasörde olsun) ve **Ayarlar → Eklentiler** içinden içe aktarın.

```
protect-desk/
  manifest.json
  main.lua
  settings.lua
  modules/desk.lua
  locale/en.json
  locale/tr.json
  assets/icon.svg
  README.md
```

## Deneme

1. Paketi kurun, açın (izinleri verin), **Eklenti ayarlarını aç**.
2. Durum kartında aşama, mod, MTU ve sayaçları görün. **Yenile** sayfayı tekrar kurar.
3. **Başlatmayı iste** — Android VPN penceresi gerekebilir. 15 sn’de bir.
4. **Koruma durunca bildir** açıkken korumayı durdurun; bildirim gelmeli (30 sn hız sınırı).
5. İzleme listesini açın, `example.com` bırakın, **Listeyi uygula**. **Kurallar** ekranında **Eklenti** rozetli “Watch list” görünür.
6. Listeyi kapatınca kural silinir.

Korumayı bu eklenti tek başına “uzak VPN” yapmaz. Trafik cihazda kalır. Discord kural adı rezervedir; bu paket `Watch list` kullanır.
