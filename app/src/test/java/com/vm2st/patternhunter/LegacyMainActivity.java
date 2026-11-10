// Original 1.0 UI fixture. Test-only, never included in the APK.
// It inflates byte-identical copies of the original layouts from src/debug/res (not packaged in release).
package com.vm2st.patternhunter;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LegacyMainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "pattern_hunter_prefs";
    private static final String KEY_THEME = "theme_mode";
    private static final String TELEGRAM_URL = "https://t.me/vm2_studios";

    private EditText inputEditText;
    private RadioGroup themeGroup;
    private RadioButton themeSystem;
    private RadioButton themeLight;
    private RadioButton themeDark;

    private LinearLayout summaryCard;
    private TextView statLength;
    private TextView statUnique;
    private TextView statEntropy;
    private TextView statType;

    private TextView statusHeader;
    private RecyclerView recyclerView;
    private PatternAdapter patternAdapter;

    private SharedPreferences prefs;

    // Performance & Async execution
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAnalysisRunnable;
    private int currentAnalysisVersion = 0;

    // Non-repeating random pool
    private final String[] allSamples = new String[]{
        "278179499912",
        "goofhfigperos",
        "123456789",
        "abacaba",
        "Mississippi",
        "a1b2c3d4e5",
        "🚀★🚀★🚀",
        "🔥🐱🔥🐱🔥",
        "777424777",
        "Racecar999racecaR",
        "abc_cba_abc_cba",
        "11235813",
        "0246824680",
        "banana_papaya_havana",
        "ABCDEFfedcbaABCDEF"
    };
    private final List<Integer> availableSampleIndices = new ArrayList<>();
    private int lastSampleIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        applySavedTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.legacy_activity_main);

        // Edge-to-edge window insets handling for Android 15+
        View rootView = findViewById(R.id.root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        initViews();
        setupThemeListeners();
        setupInputListeners();
        setupSampleChips();
        setupTelegramLink();
        initSamplePool();
    }

    private void applySavedTheme() {
        int savedMode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
    }

    private void initViews() {
        inputEditText = findViewById(R.id.input);
        themeGroup = findViewById(R.id.themeGroup);
        themeSystem = findViewById(R.id.themeSystem);
        themeLight = findViewById(R.id.themeLight);
        themeDark = findViewById(R.id.themeDark);

        summaryCard = findViewById(R.id.summaryCard);
        statLength = findViewById(R.id.statLength);
        statUnique = findViewById(R.id.statUnique);
        statEntropy = findViewById(R.id.statEntropy);
        statType = findViewById(R.id.statType);

        statusHeader = findViewById(R.id.statusHeader);
        recyclerView = findViewById(R.id.recyclerView);

        // Setup RecyclerView
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setHasFixedSize(false);
            patternAdapter = new PatternAdapter();
            recyclerView.setAdapter(patternAdapter);
        }

        // Set radio button based on current theme setting
        int currentMode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            themeLight.setChecked(true);
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            themeDark.setChecked(true);
        } else {
            themeSystem.setChecked(true);
        }
    }

    private void setupThemeListeners() {
        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (checkedId == R.id.themeLight) {
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.themeDark) {
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            }

            if (prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) != mode) {
                prefs.edit().putInt(KEY_THEME, mode).apply();
                AppCompatDelegate.setDefaultNightMode(mode);
            }
        });
    }

    private void setupInputListeners() {
        Button btnPaste = findViewById(R.id.btnPaste);
        if (btnPaste != null) {
            btnPaste.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
                    CharSequence pasteText = clipboard.getPrimaryClip().getItemAt(0).getText();
                    if (pasteText != null && pasteText.length() > 0) {
                        inputEditText.setText(pasteText);
                        inputEditText.setSelection(pasteText.length());
                        triggerAnalysisDebounced(0); // Instant for button click
                    } else {
                        Toast.makeText(LegacyMainActivity.this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(LegacyMainActivity.this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show();
                }
            });
        }

        Button btnRandom = findViewById(R.id.btnRandom);
        if (btnRandom != null) {
            btnRandom.setOnClickListener(v -> {
                String sample = getNextUniqueRandomSample();
                inputEditText.setText(sample);
                inputEditText.setSelection(sample.length());
                triggerAnalysisDebounced(0); // Instant for button click
            });
        }

        Button btnClear = findViewById(R.id.btnClear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                inputEditText.setText("");
                triggerAnalysisDebounced(0);
            });
        }

        // Live instant debounced search on text change (zero lag)
        inputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                triggerAnalysisDebounced(150); // 150ms debounce for 60fps smoothness
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void initSamplePool() {
        availableSampleIndices.clear();
        for (int i = 0; i < allSamples.length; i++) {
            availableSampleIndices.add(i);
        }
        Collections.shuffle(availableSampleIndices);
    }

    private String getNextUniqueRandomSample() {
        String currentText = inputEditText.getText() != null ? inputEditText.getText().toString() : "";
        if (availableSampleIndices.isEmpty()) {
            initSamplePool();
        }

        int chosenIdx = -1;
        for (int i = 0; i < availableSampleIndices.size(); i++) {
            int candidate = availableSampleIndices.get(i);
            if (candidate != lastSampleIndex && !allSamples[candidate].equals(currentText)) {
                chosenIdx = candidate;
                availableSampleIndices.remove(i);
                break;
            }
        }

        if (chosenIdx == -1) {
            chosenIdx = availableSampleIndices.remove(0);
        }

        lastSampleIndex = chosenIdx;
        return allSamples[chosenIdx];
    }

    private void setupSampleChips() {
        setSampleClick(R.id.sample1, R.string.sample_1);
        setSampleClick(R.id.sample2, R.string.sample_2);
        setSampleClick(R.id.sample3, R.string.sample_3);
        setSampleClick(R.id.sample4, R.string.sample_4);
        setSampleClick(R.id.sample5, R.string.sample_5);
        setSampleClick(R.id.sample6, R.string.sample_6);
        setSampleClick(R.id.sample7, R.string.sample_7);
        setSampleClick(R.id.sample8, R.string.sample_8);
    }

    private void setSampleClick(int viewId, final int stringResId) {
        TextView chip = findViewById(viewId);
        if (chip != null) {
            chip.setOnClickListener(v -> {
                String sample = getString(stringResId);
                inputEditText.setText(sample);
                inputEditText.setSelection(sample.length());
                triggerAnalysisDebounced(0);
            });
        }
    }

    private void setupTelegramLink() {
        View telegramLinkContainer = findViewById(R.id.telegramLinkContainer);
        if (telegramLinkContainer != null) {
            telegramLinkContainer.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(LegacyMainActivity.this, TELEGRAM_URL, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void triggerAnalysisDebounced(long delayMillis) {
        if (pendingAnalysisRunnable != null) {
            mainHandler.removeCallbacks(pendingAnalysisRunnable);
        }

        final int version = ++currentAnalysisVersion;
        final String text = inputEditText.getText() != null ? inputEditText.getText().toString() : "";

        if (delayMillis == 0) {
            executeAnalysisAsync(text, version);
        } else {
            pendingAnalysisRunnable = () -> executeAnalysisAsync(text, version);
            mainHandler.postDelayed(pendingAnalysisRunnable, delayMillis);
        }
    }

    private void executeAnalysisAsync(final String text, final int version) {
        final boolean isRussian = isCurrentLocaleRussian();

        if (text.trim().isEmpty()) {
            summaryCard.setVisibility(View.GONE);
            if (patternAdapter != null) {
                patternAdapter.setData(null, isRussian, isNightModeActive());
            }
            statusHeader.setText(R.string.empty_prompt);
            return;
        }

        // Run algorithmic analysis on background thread to prevent UI lag
        executorService.execute(() -> {
            final AnalysisSummary summary = PatternHunterEngine.analyze(text);

            // Post results back to UI thread
            mainHandler.post(() -> {
                if (version != currentAnalysisVersion) {
                    return; // Obsolete result, ignore
                }
                updateUI(summary, isRussian);
            });
        });
    }

    private void updateUI(AnalysisSummary summary, boolean isRussian) {
        summaryCard.setVisibility(View.VISIBLE);
        statLength.setText(getString(R.string.stat_length, summary.getLength()));
        statUnique.setText(getString(R.string.stat_unique, summary.getUniqueSymbols()));
        statEntropy.setText(getString(R.string.stat_entropy, summary.getShannonEntropy()));
        statType.setText(getString(R.string.stat_type, summary.getCharacterClass(isRussian)));

        List<PatternItem> patterns = summary.getPatterns();
        boolean isNight = isNightModeActive();

        if (patterns.isEmpty()) {
            statusHeader.setText(R.string.no_patterns_found);
            if (patternAdapter != null) {
                patternAdapter.setData(null, isRussian, isNight);
            }
            return;
        }

        statusHeader.setText(getString(R.string.results_count, patterns.size()));
        if (patternAdapter != null) {
            patternAdapter.setData(patterns, isRussian, isNight);
        }
    }

    private boolean isNightModeActive() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean isCurrentLocaleRussian() {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = getResources().getConfiguration().locale;
        }
        if (locale != null && "ru".equalsIgnoreCase(locale.getLanguage())) {
            return true;
        }
        return "ru".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingAnalysisRunnable != null) {
            mainHandler.removeCallbacks(pendingAnalysisRunnable);
        }
        executorService.shutdown();
    }

    // High-performance Recycled Adapter embedded directly inside LegacyMainActivity
    public static class PatternAdapter extends RecyclerView.Adapter<PatternAdapter.PatternViewHolder> {

        private final List<PatternItem> items = new ArrayList<>();
        private boolean isRussian = false;
        private boolean isNightMode = false;

        public void setData(List<PatternItem> newItems, boolean isRussian, boolean isNightMode) {
            this.isRussian = isRussian;
            this.isNightMode = isNightMode;
            this.items.clear();
            if (newItems != null) {
                this.items.addAll(newItems);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PatternViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.legacy_item_pattern_card, parent, false);
            return new PatternViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PatternViewHolder holder, int position) {
            PatternItem item = items.get(position);
            holder.bind(item, isRussian, isNightMode);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class PatternViewHolder extends RecyclerView.ViewHolder {
            private final TextView categoryView;
            private final TextView snippetView;
            private final TextView descView;
            private final TextView posView;

            public PatternViewHolder(@NonNull View itemView) {
                super(itemView);
                categoryView = itemView.findViewById(R.id.cardCategory);
                snippetView = itemView.findViewById(R.id.cardSnippet);
                descView = itemView.findViewById(R.id.cardDescription);
                posView = itemView.findViewById(R.id.cardPosition);
            }

            public void bind(PatternItem item, boolean isRussian, boolean isNightMode) {
                PatternCategory cat = item.getCategory();
                categoryView.setText(cat.getTitle(isRussian));

                String bgColorStr = isNightMode ? cat.getDarkBgColor() : cat.getLightBgColor();
                String textColorStr = isNightMode ? cat.getDarkTextColor() : cat.getLightTextColor();

                GradientDrawable badgeDrawable = new GradientDrawable();
                badgeDrawable.setShape(GradientDrawable.RECTANGLE);
                badgeDrawable.setCornerRadius(dpToPx(itemView.getContext(), 6));
                try {
                    badgeDrawable.setColor(Color.parseColor(bgColorStr));
                    categoryView.setTextColor(Color.parseColor(textColorStr));
                } catch (Exception ignored) {}
                categoryView.setBackground(badgeDrawable);

                if (item.getSnippet() != null && !item.getSnippet().isEmpty()) {
                    snippetView.setVisibility(View.VISIBLE);
                    snippetView.setText(item.getSnippet());
                } else {
                    snippetView.setVisibility(View.GONE);
                }

                descView.setText(item.getDescription(isRussian));

                if (item.hasPositionRange()) {
                    posView.setVisibility(View.VISIBLE);
                    if (isRussian) {
                        posView.setText(String.format(Locale.US, "Диапазон позиций в строке: %d–%d", item.getStartPos(), item.getEndPos()));
                    } else {
                        posView.setText(String.format(Locale.US, "Position range: %d–%d", item.getStartPos(), item.getEndPos()));
                    }
                } else {
                    posView.setVisibility(View.GONE);
                }
            }

            private int dpToPx(Context context, int dp) {
                return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
            }
        }
    }
}
