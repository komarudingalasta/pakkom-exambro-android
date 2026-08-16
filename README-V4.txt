PAKKOM EXAMBRO V4 - WEB INTEGRATION

Repo web saat dicek: V16.2.
Firestore rules V16.2 SUDAH mengizinkan siswa pemilik attempt menambahkan hanya:
- violationCount
- violationLog
- updatedAt
selama attempt masih status in_progress.

Control Center V16.2 juga SUDAH membaca violationCount dan violationLog, sehingga tidak perlu koleksi baru.

Yang perlu dilakukan pada app.js:
1. Masukkan isi PAKKOM-V4-PATCH.js ke dalam IIFE app.js.
2. Panggil nativeExamStart(x) saat launchExam berhasil.
3. Panggil nativeExamFinish() setelah selesai normal berhasil disimpan.
4. Panggil nativeExamFinish() setelah auto-complete pelanggaran berhasil disimpan.
5. Deploy ulang GitHub Pages.

Batas pelanggaran dipertahankan sesuai web V16.2: 2 kali.
Pelanggaran native dan pelanggaran visibility web masuk ke attempt Firestore yang sama.
