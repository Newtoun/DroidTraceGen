package com.example.gravadordeacoes.recorder;

import static android.telephony.TelephonyManager.*;

import android.app.AlarmManager;
import android.app.DownloadManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.BatteryManager;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.function.Consumer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


/**
 *
 * Esta classe traduz eventos do sistema em strings legíveis e as envia através de um callback.
 *
 */
public class DeviceStateMonitor {

    private final Consumer<String> onStateChanged;
    private final DeviceStateReceiver receiver;
    private boolean isRegistered = false;


    private MediaSessionManager mediaSessionManager;
    private ComponentName componentName;
    private MediaSessionManager.OnActiveSessionsChangedListener mediaSessionListener;
    private final Map<MediaController, MediaController.Callback> activeControllerCallbacks = new HashMap<>();

    /**
     * Constrói um novo monitor de estado do dispositivo.
     *
     * @param onStateChanged O callback a ser invocado com uma descrição da mudança de estado.
     */
    public DeviceStateMonitor(@NonNull Consumer<String> onStateChanged) {
        this.onStateChanged = onStateChanged;
        this.receiver = new DeviceStateReceiver();
    }

    /**
     * Registra o BroadcastReceiver para começar a ouvir os eventos do sistema.
     *
     * @param context O Context para registrar o receiver.
     */
    public void register(@NonNull Context context) {
        if (isRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter();

        // System Time, Locale, and Configuration
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);

        // System UI and User State
        filter.addAction(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_USER_FOREGROUND);
        filter.addAction(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED);
        filter.addAction(Intent.ACTION_UID_REMOVED);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);

        // Categoria: Agendamentos, Intenções e Foco do Usuário
        filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        filter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        filter.addAction("android.intent.action.CHOOSER_TARGETS_PROMISED"); // CORRIGIDO

        // Categoria: Contexto de Trabalho vs. Pessoal (Perfis)
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);

        // Categoria: Sincronização de Dados e Conteúdo
        filter.addAction(Intent.ACTION_PROVIDER_CHANGED);

        // Categoria: Ecossistema de Aplicativos (Nível Meta)
        filter.addAction("android.intent.action.DEFAULT_APP_CHANGED");
        filter.addAction(Intent.ACTION_MY_PACKAGE_SUSPENDED);
        filter.addAction(Intent.ACTION_APPLICATION_LOCALE_CHANGED);


        // Bateria e Energia
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);

        // Conectividade (Geral)
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);


        // Conectividade (Wi-Fi)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);

        // Conectividade (Bluetooth)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);

        // Conectividade (NFC)
        filter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);

        // Hardware e Áudio
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);

        // Telefonia e SMS
        filter.addAction(ACTION_PHONE_STATE_CHANGED);
        filter.addAction("android.intent.action.SERVICE_STATE");
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        filter.addAction("android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION");
        filter.addAction("android.provider.Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION");
        filter.addAction("android.provider.Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION");
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);

        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        registerMediaSessionListener(context);

        isRegistered = true;
    }


    public void unregister(@NonNull Context context) {
        if (isRegistered) {
            context.unregisterReceiver(receiver);
            unregisterMediaSessionListener();

            isRegistered = false;
        }
    }

    /**
     *
     * @param newConfig A nova configuração do dispositivo.
     */
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            onStateChanged.accept("Orientation set to Landscape");
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            onStateChanged.accept("Orientation set to Portrait");
        }
    }

    private void registerMediaSessionListener(@NonNull Context context) {
        mediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        componentName = new ComponentName(context, MediaSessionNotificationListener.class);

        mediaSessionListener = controllers -> {
            for (MediaController oldController : activeControllerCallbacks.keySet()) {
                oldController.unregisterCallback(activeControllerCallbacks.get(oldController));
            }
            activeControllerCallbacks.clear();

            for (MediaController controller : controllers) {
                MediaController.Callback callback = new MediaControllerCallback();
                activeControllerCallbacks.put(controller, callback);
                controller.registerCallback(callback);
                callback.onMetadataChanged(controller.getMetadata());
                callback.onPlaybackStateChanged(controller.getPlaybackState());
            }
        };

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(mediaSessionListener, componentName);
            // Pega as sessões ativas no momento do registro
            List<MediaController> initialControllers = mediaSessionManager.getActiveSessions(componentName);
            mediaSessionListener.onActiveSessionsChanged(initialControllers);

        } catch (SecurityException e) {
            onStateChanged.accept("Error: Notification Access permission not granted for Media Session monitoring.");
        }
    }

    // Método para desregistrar o listener de MediaSession
    private void unregisterMediaSessionListener() {
        if (mediaSessionManager != null && mediaSessionListener != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(mediaSessionListener);
        }
        for (MediaController controller : activeControllerCallbacks.keySet()) {
            controller.unregisterCallback(activeControllerCallbacks.get(controller));
        }
        activeControllerCallbacks.clear();
    }
    private class MediaControllerCallback extends MediaController.Callback {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (state == null) return;
            String status = "";
            switch (state.getState()) {
                case PlaybackState.STATE_PLAYING:
                    status = "is Playing";
                    break;
                case PlaybackState.STATE_PAUSED:
                    status = "is Paused";
                    break;
                case PlaybackState.STATE_STOPPED:
                    status = "is Stopped";
                    break;
                case PlaybackState.STATE_BUFFERING:
                    status = "is Buffering";
                    break;
                case PlaybackState.STATE_SKIPPING_TO_NEXT:
                    status = "is Skipping to Next";
                    break;
                case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
                    status = "is Skipping to Previous";
                    break;
            }
            if (!status.isEmpty()) {
                onStateChanged.accept("Media session " + status);
            }
        }

        @Override
        public void onMetadataChanged(android.media.MediaMetadata metadata) {
            if (metadata == null) return;
            String title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
            if (title != null && artist != null) {
                onStateChanged.accept("Media changed to: " + title + " - " + artist);
            } else if (title != null) {
                onStateChanged.accept("Media changed to: " + title);
            }
        }
    }

    /**
     * O BroadcastReceiver interno que escuta as intents do sistema.
     */
    private class DeviceStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
