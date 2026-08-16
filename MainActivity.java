package id.pakkom.exambro;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.DownloadManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://komarudingalasta.github.io/pakkom-exambro/";
    private static final String TRUSTED_HOST = "komarudingalasta.github.io";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int DEVICE_ADMIN_REQUEST = 1002;
    private static final String PREFS = "pakkom_exam_state";
    private static final String PREF_EXAM_ACTIVE = "exam_active";
    private static final String PREF_EXAM_STARTED_AT = "exam_started_at";
    private static final String PREF_VIOLATION_COUNT = "violation_count";
    private static final String PREF_LAST_VIOLATION = "last_violation";
    private static final String PREF_STUDENT_ID = "student_id";
    private static final String PREF_EXAM_ID = "exam_id";
    private static final String PREF_NIS = "nis";
    private static final String PREF_CLASS_ID = "class_id";

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private boolean webInitialized = false;
    private boolean consentDialogShown = false;
    private boolean internalSystemFlow = false;
    private boolean examActive = false;
    private boolean lockTaskStartedByUs = false;
    private long lastPauseElapsed = 0L;
    private Bundle pendingSavedState;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pendingSavedState = savedInstanceState;
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        examActive = prefs.getBoolean(PREF_EXAM_ACTIVE, false);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(247, 249, 252));
        setContentView(root);
        enterImmersiveMode();
        evaluatePermissionGate(true);
    }

    private void evaluatePermissionGate(boolean allowConsentDialog) {
        boolean usageGranted = hasUsageAccess();
        boolean adminGranted = isDeviceAdminActive();

        if (usageGranted && adminGranted) {
            showWebApp();
            if (examActive) {
                enterExamLock(false);
            }
            return;
        }

        if (examActive) {
            recordViolation("SECURITY_PERMISSION_MISSING");
        }
        showPermissionGate(usageGranted, adminGranted);
        if (allowConsentDialog && !consentDialogShown) {
            consentDialogShown = true;
            new AlertDialog.Builder(this)
                    .setTitle("Izin keamanan PakKom Exambro")
                    .setMessage("PakKom Exambro memerlukan Akses Penggunaan dan Administrator Perangkat. Jika izin wajib belum diberikan, aplikasi ujian tidak dapat dibuka. Aplikasi akan mengarahkan Anda ke pengaturan Android yang sesuai.")
                    .setCancelable(false)
                    .setPositiveButton("Setuju & aktifkan", (dialog, which) -> openNextMissingPermission())
                    .setNegativeButton("Tidak setuju", (dialog, which) -> finish())
                    .show();
        }
    }

    private void showPermissionGate(boolean usageGranted, boolean adminGranted) {
        root.removeAllViews();
        webInitialized = false;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText(examActive ? "Ujian Terkunci — Izin Harus Dipulihkan" : "Aktivasi Keamanan");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(24, 39, 75));
        title.setGravity(Gravity.CENTER);
        panel.addView(title, matchWrap(0, dp(12)));

        TextView description = new TextView(this);
        description.setText(examActive
                ? "Sesi ujian masih aktif. Izin keamanan yang hilang harus diaktifkan kembali sebelum ujian dapat dilanjutkan."
                : "Semua izin wajib harus aktif sebelum PakKom Exambro dapat digunakan. Setelah mengaktifkan izin di Pengaturan Android, kembali ke aplikasi ini.");
        description.setTextSize(16);
        description.setTextColor(Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        panel.addView(description, matchWrap(0, dp(24)));

        panel.addView(permissionRow("Akses Penggunaan", usageGranted,
                "Mendukung pendeteksian perpindahan dari PakKom Exambro selama sesi ujian."), matchWrap(0, dp(12)));
        panel.addView(permissionRow("Administrator Perangkat", adminGranted,
                "Mengaktifkan kontrol keamanan perangkat yang diperlukan oleh mode ujian."), matchWrap(0, dp(24)));

        Button activate = new Button(this);
        activate.setText("AKTIFKAN IZIN YANG BELUM AKTIF");
        activate.setAllCaps(false);
        activate.setTextSize(15);
        activate.setOnClickListener(v -> openNextMissingPermission());
        panel.addView(activate, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));

        TextView note = new TextView(this);
        note.setText("Jika izin dinonaktifkan, PakKom Exambro akan memblokir akses sampai izin dipulihkan.");
        note.setTextSize(13);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.CENTER);
        panel.addView(note, matchWrap(dp(12), 0));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        cardParams.leftMargin = dp(20);
        cardParams.rightMargin = dp(20);
        root.addView(panel, cardParams);
    }

    private View permissionRow(String name, boolean granted, String explanation) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView status = new TextView(this);
        status.setText((granted ? "✓  " : "!  ") + name + (granted ? " — Aktif" : " — Belum aktif"));
        status.setTextSize(16);
        status.setTextColor(granted ? Color.rgb(25, 110, 65) : Color.rgb(170, 60, 45));
        box.addView(status);

        TextView detail = new TextView(this);
        detail.setText(explanation);
        detail.setTextSize(13);
        detail.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(6);
        box.addView(detail, detailParams);
        return box;
    }

    private void openNextMissingPermission() {
        if (!hasUsageAccess()) {
            openUsageAccessSettings();
            return;
        }
        if (!isDeviceAdminActive()) {
            requestDeviceAdmin();
            return;
        }
        showWebApp();
        if (examActive) enterExamLock(false);
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void openUsageAccessSettings() {
        internalSystemFlow = true;
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception first) {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Exception second) {
                Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(details);
            }
        }
        Toast.makeText(this, "Aktifkan Akses Penggunaan untuk PakKom Exambro, lalu kembali.", Toast.LENGTH_LONG).show();
    }

    private boolean isDeviceAdminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(getAdminComponent());
    }

    private ComponentName getAdminComponent() {
        return new ComponentName(this, PakKomDeviceAdminReceiver.class);
    }

    private void requestDeviceAdmin() {
        internalSystemFlow = true;
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getAdminComponent());
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "PakKom Exambro memerlukan Administrator Perangkat untuk fitur keamanan selama ujian. Izin ini harus aktif agar aplikasi dapat digunakan.");
        startActivityForResult(intent, DEVICE_ADMIN_REQUEST);
    }

    private void showWebApp() {
        if (webInitialized) return;
        root.removeAllViews();

        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(4));
        progressBar.setLayoutParams(progressParams);
        root.addView(progressBar);

        configureWebView();
        webInitialized = true;
        enterImmersiveMode();

        if (pendingSavedState != null) {
            webView.restoreState(pendingSavedState);
            pendingSavedState = null;
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    private void configureWebView() {
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new ExamBridge(), "PakKomNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri.getScheme() != null && "https".equalsIgnoreCase(uri.getScheme())) {
                    view.loadUrl(uri.toString());
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                injectExamHooks();
                dispatchNativeStateToWeb();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                progressBar.setProgress(newProgress);
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallbackNew,
                                             FileChooserParams fileChooserParams) {
                if (examActive) {
                    Toast.makeText(MainActivity.this, "Pemilih file dinonaktifkan selama ujian.", Toast.LENGTH_SHORT).show();
                    filePathCallbackNew.onReceiveValue(null);
                    return true;
                }
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = filePathCallbackNew;
                internalSystemFlow = true;
                try {
                    startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    internalSystemFlow = false;
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Pemilih file tidak tersedia.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                if (examActive) {
                    Toast.makeText(MainActivity.this, "Unduhan dinonaktifkan selama ujian.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) request.addRequestHeader("Cookie", cookies);
                    if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
                    String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype);
                    if (extension == null || extension.isEmpty()) extension = "bin";
                    String fileName = "PakKomExambro_" + System.currentTimeMillis() + "." + extension;
                    request.setTitle(fileName);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    manager.enqueue(request);
                    Toast.makeText(MainActivity.this, "File sedang diunduh.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Unduhan gagal dimulai.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private class ExamBridge {
        @JavascriptInterface
        public void setExamContext(String studentId, String examId, String nis, String classId) {
            runOnUiThread(() -> {
                if (!isTrustedCurrentPage()) return;
                prefs.edit()
                        .putString(PREF_STUDENT_ID, safeValue(studentId))
                        .putString(PREF_EXAM_ID, safeValue(examId))
                        .putString(PREF_NIS, safeValue(nis))
                        .putString(PREF_CLASS_ID, safeValue(classId))
                        .apply();
            });
        }

        @JavascriptInterface
        public void startExam() {
            runOnUiThread(() -> {
                if (!isTrustedCurrentPage()) return;
                enterExamLock(true);
            });
        }

        @JavascriptInterface
        public void finishExam() {
            runOnUiThread(() -> {
                if (!isTrustedCurrentPage()) return;
                exitExamLock();
            });
        }

        @JavascriptInterface
        public boolean isExamActive() {
            return examActive;
        }

        @JavascriptInterface
        public int getViolationCount() {
            return prefs.getInt(PREF_VIOLATION_COUNT, 0);
        }
    }

    private String safeValue(String value) {
        if (value == null) return "";
        return value.replaceAll("[^A-Za-z0-9._@\\-]", "").substring(0, Math.min(value.replaceAll("[^A-Za-z0-9._@\\-]", "").length(), 120));
    }

    private boolean isTrustedCurrentPage() {
        if (webView == null) return false;
        String current = webView.getUrl();
        if (current == null) return false;
        try {
            Uri uri = Uri.parse(current);
            return "https".equalsIgnoreCase(uri.getScheme()) && TRUSTED_HOST.equalsIgnoreCase(uri.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void injectExamHooks() {
        if (webView == null || !isTrustedCurrentPage()) return;
        String js = "(function(){"
                + "if(window.__pakkomNativeHookInstalled)return;window.__pakkomNativeHookInstalled=true;"
                + "window.PakKomExam={"
                + "setContext:function(s,e,n,c){try{PakKomNative.setExamContext(String(s||''),String(e||''),String(n||''),String(c||''));}catch(x){}},"
                + "start:function(){try{PakKomNative.startExam();}catch(x){}},"
                + "finish:function(){try{PakKomNative.finishExam();}catch(x){}},"
                + "unlock:function(){try{PakKomNative.finishExam();}catch(x){}},"
                + "active:function(){try{return PakKomNative.isExamActive();}catch(x){return false;}},"
                + "violations:function(){try{return PakKomNative.getViolationCount();}catch(x){return 0;}}};"
                + "window.addEventListener('pakkom-exam-started',function(e){var d=(e&&e.detail)||{};"
                + "try{PakKomNative.setExamContext(String(d.studentId||''),String(d.examId||''),String(d.nis||''),String(d.classId||''));PakKomNative.startExam();}catch(x){}});"
                + "window.addEventListener('pakkom-exam-finished',function(){try{PakKomNative.finishExam();}catch(x){}});"
                + "function txt(el){return ((el&&el.innerText)||'').trim().toLowerCase().replace(/\\s+/g,' ');}"
                + "document.addEventListener('click',function(e){if(window.__pakkomV4Integrated)return;"
                + "var el=e.target&&e.target.closest?e.target.closest('button,a,[role=button],input[type=button],input[type=submit]'):null;"
                + "if(!el)return;var t=txt(el);if(!t&&el.value)t=String(el.value).trim().toLowerCase();"
                + "if(t.indexOf('mulai ujian')!==-1){setTimeout(function(){try{PakKomNative.startExam();}catch(x){}},350);}"
                + "if(t.indexOf('selesai ujian')!==-1||t.indexOf('akhiri ujian')!==-1){setTimeout(function(){try{PakKomNative.finishExam();}catch(x){}},350);}"
                + "},true);"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private void enterExamLock(boolean userInitiated) {
        if (!hasUsageAccess() || !isDeviceAdminActive()) {
            evaluatePermissionGate(false);
            return;
        }

        if (!examActive) {
            examActive = true;
            prefs.edit()
                    .putBoolean(PREF_EXAM_ACTIVE, true)
                    .putLong(PREF_EXAM_STARTED_AT, System.currentTimeMillis())
                    .putInt(PREF_VIOLATION_COUNT, 0)
                    .remove(PREF_LAST_VIOLATION)
                    .apply();
        }

        enterImmersiveMode();
        configureKioskIfDeviceOwner();
        startExamLockTask();
        dispatchNativeStateToWeb();

        if (userInitiated) {
            Toast.makeText(this, isFullKioskAvailable()
                    ? "Exam Lock aktif — Kiosk penuh."
                    : "Exam Lock aktif — perangkat dipertahankan di PakKom Exambro.", Toast.LENGTH_LONG).show();
        }
    }

    private void configureKioskIfDeviceOwner() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(getPackageName())) return;
        try {
            ComponentName admin = getAdminComponent();
            dpm.setLockTaskPackages(admin, new String[]{getPackageName()});
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }
        } catch (SecurityException ignored) {
        }
    }

    private boolean isFullKioskAvailable() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isLockTaskPermitted(getPackageName());
    }

    private void startExamLockTask() {
        if (isInLockTaskMode()) {
            lockTaskStartedByUs = true;
            return;
        }
        try {
            startLockTask();
            lockTaskStartedByUs = true;
        } catch (Exception e) {
            lockTaskStartedByUs = false;
            recordViolation("LOCK_TASK_START_FAILED");
        }
    }

    private void exitExamLock() {
        // V4.1: clear persisted state BEFORE Android unlock begins.
        // This prevents onResume/onWindowFocusChanged from re-locking after finish.
        examActive = false;
        prefs.edit()
                .putBoolean(PREF_EXAM_ACTIVE, false)
                .remove(PREF_STUDENT_ID).remove(PREF_EXAM_ID)
                .remove(PREF_NIS).remove(PREF_CLASS_ID).apply();

        forceStopExamLockTask();
        dispatchNativeStateToWeb();
        Toast.makeText(this, "Ujian selesai — perangkat sudah dibuka.", Toast.LENGTH_LONG).show();
    }

    private void forceStopExamLockTask() {
        try {
            if (isInLockTaskMode() || lockTaskStartedByUs) stopLockTask();
        } catch (Exception ignored) {}
        lockTaskStartedByUs = false;

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
            try {
                dpm.setLockTaskPackages(getAdminComponent(), new String[]{});
            } catch (Exception ignored) {}
        }

        // Some Android OEMs release Lock Task asynchronously.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!examActive && isInLockTaskMode()) {
                try { stopLockTask(); } catch (Exception ignored) {}
            }
        }, 300L);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!examActive && isInLockTaskMode()) {
                try { stopLockTask(); } catch (Exception ignored) {}
            }
        }, 1000L);
    }

    private boolean isInLockTaskMode() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
        }
        return am.isInLockTaskMode();
    }

    private void recordViolation(String reason) {
        if (!examActive) return;
        int count = prefs.getInt(PREF_VIOLATION_COUNT, 0) + 1;
        String event = reason + "|" + System.currentTimeMillis();
        prefs.edit()
                .putInt(PREF_VIOLATION_COUNT, count)
                .putString(PREF_LAST_VIOLATION, event)
                .apply();
        dispatchViolationToWeb(reason, count);
    }

    private void dispatchViolationToWeb(String reason, int count) {
        if (webView == null || !webInitialized) return;
        String safeReason = reason.replace("'", "");
        String studentId = prefs.getString(PREF_STUDENT_ID, "");
        String examId = prefs.getString(PREF_EXAM_ID, "");
        String nis = prefs.getString(PREF_NIS, "");
        String classId = prefs.getString(PREF_CLASS_ID, "");
        String js = "(function(){var d={reason:'" + safeReason + "',count:" + count
                + ",time:" + System.currentTimeMillis()
                + ",studentId:'" + studentId + "',examId:'" + examId
                + "',nis:'" + nis + "',classId:'" + classId + "',source:'android-native'};"
                + "try{localStorage.setItem('pakkomNativeViolation',JSON.stringify(d));}catch(e){}"
                + "window.dispatchEvent(new CustomEvent('pakkom-native-violation',{detail:d}));})();";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void dispatchNativeStateToWeb() {
        if (webView == null || !webInitialized) return;
        int violations = prefs.getInt(PREF_VIOLATION_COUNT, 0);
        String mode = isFullKioskAvailable() ? "full-kiosk" : (examActive ? "exam-lock" : "normal");
        String examId = prefs.getString(PREF_EXAM_ID, "");
        String js = "(function(){var d={active:" + examActive + ",violations:" + violations
                + ",mode:'" + mode + "',examId:'" + examId + "',version:'V4.2'};window.PAKKOM_NATIVE_STATE=d;"
                + "window.dispatchEvent(new CustomEvent('pakkom-native-state',{detail:d}));})();";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        internalSystemFlow = false;
        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
        } else if (requestCode == DEVICE_ADMIN_REQUEST) {
            evaluatePermissionGate(false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null && webInitialized) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();

        boolean wasInternal = internalSystemFlow;
        internalSystemFlow = false;

        if (!hasUsageAccess() || !isDeviceAdminActive()) {
            if (webInitialized && webView != null) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
            }
            evaluatePermissionGate(false);
            return;
        }

        if (!webInitialized) {
            showWebApp();
        } else if (webView != null) {
            webView.onResume();
        }

        if (examActive) {
            if (!wasInternal && lastPauseElapsed > 0L && SystemClock.elapsedRealtime() - lastPauseElapsed > 350L) {
                recordViolation("APP_LEFT_OR_INTERRUPTED");
            }
            enterExamLock(false);
        }

        if (webView != null) {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('pakkom-app-resumed'));", null);
        }
    }

    @Override
    protected void onPause() {
        lastPauseElapsed = SystemClock.elapsedRealtime();
        if (webView != null && webInitialized) {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('pakkom-app-backgrounded'));", null);
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("PakKomNative");
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (examActive) {
            recordViolation("BACK_BUTTON_BLOCKED");
            Toast.makeText(this, "Tombol Kembali dinonaktifkan selama ujian.", Toast.LENGTH_SHORT).show();
            enterImmersiveMode();
            return;
        }
        if (!webInitialized) {
            finish();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            Toast.makeText(this, "Keluar melalui menu PakKom Exambro.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
            if (examActive && !isInLockTaskMode()) startExamLockTask();
        }
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = top;
        p.bottomMargin = bottom;
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
