package com.example.gravadordeacoes.view;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.gravadordeacoes.R;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1234;
    private static final String TAG = "AnalisadorDeTela";

    private CheckBox checkboxOverlay;
    private CheckBox checkboxAccessibility;
    private CheckBox checkboxNotification;

    private static final int REQUEST_BLUETOOTH_CONNECT_PERMISSION = 101;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkboxOverlay = findViewById(R.id.id_overlay);
        checkboxAccessibility = findViewById(R.id.id_Acessibility2);
        checkboxNotification = findViewById(R.id.id_notification);

        Button buttonSetup = findViewById(R.id.buttonSetupService);
        buttonSetup.setOnClickListener(v -> {
            checkOverlayPermission();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void updatePermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkboxOverlay.setChecked(Settings.canDrawOverlays(this));
        } else {
            checkboxOverlay.setChecked(true);
        }
        checkboxNotification.setChecked(NotificationManagerCompat.from(this).areNotificationsEnabled());
        checkboxAccessibility.setChecked(isAccessibilityServiceEnabled(this, MyAccessibilityService.class));
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Por favor, conceda a permissão para sobrepor outros apps.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
        } else {
            goToAccessibilitySettings();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                goToAccessibilitySettings();
            } else {
                Toast.makeText(this, "Permissão de sobreposição negada.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void goToAccessibilitySettings() {
        if (!isAccessibilityServiceEnabled(this, MyAccessibilityService.class)) {
            Toast.makeText(this, "Agora, ative o serviço 'GravadorDeAcoes'", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        } else {
            checkAndRequestNotificationPermission();
        }
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            } else {
                checkAndRequestBluetoothPermission();
            }
        } else {
            checkAndRequestBluetoothPermission();
        }
    }

    private void checkAndRequestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT_PERMISSION);
            } else {
                Toast.makeText(this, "Todas as permissões necessárias já foram concedidas!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Configuração finalizada!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão de notificação concedida!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Sem notificações, você não verá o serviço em primeiro plano.", Toast.LENGTH_LONG).show();
            }
            checkAndRequestBluetoothPermission();
        } else if (requestCode == REQUEST_BLUETOOTH_CONNECT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão de Bluetooth concedida!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "A detecção de conexão Bluetooth pode não funcionar.", Toast.LENGTH_LONG).show();
            }
            Toast.makeText(this, "Configuração finalizada!", Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        String serviceId = context.getPackageName() + "/" + serviceClass.getName();
        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);

            if (accessibilityEnabled == 1) {
                String settingValue = Settings.Secure.getString(
                        context.getApplicationContext().getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

                if (settingValue != null) {
                    TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                    splitter.setString(settingValue);
                    while (splitter.hasNext()) {
                        String enabledService = splitter.next();
                        if (enabledService.equalsIgnoreCase(serviceId)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.w(TAG, "isAccessibilityServiceEnabled: ", e);
        }
        return false;
    }
}