//            Log.d("DeviceState_DEBUG", "onReceive FOI CHAMADO! Ação recebida: " + intent.getAction()); //debug line

            if (intent == null || intent.getAction() == null) {
                return;
            }

            String logEntry = null;
            String action = intent.getAction();

            switch (action) {
                // System Time, Locale, and Configuration
                case Intent.ACTION_LOCALE_CHANGED:
                    // CSV match: select_language_on_languages
                    logEntry = "Change device language";
                    break;
//                case Intent.ACTION_CONFIGURATION_CHANGED:
//                    logEntry = "Device configuration changed";
//                    break;

                // System UI and User State
                case Intent.ACTION_DREAMING_STARTED: // daydream is obsolete android 19+
                    logEntry = "Daydream mode started";
                    break;
                case Intent.ACTION_DREAMING_STOPPED:
                    logEntry = "Daydream mode stopped";
                    break;
                case Intent.ACTION_USER_BACKGROUND:
                    logEntry = "User process went to background";
                    break;
                case Intent.ACTION_USER_FOREGROUND:
                    logEntry = "User process went to foreground";
                    break;
                case Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED:
                    logEntry = "Application restrictions changed";
                    break;
                case Intent.ACTION_UID_REMOVED:
                    int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                    logEntry = "UID removed from system: " + uid;
                    break;
                case Intent.ACTION_SCREEN_ON:
                    // CSV match: turn_on_screen / enable_use_sleep_display
                    logEntry = "Turn on screen";
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    // CSV match: enable_put_display_to_sleep
                    logEntry = "Turn off screen";
                    break;
                case Intent.ACTION_PACKAGE_ADDED:
                    if (intent.getData() != null) {
                        String packageName = intent.getData().getSchemeSpecificPart();
                        // CSV match: install_app_on_app
                        logEntry = "Install app " + packageName;
                    }
                    break;

                // Categoria: Agendamentos, Intenções e Foco do Usuário
                case AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED:
                    logEntry = "Set alarm";
                    break;
                case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
                    logEntry = "Complete download";
                    break;
                case "android.intent.action.CHOOSER_TARGETS_PROMISED": // CORRIGIDO
                    logEntry = "Sharing intention initiated (Chooser menu)";
                    break;
                case Intent.ACTION_CLOSE_SYSTEM_DIALOGS:
                    logEntry = "Close system dialogs";
                    break;

//                // Categoria: Contexto de Trabalho
//                case Intent.ACTION_MANAGED_PROFILE_UNLOCKED:
//                    logEntry = "Work Profile unlocked (starting professional activity)";
//                    break;
//                case Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE:
//                    logEntry = "Work Profile unavailable/disabled (ending work)";
//                    break;

                // Categoria: Sincronização de Dados e Conteúdo
                case Intent.ACTION_PROVIDER_CHANGED:
                    logEntry = "A content provider's data has changed (e.g., Contacts, Settings)";
                    break;

                // Categoria: Ecossistema de Aplicativos (Nível Meta)
                case "android.intent.action.DEFAULT_APP_CHANGED":
                    logEntry = "The default app for an action has been changed";
                    break;
                case Intent.ACTION_MY_PACKAGE_SUSPENDED:
                    logEntry = "This application has been suspended by the system";
                    break;
                case Intent.ACTION_APPLICATION_LOCALE_CHANGED:
                    String pkg = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                    logEntry = "The locale for a specific app changed: " + pkg;
                    break;

                // Bateria e Energia
                case Intent.ACTION_POWER_CONNECTED:
                    // CSV match: select_charge_boost
                    logEntry = "Connect charger";
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    logEntry = "Disconnect charger";
                    break;
                case Intent.ACTION_BATTERY_LOW:
                    // CSV match: enable_battery_saver_reminders
                    logEntry = "Battery status: Low";
                    break;
                case Intent.ACTION_BATTERY_OKAY:
                    logEntry = "Battery status: OK";
                    break;
                case Intent.ACTION_BATTERY_CHANGED:
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    float batteryPct = level * 100 / (float)scale;
                    logEntry = "Battery state changed. Level: " + batteryPct + "%";
                    break;

                // Conectividade (Geral)
                case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                    boolean isAirplaneModeOn = intent.getBooleanExtra("state", false);
                    logEntry = isAirplaneModeOn ? "Airplane Mode enabled" : "Airplane Mode disabled";
                    break;
                // Conectividade (Wi-Fi)
                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                    // CSV match: enable_wifi
                    if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                        logEntry = "Enable Wi-Fi";
                    } else if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                        logEntry = "Disable Wi-Fi";
                    }
                    break;

                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    NetworkInfo netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (netInfo != null && netInfo.isConnected()) {
                        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        String ssid = (wm.getConnectionInfo() != null) ? wm.getConnectionInfo().getSSID() : "Unknown";
                        // CSV match: connect_saved_device / connect_wifi_ap
                        logEntry = "Connect to Wi-Fi network " + ssid;
                    }
                    break;
                // Conectividade (Bluetooth)
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    // CSV match: enable_bluetooth
                    if (btState == BluetoothAdapter.STATE_ON) {
                        logEntry = "Enable Bluetooth";
                    } else if (btState == BluetoothAdapter.STATE_OFF) {
                        logEntry = "Disable Bluetooth";
                    }
                    break;

                case BluetoothDevice.ACTION_ACL_CONNECTED:
                    // CSV match: connect_saved_device
                    logEntry = "Connect Bluetooth device";
                    break;
                case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                    logEntry = "Disconnect Bluetooth device";
                    break;

                // Conectividade (NFC)
                case NfcAdapter.ACTION_ADAPTER_STATE_CHANGED:
                    int nfcState = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF);
                    // CSV match: enable_nfc
                    if (nfcState == NfcAdapter.STATE_ON) {
                        logEntry = "Enable NFC";
                    } else if (nfcState == NfcAdapter.STATE_OFF) {
                        logEntry = "Disable NFC";
                    }
                    break;

                // Hardware e Áudio
                case Intent.ACTION_HEADSET_PLUG:
                    int headsetState = intent.getIntExtra("state", -1);
                    if (headsetState == 1) logEntry = "Connect headphones";
                    else if (headsetState == 0) logEntry = "Disconnect headphones";
                    break;

                case AudioManager.RINGER_MODE_CHANGED_ACTION:
                    int ringer = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);
                    // CSV match: enable_silence_calls_on_approach_to_silence_settings (Contexto similar)
                    if (ringer == AudioManager.RINGER_MODE_SILENT) {
                        logEntry = "Set Ringer to Silent";
                    } else if (ringer == AudioManager.RINGER_MODE_VIBRATE) {
                        logEntry = "Set Ringer to Vibrate";
                    } else if (ringer == AudioManager.RINGER_MODE_NORMAL) {
                        logEntry = "Set Ringer to Normal";
                    }
                    break;

                // Telefonia e SMS (e Estado Detalhado da Operadora)
                case ACTION_PHONE_STATE_CHANGED:
                    String stateStr = intent.getStringExtra(EXTRA_STATE);
                    if (EXTRA_STATE_RINGING.equals(stateStr)) {
                        // CSV match: is_incoming_call_icon_visible
                        logEntry = "Incoming call ringing";
                    } else if (EXTRA_STATE_OFFHOOK.equals(stateStr)) {
                        // CSV match: select_answer_button / select_call_button
                        logEntry = "Answer call or Call active";
                    } else if (EXTRA_STATE_IDLE.equals(stateStr)) {
                        // CSV match: choose_disconnect_call
                        logEntry = "End call";
                    }
                    break;
                case "android.intent.action.SERVICE_STATE":
                    try {
                        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                        // A verificação de permissão é crucial para apps que rodam em Android 6+
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            int networkType = telephonyManager.getDataNetworkType();
                            String networkTypeName = getNetworkTypeName(networkType);
                            logEntry = "Mobile network type changed to: " + networkTypeName;
                        } else {
                            logEntry = "Telephony service state changed (permission READ_PHONE_STATE not granted)";
                        }
                    } catch (Exception e) {
                        logEntry = "Error getting network type: " + e.getMessage();
                    }
                    break;

                case "android.intent.action.SIM_CARD_STATE_CHANGED":
                    int simState = intent.getIntExtra("ss", TelephonyManager.SIM_STATE_UNKNOWN);
                    switch (simState) {
                        case TelephonyManager.SIM_STATE_ABSENT:
                            logEntry = "SIM card state: Absent";
                            break;
                        case TelephonyManager.SIM_STATE_READY:
                            logEntry = "SIM card state: Ready for use";
                            break;
                        case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                            logEntry = "SIM card state: PIN required";
                            break;
                        case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                            logEntry = "SIM card state: PUK required";
                            break;
                        case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                            logEntry = "SIM card state: Network locked";
                            break;
                        default:
                            logEntry = "SIM card state: Unknown";
                            break;
                    }
                    break;

                case "android.provider.Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION":
                    logEntry = "Emergency alert (Cell Broadcast) received";
                    break;
                case "android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION":
                    logEntry = "WAP Push message received";
                    break;
                case "android.provider.Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION":
                    logEntry = "Data SMS received";
                    break;
                case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED:
                    logEntry = "Carrier configuration changed";
                    break;
            }

            if (logEntry != null) {
                onStateChanged.accept(logEntry);
            }
        }

        private String getNetworkTypeName(int networkType) {
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:return "GPRS";
                case TelephonyManager.NETWORK_TYPE_EDGE: return "EDGE";
                case TelephonyManager.NETWORK_TYPE_CDMA: return "CDMA";
                case TelephonyManager.NETWORK_TYPE_1xRTT: return "1xRTT";
                case TelephonyManager.NETWORK_TYPE_IDEN: return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS: return "UMTS";
                case TelephonyManager.NETWORK_TYPE_EVDO_0: return "EVDO";
                case TelephonyManager.NETWORK_TYPE_EVDO_A: return "EVDO_A";
                case TelephonyManager.NETWORK_TYPE_HSDPA: return "HSDPA";
                case TelephonyManager.NETWORK_TYPE_HSUPA: return "HSUPA";
                case TelephonyManager.NETWORK_TYPE_HSPA: return "HSPA";
                case TelephonyManager.NETWORK_TYPE_EVDO_B: return "EVDO_B";
                case TelephonyManager.NETWORK_TYPE_EHRPD: return "EHRPD";
                case TelephonyManager.NETWORK_TYPE_HSPAP: return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE: return "4G (LTE)";
                case TelephonyManager.NETWORK_TYPE_NR: return "5G";
                case TelephonyManager.NETWORK_TYPE_IWLAN: return "Wi-Fi Calling";
                case TelephonyManager.NETWORK_TYPE_UNKNOWN: return "Unknown";
                default: return "New/Unknown Network Type";
            }
        }
    }
}