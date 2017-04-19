package net.feheren_fekete.android.quickcontrols;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.DrawableRes;
import android.support.annotation.IdRes;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.util.ArrayMap;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Map;

import hugo.weaving.DebugLog;

public class QuickControlsService extends Service {

    public static String ACTION_TOGGLE_VOLUME = QuickControlsService.class.getSimpleName() + ".ACTION_TOGGLE_VOLUME";
    public static String ACTION_TOGGLE_VOLUME_WITH_TIMEOUT = QuickControlsService.class.getSimpleName() + ".ACTION_TOGGLE_VOLUME_WITH_TIMEOUT";
    public static String ACTION_UNMUTE_VOLUME = QuickControlsService.class.getSimpleName() + ".ACTION_UNMUTE_VOLUME";
    public static String ACTION_VOLUME_UP = QuickControlsService.class.getSimpleName() + ".ACTION_VOLUME_UP";
    public static String ACTION_VOLUME_DOWN = QuickControlsService.class.getSimpleName() + ".ACTION_VOLUME_DOWN";

    public static String EXTRA_SOUND_STREAM = QuickControlsService.class.getSimpleName() + ".EXTRA_SOUND_STREAM";

    private static final int DEFAULT_VOLUME_MUTE_TIMEOUT = 30; // seconds

    private static final int ONGOING_NOTIFICATION_ID = 111;

    private static final int REQUEST_CODE_OFFSET_SMALL = 0;
    private static final int REQUEST_CODE_OFFSET_BIG_MUSIC = 1000;
    private static final int REQUEST_CODE_OFFSET_BIG_RING = 2000;
    private static final int REQUEST_CODE_OFFSET_BIG_NOTIFICATIONS = 3000;

    private static final int REQUEST_TOGGLE_VOLUME_MUTED = 0;
    private static final int REQUEST_TOGGLE_VOLUME_MUTED_WITH_TIMEOUT = 1;
    private static final int REQUEST_VOLUME_UP = 2;
    private static final int REQUEST_VOLUME_DOWN = 3;
    private static final int REQUEST_UNMUTE_VOLUME_WITH_TIMEOUT = 4;

    private NotificationManager mNotificationManager;
    private AudioManager mAudioManager;
    private AlarmManager mAlarmManager;

    private static class VolumeInfo {
        public int stream;
        public int requestCodeOffset;
        public @IdRes int volumeUpId;
        public @IdRes int volumeDownId;
        public @IdRes int muteVolumeId;
        public @DrawableRes int volumeOnIconId;
        public @DrawableRes int volumeOffIconId;
        public @IdRes int muteVolumeWithTimeoutId;
        public @IdRes int muteVolumeWithTimeoutPrimaryIconId;
        public @IdRes int muteVolumeWithTimeoutSecondaryIconId;
        public @IdRes int volumeLevelBarId;
        public @IdRes int volumeMutedTimeoutBarId;
        public @IdRes int volumeLevelTextId;
        public PendingIntent unmuteVolumeWithTimeoutIntent;
        public int remainingVolumeMutedSeconds;
        public Runnable periodicNotificationUpdate;
        public VolumeInfo(int stream, int requestCodeOffset,
                          @IdRes int volumeUpId, @IdRes int volumeDownId,
                          @IdRes int muteVolumeId, @DrawableRes int volumeOnIconId, @DrawableRes int volumeOffIconId,
                          @IdRes int muteVolumeWithTimeoutId, @IdRes int muteVolumeWithTimeoutPrimaryIconId, @IdRes int muteVolumeWithTimeoutSecondaryIconId,
                          @IdRes int volumeLevelBarId, @IdRes int volumeMutedTimeoutBarId, @IdRes int volumeLevelTextId) {
            this.stream = stream;
            this.requestCodeOffset = requestCodeOffset;
            this.volumeUpId = volumeUpId;
            this.volumeDownId = volumeDownId;
            this.muteVolumeId = muteVolumeId;
            this.volumeOnIconId = volumeOnIconId;
            this.volumeOffIconId = volumeOffIconId;
            this.muteVolumeWithTimeoutId = muteVolumeWithTimeoutId;
            this.muteVolumeWithTimeoutPrimaryIconId = muteVolumeWithTimeoutPrimaryIconId;
            this.muteVolumeWithTimeoutSecondaryIconId = muteVolumeWithTimeoutSecondaryIconId;
            this.volumeLevelBarId = volumeLevelBarId;
            this.volumeMutedTimeoutBarId = volumeMutedTimeoutBarId;
            this.volumeLevelTextId = volumeLevelTextId;
        }
    }

