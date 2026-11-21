package com.xxq.xlai;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {

    private static final String CHANNEL_ID = "ModelDownloadChannel";
    private static final int NOTIFICATION_ID = 101;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    
    // 🚀 THE QUEUE FIX: Use a SingleThreadExecutor to process downloads sequentially!
    private ExecutorService executorService;
    
    public static volatile boolean isDownloading = false;
       //  UI INSTANT RESTORE CACHE
    public static volatile String activeFilename = "";
    public static volatile String activeModelName = "";
    public static volatile int activeProgress = 0;
    public static volatile String activeStatus = "Connecting...";
    public static volatile String activeTotalSize = "";

    
    private volatile boolean forceStop = false;
    private int queuedTasks = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        // Initializes the strict 1-by-1 queue
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String url = intent.getStringExtra("url");
        String filename = intent.getStringExtra("filename");
        String incomingModelName = intent.getStringExtra("modelName");

        final String displayModelName = (incomingModelName == null || incomingModelName.isEmpty()) 
                ? "AI Model" 
                : incomingModelName;

        isDownloading = true;
        forceStop = false;
        queuedTasks++;

        // Setup the initial foreground state so Android doesn't kill the service
        if (queuedTasks == 1) {
            setupForegroundNotification(displayModelName);
        }

        // 🚀 Add the download task to the queue! 
        // If GGUF is downloading, the MMPROJ will wait here patiently until it finishes.
        executorService.execute(() -> {
            try {
                if (!forceStop) {
                    downloadFile(url, filename, displayModelName);
                }
            } finally {
                queuedTasks--;
                if (queuedTasks <= 0) {
                    isDownloading = false;
                    stopSelf();
                }
            }
        });

        return START_NOT_STICKY;
    }
    
    private void setupForegroundNotification(String initialName) {
        Intent notificationIntent = new Intent(this, ModelDownloadActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading " + initialName)
                .setContentText("Connecting...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, 0, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notificationBuilder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    // 🚀 NO GLOBAL VARIABLES: 'filename' is now passed directly so it never gets overwritten!
    private void downloadFile(String fileUrl, String filename, String displayModelName) {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;

        try {
            // Update the notification for whichever file just started downloading
            updateNotificationTitle("Downloading: " + displayModelName);

            URL url = new URL(fileUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            File finalFile = new File(getFilesDir(), filename);
            File tempFile = new File(getFilesDir(), filename + ".temp");
            long downloaded = 0;

            if (tempFile.exists()) {
                downloaded = tempFile.length();
                conn.setRequestProperty("Range", "bytes=" + downloaded + "-");
            }

            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                sendError("Server returned HTTP " + responseCode, filename);
                return;
            }

            boolean append = (responseCode == HttpURLConnection.HTTP_PARTIAL);
            if (!append) {
                downloaded = 0; 
                if (tempFile.exists()) tempFile.delete(); 
            }

            long contentLength = -1;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                contentLength = conn.getContentLengthLong();
            } else {
                String lengthStr = conn.getHeaderField("Content-Length");
                if (lengthStr != null && !lengthStr.isEmpty()) {
                    try { contentLength = Long.parseLong(lengthStr); } catch (Exception ignored) {}
                }
            }
            
            long total = contentLength > 0 ? contentLength + downloaded : -1;

            in = conn.getInputStream();
            out = new FileOutputStream(tempFile, append);

            byte[] buffer = new byte[8192];
            int count;

            long lastUpdateTime = System.currentTimeMillis();
            long bytesDownloadedSinceLastUpdate = 0;

            // 🚀 Breaks instantly if user hits cancel
            while ((count = in.read(buffer)) != -1 && !forceStop) {
                downloaded += count;
                bytesDownloadedSinceLastUpdate += count;
                out.write(buffer, 0, count);

                long now = System.currentTimeMillis();
                long timeDiff = now - lastUpdateTime;

                if (timeDiff >= 500) {
                    int progress = (total > 0) ? (int) ((downloaded * 100L) / total) : 0;
                    long speed = (bytesDownloadedSinceLastUpdate * 1000L) / timeDiff;
                    String status = format(downloaded) + " / " + (total > 0 ? format(total) : "?") + " • " + format(speed) + "/s";

                    // 🏆 UPDATE CACHE FOR INSTANT UI RESTORE
                    activeFilename = filename;
                    activeModelName = displayModelName;
                    activeProgress = progress;
                    activeStatus = status;
                    if (total > 0) activeTotalSize = format(total);

                    updateNotificationProgress(progress, format(speed));
                    sendProgress(progress, downloaded, total, speed, displayModelName, filename);

                    lastUpdateTime = now;
                    bytesDownloadedSinceLastUpdate = 0;
                }

            }

            if (!forceStop && total > 0 && downloaded >= total) {
                if (out != null) {
                    out.close(); 
                    out = null;
                }
                if (tempFile.renameTo(finalFile)) {
                    saveCompletionStateToPrefs(filename); 
                    sendProgress(100, downloaded, total, 0, displayModelName, filename);
                    sendComplete(displayModelName, filename);
                } else {
                    sendError("Failed to finalize model file.", filename);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            if (!forceStop) {
                sendError(e.getMessage(), filename);
            }
        } finally {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
            } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private void saveCompletionStateToPrefs(String filename) {
        if ("model.gguf".equals(filename)) {
            getSharedPreferences("model_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("model_downloaded", true)
                    .apply();
        } else {
            android.content.SharedPreferences prefs = getSharedPreferences("custom_models", MODE_PRIVATE);
            int count = prefs.getInt("custom_model_count", 0);
            for (int i = 0; i < count; i++) {
                if (filename.equals(prefs.getString("custom_model_" + i + "_filename", "")) ||
                    filename.equals(prefs.getString("custom_model_" + i + "_mmproj_filename", ""))) {
                    
                    // Mark the specific model card as downloaded only if the GGUF finished!
                    if (filename.endsWith(".gguf")) {
                        prefs.edit().putBoolean("custom_model_" + i + "_downloaded", true).apply();
                    }
                    break;
                }
            }
        }
    }

    private void updateNotificationTitle(String title) {
        if (notificationBuilder != null) {
            notificationBuilder.setContentTitle(title);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    private void updateNotificationProgress(int progress, String speedStr) {
        if (notificationBuilder != null) {
            notificationBuilder.setProgress(100, progress, false)
                    .setContentText(progress + "% • " + speedStr + "/s");
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    private void sendProgress(int progress, long downloaded, long total, long speed, String modelName, String filename) {
        Intent intent = new Intent("DOWNLOAD_PROGRESS");
        intent.setPackage(getPackageName()); 
        
        String status = format(downloaded) + " / " + (total > 0 ? format(total) : "?") + " • " + format(speed) + "/s";

        intent.putExtra("progress", progress);
        intent.putExtra("status", status);
        intent.putExtra("filename", filename);
        intent.putExtra("model_name", modelName);

        if (total > 0) {
            intent.putExtra("total_size", format(total));
        }

        sendBroadcast(intent);
    }

    private void sendComplete(String modelName, String filename) {
        Intent intent = new Intent("DOWNLOAD_COMPLETE");
        intent.setPackage(getPackageName()); 
        intent.putExtra("filename", filename);
        intent.putExtra("model_name", modelName);
        sendBroadcast(intent);
    }

    private void sendError(String error, String filename) {
        Intent intent = new Intent("DOWNLOAD_ERROR");
        intent.setPackage(getPackageName()); 
        intent.putExtra("error", error != null ? error : "Unknown error");
        intent.putExtra("filename", filename);
        sendBroadcast(intent);
    }

    private String format(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Model Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        forceStop = true;
        isDownloading = false;
        
        // Safely kill the queue and the active thread
        if (executorService != null) {
            executorService.shutdownNow();
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        super.onDestroy();
    }
}
