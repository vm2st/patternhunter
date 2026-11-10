package com.vm2st.patternhunter;

public class PatternItem implements Comparable<PatternItem> {
    private final PatternCategory category;
    private final String descriptionEn;
    private final String descriptionRu;
    private final String snippet;
    private final int startPos;
    private final int endPos;
    private final int priority;

    public PatternItem(PatternCategory category, String descriptionEn, String descriptionRu, String snippet, int startPos, int endPos, int priority) {
        this.category = category;
        this.descriptionEn = descriptionEn;
        this.descriptionRu = descriptionRu;
        this.snippet = snippet != null ? snippet : "";
        this.startPos = startPos;
        this.endPos = endPos;
        this.priority = priority;
    }

    public PatternItem(PatternCategory category, String descriptionEn, String descriptionRu, String snippet, int priority) {
        this(category, descriptionEn, descriptionRu, snippet, 0, 0, priority);
    }

    public PatternCategory getCategory() {
        return category;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public String getDescriptionRu() {
        return descriptionRu;
    }

    public String getDescription(boolean isRussian) {
        return isRussian ? descriptionRu : descriptionEn;
    }

    public String getSnippet() {
        return snippet;
    }

    public int getStartPos() {
        return startPos;
    }

    public int getEndPos() {
        return endPos;
    }

    public boolean hasPositionRange() {
        return startPos > 0 && endPos >= startPos;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public int compareTo(PatternItem other) {
        if (this.priority != other.priority) {
            return Integer.compare(other.priority, this.priority); // Higher priority first
        }
        return Integer.compare(this.startPos, other.startPos);
    }
}