    private Map<Integer, VolumeInfo> mVolumeInfos = new ArrayMap<>();

    private static Map<Integer, Boolean> sIsVolumeMuted = new ArrayMap<>();

    {
        mVolumeInfos.put(
                AudioManager.STREAM_MUSIC,
                new VolumeInfo(
                        AudioManager.STREAM_MUSIC,
                        REQUEST_CODE_OFFSET_BIG_MUSIC,
                        R.id.music_volume_up_button,
                        R.id.music_volume_down_button,
                        R.id.music_volume_icon,
                        R.drawable.ic_music_volume_on,
                        R.drawable.ic_music_volume_off,
                        R.id.music_volume_mute_with_timeout_button,
                        R.id.music_volume_mute_with_timeout_primary_icon,
                        R.id.music_volume_mute_with_timeout_secondary_icon,
                        R.id.music_volume_level_bar,
                        R.id.music_volume_muted_timeout_bar,
                        R.id.music_volume_level_text));
        mVolumeInfos.put(
                AudioManager.STREAM_RING,
                new VolumeInfo(
                        AudioManager.STREAM_RING,
                        REQUEST_CODE_OFFSET_BIG_RING,
                        R.id.ring_volume_up_button,
                        R.id.ring_volume_down_button,
                        R.id.ring_volume_icon,
                        R.drawable.ic_ring_volume_on,
                        R.drawable.ic_ring_volume_off,
                        R.id.ring_volume_mute_with_timeout_button,
                        R.id.ring_volume_mute_with_timeout_primary_icon,
                        R.id.ring_volume_mute_with_timeout_secondary_icon,
                        R.id.ring_volume_level_bar,
                        R.id.ring_volume_muted_timeout_bar,
                        R.id.ring_volume_level_text));
        mVolumeInfos.put(
                AudioManager.STREAM_NOTIFICATION,
                new VolumeInfo(
                        AudioManager.STREAM_NOTIFICATION,
                        REQUEST_CODE_OFFSET_BIG_NOTIFICATIONS,
                        R.id.notifications_volume_up_button,
                        R.id.notifications_volume_down_button,
                        R.id.notifications_volume_icon,
                        R.drawable.ic_notifications_volume_on,
                        R.drawable.ic_notifications_volume_off,
                        R.id.notifications_volume_mute_with_timeout_button,
                        R.id.notifications_volume_mute_with_timeout_primary_icon,
                        R.id.notifications_volume_mute_with_timeout_secondary_icon,
                        R.id.notifications_volume_level_bar,
                        R.id.notifications_volume_muted_timeout_bar,
                        R.id.notifications_volume_level_text));
    }

    static {
        sIsVolumeMuted.put(AudioManager.STREAM_MUSIC, false);
        sIsVolumeMuted.put(AudioManager.STREAM_RING, false);
        sIsVolumeMuted.put(AudioManager.STREAM_NOTIFICATION, false);
    }

