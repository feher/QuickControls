package net.feheren_fekete.android.quickcontrols;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.view.View;
import android.widget.RemoteViews;

public class QuickControlsService extends Service {

    public static String ACTION_TOGGLE_MUSIC_MUTED = QuickControlsService.class.getSimpleName() + ".ACTION_TOGGLE_MUSIC_MUTED";
    public static String ACTION_TOGGLE_MUSIC_MUTED_WITH_TIMEOUT = QuickControlsService.class.getSimpleName() + ".ACTION_TOGGLE_MUSIC_MUTED_WITH_TIMEOUT";
    public static String ACTION_UNMUTE_MUSIC = QuickControlsService.class.getSimpleName() + ".ACTION_UNMUTE_MUSIC";
    public static String ACTION_MUSIC_VOLUME_UP = QuickControlsService.class.getSimpleName() + ".ACTION_MUSIC_VOLUME_UP";
    public static String ACTION_MUSIC_VOLUME_DOWN = QuickControlsService.class.getSimpleName() + ".ACTION_MUSIC_VOLUME_DOWN";
    public static String ACTION_TOGGLE_KEEP_SCREEN_ON = QuickControlsService.class.getSimpleName() + ".ACTION_TOGGLE_KEEP_SCREEN_ON";

    private static final int DEFAULT_MUSIC_MUTE_TIMEOUT = 30; // seconds

    private static final int ONGOING_NOTIFICATION_ID = 123;

    private static final int REQUEST_TOGGLE_MUSIC_MUTED = 0;
    private static final int REQUEST_TOGGLE_MUSIC_MUTED_WITH_TIMEOUT = 1;
    private static final int REQUEST_MUSIC_VOLUME_UP = 2;
    private static final int REQUEST_MUSIC_VOLUME_DOWN = 3;
    private static final int REQUEST_TOGGLE_KEEP_SCREEN_ON = 4;
    private static final int REQUEST_UNMUTE_MUSIC_WITH_TIMEOUT = 5;

    private NotificationManager mNotificationManager;
    private AudioManager mAudioManager;
    private PowerManager mPowerManager;
    private AlarmManager mAlarmManager;

    @Nullable
    private PowerManager.WakeLock mWakeLock;
    @Nullable
    private PendingIntent mUnmuteMusicWithTimeoutIntent;
    private int mRemainingMusicMutedSeconds;

    private static boolean sIsMusicMuted;

