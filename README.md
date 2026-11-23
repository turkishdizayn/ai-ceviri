# AI Çeviri — Markadan bağımsız, yapay zekâ destekli canlı çeviri

Herhangi bir Bluetooth veya kablolu kulaklıkla (AirPods, Galaxy Buds, JBL, Xiaomi, Sony, Monoise P-Q16, isimsiz TWS modeller…) ya da kulaklık olmadan yalnızca telefonla çalışan Android çeviri uygulaması.

Kelime kelime değil, **anlamı, niyeti ve üslubu** koruyarak çevirir: konuşma geçmişini bağlam olarak kullanır, deyimleri karşılığıyla değiştirir, konuşma tanıma hatalarını düzeltir ve çeviriyi doğal bir yapay zekâ sesiyle okur.

## Modlar

| Mod | Nasıl çalışır |
|---|---|
| **Yüz Yüze** | Telefon ortada, ekran ikiye bölünür (üst yarı karşıdaki kişi için ters döner). Her kişi kendi butonuna basıp konuşur; cümle bitince otomatik çevrilir ve hoparlörden okunur. |
| **Kulaklıkla Çeviri** | Siz kulaklık takarsınız. Dil otomatik algılanır: karşınızdakinin sözleri **kulaklığınıza**, sizin sözleriniz **telefon hoparlöründen** ona çevrilir. |
| **Dinle ve Çevir** | Rehber, toplantı, ders, anons… Ortamı sürekli dinler, altyazı gibi gösterir, isterseniz kulaklığınıza okur. |

| **Foto Çeviri** | Tabela, menü, etiket veya belge fotoğrafı: yazı telefonda okunur (ML Kit), bağlama uygun çevrilir. |
| **Kayıtlar** | Her konuşma ve foto çevirisi otomatik saklanır; açılabilir, tekrar okunabilir, paylaşılabilir, silinebilir. |

Her çeviride 🔊 tekrar oku ve kopyala; yazarak çeviri; kulaklığın oynat/duraklat tuşuyla başlat/durdur.

### Ücretsiz kullanım (Groq)
Ayarlar → **Groq (ücretsiz) + telefon sesi** hazır ayarı → console.groq.com'dan alınan anahtar. Seslendirme telefonun kendi (ücretsiz, internetsiz) sesiyle yapılır.

## Mimari (özet)

```
Mikrofon (telefon / kulaklık SCO)
   │  16 kHz PCM, 20 ms kareler
   ▼
UtteranceSegmenter (VAD, adaptif gürültü eşiği)  ──►  cümle (WAV)
   ▼
SpeechToText  (gpt-4o-transcribe)         → metin
   ▼
Translator    (LLM + bağlam + üslup)      → {"src","text"}  ← dil algılama da burada
   ▼
TextToSpeech  (gpt-4o-mini-tts)           → mp3
   ▼
SpeechPlayer  → dinleyiciye göre kulaklık veya hoparlör
```

- `core/` — saf Kotlin, Android'e bağımlı değil: dil listesi, VAD, WAV, çeviri talimatları, hat (pipeline), OpenAI uyumlu servis istemcileri. **Birim testleri burada.**
- `app/` — Android (Jetpack Compose): kulaklık algılama ve ses yönlendirme, mikrofon, oynatıcı, kulaklık tuşu, ekranlar.

Ayrıntılar ve yol haritası: [docs/MIMARI.md](docs/MIMARI.md)

## Kurulum

1. **APK indirme:** [ai-ceviri.apk (son sürüm)](https://github.com/turkishdizayn/ai-ceviri/releases/latest/download/ai-ceviri.apk) — `main`'e her push'ta otomatik güncellenir. Tüm sürümler: *Releases* sekmesi.
2. Telefona kurun, mikrofon ve “Yakındaki cihazlar” izinlerini verin.
3. **Ayarlar** → API anahtarını girin (OpenAI veya OpenAI uyumlu bir servis).
4. Dilleri seçin, bir mod açın.

### Kaynaktan derleme

```bash
./gradlew :core:test          # birim testleri
./gradlew :app:assembleDebug  # APK → app/build/outputs/apk/debug/
```
JDK 17 ve Android SDK (compileSdk 35) gerekir. Android Studio ile doğrudan açılabilir.

## Güvenlik notu

Prototipte API anahtarı uygulamada saklanır. **Mağazaya çıkmadan önce** anahtar kendi sunucunuza taşınmalı (Ayarlar'daki “Servis adresi” buna göre tasarlandı). Bkz. `docs/MIMARI.md` → “Üretim”.
