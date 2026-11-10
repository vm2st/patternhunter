package com.vm2st.patternhunter;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public class AnalysisSummary {
    private final int length;
    private final int uniqueSymbols;
    private final double shannonEntropy;
    private final String characterClassEn;
    private final String characterClassRu;
    private final List<PatternItem> patterns;

    public AnalysisSummary(int length, int uniqueSymbols, double shannonEntropy, String characterClassEn, String characterClassRu, List<PatternItem> patterns) {
        this.length = length;
        this.uniqueSymbols = uniqueSymbols;
        this.shannonEntropy = shannonEntropy;
        this.characterClassEn = characterClassEn;
        this.characterClassRu = characterClassRu;
        this.patterns = patterns != null ? Collections.unmodifiableList(new ArrayList<>(patterns)) : Collections.<PatternItem>emptyList();
    }

    public int getLength() {
        return length;
    }

    public int getUniqueSymbols() {
        return uniqueSymbols;
    }

    public double getShannonEntropy() {
        return shannonEntropy;
    }

    public String getCharacterClass(boolean isRussian) {
        return isRussian ? characterClassRu : characterClassEn;
    }

    public List<PatternItem> getPatterns() {
        return patterns;
    }
}
