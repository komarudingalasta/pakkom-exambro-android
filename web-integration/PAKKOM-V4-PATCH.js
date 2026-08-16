/*
PAKKOM EXAMBRO WEB V16.2 -> V4 NATIVE BRIDGE PATCH
Tempatkan potongan ini DI DALAM IIFE app.js agar dapat mengakses db, auth, state,
attemptId(), getAttempt(), getExamWarningCount(), setExamWarningCount(), beepWarning(),
forceCompleteForViolation(), pakkomAlert(), examGuardActive dan lastExamViolationAt.
*/

window.__pakkomV4Integrated=true;

function nativeExamStart(x){
  try{
    window.dispatchEvent(new CustomEvent('pakkom-exam-started',{detail:{
      studentId:state.student&&state.student.id||'',
      examId:x&&x.id||'',
      nis:state.student&&state.student.nis||'',
      classId:state.classId||''
    }}));
  }catch(e){console.warn('native start',e);}
}

function nativeExamFinish(){
  try{window.dispatchEvent(new CustomEvent('pakkom-exam-finished'));}catch(e){}
}

function nativeViolationLabel(reason){
  var map={
    APP_LEFT_OR_INTERRUPTED:'Keluar / berpindah aplikasi',
    SECURITY_PERMISSION_MISSING:'Izin keamanan dinonaktifkan',
    LOCK_TASK_START_FAILED:'Exam Lock gagal diaktifkan',
    BACK_BUTTON:'Menekan tombol kembali',
    SCREEN_OFF:'Layar/perangkat meninggalkan sesi ujian'
  };
  return map[String(reason||'')]||('Pelanggaran aplikasi: '+String(reason||'unknown'));
}

window.addEventListener('pakkom-native-violation',async function(ev){
  if(!examGuardActive||!state.currentExam||!state.student)return;
  if(!navigator.onLine)return;
  var d=ev&&ev.detail||{};
  if(d.examId&&String(d.examId)!==String(state.currentExam.id))return;
  if(d.studentId&&String(d.studentId)!==String(state.student.id))return;
  var now=nowMs();
  if(now-lastExamViolationAt<1400)return;
  lastExamViolationAt=now;
  var x=state.currentExam;
  var count=getExamWarningCount(x.id)+1;
  setExamWarningCount(x.id,count);
  beepWarning();
  try{
    var ref=db.collection('examAttempts').doc(attemptId(x.id));
    var at=await getAttempt(x.id);
    if(!at||at.status!=='in_progress')return;
    var item={
      type:'native_'+String(d.reason||'unknown').toLowerCase(),
      count:count,
      at:new Date(nowMs()).toISOString(),
      label:nativeViolationLabel(d.reason),
      source:'android-native'
    };
    await ref.set({
      violationCount:count,
      violationLog:firebase.firestore.FieldValue.arrayUnion(item),
      updatedAt:firebase.firestore.FieldValue.serverTimestamp()
    },{merge:true});
  }catch(e){console.warn('native violation firestore',e);}
  if(count>=2){
    disableExamGuard();
    await forceCompleteForViolation(x);
    nativeExamFinish();
    return;
  }
  await pakkomAlert('Peringatan 1 dari 2. PakKom Exambro mendeteksi Anda meninggalkan aplikasi atau melakukan tindakan yang tidak diizinkan. Pelanggaran kedua akan otomatis mengakhiri ujian.','Peringatan Ujian');
});

/* INTEGRASI WAJIB DI FUNGSI EXISTING:

1) Di launchExam(x), SETELAH examAttempts berhasil dibuat/tersedia dan SEBELUM resumeExam(x):
   nativeExamStart(x);

   Contoh akhir fungsi:
   saveActiveExam(x.id); nativeExamStart(x); resumeExam(x);

2) Di finishCurrentExam(), SETELAH Firestore berhasil status completed dan SEBELUM studentDashboard():
   nativeExamFinish();

3) Di forceCompleteForViolation(x), SETELAH status completed berhasil tersimpan:
   nativeExamFinish();

Dengan integrasi ini native lock dimulai setelah attempt valid, dan dilepas hanya setelah
penyelesaian berhasil tersimpan di Firestore.
*/
