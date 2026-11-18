package ua.atelier.day;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        TaskScheduler.createChannel(c);
        TaskScheduler.scheduleAll(c);
    }
}
