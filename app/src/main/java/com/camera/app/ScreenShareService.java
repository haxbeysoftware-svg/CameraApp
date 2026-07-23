package com.camera.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Android 10+ (API 29) itibarıyla MediaProjection (ekran yakalama) yalnızca
 * bir foreground service çalışırken başlatılabilir/sürdürülebilir.
 * Bu servis sadece bildirimi ayakta tutmak için var, başka bir iş yapmıyor.
 */
public class ScreenShareService extends Service {

    private static final String CHANNEL_ID = "screen_share_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannelIfNeeded();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ekran paylaşılıyor")
                .setContentText("Ekranınız Monitor uygulamasına aktarılıyor")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ (API 34) foregroundServiceType belirtilmesini zorunlu kılıyor.
            // Build.VERSION_CODES.UPSIDE_DOWN_CAKE ve ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            // sabitleri yerine ham sayısal değerleri kullanıyoruz; böylece proje compileSdk 34'ün
            // altında olsa bile derleme hatası vermiyor (bu sabitler yalnızca compileSdk>=34 ile bulunabilir).
            startForeground(NOTIFICATION_ID, notification, 32 /* FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION */);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        return START_NOT_STICKY;
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ekran Paylaşımı",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
