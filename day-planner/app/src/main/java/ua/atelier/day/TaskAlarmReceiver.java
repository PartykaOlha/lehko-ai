package ua.atelier.day;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TaskAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        if(intent==null)return;
        TaskScheduler.createChannel(c);
        String id=intent.getStringExtra("id");
        String title=intent.getStringExtra("title");
        if(id==null||title==null)return;
        Intent open=new Intent(c,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent click=PendingIntent.getActivity(c,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,TaskScheduler.channel())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Atelier Day · Час для справи")
            .setContentText(title)
            .setStyle(new Notification.BigTextStyle().bigText(title))
            .setContentIntent(click)
            .setAutoCancel(true)
            .build();
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)try{nm.notify(id.hashCode()&0x7fffffff,n);}catch(SecurityException ignored){}
        TaskScheduler.scheduleAll(c);
    }
}
