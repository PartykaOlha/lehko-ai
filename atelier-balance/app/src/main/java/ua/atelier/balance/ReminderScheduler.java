package ua.atelier.balance;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

public final class ReminderScheduler {
    private static final String PREFS = "native_reminders";
    private static final String JSON_KEY = "settings";
    private static final String ACTIVE_KEY = "user_opted_in";
    private static final String CHANNEL = "atelier_balance_daily";
    private static final String[] IDS = {"spark", "breakfast", "lunch", "move", "dinner", "shopping"};
    private static final String[] TITLES = {"Ранкове натхнення", "Сніданок", "Обід", "Рух або відновлення", "Вечеря", "Покупки"};
    private static final String[] TEXTS = {
        "Достатньо одного маленького кроку. Відкрий свій план.",
        "Нагадування про сніданок. Переглянь меню.",
        "Час пообідати й зробити паузу.",
        "Орбітрек, прогулянка або відпочинок — обирай свій ритм.",
        "Вечеря без поспіху.",
        "Перевір список покупок і домашні запаси."
    };

    private ReminderScheduler() {}

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                "Atelier Balance — нагадування", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Доброзичливі нагадування про меню, рух і покупки");
            NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    static void storeAndSchedule(Context context, String json, boolean enable) {
        if (json == null || json.length() > 30000) return;
        try { new JSONArray(json); } catch (Exception e) { return; }
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = p.edit().putString(JSON_KEY, json);
        if (enable) ed.putBoolean(ACTIVE_KEY, true);
        ed.apply();
        if (enable || p.getBoolean(ACTIVE_KEY, false)) scheduleAll(context);
    }

    static void scheduleAll(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean(ACTIVE_KEY, false)) return;
        JSONArray json;
        try { json = new JSONArray(p.getString(JSON_KEY, "[]")); } catch (Exception e) { return; }
        for (int i = 0; i < IDS.length; i++) {
            JSONObject entry = null;
            for (int j = 0; j < json.length(); j++) {
                JSONObject x = json.optJSONObject(j);
                if (x != null && IDS[i].equals(x.optString("id"))) {entry = x; break;}
            }
            scheduleOne(context, i, entry);
        }
    }

    private static void scheduleOne(Context context, int i, JSONObject entry) {
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction("ua.atelier.balance.REMINDER." + IDS[i]);
        intent.putExtra("index", i);
        PendingIntent pi = PendingIntent.getBroadcast(context, 100+i, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (entry == null || !entry.optBoolean("enabled",false)) return;
        String t = entry.optString("time","");
        if (!t.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) return;
        String[] parts = t.split(":");
        Calendar now = Calendar.getInstance();
        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY,Integer.parseInt(parts[0]));
        when.set(Calendar.MINUTE,Integer.parseInt(parts[1]));
        when.set(Calendar.SECOND,0);
        when.set(Calendar.MILLISECOND,0);
        if ("shopping".equals(IDS[i])) {
            int weekday = entry.optInt("weekday",6);
            if (weekday < 0 || weekday > 6) weekday = 6;
            int calendarDay = weekday + 1;
            while (when.get(Calendar.DAY_OF_WEEK) != calendarDay) when.add(Calendar.DAY_OF_MONTH,1);
            if (!when.after(now)) when.add(Calendar.DAY_OF_MONTH,7);
        } else if (!when.after(now)) when.add(Calendar.DAY_OF_MONTH,1);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pi);
    }

    static void onFired(Context context, int index) {
        if (index < 0 || index >= IDS.length) return;
        if (!context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(ACTIVE_KEY,false)) return;
        scheduleAll(context);
        createChannel(context);
        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context,200+index,open,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Atelier Balance · " + TITLES[index])
            .setContentText(TEXTS[index])
            .setStyle(new Notification.BigTextStyle().bigText(TEXTS[index]))
            .setContentIntent(pi).setAutoCancel(true).build();
        if (nm != null) try { nm.notify(300+index,n); }
        catch (SecurityException ignored) {}
    }
}