    private Handler mHandler = new Handler();
    private ContentObserver mVolumeObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateNotification();
        }
    };

    public QuickControlsService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
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
            if (ACTION_TOGGLE_VOLUME.equals(intent.getAction())) {
                commandToggleVolume(intent.getIntExtra(EXTRA_SOUND_STREAM, AudioManager.STREAM_MUSIC));
            } else if (ACTION_TOGGLE_VOLUME_WITH_TIMEOUT.equals(intent.getAction())) {
                commandToggleVolumeWithTimeout(intent.getIntExtra(EXTRA_SOUND_STREAM, AudioManager.STREAM_MUSIC));
            } else if (ACTION_UNMUTE_VOLUME.equals(intent.getAction())) {
                commandUnmuteVolume(intent.getIntExtra(EXTRA_SOUND_STREAM, AudioManager.STREAM_MUSIC));
            } else if (ACTION_VOLUME_UP.equals(intent.getAction())) {
                commandVolumeUp(intent.getIntExtra(EXTRA_SOUND_STREAM, AudioManager.STREAM_MUSIC));
            } else if (ACTION_VOLUME_DOWN.equals(intent.getAction())) {
                commandVolumeDown(intent.getIntExtra(EXTRA_SOUND_STREAM, AudioManager.STREAM_MUSIC));
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
                .setCustomBigContentView(createBigRemoteViews())
                .setSmallIcon(R.drawable.ic_small_icon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build();
    }

    private RemoteViews createSmallRemoteViews() {
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.quick_controls_small_4);
        initSmallRemoteViews(remoteViews);
        return remoteViews;
    }

    private void initSmallRemoteViews(RemoteViews remoteViews) {
        initToggleVolumeButton(
                remoteViews,
                REQUEST_CODE_OFFSET_SMALL + 1,
                AudioManager.STREAM_MUSIC,
                R.id.music_volume_icon, R.drawable.ic_music_volume_on, R.drawable.ic_music_volume_off);

        initToggleVolumeButton(
                remoteViews,
                REQUEST_CODE_OFFSET_SMALL + 2,
                AudioManager.STREAM_RING,
                R.id.ring_volume_icon, R.drawable.ic_ring_volume_on, R.drawable.ic_ring_volume_off);

        initToggleVolumeButton(
                remoteViews,
                REQUEST_CODE_OFFSET_SMALL + 3,
                AudioManager.STREAM_NOTIFICATION,
                R.id.notifications_volume_icon, R.drawable.ic_notifications_volume_on, R.drawable.ic_notifications_volume_off);

        final VolumeInfo volumeInfo = mVolumeInfos.get(AudioManager.STREAM_MUSIC);

        initVolumeDownButton(remoteViews, REQUEST_CODE_OFFSET_SMALL + 4, volumeInfo.stream, R.id.volume_down_button);

        initVolumeUpButton(remoteViews, REQUEST_CODE_OFFSET_SMALL + 5, volumeInfo.stream, R.id.volume_up_button);

        initToggleVolumeWithTimeoutButton(
                remoteViews,
                REQUEST_CODE_OFFSET_SMALL + 6,
                volumeInfo.stream,
                volumeInfo.remainingVolumeMutedSeconds,
                R.id.volume_muted_with_timeout_button,
                R.id.volume_muted_with_timeout_primary_icon,
                R.id.volume_muted_with_timeout_secondary_icon);

        initProgressBars(
                remoteViews, volumeInfo.stream, volumeInfo.remainingVolumeMutedSeconds,
                R.id.volume_level_bar, R.id.volume_muted_timeout_bar, R.id.volume_level_text, "s");
    }

    private void initBigRemoteViewsPart(RemoteViews remoteViews, int stream) {
        final VolumeInfo volumeInfo = mVolumeInfos.get(stream);

        initToggleVolumeButton(
                remoteViews, volumeInfo.requestCodeOffset + REQUEST_TOGGLE_VOLUME_MUTED, stream,
                volumeInfo.muteVolumeId, volumeInfo.volumeOnIconId, volumeInfo.volumeOffIconId);

        initVolumeDownButton(
                remoteViews, volumeInfo.requestCodeOffset + REQUEST_VOLUME_DOWN,
                stream, volumeInfo.volumeDownId);

        initVolumeUpButton(
                remoteViews, volumeInfo.requestCodeOffset + REQUEST_VOLUME_UP,
                stream, volumeInfo.volumeUpId);

        initToggleVolumeWithTimeoutButton(
                remoteViews,
                volumeInfo.requestCodeOffset + REQUEST_TOGGLE_VOLUME_MUTED_WITH_TIMEOUT,
                volumeInfo.stream,
                volumeInfo.remainingVolumeMutedSeconds,
                volumeInfo.muteVolumeWithTimeoutId,
                volumeInfo.muteVolumeWithTimeoutPrimaryIconId,
                volumeInfo.muteVolumeWithTimeoutSecondaryIconId);

        initProgressBars(
                remoteViews, volumeInfo.stream, volumeInfo.remainingVolumeMutedSeconds,
                volumeInfo.volumeLevelBarId, volumeInfo.volumeMutedTimeoutBarId, volumeInfo.volumeLevelTextId, "s");
    }

    private void initToggleVolumeButton(RemoteViews remoteViews, int requestCode, int stream,
                                        @IdRes int viewId, @DrawableRes int iconOnId, @DrawableRes int iconOffId) {
        Intent intent = new Intent(this, QuickControlsService.class);
        intent.setAction(ACTION_TOGGLE_VOLUME);
        intent.putExtra(EXTRA_SOUND_STREAM, stream);
        remoteViews.setOnClickPendingIntent(
                viewId, PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT));
        remoteViews.setImageViewResource(
                viewId, isVolumeMuted(stream) ? iconOffId : iconOnId);
    }

    private void initVolumeUpButton(RemoteViews remoteViews, int requestCode, int stream, @IdRes int viewId) {
        Intent intent = new Intent(this, QuickControlsService.class);
        intent.setAction(ACTION_VOLUME_UP);
        intent.putExtra(EXTRA_SOUND_STREAM, stream);
        remoteViews.setOnClickPendingIntent(
                viewId, PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT));
    }

    private void initVolumeDownButton(RemoteViews remoteViews, int requestCode, int stream, @IdRes int viewId) {
        Intent intent = new Intent(this, QuickControlsService.class);
        intent.setAction(ACTION_VOLUME_DOWN);
        intent.putExtra(EXTRA_SOUND_STREAM, stream);
        remoteViews.setOnClickPendingIntent(
                viewId, PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT));
    }

    private void initToggleVolumeWithTimeoutButton(RemoteViews remoteViews, int requestCode, int stream,
                                                   int remainingVolumeMutedSeconds,
                                                   @IdRes int viewId, @IdRes int primaryIconId, @IdRes int secondaryIconId) {
        Intent intent = new Intent(this, QuickControlsService.class);
        intent.setAction(ACTION_TOGGLE_VOLUME_WITH_TIMEOUT);
        intent.putExtra(EXTRA_SOUND_STREAM, stream);
        remoteViews.setOnClickPendingIntent(
                viewId,
                PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT));
        remoteViews.setImageViewResource(
                secondaryIconId,
                (remainingVolumeMutedSeconds == 0) ? R.drawable.ic_timer_off : R.drawable.ic_timer_on);
        remoteViews.setImageViewResource(
                primaryIconId,
                (remainingVolumeMutedSeconds == 0) ? R.drawable.ic_volume_timer_off : R.drawable.ic_volume_timer_on);
    }

    private void initProgressBars(RemoteViews remoteViews, int stream, int remainingVolumeMutedSeconds,
                                  @IdRes int volumeLevelBarId, @IdRes int volumeMutedTimeoutBarId, @IdRes int volumeLevelTextId,
                                  String secondsPostfix) {
        if (remainingVolumeMutedSeconds == 0) {
            remoteViews.setViewVisibility(volumeLevelBarId, View.VISIBLE);
            remoteViews.setProgressBar(volumeLevelBarId, 100, getVolumeLevel(stream), false);
            remoteViews.setTextViewText(volumeLevelTextId, getVolumeLevel(stream) + "%");
            remoteViews.setTextColor(volumeLevelTextId, ResourcesCompat.getColor(getResources(), R.color.notification_icon, null));
            remoteViews.setViewVisibility(volumeMutedTimeoutBarId, View.INVISIBLE);
        } else {
            remoteViews.setViewVisibility(volumeLevelBarId, View.INVISIBLE);
            remoteViews.setViewVisibility(volumeMutedTimeoutBarId, View.VISIBLE);
            int timeoutBarValue = Math.round(100 - remainingVolumeMutedSeconds * 100.0f / DEFAULT_VOLUME_MUTE_TIMEOUT);
            remoteViews.setProgressBar(volumeMutedTimeoutBarId, 100, timeoutBarValue, false);
            remoteViews.setViewVisibility(volumeLevelTextId, View.VISIBLE);
            remoteViews.setTextViewText(volumeLevelTextId, remainingVolumeMutedSeconds + secondsPostfix);
            remoteViews.setTextColor(volumeLevelTextId, ResourcesCompat.getColor(getResources(), R.color.notification_mute_timeout_bar, null));
        }
    }

    private RemoteViews createBigRemoteViews() {
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.quick_controls_large);
        initBigRemoteViewsPart(remoteViews, AudioManager.STREAM_MUSIC);
        initBigRemoteViewsPart(remoteViews, AudioManager.STREAM_RING);
        initBigRemoteViewsPart(remoteViews, AudioManager.STREAM_NOTIFICATION);
        return remoteViews;
    }

    private void updateNotification() {
        mNotificationManager.notify(ONGOING_NOTIFICATION_ID, createNotification());
    }

    @DebugLog
    private void commandToggleVolume(int stream) {
        if (isVolumeMuted(stream)) {
            unmuteVolume(stream);
        } else {
            muteVolume(stream);
        }
        updateNotification();
    }

    @DebugLog
    private void commandToggleVolumeWithTimeout(int stream) {
        if (mVolumeInfos.get(stream).unmuteVolumeWithTimeoutIntent != null) {
            unmuteVolume(stream);
        } else {
            muteVolume(stream);

            mVolumeInfos.get(stream).remainingVolumeMutedSeconds = DEFAULT_VOLUME_MUTE_TIMEOUT;

            Intent unmuteIntent = new Intent(this, UnmuteMusicWithTimeoutReceiver.class);
            unmuteIntent.putExtra(QuickControlsService.EXTRA_SOUND_STREAM, stream);
            mVolumeInfos.get(stream).unmuteVolumeWithTimeoutIntent = PendingIntent.getBroadcast(
                    this, REQUEST_UNMUTE_VOLUME_WITH_TIMEOUT, unmuteIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            mAlarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + mVolumeInfos.get(stream).remainingVolumeMutedSeconds * 1000,
                    mVolumeInfos.get(stream).unmuteVolumeWithTimeoutIntent);

            Runnable periodicNotificationUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    int remaining = mVolumeInfos.get(stream).remainingVolumeMutedSeconds;
                    if (remaining > 0) {
                        mVolumeInfos.get(stream).remainingVolumeMutedSeconds = remaining - 1;
                        updateNotification();
                        mHandler.postDelayed(this, 1000);
                    }
                }
            };
            mVolumeInfos.get(stream).periodicNotificationUpdate = periodicNotificationUpdateRunnable;
            mHandler.postDelayed(periodicNotificationUpdateRunnable, 1000);
        }
        updateNotification();
    }

    @DebugLog
    private void commandVolumeUp(int stream) {
        if (isVolumeMuted(stream)) {
            unmuteVolume(stream);
        }
        volumeUp(stream);
        updateNotification();
    }

    @DebugLog
    private void commandVolumeDown(int stream) {
        if (isVolumeMuted(stream)) {
            unmuteVolume(stream);
        }
        volumeDown(stream);
        updateNotification();
    }

    @DebugLog
    private void commandUnmuteVolume(int stream) {
        unmuteVolume(stream);
        updateNotification();
    }

    private void cancelUnmuteVolumeWithTimeout(int stream) {
        if (mVolumeInfos.get(stream).unmuteVolumeWithTimeoutIntent != null) {
            mAlarmManager.cancel(mVolumeInfos.get(stream).unmuteVolumeWithTimeoutIntent);
            mHandler.removeCallbacks(mVolumeInfos.get(stream).periodicNotificationUpdate);
            mVolumeInfos.get(stream).unmuteVolumeWithTimeoutIntent = null;
            mVolumeInfos.get(stream).remainingVolumeMutedSeconds = 0;
        }
    }

    private void unmuteVolume(int stream) {
        cancelUnmuteVolumeWithTimeout(stream);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0);
        } else {
            mAudioManager.setStreamMute(stream, false);
        }
    }

    private void muteVolume(int stream) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (stream == AudioManager.USE_DEFAULT_STREAM_TYPE) {
                mAudioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0);
            } else {
                mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
            }
        } else {
            mAudioManager.setStreamMute(stream, true);
        }
    }

    @DebugLog
    private boolean isVolumeMuted(int stream) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return mAudioManager.isStreamMute(stream) || (getVolumeLevel(stream) == 0);
        } else {
            return (getVolumeLevel(stream) == 0);
        }
    }

    private void volumeUp(int stream) {
        mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0);
    }

    private void volumeDown(int stream) {
        mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0);
    }

    private int getVolumeLevel(int stream) {
        int maxVolume = mAudioManager.getStreamMaxVolume(stream);
        int volume = mAudioManager.getStreamVolume(stream);
        return Math.round(volume * 100.0f / maxVolume);
    }

}
