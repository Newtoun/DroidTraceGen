
package com.example.gravadordeacoes.data;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.example.gravadordeacoes.model.SocketRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class AnalysisRepository {

    private static final String TAG = "GravadorDeAcoes";
    private final Context context;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final SocketRepository socketRepository;

    public AnalysisRepository(Context context) {
        this.context = context.getApplicationContext();
        this.socketRepository = new SocketRepository(this.context);
    }

    /**
     * Salva o log localmente no dispositivo e envia para o servidor.
     * Arquivo salvo em: Android/data/com.example.gravadordeacoes/files/logs/log_YYYYMMDD_HHmmss.txt
     */
    public void sendAnalysisToServer(final String logData) {
        Log.d(TAG, "Logs sendo preparados para envio...");
        Log.w(TAG, "============== AÇÕES GRAVADAS (ENVIADAS) ===============");
        Log.d(TAG, logData);
        Log.w(TAG, "=========================================================");

        saveLogLocally(logData);

        socketRepository.sendAnalysisData(logData, new SocketRepository.SendCallback() {
            @Override
            public void onSuccess() {
                mainThreadHandler.post(() ->
                    Toast.makeText(context, "Logs salvos e enviados para o servidor!", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Erro ao enviar logs para o servidor.", e);
                mainThreadHandler.post(() ->
                    Toast.makeText(context, "Log salvo no dispositivo, mas falhou ao enviar ao servidor.", Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void saveLogLocally(String logData) {
        try {
            File logsDir = new File(context.getExternalFilesDir(null), "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File logFile = new File(logsDir, "log_" + timestamp + ".txt");

            try (FileWriter writer = new FileWriter(logFile)) {
                writer.write(logData);
            }

            Log.i(TAG, "Log salvo localmente em: " + logFile.getAbsolutePath());

        } catch (IOException e) {
            Log.e(TAG, "Erro ao salvar log localmente.", e);
        }
    }
}