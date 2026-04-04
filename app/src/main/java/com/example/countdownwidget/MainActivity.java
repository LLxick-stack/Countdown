package com.example.countdownwidget;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 1;
    private static final int PERMISSION_REQUEST = 2;
    private DatePicker datePicker;
    private EditText eventName;
    private Button startButton;
    private Button selectImageButton;
    private ImageView imagePreview;
    private Uri selectedImageUri;
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Устанавливаем результат по умолчанию как CANCELED
        setResult(RESULT_CANCELED);

        // Получаем ID виджета, если запущено из конфигурации виджета
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            widgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        
        // Если запущено из лаунчера (нет widget ID), показываем инструкцию
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setContentView(R.layout.activity_instructions);
            Button closeButton = findViewById(R.id.closeButton);
            closeButton.setOnClickListener(v -> finish());
            return;
        }
        
        setContentView(R.layout.activity_main);

        datePicker = findViewById(R.id.datePicker);
        eventName = findViewById(R.id.eventName);
        startButton = findViewById(R.id.startButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        imagePreview = findViewById(R.id.imagePreview);

        // Загружаем существующие данные виджета, если есть
        loadWidgetData(widgetId);

        selectImageButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PERMISSION_REQUEST);
                } else {
                    openImagePicker();
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
                } else {
                    openImagePicker();
                }
            } else {
                openImagePicker();
            }
        });

        startButton.setOnClickListener(v -> {
            saveWidgetConfiguration();
        });
    }

    private void loadWidgetData(int widgetId) {
        SharedPreferences prefs = getSharedPreferences("widget_" + widgetId, Context.MODE_PRIVATE);
        long eventDate = prefs.getLong("eventDate", 0);
        String name = prefs.getString("eventName", "");
        boolean hasCustomBg = prefs.getBoolean("hasCustomBg", false);
        
        if (eventDate > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(eventDate);
            datePicker.updateDate(calendar.get(Calendar.YEAR), 
                calendar.get(Calendar.MONTH), 
                calendar.get(Calendar.DAY_OF_MONTH));
        }
        
        if (!name.isEmpty()) {
            eventName.setText(name);
        }
        
        // Загружаем превью фона, если есть
        if (hasCustomBg) {
            try {
                java.io.File file = new java.io.File(getFilesDir(), "widget_bg_" + widgetId + ".png");
                if (file.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    if (bitmap != null) {
                        imagePreview.setImageBitmap(bitmap);
                        imagePreview.setVisibility(android.view.View.VISIBLE);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveWidgetConfiguration() {
        int day = datePicker.getDayOfMonth();
        int month = datePicker.getMonth();
        int year = datePicker.getYear();
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        long eventDate = calendar.getTimeInMillis();
        String name = eventName.getText().toString();
        if (name.isEmpty()) {
            name = "Event";
        }
        
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        
        // Сохраняем данные для конкретного виджета
        saveWidgetData(widgetId, eventDate, name);
        CountdownWidgetProvider.updateWidget(this, appWidgetManager, widgetId);
        
        // Возвращаем результат для конфигурации виджета
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, resultValue);
        
        Toast.makeText(this, "Виджет настроен", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private void saveWidgetData(int widgetId, long eventDate, String name) {
        SharedPreferences prefs = getSharedPreferences("widget_" + widgetId, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("eventDate", eventDate);
        editor.putString("eventName", name);
        
        if (selectedImageUri != null) {
            try {
                InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                
                int maxSize = 1024;
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float scale = Math.min(((float)maxSize / width), ((float)maxSize / height));
                
                if (scale < 1) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, 
                        (int)(width * scale), (int)(height * scale), true);
                }
                
                FileOutputStream fos = openFileOutput("widget_bg_" + widgetId + ".png", Context.MODE_PRIVATE);
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                fos.close();
                inputStream.close();
                editor.putBoolean("hasCustomBg", true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        editor.apply();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                Toast.makeText(this, "Разрешение не предоставлено", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            
            // Показываем превью выбранного изображения
            try {
                InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                imagePreview.setImageBitmap(bitmap);
                imagePreview.setVisibility(android.view.View.VISIBLE);
                inputStream.close();
                Toast.makeText(this, "Фон выбран", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
