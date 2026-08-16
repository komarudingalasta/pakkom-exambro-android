package id.pakkom.exambro;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class PakKomDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "Administrator perangkat PakKom Exambro aktif.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "Administrator perangkat dinonaktifkan. PakKom Exambro akan meminta aktivasi kembali.", Toast.LENGTH_LONG).show();
    }
}
