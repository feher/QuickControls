package net.feheren_fekete.android.quickcontrols;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

public class UnmuteMusicWithTimeoutReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent unmuteMusicItent = new Intent(context, QuickControlsService.class);
        unmuteMusicItent.setAction(QuickControlsService.ACTION_UNMUTE_VOLUME);
        unmuteMusicItent.putExtra(
                QuickControlsService.EXTRA_SOUND_STREAM,
                intent.getIntExtra(QuickControlsService.EXTRA_SOUND_STREAM, AudioManager.USE_DEFAULT_STREAM_TYPE));
        context.startService(unmuteMusicItent);
    }

}
