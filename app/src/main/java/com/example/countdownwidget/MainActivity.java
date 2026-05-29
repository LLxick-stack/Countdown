package com.example.countdownwidget;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    static final String BACKGROUND_MODE_GRADIENT = "gradient";
    static final String BACKGROUND_MODE_TRANSPARENT = "transparent";
    static final String BACKGROUND_MODE_DYNAMIC = "dynamic";
    static final String BACKGROUND_MODE_IMAGE = "image";
    static final String DEFAULT_GRADIENT_START = "#365B6D";
    static final String DEFAULT_GRADIENT_END = "#C95C54";
    static final String DEFAULT_TEXT_COLOR = "#FFFFFF";

    private static final String[] FONT_LABELS = {
        "Обычный",
        "Жирный",
        "Моно",
        "С засечками"
    };

    private static final String[] FONT_VALUES = {
        "sans",
        "bold",
        "mono",
        "serif"
    };

    private static final int PICK_IMAGE = 1;
    private static final int PERMISSION_REQUEST = 2;
    private DatePicker datePicker;
    private TimePicker timePicker;
    private EditText eventName;
    private Button startButton;
    private Button selectImageButton;
    private ImageView imagePreview;
    private FrameLayout widgetEditor;
    private View timerPreview;
    private TextView eventPreview;
    private TextView timerPreviewDays;
    private TextView timerPreviewLabel;
    private TextView timerPreviewTime;
    private RadioGroup backgroundModeGroup;
    private RadioButton gradientMode;
    private RadioButton transparentMode;
    private RadioButton dynamicMode;
    private RadioButton imageMode;
    private EditText gradientStartInput;
    private EditText gradientEndInput;
    private EditText textColorInput;
    private Spinner fontSpinner;
    private float timerX = 0.5f;
    private float timerY = 0.5f;
    private float eventX = 0.5f;
    private float eventY = 0.85f;
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
        timePicker = findViewById(R.id.timePicker);
        eventName = findViewById(R.id.eventName);
        startButton = findViewById(R.id.startButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        imagePreview = findViewById(R.id.imagePreview);
        widgetEditor = findViewById(R.id.widgetEditor);
        timerPreview = findViewById(R.id.timerPreview);
        eventPreview = findViewById(R.id.eventPreview);
        timerPreviewDays = findViewById(R.id.timerPreviewDays);
        timerPreviewLabel = findViewById(R.id.timerPreviewLabel);
        timerPreviewTime = findViewById(R.id.timerPreviewTime);
        backgroundModeGroup = findViewById(R.id.backgroundModeGroup);
        gradientMode = findViewById(R.id.gradientMode);
        transparentMode = findViewById(R.id.transparentMode);
        dynamicMode = findViewById(R.id.dynamicMode);
        imageMode = findViewById(R.id.imageMode);
        gradientStartInput = findViewById(R.id.gradientStartInput);
        gradientEndInput = findViewById(R.id.gradientEndInput);
        textColorInput = findViewById(R.id.textColorInput);
        fontSpinner = findViewById(R.id.fontSpinner);
        timePicker.setIs24HourView(true);
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, FONT_LABELS);
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fontSpinner.setAdapter(fontAdapter);
        makeDraggable(timerPreview, true);
        makeDraggable(eventPreview, false);
        bindEditorPreviewUpdates();

        // Загружаем существующие данные виджета, если есть
        loadWidgetData(widgetId);
        updateEditorPreviewText();
        updateEditorStyle();
        widgetEditor.post(this::updateEditorPositions);

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
        String backgroundMode = prefs.getString("backgroundMode",
            hasCustomBg ? BACKGROUND_MODE_IMAGE : BACKGROUND_MODE_GRADIENT);
        timerX = prefs.getFloat("timerX", 0.5f);
        timerY = prefs.getFloat("timerY", 0.5f);
        eventX = prefs.getFloat("eventX", 0.5f);
        eventY = prefs.getFloat("eventY", 0.85f);
        gradientStartInput.setText(prefs.getString("gradientStart", DEFAULT_GRADIENT_START));
        gradientEndInput.setText(prefs.getString("gradientEnd", DEFAULT_GRADIENT_END));
        textColorInput.setText(prefs.getString("textColor", DEFAULT_TEXT_COLOR));
        fontSpinner.setSelection(getFontIndex(prefs.getString("fontStyle", FONT_VALUES[0])));
        
        if (eventDate > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(eventDate);
            datePicker.updateDate(calendar.get(Calendar.YEAR), 
                calendar.get(Calendar.MONTH), 
                calendar.get(Calendar.DAY_OF_MONTH));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                timePicker.setHour(calendar.get(Calendar.HOUR_OF_DAY));
                timePicker.setMinute(calendar.get(Calendar.MINUTE));
            } else {
                timePicker.setCurrentHour(calendar.get(Calendar.HOUR_OF_DAY));
                timePicker.setCurrentMinute(calendar.get(Calendar.MINUTE));
            }
        }
        
        if (!name.isEmpty()) {
            eventName.setText(name);
        }
        updateEditorPreviewText();

        checkBackgroundMode(backgroundMode);
        
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
        calendar.set(year, month, day, getSelectedHour(), getSelectedMinute(), 0);
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
        editor.putString("backgroundMode", getSelectedBackgroundMode());
        editor.putFloat("timerX", timerX);
        editor.putFloat("timerY", timerY);
        editor.putFloat("eventX", eventX);
        editor.putFloat("eventY", eventY);
        editor.putString("gradientStart", normalizeColorInput(gradientStartInput.getText().toString(), DEFAULT_GRADIENT_START));
        editor.putString("gradientEnd", normalizeColorInput(gradientEndInput.getText().toString(), DEFAULT_GRADIENT_END));
        editor.putString("textColor", normalizeColorInput(textColorInput.getText().toString(), DEFAULT_TEXT_COLOR));
        editor.putString("fontStyle", getSelectedFontStyle());
        
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
                editor.putString("backgroundMode", BACKGROUND_MODE_IMAGE);
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
                imageMode.setChecked(true);
                inputStream.close();
                Toast.makeText(this, "Фон выбран", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getSelectedBackgroundMode() {
        int checkedId = backgroundModeGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.transparentMode) {
            return BACKGROUND_MODE_TRANSPARENT;
        }
        if (checkedId == R.id.dynamicMode) {
            return BACKGROUND_MODE_DYNAMIC;
        }
        if (checkedId == R.id.imageMode) {
            return BACKGROUND_MODE_IMAGE;
        }
        return BACKGROUND_MODE_GRADIENT;
    }

    private void checkBackgroundMode(String backgroundMode) {
        if (BACKGROUND_MODE_TRANSPARENT.equals(backgroundMode)) {
            transparentMode.setChecked(true);
        } else if (BACKGROUND_MODE_DYNAMIC.equals(backgroundMode)) {
            dynamicMode.setChecked(true);
        } else if (BACKGROUND_MODE_IMAGE.equals(backgroundMode)) {
            imageMode.setChecked(true);
        } else {
            gradientMode.setChecked(true);
        }
    }

    private void makeDraggable(View view, boolean timer) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private float offsetX;
            private float offsetY;

            @Override
            public boolean onTouch(View touchedView, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    widgetEditor.getParent().requestDisallowInterceptTouchEvent(true);
                    offsetX = event.getRawX() - touchedView.getX();
                    offsetY = event.getRawY() - touchedView.getY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
                    float x = event.getRawX() - offsetX;
                    float y = event.getRawY() - offsetY;
                    placeEditorView(touchedView, x, y);
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        snapEditorView(touchedView);
                    }
                    saveEditorPosition(touchedView, timer);
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        widgetEditor.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    widgetEditor.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        });
    }

    private void updateEditorPositions() {
        placeEditorView(timerPreview,
            timerX * widgetEditor.getWidth() - timerPreview.getWidth() / 2f,
            timerY * widgetEditor.getHeight() - timerPreview.getHeight() / 2f);
        placeEditorView(eventPreview,
            eventX * widgetEditor.getWidth() - eventPreview.getWidth() / 2f,
            eventY * widgetEditor.getHeight() - eventPreview.getHeight() / 2f);
    }

    private void placeEditorView(View view, float x, float y) {
        float maxX = Math.max(0, widgetEditor.getWidth() - view.getWidth());
        float maxY = Math.max(0, widgetEditor.getHeight() - view.getHeight());
        view.setX(Math.max(0, Math.min(x, maxX)));
        view.setY(Math.max(0, Math.min(y, maxY)));
    }

    private void saveEditorPosition(View view, boolean timer) {
        float centerX = (view.getX() + view.getWidth() / 2f) / Math.max(1, widgetEditor.getWidth());
        float centerY = (view.getY() + view.getHeight() / 2f) / Math.max(1, widgetEditor.getHeight());
        if (timer) {
            timerX = centerX;
            timerY = centerY;
        } else {
            eventX = centerX;
            eventY = centerY;
        }
    }

    private void bindEditorPreviewUpdates() {
        eventName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateEditorPreviewText();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        TextWatcher styleWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateEditorStyle();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        gradientStartInput.addTextChangedListener(styleWatcher);
        gradientEndInput.addTextChangedListener(styleWatcher);
        textColorInput.addTextChangedListener(styleWatcher);
        backgroundModeGroup.setOnCheckedChangeListener((group, checkedId) -> updateEditorStyle());
        fontSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateEditorStyle();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        datePicker.init(datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth(),
            (view, year, monthOfYear, dayOfMonth) -> updateEditorPreviewText());
        timePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> updateEditorPreviewText());
    }

    private void updateEditorPreviewText() {
        String name = eventName.getText().toString().trim();
        eventPreview.setText(name.isEmpty() ? "Название события" : name);

        Calendar calendar = Calendar.getInstance();
        calendar.set(datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth(),
            getSelectedHour(), getSelectedMinute(), 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long remainingTime = calendar.getTimeInMillis() - System.currentTimeMillis();
        if (remainingTime <= 0) {
            timerPreviewDays.setText("0");
            timerPreviewLabel.setText("дней");
            timerPreviewTime.setText("00:00");
            return;
        }

        long days = TimeUnit.MILLISECONDS.toDays(remainingTime);
        long hours = TimeUnit.MILLISECONDS.toHours(remainingTime) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60;
        timerPreviewDays.setText(days > 0 ? String.valueOf(days) : "");
        timerPreviewLabel.setText(days > 0 ? getDaysLabel(days) : "");
        timerPreviewTime.setText(String.format("%02d:%02d", hours, minutes));
    }

    private int getSelectedHour() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return timePicker.getHour();
        }
        return timePicker.getCurrentHour();
    }

    private int getSelectedMinute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return timePicker.getMinute();
        }
        return timePicker.getCurrentMinute();
    }

    private void updateEditorStyle() {
        int start = parseColorInput(gradientStartInput.getText().toString(), DEFAULT_GRADIENT_START);
        int end = parseColorInput(gradientEndInput.getText().toString(), DEFAULT_GRADIENT_END);
        int textColor = parseColorInput(textColorInput.getText().toString(), DEFAULT_TEXT_COLOR);

        if (transparentMode.isChecked()) {
            widgetEditor.setBackgroundColor(Color.TRANSPARENT);
        } else {
            GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
            background.setCornerRadius(16f);
            widgetEditor.setBackground(background);
        }

        Typeface typeface = getSelectedTypeface();
        timerPreviewDays.setTextColor(textColor);
        timerPreviewLabel.setTextColor(textColor);
        timerPreviewTime.setTextColor(textColor);
        eventPreview.setTextColor(adjustAlpha(textColor, 0.92f));
        timerPreviewDays.setTypeface(typeface, Typeface.BOLD);
        timerPreviewLabel.setTypeface(typeface);
        timerPreviewTime.setTypeface(typeface, Typeface.BOLD);
        eventPreview.setTypeface(typeface, getSelectedTypefaceStyle());
    }

    private void snapEditorView(View view) {
        float[] anchorsX = {0.08f, 0.5f, 0.92f};
        float[] anchorsY = {0.15f, 0.5f, 0.85f};
        float centerX = (view.getX() + view.getWidth() / 2f) / Math.max(1, widgetEditor.getWidth());
        float centerY = (view.getY() + view.getHeight() / 2f) / Math.max(1, widgetEditor.getHeight());
        float snappedX = snapIfClose(centerX, anchorsX);
        float snappedY = snapIfClose(centerY, anchorsY);
        placeEditorView(view,
            snappedX * widgetEditor.getWidth() - view.getWidth() / 2f,
            snappedY * widgetEditor.getHeight() - view.getHeight() / 2f);
    }

    private float snapIfClose(float value, float[] anchors) {
        for (float anchor : anchors) {
            if (Math.abs(value - anchor) < 0.08f) {
                return anchor;
            }
        }
        return value;
    }

    private String getDaysLabel(long days) {
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

    private int parseColorInput(String value, String fallback) {
        try {
            return Color.parseColor(normalizeColorInput(value, fallback));
        } catch (IllegalArgumentException e) {
            return Color.parseColor(fallback);
        }
    }

    private String normalizeColorInput(String value, String fallback) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        if (trimmed.matches("#[0-9a-fA-F]{6}") || trimmed.matches("#[0-9a-fA-F]{8}")) {
            return trimmed;
        }
        return fallback;
    }

    private String getSelectedFontStyle() {
        int index = fontSpinner.getSelectedItemPosition();
        if (index < 0 || index >= FONT_VALUES.length) {
            return FONT_VALUES[0];
        }
        return FONT_VALUES[index];
    }

    private int getFontIndex(String value) {
        for (int i = 0; i < FONT_VALUES.length; i++) {
            if (FONT_VALUES[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private Typeface getSelectedTypeface() {
        String style = getSelectedFontStyle();
        if ("mono".equals(style)) {
            return Typeface.MONOSPACE;
        }
        if ("serif".equals(style)) {
            return Typeface.SERIF;
        }
        return Typeface.SANS_SERIF;
    }

    private int getSelectedTypefaceStyle() {
        if ("bold".equals(getSelectedFontStyle())) {
            return Typeface.BOLD;
        }
        return Typeface.NORMAL;
    }

    private int adjustAlpha(int color, float factor) {
        return Color.argb(Math.round(Color.alpha(color) * factor),
            Color.red(color), Color.green(color), Color.blue(color));
    }
}
