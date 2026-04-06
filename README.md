# ☁️ Hava Durumu

Turkiye'nin 81 ili ve dunyanin buyuk sehirleri icin anlik hava durumu bilgisi sunan, **Java Swing** ile gelistirilmis masaustu uygulamasi.

![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Swing](https://img.shields.io/badge/GUI-Swing-007396?style=flat)
![API](https://img.shields.io/badge/API-OpenWeatherMap-orange?style=flat)
![License](https://img.shields.io/badge/License-MIT-green?style=flat)

---

## ✨ Ozellikler

- **Anlik Hava Durumu** — Sicaklik, nem, ruzgar, basinc, gorunurluk, hissedilen sicaklik, min/max degerler
- **Dinamik Tema Sistemi** — Hava durumuna ve gunduz/gece durumuna gore otomatik degisen arka plan renkleri
- **Hava Animasyonlari** — Particle sistemi ile yagmur, kar, sis, gunes isinlari ve yildirim efektleri
- **Cam Efekti (Glassmorphism)** — Modern, yari-saydam arayuz panelleri
- **Ozel Emoji Rendereri** — Gunes, ay, bulut, yagmur, kar gibi ikonlarin Graphics2D ile vektorel cizimi
- **Ay Evresi Hesaplama** — Julian takvim algoritmasi ile gercek zamanli ay evresi gosterimi
- **Sehir Otomatik Tamamlama** — Yazdikca anlik oneriler (yerel liste + OpenWeatherMap Geocoding API)
- **Hava Kalitesi (AQI) ve UV Endeksi** — Şehre özel hava kalitesi ve ultraviyole endeksi
- **Yarin Tahmini** — Kısa açıklama ve sıcaklık (OpenWeatherMap forecast API)
- **Genis Sehir Destegi** — Turkiye'nin 81 ili + 28 dunya metropolu hazir listede
- **Gun Dogumu / Batimi** — Sehre ozel gunes saatleri ve gun suresi hesabi
- **Kart Animasyonlari** — Arama sonrasi yumusak fade-in gecisleri

---

## 🛠️ Kullanilan Teknolojiler

| Teknoloji | Aciklama | Kullanim Alani |
|-----------|----------|----------------|
| **Java 21+** | Ana programlama dili | Tum uygulama mantigi |
| **Swing (javax.swing)** | Java GUI kutuphanesi | Pencere, panel, buton, metin alani, popup menu |
| **AWT Graphics2D (java.awt)** | 2D grafik motoru | Arka plan cizimi, gradient, anti-aliasing, animasyonlar |
| **java.awt.geom** | Geometrik sekiller | Yildirim cizimi (GeneralPath), daire, cizgi, egri |
| **java.awt.Area** | Sekil operasyonlari | Ay evresi hilal efekti (subtract/intersect) |
| **HttpURLConnection** | HTTP istemcisi | OpenWeatherMap API istekleri |
| **java.util.Properties** | Konfigurasyon okuyucu | API anahtarini config dosyasindan yukleme |
| **OpenWeatherMap API** | Hava durumu servisi | Anlik hava verisi + Geocoding (sehir arama) |

### Harici Bagimlilik: **Yok**

Proje tamamen Java standart kutuphanesi ile calisir. Maven, Gradle veya herhangi bir harici kutuphane gerektirmez. JSON ayristirma bile elle (manuel parser) yapilmistir.

---

## 🎨 Mimari

```
hava-durumu/
├── src/
│   └── HavaDurumu.java      # Tek dosyali mimari (~1500 satir)
├── config.properties         # API anahtari (git'e dahil degil)
├── config.properties.example # API anahtari sablonu
├── calistir.bat              # Windows baslatma scripti
├── .gitignore
└── README.md
```

### Kod Yapisi (HavaDurumu.java)

| Bolum | Satir Araligi | Aciklama |
|-------|--------------|----------|
| **loadApiKey()** | Ust kisim | Ortam degiskeni veya config dosyasindan guvenli API key yukleme |
| **Particle System** | ~135-260 | Yagmur, kar, sis, gunes isini ve ortam parcaciklari |
| **Background Painting** | ~260-380 | Dinamik gradient, gunes, ay, yildiz ve yildirim cizimi |
| **Theme Engine** | ~380-420 | 12 farkli hava/gece kombinasyonu icin renk paleti |
| **Moon Phase** | ~430-480 | Julian Day hesabi ile gercek ay evresi |
| **UI Components** | ~490-860 | Cam efektli paneller, arama cubugu, otomatik tamamlama |
| **Custom Emoji Renderer** | ~920-1150 | 16+ hava ikonu icin vektorel cizim |
| **API & JSON Parser** | ~1270-1430 | HTTP istek, manuel JSON ayristirma, veri gosterimi |

---

## 🚀 Kurulum ve Calistirma

### Gereksinimler

- **Java JDK 21** veya ustu ([indirin](https://adoptium.net/))
- **OpenWeatherMap API Key** (ucretsiz: [kayit ol](https://openweathermap.org/api))

### 1. Projeyi Klonlayin

```bash
git clone https://github.com/Semai-Mirac/hava-durumu.git
cd hava-durumu
```

### 2. API Anahtarini Ayarlayin

config.properties.example dosyasini kopyalayip anahtarinizi yazin:

```bash
cp config.properties.example config.properties
```

config.properties dosyasini acip kendi anahtarinizi girin:

```properties
api.key=BURAYA_KENDI_API_ANAHTARINIZI_YAZIN
```

**Alternatif:** Ortam degiskeni de kullanabilirsiniz:

```bash
# Windows PowerShell
$env:OPENWEATHER_API_KEY = "anahtariniz"

# Linux / macOS
export OPENWEATHER_API_KEY="anahtariniz"
```
### 3. Derleyin ve Calistirin

**Windows (kolay yol):**

```
calistir.bat
```

**Manuel:**

```bash
javac -encoding UTF-8 src/HavaDurumu.java -d out
java -cp out HavaDurumu
```

---

## 🎬 Nasil Calisir?

1. **Arama cubuguna** bir sehir adi yazin (ornegin Ankara, Tokyo, Paris)
2. Otomatik tamamlama onerilerinden birini secin veya **Ara** butonuna tiklayin
3. Uygulama OpenWeatherMap API'ye HTTP istegi gonderir
4. Donen JSON verisi elle ayristirilir (pd(), pi(), ps() yardimci metodlari)
5. Hava durumuna gore **tema degisir**, **parcacik animasyonlari baslar**
6. Sonuclar cam efektli kartlarda gosterilir

### API Kullanimi

| Endpoint | Amac |
|----------|------|
| /data/2.5/weather | Anlik hava durumu verisi (sicaklik, nem, ruzgar vb.) |
| /geo/1.0/direct | Sehir arama / otomatik tamamlama |

---

## 🔒 Guvenlik

- API anahtari **kodda yer almaz**, config.properties veya ortam degiskeninden okunur
- config.properties dosyasi .gitignore ile repo disinda tutulur
- Uygulama baslatildiginda anahtar bulunamazsa kullaniciya hata mesaji gosterilir

---

## 💡 Teknik Detaylar

- **Rendering:** Tum cizimler Graphics2D ile RenderingHints.VALUE_ANTIALIAS_ON kullanilarak yapilir
- **Animasyon Dongusu:** 30 FPS javax.swing.Timer ile (~33ms aralik)
- **Parcacik Sistemi:** 5 farkli tip (ortam, yagmur, kar, sis, gunes isini), her biri fizik tabanli hareket
- **Glassmorphism:** AlphaComposite.SRC_OVER + RadialGradientPaint + yari-saydam renkler
- **Vignette Efekti:** Ekran kenarlarinda radyal gradyanla karartma
- **JSON Parser:** Harici kutuphane kullanmadan String.indexOf() tabanli hafif parser
- **Encoding:** UTF-8 destegi ile Turkce karakterler sorunsuz calisir
