/*
 * Copyright 2026 deepcodecreate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terbian.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.terbian.R;
import com.terbian.terminal.session.SessionRegistry;
import com.terbian.ui.MainActivity;

import java.util.LinkedHashMap;
import java.util.Map;

public class TerminalService extends Service {
    private static final String CHANNEL_ID = "terbian_running";
    private static final int SUMMARY_NOTIFICATION_ID = 1001;
    private static final int SESSION_NOTIFICATION_ID_BASE = 2000;

    public static final String ACTION_SESSION_STARTED = "com.terbian.action.SESSION_STARTED";
    public static final String ACTION_SESSION_STOPPED = "com.terbian.action.SESSION_STOPPED";
    public static final String ACTION_STOP_SESSION = "com.terbian.action.STOP_SESSION";
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_SESSION_TITLE = "session_title";

    public class LocalBinder extends Binder {
        public TerminalService getService() {
            return TerminalService.this;
        }
    }

    private final LocalBinder binder = new LocalBinder();
    private final Map<String, Integer> sessionNotificationIds = new LinkedHashMap<>();
    private int nextNotificationId = SESSION_NOTIFICATION_ID_BASE;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannelIfNeeded();
        startForeground(SUMMARY_NOTIFICATION_ID, buildSummaryNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(SUMMARY_NOTIFICATION_ID, buildSummaryNotification());

        if (intent != null) {
            String action = intent.getAction();
            String sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
            if (ACTION_SESSION_STARTED.equals(action) && sessionId != null) {
                showSessionNotification(sessionId, intent.getStringExtra(EXTRA_SESSION_TITLE));
            } else if (ACTION_SESSION_STOPPED.equals(action) && sessionId != null) {
                removeSessionNotification(sessionId);
            } else if (ACTION_STOP_SESSION.equals(action) && sessionId != null) {
                SessionRegistry.getInstance(this).stopAndRemove(sessionId);
                removeSessionNotification(sessionId);
            }
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notification_channel_running),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent openAppIntent() {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 0, openApp, flags);
    }

    private Notification buildSummaryNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_running_title))
                .setContentText(getString(R.string.notification_running_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openAppIntent())
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setGroup(CHANNEL_ID)
                .setGroupSummary(true)
                .build();
    }

    private void showSessionNotification(String sessionId, String title) {
        int notificationId = sessionNotificationIds.computeIfAbsent(sessionId, id -> nextNotificationId++);

        Intent stopIntent = new Intent(this, TerminalService.class);
        stopIntent.setAction(ACTION_STOP_SESSION);
        stopIntent.putExtra(EXTRA_SESSION_ID, sessionId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, notificationId, stopIntent, flags);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title == null ? getString(R.string.notification_running_title) : title)
                .setContentText(getString(R.string.notification_session_running_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openAppIntent())
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setGroup(CHANNEL_ID)
                .addAction(R.drawable.ic_notification, getString(R.string.notification_action_stop), stopPendingIntent)
                .build();

        NotificationManagerCompat.from(this).notify(notificationId, notification);
    }

    private void removeSessionNotification(String sessionId) {
        Integer notificationId = sessionNotificationIds.remove(sessionId);
        if (notificationId != null) {
            NotificationManagerCompat.from(this).cancel(notificationId);
        }
    }
}
