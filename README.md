# SIPEDAS (Sistem Informasi Pedestrian dan Aksi Satgas Linmas)

Aplikasi Android Native untuk pelaporan patroli pedestrian Satuan Linmas.

## Fitur Utama
- **Pelaporan Cerdas**: Pengolahan gambar, kompresi, dan watermark koordinat GPS secara offline.
- **Draf Lokal & Cloud**: Penyimpanan draf laporan secara offline dengan SQLite (Room) serta sinkronisasi Cloud (Firebase RTDB & Cloudinary).
- **Monitoring CCTV**: Pemantauan langsung area pedestrian.

## Cara Menjalankan
1. Buka proyek menggunakan **Android Studio** (Koala / Ladybug atau yang lebih baru).
2. Biarkan Gradle melakukan sinkronisasi otomatis.
3. Buat file `.env` di root direktori proyek berdasarkan format pada `.env.example`.
4. Hubungkan perangkat fisik Android atau jalankan Emulator.
5. Tekan tombol **Run** di Android Studio untuk memasang aplikasi.
