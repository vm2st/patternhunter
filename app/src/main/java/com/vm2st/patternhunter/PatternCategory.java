package com.vm2st.patternhunter;

public enum PatternCategory {
    BOUNDARY("Boundary & Symmetry", "Границы и симметрия", "#1D4ED8", "#EFF6FF", "#93C5FD", "#1E3A8A"),
    RUNS("Identical Runs & Clusters", "Повторы и скопления", "#B45309", "#FEF3C7", "#FDE68A", "#78350F"),
    SANDWICH("Sandwich Structures", "Сэндвич-структуры", "#047857", "#ECFDF5", "#6EE7B7", "#064E3B"),
    PROGRESSION("Progressions & Order", "Прогрессии и порядок", "#6D28D9", "#F3E8FF", "#C4B5FD", "#4C1D95"),
    PALINDROME("Palindromes & Reflections", "Палиндромы и отражения", "#BE185D", "#FDF2F8", "#F5D0FE", "#701A75"),
    PERIODICITY("Periodicity & Distances", "Периодичность и расстояния", "#4338CA", "#EEF2FF", "#A5B4FC", "#312E81"),
    ALTERNATION("Alternations & Rhythms", "Чередования и ритмы", "#C2410C", "#FFF7ED", "#FED7AA", "#7C2D12"),
    FREQUENCY("Frequency & Dominance", "Частоты и баланс", "#0369A1", "#F0F9FF", "#7DD3FC", "#0C4A6E"),
    STRUCTURE("Structural Regularity", "Структурный порядок", "#374151", "#F3F4F6", "#D1D5DB", "#1F2937");

    private final String titleEn;
    private final String titleRu;
    private final String lightTextColor;
    private final String lightBgColor;
    private final String darkTextColor;
    private final String darkBgColor;

    PatternCategory(String titleEn, String titleRu, String lightTextColor, String lightBgColor, String darkTextColor, String darkBgColor) {
        this.titleEn = titleEn;
        this.titleRu = titleRu;
        this.lightTextColor = lightTextColor;
        this.lightBgColor = lightBgColor;
        this.darkTextColor = darkTextColor;
        this.darkBgColor = darkBgColor;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public String getTitle(boolean isRussian) {
        return isRussian ? titleRu : titleEn;
    }

    public String getLightTextColor() {
        return lightTextColor;
    }

    public String getLightBgColor() {
        return lightBgColor;
    }

    public String getDarkTextColor() {
        return darkTextColor;
    }

    public String getDarkBgColor() {
        return darkBgColor;
    }
}
