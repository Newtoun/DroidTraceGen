package com.example.gravadordeacoes.recorder;

import android.service.notification.NotificationListenerService;

/**
 * Serviço necessário para obter a permissão BIND_NOTIFICATION_LISTENER_SERVICE,
 * que permite que o MediaSessionManager nos notifique sobre sessões de mídia ativas.
 */
public class MediaSessionNotificationListener extends NotificationListenerService {
}