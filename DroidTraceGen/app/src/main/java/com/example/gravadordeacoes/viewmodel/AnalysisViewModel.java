package com.example.gravadordeacoes.viewmodel;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.gravadordeacoes.model.SocketRepository;

public class AnalysisViewModel extends AndroidViewModel {
    private static final String TAG = "AnalysisViewModel";
    private final SocketRepository socketRepository;

    public AnalysisViewModel(@NonNull Application application) {
        super(application);
        this.socketRepository = new SocketRepository(application);
    }

    public void sendAnalysisToServer( String logData) {
        Log.d(TAG, "Iniciando o envio da análise para o servidor.");
        socketRepository.sendAnalysisData( logData, new SocketRepository.SendCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Dados enviados com sucesso para o servidor.");
                Toast.makeText(getApplication(), "Análise enviada para o servidor!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Falha ao enviar dados para o servidor.", e);
                Toast.makeText(getApplication(), "Erro ao conectar com o servidor.", Toast.LENGTH_LONG).show();
            }
        });
    }
}