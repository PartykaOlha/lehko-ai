package ua.atelier.day;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class TaskScheduler {
    private static final String PREF="atelier_day_scheduled";
    private static final String DATA="tasks";
    private static final String CHANNEL="atelier_day_reminders";
    private TaskScheduler() {}

    static void createChannel(Context c) {
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Нагадування Atelier Day",NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Сповіщення про заплановані справи та ритуали");
            NotificationManager mgr=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            if(mgr!=null)mgr.createNotificationChannel(channel);
        }
    }
    static String channel(){return CHANNEL;}
    static void saveAndSchedule(Context c,String json) {
        if(json==null || json.length()>300_000)return;
        try{JSONObject obj=new JSONObject(json);if(!(obj.opt("tasks") instanceof JSONArray))return;}
        catch(Exception e){return;}
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String previous=p.getString(DATA,"{}");
        cancelAll(c,previous);
        p.edit().putString(DATA,json).apply();
        scheduleAll(c);
    }
    private static PendingIntent intentFor(Context c,String id,String title) {
        Intent i=new Intent(c,TaskAlarmReceiver.class);
        i.setAction("ua.atelier.day.REMIND").setData(android.net.Uri.parse("atelierday://task/"+id));
        i.putExtra("id",id);i.putExtra("title",title);
        return PendingIntent.getBroadcast(c, id.hashCode()&0x7fffffff,i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void cancelAll(Context c,String json) {
        AlarmManager alarm=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(alarm==null)return;
        try{JSONArray tasks=new JSONObject(json).optJSONArray("tasks");if(tasks==null)return;
            for(int j=0;j<tasks.length();j++){JSONObject t=tasks.optJSONObject(j);if(t!=null){String id=t.optString("id","");if(!id.isEmpty())alarm.cancel(intentFor(c,id,t.optString("title","Справи")));}}
        }catch(Exception ignored){}
    }
    static void scheduleAll(Context c) {
        AlarmManager alarm=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(alarm==null)return;
        JSONObject settings;try{settings=new JSONObject(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(DATA,"{}"));}
        catch(Exception e){return;}
        if(!settings.optBoolean("enabled",false))return;
        JSONArray tasks=settings.optJSONArray("tasks");if(tasks==null)return;
        for(int j=0;j<tasks.length()&&j<300;j++){
            JSONObject t=tasks.optJSONObject(j);if(t==null)continue;
            String id=t.optString("id","");if(id.length()<1||id.length()>128)continue;
            long timestamp=nextDue(t);
            if(timestamp<=0)continue;
            PendingIntent pi=intentFor(c,id,t.optString("title","Справи"));
            try{alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,timestamp,pi);}
            catch(SecurityException ignored){}
        }
    }
    private static long nextDue(JSONObject t) {
        try{
            String rep=t.optString("repeat","none");
            int mins=t.optInt("reminder",-1);
            if(mins<0||mins>1440)return -1;
            LocalDate start=LocalDate.parse(t.optString("date"));
            LocalTime time=LocalTime.parse(t.optString("time"));
            LocalDate day=LocalDate.now().minusDays(1);
            long now=System.currentTimeMillis();
            for(int i=0;i<370;i++) {
                LocalDate candidate=day.plusDays(i);
                if(candidate.isBefore(start))continue;
                boolean matches=rep.equals("daily") || (rep.equals("weekdays")&&candidate.getDayOfWeek().getValue()<=5) ||
                    (rep.equals("weekly") && candidate.getDayOfWeek()==start.getDayOfWeek()) ||
                    (rep.equals("none") && candidate.equals(start));
                if(!matches)continue;
                JSONArray completed=t.optJSONArray("doneDates");
                if(completed!=null){boolean alreadyDone=false;for(int k=0;k<completed.length();k++){if(candidate.toString().equals(completed.optString(k))){alreadyDone=true;break;}}if(alreadyDone)continue;}
                ZonedDateTime moment=ZonedDateTime.of(candidate,time,ZoneId.systemDefault()).minusMinutes(mins);
                long millis=moment.toInstant().toEpochMilli();
                if(millis>now+500)return millis;
            }
        }catch(Exception ignored){}
        return -1;
    }
}
