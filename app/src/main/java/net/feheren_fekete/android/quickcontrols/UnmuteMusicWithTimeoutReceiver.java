package net.feheren_fekete.android.quickcontrols;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UnmuteMusicWithTimeoutReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent unmuteMusicItent = new Intent(context, QuickControlsService.class);
        unmuteMusicItent.setAction(QuickControlsService.ACTION_UNMUTE_MUSIC);
        context.startService(unmuteMusicItent);
    }

}
