# PakKom Exambro Android V4.2 — Exam Lock + Kiosk

Web target: https://komarudingalasta.github.io/pakkom-exambro/

## Yang baru di V4.2

- Mempertahankan Permission Gate V2: **Usage Access + Device Admin** harus aktif sebelum web dibuka.
- Tombol web yang mengandung teks **“Mulai Ujian”** otomatis memanggil Exam Lock native.
- Tombol **“Selesai Ujian” / “Akhiri Ujian”** melepas Exam Lock.
- Web juga dapat memanggil eksplisit `window.PakKomExam.start()` dan `window.PakKomExam.finish()` pada halaman PakKom.
- Status ujian disimpan secara native. Jika aplikasi crash/restart saat ujian, Exam Lock dipulihkan ketika aplikasi dibuka kembali.
- Tombol Back diblokir selama ujian dan dicatat sebagai pelanggaran.
- Ketika aplikasi ditinggalkan/interupsi selama ujian, kejadian dicatat sebagai pelanggaran lokal.
- Jumlah pelanggaran disimpan di Android dan diteruskan ke halaman web melalui event `pakkom-native-violation`.
- Upload file dan download diblokir selama sesi ujian; tetap tersedia di luar ujian untuk admin.
- `FLAG_SECURE`, immersive fullscreen, dan keep-screen-on tetap aktif.

## Mode penguncian

### 1. Full Kiosk — perangkat sekolah / Device Owner
Jika aplikasi telah diprovisikan sebagai **Device Owner**, V4.2 meng-allowlist paket sendiri menggunakan `DevicePolicyManager.setLockTaskPackages()`, menonaktifkan fitur Lock Task tambahan dengan `LOCK_TASK_FEATURE_NONE`, lalu menjalankan `startLockTask()`.

Dalam konfigurasi ini Android menjalankan **Lock Task Mode penuh**.

### 2. Exam Lock / Screen Pinning — HP pribadi
Jika aplikasi bukan Device Owner / tidak di-allowlist untuk Lock Task, Android dapat menjalankan mode pinning saat `startLockTask()` dipanggil. Ini lebih terbatas daripada Full Kiosk dan pengguna Android tetap memiliki mekanisme sistem untuk keluar dari pinning.

Karena itu V4.2 juga mencatat perpindahan/keluar aplikasi sebagai pelanggaran.

## Integrasi web yang disarankan

Agar deteksi tombol tidak hanya berdasarkan teks, halaman PakKom Exambro dapat memanggil:

```javascript
// tepat saat status ujian benar-benar berubah menjadi dimulai
window.PakKomExam?.start();

// hanya setelah proses submit/selesai ujian berhasil
window.PakKomExam?.finish();
```

Menerima status native:

```javascript
window.addEventListener('pakkom-native-state', (event) => {
  console.log(event.detail);
  // { active, violations, mode }
});
```

Menerima pelanggaran:

```javascript
window.addEventListener('pakkom-native-violation', (event) => {
  console.log(event.detail);
  // { reason, count, time }
  // Dapat dikirim ke Firestore oleh web agar terlihat di panel admin.
});
```

V4.2 belum mengirim langsung ke Firestore dari kode Android agar konfigurasi Firebase/API key tidak digandakan di APK. Event sudah disediakan agar web PakKom yang sudah memakai Firebase dapat menyimpannya dengan identitas siswa yang sedang login.

## Provisioning Full Kiosk untuk perangkat sekolah

Full Kiosk memerlukan aplikasi menjadi Device Owner atau di-allowlist oleh DPC/EMM. Device Admin biasa **tidak sama** dengan Device Owner. Untuk perangkat sekolah, provisioning sebaiknya dilakukan pada perangkat yang disiapkan khusus/managed sebelum digunakan siswa.

## Build APK melalui GitHub Actions

1. Upload isi folder project ini ke repository GitHub.
2. Buka tab **Actions**.
3. Jalankan workflow **Build PakKom Exambro APK**.
4. Unduh artifact **PakKom-Exambro-APK** setelah build selesai.

Atau buka project dengan Android Studio dan jalankan `assembleDebug` / Build APK.


## V4.2 — Native Violation Sync

V4.2 menambahkan konteks siswa/ujian pada bridge Android dan event `pakkom-native-violation` yang siap disimpan oleh web ke `examAttempts.violationCount` + `violationLog`. Gunakan patch pada folder `web-integration/`.


## V4.2 Stable Build
Build workflow diperbarui dan compile hotfix V4.1 dilengkapi dengan import Android yang diperlukan.
