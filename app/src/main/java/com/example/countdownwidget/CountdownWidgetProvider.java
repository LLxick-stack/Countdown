package com.example.countdownwidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.widget.RemoteViews;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class CountdownWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_UPDATE = "com.example.countdownwidget.UPDATE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        scheduleNextUpdate(context);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        scheduleNextUpdate(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        cancelUpdates(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        // Удаляем данные удаленных виджетов
        for (int appWidgetId : appWidgetIds) {
            SharedPreferences prefs = context.getSharedPreferences("widget_" + appWidgetId, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
            
            // Удаляем файл фона
            File bgFile = new File(context.getFilesDir(), "widget_bg_" + appWidgetId + ".png");
            if (bgFile.exists()) {
                bgFile.delete();
            }
        }
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences("widget_" + appWidgetId, Context.MODE_PRIVATE);
        long eventDate = prefs.getLong("eventDate", 0);
        String eventName = prefs.getString("eventName", "Настройте виджет");
        boolean hasCustomBg = prefs.getBoolean("hasCustomBg", false);
        
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.countdown_widget);
        
        // Устанавливаем фон
        if (hasCustomBg) {
            try {
                File file = new File(context.getFilesDir(), "widget_bg_" + appWidgetId + ".png");
                if (file.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_background, bitmap);
                    } else {
                        views.setImageViewResource(R.id.widget_background, R.drawable.widget_background);
                    }
                } else {
                    views.setImageViewResource(R.id.widget_background, R.drawable.widget_background);
                }
            } catch (Exception e) {
                e.printStackTrace();
                views.setImageViewResource(R.id.widget_background, R.drawable.widget_background);
            }
        } else {
            // Если нет кастомного фона, используем дефолтный
            views.setImageViewResource(R.id.widget_background, R.drawable.widget_background);
        }
        
        // Устанавливаем название события
        views.setTextViewText(R.id.event_name, eventName);
        
        // Если дата не установлена, показываем подсказку
        if (eventDate == 0) {
            views.setTextViewText(R.id.countdown_days, "");
            views.setTextViewText(R.id.days_label, "");
            views.setTextViewText(R.id.time_remaining, "Нажмите для настройки");
        } else {
            long currentTime = System.currentTimeMillis();
            long remainingTime = eventDate - currentTime;
        
        // Обновляем отсчет времени
        if (remainingTime > 0) {
            long days = TimeUnit.MILLISECONDS.toDays(remainingTime);
            long hours = TimeUnit.MILLISECONDS.toHours(remainingTime) % 24;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60;
            
            if (days > 0) {
                views.setTextViewText(R.id.countdown_days, String.valueOf(days));
                String daysLabel = "дней";
                if (days == 1) {
                    daysLabel = "день";
                } else if (days >= 2 && days <= 4) {
                    daysLabel = "дня";
                }
                views.setTextViewText(R.id.days_label, daysLabel);
                views.setTextViewText(R.id.time_remaining, String.format("%02d:%02d", hours, minutes));
            } else {
                views.setTextViewText(R.id.countdown_days, "");
                views.setTextViewText(R.id.days_label, "");
                views.setTextViewText(R.id.time_remaining, String.format("%02d:%02d", hours, minutes));
            }
        } else {
            views.setTextViewText(R.id.countdown_days, "0");
            views.setTextViewText(R.id.days_label, "дней");
            views.setTextViewText(R.id.time_remaining, "00:00");
        }
        }
        
        // Добавляем клик для открытия настроек виджета
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_background, pendingIntent);
        
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateAllWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void scheduleNextUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, CountdownWidgetProvider.class);
        intent.setAction(ACTION_UPDATE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        alarmManager.cancel(pendingIntent);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 30000, 30000, pendingIntent);
    }

    private void cancelUpdates(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, CountdownWidgetProvider.class);
        intent.setAction(ACTION_UPDATE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_UPDATE.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new android.content.ComponentName(context, CountdownWidgetProvider.class));
            for (int appWidgetId : appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId);
            }
        }
    }
}
