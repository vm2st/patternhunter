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
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "pattern_hunter_prefs";
    private static final String KEY_THEME = "theme_mode";
    private static final String TELEGRAM_URL = "https://t.me/vm2_studios";
    /** Stored in KEY_THEME next to AppCompat night modes; distinct from every AppCompat mode value. */
    static final int THEME_BURGUNDY = 100;

    private EditText inputEditText;
    private RadioButton themeSystem;
    private RadioButton themeLight;
    private RadioButton themeDark;
    private RadioButton themeBurgundy;
    private boolean burgundyThemeApplied;

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
    private AnalysisDispatcher analysisDispatcher;
    private boolean destroyed;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAnalysisRunnable;
    private long currentAnalysisVersion = 0;

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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        analysisDispatcher = new AnalysisDispatcher(command -> mainHandler.post(command), this::analyzeInput);
        androidx.core.view.WindowInsetsControllerCompat barsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        barsController.setAppearanceLightNavigationBars(!isNightModeActive() && !burgundyThemeApplied);
        if (burgundyThemeApplied) barsController.setAppearanceLightStatusBars(false);

        // Older Android cannot draw dark system-bar icons on the original light surface.
        if (!isNightModeActive() && !burgundyThemeApplied) {
            if (Build.VERSION.SDK_INT < 23) getWindow().setStatusBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_primary_variant));
            if (Build.VERSION.SDK_INT < 26) getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_primary_variant));
        }
        // Keep content and the keyboard outside system bars on all supported Android versions.
        View rootView = findViewById(R.id.root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, imeBottom));
                return insets;
            });
        }

        initViews();
        setupThemeListeners();
        setupInputListeners();
        setupSampleChips();
        setupTelegramLink();
        if (!restoreSamplePool(savedInstanceState)) initSamplePool();
    }

    private int readThemeMode() {
        try {
            Object value = prefs.getAll().get(KEY_THEME);
            if (value instanceof Integer) {
                int mode = (Integer) value;
                if (mode == AppCompatDelegate.MODE_NIGHT_NO || mode == AppCompatDelegate.MODE_NIGHT_YES ||
                        mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM || mode == THEME_BURGUNDY) return mode;
            }
        } catch (RuntimeException ignored) { }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    private static int nightModeFor(int theme) {
        // Burgundy uses light text, so system widgets follow the dark variant.
        return theme == THEME_BURGUNDY ? AppCompatDelegate.MODE_NIGHT_YES : theme;
    }

    private void applySavedTheme() {
        int savedTheme = readThemeMode();
        burgundyThemeApplied = savedTheme == THEME_BURGUNDY;
        setTheme(burgundyThemeApplied ? R.style.Theme_PatternHunter_Burgundy : R.style.Theme_PatternHunter_Standard);
        AppCompatDelegate.setDefaultNightMode(nightModeFor(savedTheme));
    }

    private void initViews() {
        inputEditText = findViewById(R.id.input);
        inputEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PatternHunterEngine.MAX_INPUT_UTF16_LENGTH)});
        themeSystem = findViewById(R.id.themeSystem);
        themeLight = findViewById(R.id.themeLight);
        themeDark = findViewById(R.id.themeDark);
        themeBurgundy = findViewById(R.id.themeBurgundy);

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
        int currentMode = readThemeMode();
        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            themeLight.setChecked(true);
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            themeDark.setChecked(true);
        } else if (currentMode == THEME_BURGUNDY) {
            themeBurgundy.setChecked(true);
        } else {
            themeSystem.setChecked(true);
        }
    }

    private void setupThemeListeners() {
        // Four options are arranged in two rows, so exclusivity is kept here instead of by one RadioGroup row.
        final RadioButton[] themeButtons = {themeSystem, themeLight, themeDark, themeBurgundy};
        for (RadioButton button : themeButtons) {
            button.setOnCheckedChangeListener((changed, isChecked) -> {
                if (!isChecked) return;
                for (RadioButton other : themeButtons) {
                    if (other != changed) other.setChecked(false);
                }
                int checkedId = changed.getId();
                int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                if (checkedId == R.id.themeLight) {
                    mode = AppCompatDelegate.MODE_NIGHT_NO;
                } else if (checkedId == R.id.themeDark) {
                    mode = AppCompatDelegate.MODE_NIGHT_YES;
                } else if (checkedId == R.id.themeBurgundy) {
                    mode = THEME_BURGUNDY;
                }
                selectTheme(mode);
            });
        }
    }

    private void selectTheme(int mode) {
        if (readThemeMode() == mode) return;
        prefs.edit().putInt(KEY_THEME, mode).apply();
        if ((mode == THEME_BURGUNDY) != burgundyThemeApplied) {
            // A different style must be set before AppCompat creates the screen; the new instance also applies night mode.
            recreate();
        } else {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    private void setupInputListeners() {
        Button btnPaste = findViewById(R.id.btnPaste);
        if (btnPaste != null) {
            btnPaste.setOnClickListener(v -> {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
                    CharSequence pasteText = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).getText() : null;
                    if (pasteText == null || pasteText.length() == 0) {
                        Toast.makeText(MainActivity.this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int end = Math.min(pasteText.length(), PatternHunterEngine.MAX_INPUT_UTF16_LENGTH);
                    if (end < pasteText.length() && end > 0 && Character.isHighSurrogate(pasteText.charAt(end - 1)) &&
                            Character.isLowSurrogate(pasteText.charAt(end))) end--;
                    // Never import clickable/image spans or dereference clipboard content URIs.
                    String plainText = pasteText.subSequence(0, end).toString();
                    inputEditText.setText(plainText);
                    inputEditText.setSelection(inputEditText.length());
                    if (end < pasteText.length()) Toast.makeText(this, R.string.input_too_long, Toast.LENGTH_SHORT).show();
                    triggerAnalysisDebounced(0);
                } catch (RuntimeException unavailable) {
                    Toast.makeText(MainActivity.this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show();
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
            // The last remaining example may be the text the user entered manually.
            initSamplePool();
            for (int i = 0; i < availableSampleIndices.size(); i++) {
                int candidate = availableSampleIndices.get(i);
                if (candidate != lastSampleIndex && !allSamples[candidate].equals(currentText)) {
                    chosenIdx = candidate; availableSampleIndices.remove(i); break;
                }
            }
        }
        if (chosenIdx < 0) throw new IllegalStateException("No distinct example");

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
                    Toast.makeText(MainActivity.this, TELEGRAM_URL, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void triggerAnalysisDebounced(long delayMillis) {
        if (destroyed || analysisDispatcher == null) return;
        if (pendingAnalysisRunnable != null) mainHandler.removeCallbacks(pendingAnalysisRunnable);
        analysisDispatcher.cancel(); // Invalidate old results immediately, not only after debounce.
        final long version = ++currentAnalysisVersion;
        final boolean empty = inputEditText.length() == 0;
        summaryCard.setVisibility(View.GONE);
        patternAdapter.setData(null, isCurrentLocaleRussian(), isNightModeActive());
        statusHeader.setText(empty ? R.string.empty_prompt : R.string.analysis_pending);
        if (empty) { pendingAnalysisRunnable = null; return; }
        if (delayMillis == 0) executeAnalysisAsync(version);
        else {
            pendingAnalysisRunnable = () -> { pendingAnalysisRunnable = null; executeAnalysisAsync(version); };
            mainHandler.postDelayed(pendingAnalysisRunnable, delayMillis);
        }
    }

    protected AnalysisSummary analyzeInput(String text) { return PatternHunterEngine.analyze(text); }

    private void executeAnalysisAsync(final long version) {
        if (destroyed || version != currentAnalysisVersion) return;
        final String text = inputEditText.getText() != null ? inputEditText.getText().toString() : "";
        if (text.isEmpty()) return;
        analysisDispatcher.submit(text, new AnalysisDispatcher.Listener() {
            @Override public void onResult(AnalysisSummary summary) {
                if (!destroyed && version == currentAnalysisVersion) updateUI(summary, isCurrentLocaleRussian());
            }
            @Override public void onError(RuntimeException error) {
                if (!destroyed && version == currentAnalysisVersion) {
                    summaryCard.setVisibility(View.GONE);
                    patternAdapter.setData(null, isCurrentLocaleRussian(), isNightModeActive());
                    statusHeader.setText(R.string.analysis_failed);
                }
            }
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
        return "ru".equalsIgnoreCase((locale != null ? locale : Locale.getDefault()).getLanguage());
    }

    private boolean restoreSamplePool(Bundle state) {
        if (state == null) return false;
        try {
            int[] pool = state.getIntArray("sample_pool");
            int last = state.getInt("last_sample", -1);
            if (pool == null || pool.length > allSamples.length || last < -1 || last >= allSamples.length) return false;
            boolean[] seen = new boolean[allSamples.length];
            for (int candidate : pool) {
                if (candidate < 0 || candidate >= allSamples.length || seen[candidate]) return false;
                seen[candidate] = true;
            }
            availableSampleIndices.clear();
            for (int candidate : pool) availableSampleIndices.add(candidate);
            lastSampleIndex = last;
            return true;
        } catch (RuntimeException badState) { return false; }
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        int[] pool = new int[availableSampleIndices.size()];
        for (int i = 0; i < pool.length; i++) pool[i] = availableSampleIndices.get(i);
        outState.putIntArray("sample_pool", pool);
        outState.putInt("last_sample", lastSampleIndex);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        currentAnalysisVersion++;
        if (analysisDispatcher != null) analysisDispatcher.close();
        mainHandler.removeCallbacksAndMessages(null);
        pendingAnalysisRunnable = null;
        super.onDestroy();
    }

    // High-performance Recycled Adapter embedded directly inside MainActivity
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pattern_card, parent, false);
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
