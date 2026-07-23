package com.camera.app;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

// Bu servis arka planda sessizce bildirimleri dinler, kamera/mikrofonu etkilemez.
public class NotificationService extends NotificationListenerService {

    public interface NotificationListener {
        void onNotificationReceived(String pkg, String title, String text);
    }

    private static NotificationListener listener;

    public static void setListener(NotificationListener l) {
        listener = l;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (listener != null) {
            String packageName = sbn.getPackageName();
            String title = sbn.getNotification().extras.getString("android.title", "Başlıksız");
            
            CharSequence textChar = sbn.getNotification().extras.getCharSequence("android.text");
            String text = (textChar != null) ? textChar.toString() : "";

            listener.onNotificationReceived(packageName, title, text);
        }
    }
}
