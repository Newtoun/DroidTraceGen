package com.example.gravadordeacoes.recorder;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * Gerencia o estado da gravação (iniciada/parada) e a lista de ações gravadas.
 */
public class ActionRecorder {

    private static final String TAG = "GravadorDeAcoes";
    private final Context context;
    private final Consumer<String> onRecordingFinished; // Callback para enviar os dados

    private boolean isRecording = false;
    private final List<String> recordedActions = new ArrayList<>();
    private String lastRecordedScreen = "";

    public ActionRecorder(Context context, Consumer<String> onRecordingFinished) {
        this.context = context;
        this.onRecordingFinished = onRecordingFinished;
    }

    public void start(String initialScreen) {
        isRecording = true;
        recordedActions.clear();
        lastRecordedScreen = "";
        Toast.makeText(context, "Gravação iniciada!", Toast.LENGTH_SHORT).show();

        recordScreenChange("Start recording on screen: ", initialScreen);
    }

    public void stop() {
        isRecording = false;
        Toast.makeText(context, "Gravação finalizada!", Toast.LENGTH_SHORT).show();
        processAndSendRecording();
    }

    public void recordClick(String clickDescription) {
        if (!isRecording) return;
        addLogEntry(clickDescription);
    }

    public void recordScreenChange(String prefix, String screenDescription) {
        if (!isRecording || screenDescription.equals(lastRecordedScreen)) return;

        lastRecordedScreen = screenDescription;
        addLogEntry(prefix + screenDescription);
    }

    public void recordDeviceStateChange(String stateDescription) {
        if (!isRecording) return;
        addLogEntry("Device state changed: " + stateDescription);
    }

    private void addLogEntry(String logEntry) {
        if (recordedActions.isEmpty() || !recordedActions.get(recordedActions.size() - 1).equals(logEntry)) {
            recordedActions.add(logEntry);
            Log.d(TAG, logEntry);
        }
    }

    private void processAndSendRecording() {
        if (recordedActions.isEmpty()) {
            Toast.makeText(context, "Nenhuma ação foi gravada.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringJoiner logJoiner = new StringJoiner("\n");
        recordedActions.forEach(logJoiner::add);
        onRecordingFinished.accept(logJoiner.toString());
    }

    public boolean isRecording() {
        return isRecording;
    }
}