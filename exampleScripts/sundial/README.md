# Sundial

Uygulamada olmayan bir şey: **saat penceresi**. Gece korumayı duraklatır (`quiet`) veya yalnızca vardiya saatlerinde açık tutar (`guard`). Hosts, kural listesi veya Pulse masası değil.

Klasörü ZIP’leyin (`manifest.json` kökte veya tek bir üst klasörde) → **Ayarlar → Eklentiler**.

```
sundial/
  manifest.json
  main.lua
  settings.lua
  modules/sundial.lua
  modules/page.lua
  locale/en.json
  locale/tr.json
  assets/icon.svg
  README.md
```

## Deneme

1. Kurun, **açın** (izinleri verin), **Eklenti ayarlarını aç**.
2. Varsayılan pencere `23:00 → 07:00`, politika `quiet`, program **kapalı**. Durum kartı, rozetler, ilerleme ve günlük görünür.
3. Saat kaydırıcılarını değiştirin; önizleme güncellenir. Dakikalar sayı alanıdır.
4. Haftanın günlerini işaretleyin. Pencere adı ve not yazın.
5. **Programı kur**. Dakikada bir (en fazla 120 sn) aynı denetim çalışır. **Şimdi uygula** hemen bakar.
6. `quiet` iken penceredeyseniz durdurma ister; dışındaysa başlatma isteyebilir. Android VPN penceresi yine çıkabilir.
7. Uygulamada **kopunca yeniden bağlan** açıksa durum kartında uyarı görünür — o ayar Sundial’ın durdurmasını bozabilir.
8. **Sonraki otomatik işlemi atla** bir kez erteledikten sonra kalkar.
9. **Günlüğü temizle** kayıtları siler.

Korumayı uzak sunucuya çevirmez. Kurallara veya hosts dosyasına yazmaz. Başlat/durdur 15 sn, bildirim 30 sn hız sınırına uyar.
