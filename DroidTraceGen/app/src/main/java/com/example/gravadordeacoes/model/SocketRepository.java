package com.example.gravadordeacoes.model;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class SocketRepository {
    private static final String TAG = "SocketRepository";
    private static final int TCP_PORT = 12345;
    private static final int SCAN_TIMEOUT_MS = 300;   // timeout por IP na varredura
    private static final int SCAN_THREADS = 50;        // IPs verificados em paralelo

    private static final byte[] DISCOVERY_REQUEST  = "DISC".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DISCOVERY_RESPONSE = "OK\n".getBytes(StandardCharsets.UTF_8);

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface SendCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public SocketRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    // ---------------------------------------------------------------
    // Descobre o IP do servidor varrendo a sub-rede via TCP
    // ---------------------------------------------------------------
    private String discoverServerIp() {
        String subnet = getSubnet();
        if (subnet == null) {
            Log.e(TAG, "Não foi possível obter o IP do Wi-Fi.");
            return null;
        }
        Log.d(TAG, "Varrendo sub-rede: " + subnet + "0/24 na porta " + TCP_PORT);

        AtomicReference<String> found = new AtomicReference<>(null);
        ExecutorService scanPool = Executors.newFixedThreadPool(SCAN_THREADS);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 1; i <= 254; i++) {
            if (found.get() != null) break;
            final String ip = subnet + i;
            futures.add(scanPool.submit(() -> {
                if (found.get() != null) return;
                if (isDiscoveryServer(ip)) {
                    found.compareAndSet(null, ip);
                }
            }));
        }

        // Aguarda todas as threads terminarem
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        scanPool.shutdown();

        if (found.get() != null) {
            Log.i(TAG, "Servidor encontrado: " + found.get());
        } else {
            Log.e(TAG, "Nenhum servidor encontrado na sub-rede.");
        }
        return found.get();
    }

    /** Tenta o handshake de descoberta com um IP. Retorna true se for o servidor. */
    private boolean isDiscoveryServer(String ip) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, TCP_PORT), SCAN_TIMEOUT_MS);
            s.setSoTimeout(SCAN_TIMEOUT_MS);

            s.getOutputStream().write(DISCOVERY_REQUEST);
            s.getOutputStream().flush();

            InputStream in = s.getInputStream();
            byte[] resp = new byte[DISCOVERY_RESPONSE.length];
            int read = in.read(resp);
            if (read == DISCOVERY_RESPONSE.length) {
                String response = new String(resp, 0, read, StandardCharsets.UTF_8);
                return response.equals(new String(DISCOVERY_RESPONSE, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {}
        return false;
    }

    /** Retorna o prefixo da sub-rede (ex: "192.168.0.") a partir do IP Wi-Fi do dispositivo. */
    private String getSubnet() {
        try {
            WifiManager wifiManager = (WifiManager)
                    context.getSystemService(Context.WIFI_SERVICE);
            int ipInt = wifiManager.getConnectionInfo().getIpAddress();
            if (ipInt == 0) return null;
            String ip = String.format("%d.%d.%d.",
                    (ipInt & 0xff),
                    (ipInt >> 8  & 0xff),
                    (ipInt >> 16 & 0xff));
            Log.d(TAG, "IP do dispositivo: " + ip + (ipInt >> 24 & 0xff));
            return ip;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter IP Wi-Fi: " + e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Envia o log para o servidor após descoberta
    // ---------------------------------------------------------------
    public void sendAnalysisData(String logData, SendCallback callback) {
        executor.execute(() -> {
            try {
                String serverIp = discoverServerIp();
                if (serverIp == null) {
                    throw new IOException("Servidor não encontrado na rede.");
                }

                try (Socket socket = new Socket(serverIp, TCP_PORT);
                     DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                    Log.d(TAG, "Conectado ao servidor " + serverIp + ":" + TCP_PORT);

                    byte[] logBytes = logData.getBytes(StandardCharsets.UTF_8);
                    dos.writeInt(logBytes.length);
                    dos.write(logBytes);
                    dos.flush();

                    Log.i(TAG, "Log enviado (" + logBytes.length + " bytes).");

                    if (callback != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(callback::onSuccess);
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "Erro ao enviar log.", e);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> callback.onError(e));
                }
            }
        });
    }

    public void shutdown() {
        if (!executor.isShutdown()) executor.shutdown();
    }
}
