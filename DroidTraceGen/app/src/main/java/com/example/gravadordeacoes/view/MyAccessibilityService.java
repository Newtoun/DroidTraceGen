package com.example.gravadordeacoes.view;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

import com.example.gravadordeacoes.R;
import com.example.gravadordeacoes.recorder.DeviceStateMonitor;
import com.example.gravadordeacoes.utils.DebugUtils;
import com.example.gravadordeacoes.utils.ScreenParser;
import com.example.gravadordeacoes.viewmodel.RecordingViewModel;

public class MyAccessibilityService extends AccessibilityService implements ViewModelStoreOwner, LifecycleOwner {

    private static final String TAG = "GravadorDeAcoes";
    private static final String NOTIFICATION_CHANNEL_ID = "GravadorChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_STOP_SERVICE = "com.example.gravadordeacoes.STOP_SERVICE";

    @NonNull
    private final ViewModelStore viewModelStore = new ViewModelStore();
    @NonNull
    private final LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);

    private RecordingViewModel recordingViewModel;
    private FloatingButtonManager floatingButtonManager;
    private DeviceStateMonitor deviceStateMonitor;
    private String myAppPackageName;

    // --- Variáveis de Controle de Tela ---
    private String lastScreenNamePure = "";
    private final Handler screenChangeHandler = new Handler(Looper.getMainLooper());
    private Runnable screenCheckRunnable;

    // --- Variáveis de Controle de Digitação (Debounce) ---
    private final Handler textDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingTextRunnable;
    private String lastTypedText = "";

    private boolean isInspectMode = false;

    @NonNull @Override public ViewModelStore getViewModelStore() { return viewModelStore; }
    @NonNull @Override public Lifecycle getLifecycle() { return lifecycleRegistry; }

    @Override
    public void onCreate() {
        super.onCreate();
        myAppPackageName = getPackageName();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);

        recordingViewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(RecordingViewModel.class);
        deviceStateMonitor = new DeviceStateMonitor(recordingViewModel::recordDeviceStateChange);

        // Inicializa o Runnable de checagem de tela
        screenCheckRunnable = () -> {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                checkScreenState(rootNode);
                rootNode.recycle();
            }
        };

        floatingButtonManager = new FloatingButtonManager(this, new FloatingButtonManager.Listener() {
            @Override
            public void onStartStopRecording() {
                if (isInspectMode) return;
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    // Log inicial
                    String context = ScreenParser.getScreenContext(rootNode);
                    recordingViewModel.toggleRecording("Start Recording" + context);
                    rootNode.recycle();
                } else {
                    recordingViewModel.toggleRecording("Start Recording | Unknown Screen | Unknown Package");
                }
            }

            @Override
            public void onDebugRequested() {
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if(rootNode!=null){
                    DebugUtils.dumpViewHierarchy(rootNode);
                    rootNode.recycle();
                }
            }

            @Override
            public void onSnapshotRequested() {
                isInspectMode = !isInspectMode;
                floatingButtonManager.setInspectModeState(isInspectMode);
                String msg = isInspectMode ? "INSPEÇÃO: Toque em um elemento" : "Inspeção cancelada";
                Toast.makeText(MyAccessibilityService.this, msg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onInspectTouch(float x, float y) {
                handleInspectTouch((int) x, (int) y);
            }
        });

        recordingViewModel.isRecording.observe(this, isRecording -> {
            if (isRecording != null) floatingButtonManager.setRecordingState(isRecording);
        });

        deviceStateMonitor.register(this);
        createNotificationChannel();
    }

    /**
     * Verifica se houve mudança de tela
     */
    private void checkScreenState(AccessibilityNodeInfo rootNode) {
        String newScreenNamePure = ScreenParser.identifyScreen(rootNode);

        // --- REGRA 1: IGNORAR NÚMEROS ---
        // Se o nome da tela for apenas números (ex: "85") ou símbolos de telefone,
        // ignoramos completamente este evento.
        // Regex: Aceita números (0-9), espaços, traços, parênteses, mais, asterisco e jogo da velha.
        if (newScreenNamePure.matches("^[0-9\\s\\-\\(\\)\\+\\*#]+$")) {
            return;
        }

        // Se mudou, gera o log
        if (!newScreenNamePure.equals(lastScreenNamePure)) {
            Log.d(TAG, "Tela alterada: " + lastScreenNamePure + " -> " + newScreenNamePure);
            lastScreenNamePure = newScreenNamePure;

            String contextSuffix = ScreenParser.getScreenContext(rootNode);

            String logMessage = newScreenNamePure + contextSuffix;

            recordingViewModel.recordScreenChange(logMessage);
        }
    }

    private void handleInspectTouch(int x, int y) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Toast.makeText(this, "Erro: Tela não acessível", Toast.LENGTH_SHORT).show();
            disableInspectMode();
            return;
        }

        AccessibilityNodeInfo targetNode = null;
        try {
            targetNode = findNodeByCoordinates(rootNode, x, y);
            if (targetNode != null) {
                String elementContent = ScreenParser.getSmartDescription(targetNode);
                String contextSuffix = ScreenParser.getScreenContext(rootNode);

                // FORMATO: Verify Elemento | Tela | Pacote
                String logMessage = "Verify " + elementContent + contextSuffix;

                recordingViewModel.recordClick(logMessage);
                Log.d(TAG, logMessage);
                Toast.makeText(this, "Salvo: " + elementContent, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro na inspeção", e);
        } finally {
            if (targetNode != null) targetNode.recycle();
            if (rootNode != null) rootNode.recycle();
            disableInspectMode();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (myAppPackageName.equals(event.getPackageName())) return;
        if (isInspectMode) return;
        if (Boolean.FALSE.equals(recordingViewModel.isRecording.getValue())) return;

        int eventType = event.getEventType();
        AccessibilityNodeInfo sourceNode = event.getSource();
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();

        try {
            // --- DETECÇÃO DE TELA ---
            if (rootNode != null && (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
                screenChangeHandler.removeCallbacks(screenCheckRunnable);
                screenChangeHandler.postDelayed(screenCheckRunnable, 500); // Atraso levemente maior para estabilidade
            }

            String contextSuffix = ScreenParser.getScreenContext(rootNode);

            // --- LÓGICA DE CLIQUES ---
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED || eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {

                String verb = (eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) ? "Long Press" : "Select";
                String elementLabel;

                if (sourceNode != null) {
                    elementLabel = ScreenParser.getNodeDescription(sourceNode);
                } else {
                    elementLabel = (event.getClassName() != null) ? event.getClassName().toString() : "Generic Item";
                }

                // FORMATO: Select Elemento | Tela | Package
                String log = verb + " " + elementLabel + contextSuffix;
                recordingViewModel.recordClick(log);

                screenChangeHandler.removeCallbacks(screenCheckRunnable);
                screenChangeHandler.postDelayed(screenCheckRunnable, 600);
            }

            // --- LÓGICA DE DIGITAÇÃO (COM DEBOUNCE E SEM SUFIXO) ---
            else if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                if (event.getText() != null && !event.getText().isEmpty() && sourceNode != null) {

                    String newText = event.getText().toString().replace("[", "").replace("]", "");
                    String elementLabel = ScreenParser.getNodeDescription(sourceNode);

                    // Ignora se for o mesmo texto (duplicação de evento)
                    if (newText.equals(lastTypedText)) return;
                    lastTypedText = newText;

                    // 1. Remove qualquer agendamento anterior (reset do timer)
                    if (pendingTextRunnable != null) {
                        textDebounceHandler.removeCallbacks(pendingTextRunnable);
                    }

                    // 2. Agenda a gravação para daqui a 1.5 segundos (quando o usuário parar de digitar)
                    pendingTextRunnable = () -> {
                        // FORMATO: Type "85" in Campo (Sem Screen | Package)
                        String log = "Type " + newText + " in " + elementLabel;
                        recordingViewModel.recordClick(log);
                        Log.d(TAG, "Texto consolidado: " + log);
                    };

                    // 3. Inicia a espera
                    textDebounceHandler.postDelayed(pendingTextRunnable, 1500);
                }
            }

        } finally {
            if (sourceNode != null) sourceNode.recycle();
            if (rootNode != null) rootNode.recycle();
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;

        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 50;
        setServiceInfo(info);
        startForegroundServiceWithNotification();
    }

    // Métodos padrão (onStartCommand, onDestroy, disableInspectMode, findNodeByCoordinates, etc.)
    // mantidos iguais.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) disableSelf();
            else stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        stopForeground(true);
        if (floatingButtonManager != null) floatingButtonManager.destroy();
        if (deviceStateMonitor != null) deviceStateMonitor.unregister(this);
        viewModelStore.clear();
        super.onDestroy();
    }

    private void disableInspectMode() {
        isInspectMode = false;
        floatingButtonManager.setInspectModeState(false);
    }

    private AccessibilityNodeInfo findNodeByCoordinates(AccessibilityNodeInfo node, int x, int y) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) return null;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo foundInChild = findNodeByCoordinates(child, x, y);
                if (foundInChild != null) {
                    if (foundInChild != child) child.recycle();
                    return foundInChild;
                }
                child.recycle();
            }
        }
        return AccessibilityNodeInfo.obtain(node);
    }

    private void startForegroundServiceWithNotification() {
        Intent stopIntent = new Intent(this, MyAccessibilityService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Gravador de Ações Ativo")
                .setContentText("Toque para interagir.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .addAction(R.drawable.shape_floating_button, "Parar Serviço", pendingStopIntent)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Gravador Service", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (deviceStateMonitor != null) deviceStateMonitor.onConfigurationChanged(newConfig);
    }

    @Override
    public void onInterrupt() {}
}