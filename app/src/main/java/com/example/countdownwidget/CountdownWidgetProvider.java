package com.example.countdownwidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
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
        String backgroundMode = prefs.getString("backgroundMode",
            hasCustomBg ? MainActivity.BACKGROUND_MODE_IMAGE : MainActivity.BACKGROUND_MODE_GRADIENT);
        float timerX = prefs.getFloat("timerX", 0.5f);
        float timerY = prefs.getFloat("timerY", 0.5f);
        float eventX = prefs.getFloat("eventX", 0.5f);
        float eventY = prefs.getFloat("eventY", 0.85f);
        String gradientStart = prefs.getString("gradientStart", MainActivity.DEFAULT_GRADIENT_START);
        String gradientEnd = prefs.getString("gradientEnd", MainActivity.DEFAULT_GRADIENT_END);
        String textColor = prefs.getString("textColor", MainActivity.DEFAULT_TEXT_COLOR);
        String fontStyle = prefs.getString("fontStyle", "sans");
        
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.countdown_widget);

        views.setImageViewBitmap(R.id.widget_background, createWidgetBitmap(
            context, appWidgetId, eventDate, eventName, backgroundMode, hasCustomBg,
            timerX, timerY, eventX, eventY, gradientStart, gradientEnd, textColor, fontStyle));
        views.setTextViewText(R.id.countdown_days, "");
        views.setTextViewText(R.id.days_label, "");
        views.setTextViewText(R.id.time_remaining, "");
        views.setTextViewText(R.id.event_name, "");
        
        // Добавляем клик для открытия настроек виджета
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_background, pendingIntent);
        views.setOnClickPendingIntent(R.id.timer_container, pendingIntent);
        views.setOnClickPendingIntent(R.id.event_container, pendingIntent);
        
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

    private static Bitmap createWidgetBitmap(
        Context context,
        int appWidgetId,
        long eventDate,
        String eventName,
        String backgroundMode,
        boolean hasCustomBg,
        float timerX,
        float timerY,
        float eventX,
        float eventY,
        String gradientStart,
        String gradientEnd,
        String textColor,
        String fontStyle
    ) {
        int width = 800;
        int height = 360;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        if (!MainActivity.BACKGROUND_MODE_TRANSPARENT.equals(backgroundMode)) {
            Bitmap background = createBackgroundBitmap(context, appWidgetId, backgroundMode,
                hasCustomBg, width, height, gradientStart, gradientEnd);
            canvas.drawBitmap(background, 0, 0, null);
        }

        CountdownText countdownText = createCountdownText(eventDate);
        int parsedTextColor = parseColor(textColor, MainActivity.DEFAULT_TEXT_COLOR);
        Typeface typeface = resolveTypeface(fontStyle);
        drawCenteredText(canvas, countdownText.days, width * timerX, height * timerY - 52,
            96, true, parsedTextColor, width - 24, typeface);
        drawCenteredText(canvas, countdownText.daysLabel, width * timerX, height * timerY + 18,
            30, false, parsedTextColor, width - 24, typeface);
        drawCenteredText(canvas, countdownText.time, width * timerX, height * timerY + 74,
            58, true, parsedTextColor, width - 24, typeface);
        drawCenteredText(canvas, eventName, width * eventX, height * eventY,
            28, "bold".equals(fontStyle), adjustAlpha(parsedTextColor, 0.92f), width - 32, typeface);
        return bitmap;
    }

    private static Bitmap createBackgroundBitmap(
        Context context,
        int appWidgetId,
        String backgroundMode,
        boolean hasCustomBg,
        int width,
        int height,
        String gradientStart,
        String gradientEnd
    ) {
        if (MainActivity.BACKGROUND_MODE_IMAGE.equals(backgroundMode) && hasCustomBg) {
            try {
                File file = new File(context.getFilesDir(), "widget_bg_" + appWidgetId + ".png");
                Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (image != null) {
                    return centerCrop(image, width, height);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return createDynamicBackground(context,
            MainActivity.BACKGROUND_MODE_DYNAMIC.equals(backgroundMode), width, height,
            gradientStart, gradientEnd);
    }

    private static Bitmap createDynamicBackground(
        Context context,
        boolean useWallpaperColors,
        int width,
        int height,
        String gradientStart,
        String gradientEnd
    ) {
        int startColor = parseColor(gradientStart, MainActivity.DEFAULT_GRADIENT_START);
        int endColor = parseColor(gradientEnd, MainActivity.DEFAULT_GRADIENT_END);

        if (useWallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                WallpaperColors wallpaperColors = WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (wallpaperColors != null && wallpaperColors.getPrimaryColor() != null) {
                    startColor = wallpaperColors.getPrimaryColor().toArgb();
                    if (wallpaperColors.getSecondaryColor() != null) {
                        endColor = wallpaperColors.getSecondaryColor().toArgb();
                    } else if (wallpaperColors.getTertiaryColor() != null) {
                        endColor = wallpaperColors.getTertiaryColor().toArgb();
                    } else {
                        endColor = adjustColor(startColor, 0.72f);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, width, height,
            startColor, endColor, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        return bitmap;
    }

    private static Bitmap centerCrop(Bitmap source, int width, int height) {
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        float scale = Math.max((float) width / source.getWidth(), (float) height / source.getHeight());
        float scaledWidth = source.getWidth() * scale;
        float scaledHeight = source.getHeight() * scale;
        Rect dst = new Rect(
            Math.round((width - scaledWidth) / 2f),
            Math.round((height - scaledHeight) / 2f),
            Math.round((width + scaledWidth) / 2f),
            Math.round((height + scaledHeight) / 2f));
        canvas.drawBitmap(source, null, dst, null);
        return output;
    }

    private static CountdownText createCountdownText(long eventDate) {
        if (eventDate == 0) {
            return new CountdownText("", "", "Нажмите для настройки");
        }

        long remainingTime = eventDate - System.currentTimeMillis();
        if (remainingTime <= 0) {
            return new CountdownText("0", "дней", "00:00");
        }

        long days = TimeUnit.MILLISECONDS.toDays(remainingTime);
        long hours = TimeUnit.MILLISECONDS.toHours(remainingTime) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60;
        String daysText = days > 0 ? String.valueOf(days) : "";
        String daysLabel = days > 0 ? getDaysLabel(days) : "";
        return new CountdownText(daysText, daysLabel, String.format("%02d:%02d", hours, minutes));
    }

    private static String getDaysLabel(long days) {
        long mod100 = days % 100;
        long mod10 = days % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "дней";
        }
        if (mod10 == 1) {
            return "день";
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return "дня";
        }
        return "дней";
    }

    private static void drawCenteredText(
        Canvas canvas,
        String text,
        float centerX,
        float baselineY,
        float textSize,
        boolean bold,
        int color,
        int maxWidth,
        Typeface typeface
    ) {
        if (text == null || text.isEmpty()) {
            return;
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textSize);
        paint.setFakeBoldText(bold);
        paint.setTypeface(typeface);
        paint.setShadowLayer(6f, 3f, 3f, Color.BLACK);
        while (paint.measureText(text) > maxWidth && paint.getTextSize() > 18f) {
            paint.setTextSize(paint.getTextSize() - 2f);
        }
        canvas.drawText(text, centerX, baselineY, paint);
    }

    private static int adjustColor(int color, float factor) {
        return Color.rgb(
            Math.min(255, Math.round(Color.red(color) * factor)),
            Math.min(255, Math.round(Color.green(color) * factor)),
            Math.min(255, Math.round(Color.blue(color) * factor)));
    }

    private static int adjustAlpha(int color, float factor) {
        return Color.argb(Math.round(Color.alpha(color) * factor),
            Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int parseColor(String value, String fallback) {
        try {
            String normalized = value == null ? fallback : value.trim();
            if (!normalized.startsWith("#")) {
                normalized = "#" + normalized;
            }
            return Color.parseColor(normalized);
        } catch (IllegalArgumentException e) {
            return Color.parseColor(fallback);
        }
    }

    private static Typeface resolveTypeface(String fontStyle) {
        if ("mono".equals(fontStyle)) {
            return Typeface.MONOSPACE;
        }
        if ("serif".equals(fontStyle)) {
            return Typeface.SERIF;
        }
        return Typeface.SANS_SERIF;
    }

    private static final class CountdownText {
        final String days;
        final String daysLabel;
        final String time;

        CountdownText(String days, String daysLabel, String time) {
            this.days = days;
            this.daysLabel = daysLabel;
            this.time = time;
        }
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
