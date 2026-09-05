package com.ar.hostmaster;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;

public class NotificationHelper {
    private static final String CHANNEL_ID = "ftp_events";
    private static final String CHANNEL_NAME = "FTP Events";

    public static void showNotification(Context context, String title, String message) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] vibrationPattern;
            if (title.contains("Connected")) {
                vibrationPattern = new long[]{0, 100, 200, 100};
            } else if (title.contains("Disconnected")) {
                vibrationPattern = new long[]{0, 300, 200, 100};
            } else {
                vibrationPattern = new long[]{0, 200, 100, 200};
            }

            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("FTP client connection events");
            channel.enableVibration(true);
            channel.setVibrationPattern(vibrationPattern);
            channel.setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            );
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.hostmaster_notification_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (title.contains("Connected")) {
            builder.setVibrate(new long[]{0, 100, 200, 100});
        } else if (title.contains("Disconnected")) {
            builder.setVibrate(new long[]{0, 300, 200, 100});
        } else {
            builder.setVibrate(new long[]{0, 200, 100, 200});
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (title.contains("Connected")) {
                        vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 100, 200, 100}, -1));
                    } else if (title.contains("Disconnected")) {
                        vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 300, 200, 100}, -1));
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}