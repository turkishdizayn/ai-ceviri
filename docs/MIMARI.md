# Teknik Plan ve Mimari

## 1. Hedef

Belirli bir kulaklığa bağlı olmayan, Türkiye'de ve dünyada satılan tüm kulaklıklarla (ve kulaklıksız) çalışan, mevcut “Dana AI / P-Q16” uygulamasından daha doğal ve bağlama duyarlı çeviri yapan bir Android uygulaması.

## 2. Neden her kulaklıkla çalışır?

Uygulama hiçbir üreticiye özel protokol (özel BLE servisi, lisans/eşleme kodu) kullanmaz. Yalnızca tüm kulaklıkların desteklediği standartları kullanır:

| İhtiyaç | Standart | Android API |
|---|---|---|
| Kulaklık mikrofonu | Bluetooth HFP (SCO), LE Audio, kablolu/USB | `AudioManager.setCommunicationDevice` (API 31+), `startBluetoothSco` (eski) |
| Kulağa ses | A2DP / LE Audio / SCO | `MediaPlayer.setPreferredDevice` |
| Hoparlöre ses (karşı tarafa) | Telefon hoparlörü | `TYPE_BUILTIN_SPEAKER` |
| Tuşla kontrol | AVRCP oynat/duraklat | `MediaSession` |
| Kulaklık algılama | — | `AudioDeviceCallback` |

### Bilinen kısıtlar
- **Bluetooth mikrofonu dar bantlıdır** (8–16 kHz). Bu yüzden varsayılan giriş telefon mikrofonudur; Ayarlar'dan kulaklık mikrofonu seçilebilir.
- SCO açıkken çoğu telefon tüm sesi SCO'ya verir (mono, telefon kalitesi). “Kulaklıkla Çeviri” modunda en iyi sonuç için telefon mikrofonu + kulaklık çıkışı kullanılır.
- Aynı anda iki ayrı Bluetooth kulaklığa farklı ses göndermek çoğu telefonda mümkün değildir (LE Audio/Auracast ile gelecek).
- Markaya özel dokunma hareketleri (üçlü dokunma vb.) genel uygulamada okunamaz; standart oynat/duraklat kullanılır.

## 3. Çeviri hattı

1. **VAD / cümle bölme** (`core/audio/UtteranceSegmenter`): enerji tabanlı, adaptif gürültü tabanı; 700 ms sessizlikte cümleyi kapatır, en fazla 15 sn. Sabit uğultu birkaç saniyede bastırılır.
2. **Konuşma → metin**: `gpt-4o-transcribe` (dil biliniyorsa `language` ipucu gönderilir).
3. **Çeviri**: LLM; sistem talimatı `core/pipeline/TranslationPrompt`:
   - anlam/niyet aktarımı, deyim karşılıkları, üslup (doğal/resmi/samimi), alan terminolojisi,
   - STT hatalarını ve dolgu kelimeleri (ııı, şey) temizleme,
   - son 6 tur bağlam olarak verilir (zamir, cinsiyet, konu tutarlılığı),
   - konuşan bilinmiyorsa iki dilden hangisi olduğunu **model tespit eder** ve JSON döner.
4. **Metin → ses**: `gpt-4o-mini-tts`, dil ve üslup talimatıyla doğal tonlama.
5. **Yönlendirme**: çeviri konuşanın *karşısındaki* kişiye çalınır (kulaklık veya hoparlör). Hoparlörden çalarken mikrofon susturulur (yankı döngüsü olmaz).

Tüm servisler arayüz arkasındadır (`SpeechToText`, `Translator`, `TextToSpeech`); Google, Azure, DeepL, yerel model vb. eklemek uygulamanın geri kalanını değiştirmez.

### Gecikme hedefi
Cümle sonu → çeviri sesi başlangıcı: ~1,5–3 sn (STT ~0,5–1 sn, çeviri ~0,5–1 sn, TTS ~0,5 sn).

## 4. Yol haritası

| Aşama | İçerik |
|---|---|
| v0.1 | 3 mod, 32 dil, her kulaklık, yazarak çeviri, paylaşma, CI + APK |
| **v0.2 (bu sürüm)** | Okuma sırasında sonraki cümlenin işlenmesi, seslendirmenin önceden hazırlanması, reasoning modellerde düşük düşünme süresi; STT bağlam ipucu ve temperature 0; Foto Çeviri; Kayıtlar; tekrar oku/kopyala; Groq hazır ayarı + telefon TTS; yeni tasarım |
| v0.3 | Akış (streaming) STT/TTS; ön plan servisi (ekran kapalıyken dinleme); ses dosyası transkripsiyonu; Latin dışı alfabeler için foto çeviri |
| v0.4 | Gerçek zamanlı sesten-sese mod (Realtime API), konuşanın sesine benzer seslendirme (açık rıza ile) |
| v1.0 | Kendi sunucu (proxy) + kullanıcı hesabı + abonelik/kredi, iOS sürümü, Play Store |

## 5. Üretim (mağaza) öncesi zorunlular

- **API anahtarı sunucuda olmalı**: Uygulama → sizin sunucunuz (kullanıcı oturumu, kota, ücretlendirme) → yapay zekâ servisi. Uygulamadaki “Servis adresi” alanı bu sunucuya yönlendirilir.
- Release imzalama anahtarı (şu an debug anahtarıyla imzalanıyor).
- KVKK / gizlilik politikası: ses kayıtları buluta gönderiliyor; açık rıza metni.
- Popüler 15–20 kulaklıkla saha testi ve uyumluluk listesi.

## 6. Maliyet tahmini (yaklaşık)

Dakika başına konuşma: STT + kısa LLM çağrıları + TTS ≈ birkaç Türk lirası seviyesinde (servis fiyatlarına göre değişir). İş modelinde dakika paketi veya aylık abonelik önerilir.