    private Handler mHandler = new Handler();
    private ContentObserver mVolumeObserver = new ContentObserver(mHandler) {
        private int previousVolumeLevel;
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            int volumeLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (volumeLevel != previousVolumeLevel) {
                previousVolumeLevel = volumeLevel;
                updateNotification();
            }
        }
    };

    public QuickControlsService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, mVolumeObserver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mVolumeObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_TOGGLE_MUSIC_MUTED.equals(intent.getAction())) {
                commandToggleMusicMuted();
            } else if (ACTION_TOGGLE_MUSIC_MUTED_WITH_TIMEOUT.equals(intent.getAction())) {
                commandToggleMusicMutedWithTimeout();
            } else if (ACTION_UNMUTE_MUSIC.equals(intent.getAction())) {
                commandUnmuteMusic();
            } else if (ACTION_MUSIC_VOLUME_UP.equals(intent.getAction())) {
                commandMusicVolumeUp();
            } else if (ACTION_MUSIC_VOLUME_DOWN.equals(intent.getAction())) {
                commandMusicVolumeDown();
            } else if (ACTION_TOGGLE_KEEP_SCREEN_ON.equals(intent.getAction())) {
                commandToggleKeepScreenOn();
            } else {
                startUp();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startUp() {
        Notification notification = createNotification();
        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this)
                .setCustomContentView(createSmallRemoteViews())
                .setSmallIcon(R.drawable.ic_small_icon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build();
    }

    private RemoteViews createSmallRemoteViews() {
        Intent intent;

        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.quick_controls_small);

        intent = new Intent(this, QuickControlsService.class);
        intent.setAction(ACTION_TOGGLE_MUSIC_MUTED);
        remoteViews.setOnClickPendingIntent(
                R.id.toggle_music_muted_button,
                PendingIntent.getService(this, REQUEST_TOGGLE_MUSIC_MUTED, intent, 0));
        remoteViews.setImageViewResource(
                R.id.toggle_music_muted_button,
                isMusicMuted() ? R.drawable.ic_volume_off : R.drawable.ic_volume_on);

        intent = new Intent(this, QuickControlsService.class);
        intent.setAction(ACTION_TOGGLE_MUSIC_MUTED_WITH_TIMEOUT);
        remoteViews.setOnClickPendingIntent(
                R.id.toggle_music_muted_with_timeout_button,
                PendingIntent.getService(this, REQUEST_TOGGLE_MUSIC_MUTED_WITH_TIMEOUT, intent, 0));
        remoteViews.setImageViewResource(
                R.id.toggle_music_muted_with_timeout_secondary_icon,
                (mRemainingMusicMutedSeconds == 0) ? R.drawable.ic_timer_off : R.drawable.ic_timer_on);
        remoteViews.setImageViewResource(
                R.id.toggle_music_muted_with_timeout_primary_icon,
                (mRemainingMusicMutedSeconds == 0) ? R.drawable.ic_volume_timer_off : R.drawable.ic_volume_timer_on);

        intent = new Intent(this, QuickControlsService.class);
        intent.setAction(ACTION_MUSIC_VOLUME_DOWN);
        remoteViews.setOnClickPendingIntent(
                R.id.music_volume_down_button,
                PendingIntent.getService(this, REQUEST_MUSIC_VOLUME_DOWN, intent, 0));

        intent = new Intent(this, QuickControlsService.class);
        intent.setAction(ACTION_MUSIC_VOLUME_UP);
        remoteViews.setOnClickPendingIntent(
                R.id.music_volume_up_button,
                PendingIntent.getService(this, REQUEST_MUSIC_VOLUME_UP, intent, 0));

        intent = new Intent(this, QuickControlsService.class);
        intent.setAction(ACTION_TOGGLE_KEEP_SCREEN_ON);
        remoteViews.setOnClickPendingIntent(
                R.id.toggle_keep_screen_on_button,
                PendingIntent.getService(this, REQUEST_TOGGLE_KEEP_SCREEN_ON, intent, 0));
        remoteViews.setImageViewResource(
                R.id.toggle_keep_screen_on_button,
                isScreenKeptOn() ? R.drawable.ic_screen_on : R.drawable.ic_screen_off);

        if (mRemainingMusicMutedSeconds == 0) {
            remoteViews.setViewVisibility(R.id.music_volume_level_bar, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.music_muted_timeout_bar, View.INVISIBLE);
            remoteViews.setProgressBar(R.id.music_volume_level_bar, 100, getMusicVolumeLevel(), false);
            remoteViews.setTextViewText(R.id.music_volume_level_text, getMusicVolumeLevel() + "%");
            remoteViews.setTextColor(R.id.music_volume_level_text, ResourcesCompat.getColor(getResources(), R.color.notification_icon, null));
        } else {
            remoteViews.setViewVisibility(R.id.music_volume_level_bar, View.INVISIBLE);
            remoteViews.setViewVisibility(R.id.music_muted_timeout_bar, View.VISIBLE);
            int timeoutBarValue = Math.round(100 - mRemainingMusicMutedSeconds * 100.0f / DEFAULT_MUSIC_MUTE_TIMEOUT);
            remoteViews.setProgressBar(R.id.music_muted_timeout_bar, 100, timeoutBarValue, false);
            remoteViews.setTextViewText(R.id.music_volume_level_text, mRemainingMusicMutedSeconds + " sec");
            remoteViews.setTextColor(R.id.music_volume_level_text, ResourcesCompat.getColor(getResources(), R.color.notification_mute_timeout_bar, null));
        }

        return remoteViews;
    }

    private void updateNotification() {
        mNotificationManager.notify(ONGOING_NOTIFICATION_ID, createNotification());
    }

    private void commandToggleMusicMuted() {
        if (isMusicMuted()) {
            unmuteMusic();
        } else {
            muteMusic();
        }
        updateNotification();
    }

    private Runnable mPeriodicUpdateNotificationRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRemainingMusicMutedSeconds > 0) {
                --mRemainingMusicMutedSeconds;
                updateNotification();
                mHandler.postDelayed(this, 1000);
            }
        }
    };

    private void commandToggleMusicMutedWithTimeout() {
        if (mUnmuteMusicWithTimeoutIntent != null) {
            unmuteMusic();
        } else {
            muteMusic();

            mRemainingMusicMutedSeconds = DEFAULT_MUSIC_MUTE_TIMEOUT;

            Intent intent = new Intent(this, UnmuteMusicWithTimeoutReceiver.class);
            mUnmuteMusicWithTimeoutIntent = PendingIntent.getBroadcast(this, REQUEST_UNMUTE_MUSIC_WITH_TIMEOUT, intent, 0);
            mAlarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + mRemainingMusicMutedSeconds * 1000,
                    mUnmuteMusicWithTimeoutIntent);

            mHandler.postDelayed(mPeriodicUpdateNotificationRunnable, 1000);
        }
        updateNotification();
    }

    private void commandMusicVolumeUp() {
        if (isMusicMuted()) {
            unmuteMusic();
        }
        musicVolumeUp();
        updateNotification();
    }

    private void commandMusicVolumeDown() {
        if (isMusicMuted()) {
            unmuteMusic();
        }
        musicVolumeDown();
        updateNotification();
    }

    private void commandToggleKeepScreenOn() {
        if (mWakeLock == null) {
            mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, QuickControlsService.class.getSimpleName());
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        } else {
            mWakeLock.acquire();
        }
        updateNotification();
    }

    private void commandUnmuteMusic() {
        unmuteMusic();
        updateNotification();
    }

    private void cancelUnmuteMusicWithTimeout() {
        if (mUnmuteMusicWithTimeoutIntent != null) {
            mAlarmManager.cancel(mUnmuteMusicWithTimeoutIntent);
            mHandler.removeCallbacks(mPeriodicUpdateNotificationRunnable);
            mUnmuteMusicWithTimeoutIntent = null;
            mRemainingMusicMutedSeconds = 0;
        }
    }

    private void unmuteMusic() {
        cancelUnmuteMusicWithTimeout();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        } else {
            mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            sIsMusicMuted = false;
        }
    }

    private void muteMusic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        } else {
            mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            sIsMusicMuted = true;
        }
    }

    private boolean isMusicMuted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC);
        } else {
            return sIsMusicMuted;
        }
    }

    private void musicVolumeUp() {
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
    }

    private void musicVolumeDown() {
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
    }

    private int getMusicVolumeLevel() {
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        return Math.round(volume * 100.0f / maxVolume);
    }

    private boolean isScreenKeptOn() {
        return mWakeLock != null && mWakeLock.isHeld();
    }

}
