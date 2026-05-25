package com.example.gravadordeacoes.viewmodel;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.gravadordeacoes.data.AnalysisRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * ViewModel que gerencia o estado e a lógica de negócio da gravação de ações.
 */
public class RecordingViewModel extends AndroidViewModel {

    private static final String TAG = "GravadorDeAcoes";
    private static final String SCREEN_LOG_PREFIX = "Get current screen: ";

    private final AnalysisRepository analysisRepository;

    private final MutableLiveData<Boolean> _isRecording = new MutableLiveData<>(false);
    public final LiveData<Boolean> isRecording = _isRecording;

    private final List<String> recordedActions = new ArrayList<>();
    private String lastRecordedScreen = "";

    public RecordingViewModel(@NonNull Application application) {
        super(application);
        this.analysisRepository = new AnalysisRepository(application);
    }

    public void toggleRecording(String currentScreen) {
        boolean recording = _isRecording.getValue() != null && _isRecording.getValue();
        if (!recording) {
            start(currentScreen);
        } else {
            stop();
        }
    }

    private void start(String initialScreen) {
        _isRecording.setValue(true);
        recordedActions.clear();
        lastRecordedScreen = "";
        Toast.makeText(getApplication(), "Gravação iniciada!", Toast.LENGTH_SHORT).show();

        // Alterado: Usa o método padronizado para registrar a tela inicial
        recordScreenChange(initialScreen);
    }

    private void stop() {
        _isRecording.setValue(false);
        Toast.makeText(getApplication(), "Gravação finalizada!", Toast.LENGTH_SHORT).show();
        processAndSendRecording();
    }

    public void recordClick(String clickDescription) {
        if (Boolean.FALSE.equals(_isRecording.getValue())) return;
        addLogEntry(clickDescription);
    }

    /**
     * Registra a mudança de tela usando uma semântica de verificação de estado
     * para alinhar com a função 'get_current_screen_on_screen_actions'.
     * * @param screenDescription O nome da nova tela (ex: "Settings").
     */
    public void recordScreenChange(String screenDescription) {
        if (Boolean.FALSE.equals(_isRecording.getValue())) return;

        // Evita logs duplicados consecutivos da mesma tela
        if (screenDescription.equals(lastRecordedScreen)) return;

        lastRecordedScreen = screenDescription;

        // Gera o log: "Get current screen: Home"
        addLogEntry(SCREEN_LOG_PREFIX + screenDescription);
    }

    public void recordDeviceStateChange(String stateDescription) {
        if (Boolean.FALSE.equals(_isRecording.getValue())) return;
        addLogEntry(stateDescription);
    }

    private void addLogEntry(String logEntry) {
        if (recordedActions.isEmpty() || !recordedActions.get(recordedActions.size() - 1).equals(logEntry)) {
            recordedActions.add(logEntry);
            Log.d(TAG, "LOG GRAVADO: " + logEntry);
        }
    }

    private void processAndSendRecording() {
        if (recordedActions.isEmpty()) {
            Toast.makeText(getApplication(), "Nenhuma ação foi gravada.", Toast.LENGTH_SHORT).show();
            return;
        }
        StringJoiner logJoiner = new StringJoiner("\n");
        recordedActions.forEach(logJoiner::add);
        analysisRepository.sendAnalysisToServer(logJoiner.toString());
    }
}