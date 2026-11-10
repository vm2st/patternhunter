package com.vm2st.patternhunter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.CancellationException;

public class PatternHunterEngineTest {
    private AnalysisSummary analyze(String text) { return PatternHunterEngine.analyze(text); }
    private boolean has(String text, PatternCategory category, String description) {
        for (PatternItem item : analyze(text).getPatterns()) if (item.getCategory()==category && item.getDescriptionEn().contains(description)) return true;
        return false;
    }
    @Test public void nullAndEmptyAreSafe() {
        for (String value : new String[]{null, ""}) {
            AnalysisSummary s=analyze(value);assertEquals(0,s.getLength());assertEquals(0,s.getUniqueSymbols());assertEquals(0,s.getShannonEntropy(),0);assertTrue(s.getPatterns().isEmpty());
        }
    }
    @Test public void symbolsAreUnicodeCodePointsNotUtf16Units() {
        AnalysisSummary s=analyze("😀a😀🚀");assertEquals(4,s.getLength());assertEquals(3,s.getUniqueSymbols());
        assertArrayEquals(new String[]{"😀","a","😀","🚀"},PatternHunterEngine.tokenizeToCodePoints("😀a😀🚀"));
    }
    @Test public void shannonEntropyHasKnownValues() {
        assertEquals(0,analyze("aaaa").getShannonEntropy(),1e-12);
        assertEquals(1,analyze("aabb").getShannonEntropy(),1e-12);
        assertEquals(2,analyze("abcd").getShannonEntropy(),1e-12);
    }
    @Test public void supportedCharacterClassesRemainCorrect() {
        String[][] cases={{"0101","Binary"},{"1239","Decimal"},{"9aB0","Hexadecimal"},{"Pattern","Latin"},{"Ёжик","Cyrillic"},{"a2Z9","Alphanumeric"},{"★😀","Mixed"}};
        for(String[] c:cases)assertTrue(c[0],analyze(c[0]).getCharacterClass(false).contains(c[1]));
    }
    @Test public void nonAsciiCaseMappingDoesNotPretendToBeAsciiLatin() { assertFalse(analyze("İ").getCharacterClass(false).contains("Latin")); }
    @Test public void ascendingAndDescendingDigitsKeepOriginalRules() {
        assertTrue(has("123456789",PatternCategory.PROGRESSION,"Ascending digit"));
        assertTrue(has("987654",PatternCategory.PROGRESSION,"Descending digit"));
        assertTrue(has("02468",PatternCategory.PROGRESSION,"step +2"));
    }
    @Test public void russianAlphabetIncludesYoInTheCorrectPosition() {
        assertTrue(has("деёж",PatternCategory.PROGRESSION,"Alphabetical ascending"));
        assertTrue(has("жёе",PatternCategory.PROGRESSION,"Alphabetical descending"));
        assertTrue(has("ДЕЁЖ",PatternCategory.PROGRESSION,"Alphabetical ascending"));
        assertFalse(has("деж",PatternCategory.PROGRESSION,"Alphabetical ascending"));
    }
    @Test public void alphabeticalRunsDoNotCrossScripts() {
        for(PatternItem p:analyze("абcde").getPatterns())assertFalse(p.getDescriptionEn().startsWith("Alphabetical")&&p.getStartPos()==1&&p.getEndPos()==5);
    }
    @Test public void fullEmojiPalindromeHasCorrectPositions() {
        assertTrue(has("😀a😀",PatternCategory.PALINDROME,"Entire string"));
        for(PatternItem p:analyze("😀a😀").getPatterns())if(p.getDescriptionEn().startsWith("Entire string")){assertEquals(1,p.getStartPos());assertEquals(3,p.getEndPos());}
    }
    @Test public void mirrorPairsUseCodePointPositions() {
        boolean found=false;
        for(PatternItem p:analyze("😀abcXYZcba").getPatterns())if(p.getDescriptionEn().startsWith("Reversed mirror")&&p.getSnippet().equals("abc ⇄ cba")){
            assertEquals(2,p.getStartPos());assertEquals(10,p.getEndPos());found=true;
        }
        assertTrue(found);
    }
    @Test public void sandwichSearchDoesNotStopAfter1000Symbols() {
        for(PatternItem p:analyze("x".repeat(1200)+"abaz").getPatterns())if(p.getCategory()==PatternCategory.SANDWICH&&p.getStartPos()==1201&&p.getSnippet().equals("aba"))return;
        fail("Tail sandwich was skipped");
    }
    @Test public void palindromeSearchDoesNotStopAfter1000Symbols() {
        for(PatternItem p:analyze("x".repeat(1200)+"abcbaZ").getPatterns())if(p.getCategory()==PatternCategory.PALINDROME&&p.getStartPos()==1201&&p.getSnippet().equals("abcba"))return;
        fail("Tail palindrome was skipped");
    }
    @Test public void mirrorSearchDoesNotStopAfter1000Symbols() { assertTrue(has("x".repeat(1200)+"abcZcba",PatternCategory.PALINDROME,"Reversed mirror pair: 'abc'")); }
    @Test public void tandemSearchDoesNotStopAfter1000Symbols() {
        for(PatternItem p:analyze("x".repeat(1200)+"abababZ").getPatterns())if(p.getDescriptionEn().startsWith("Tandem repeat: 'ab'")&&p.getStartPos()==1201&&p.getEndPos()==1206)return;
        fail("Tail tandem was skipped");
    }
    @Test public void unrelatedScriptsAndSignsAreNotCalledConsonants() {
        assertFalse(has("a漢a漢a",PatternCategory.ALTERNATION,"consonants"));
        assertFalse(has("аьаьа",PatternCategory.ALTERNATION,"consonants"));
        assertTrue(has("банана",PatternCategory.ALTERNATION,"consonants"));
    }
    @Test public void whitespaceIsStillRealInputForTheEngine() { assertEquals(3,analyze("   ").getLength());assertTrue(has("   ",PatternCategory.RUNS,"consecutive")); }
    @Test public void longExcerptsAreBoundedAndNeverSplitAnEmoji() {
        AnalysisSummary summary=analyze("😀".repeat(1000));
        for(PatternItem item:summary.getPatterns()){
            assertTrue(item.getSnippet().codePointCount(0,item.getSnippet().length())<=97);
            assertTrue(item.getDescriptionEn().length()<650);assertTrue(item.getDescriptionRu().length()<650);
            String snippet=item.getSnippet();
            for(int i=0;i<snippet.length();i++){
                char c=snippet.charAt(i);
                if(Character.isHighSurrogate(c)){assertTrue(i+1<snippet.length()&&Character.isLowSurrogate(snippet.charAt(++i)));}
                else assertFalse(Character.isLowSurrogate(c));
            }
        }
        assertEquals(1000,summary.getLength());
    }
    @Test(timeout=10000) public void maximumInputKeepsResultCountAndDescriptionsBounded() {
        AnalysisSummary s=analyze(("abac0123210#".repeat(1500)).substring(0,PatternHunterEngine.MAX_INPUT_UTF16_LENGTH));
        assertEquals(PatternHunterEngine.MAX_INPUT_UTF16_LENGTH,s.getLength());assertTrue(s.getPatterns().size()<=150);
        for(PatternItem p:s.getPatterns()){assertTrue(p.getDescriptionEn().length()<650);assertTrue(p.getDescriptionRu().length()<650);}
    }
    @Test(expected=IllegalArgumentException.class) public void excessiveInputIsRejectedBeforeTokenAllocation() { analyze("a".repeat(PatternHunterEngine.MAX_INPUT_UTF16_LENGTH+1)); }
    @Test public void cancellationIsCooperativeAndNeverReturnsPartialStatistics() {
        Thread.currentThread().interrupt();
        try{analyze("abacaba");fail("Expected cancellation");}catch(CancellationException expected){}finally{Thread.interrupted();}
    }
    @Test public void resultsRemainSortedAndHaveValidPositions() {
        AnalysisSummary s=analyze("12321abcabcabc😀abcdcba");
        for(int i=1;i<s.getPatterns().size();i++)assertTrue(s.getPatterns().get(i-1).compareTo(s.getPatterns().get(i))<=0);
        for(PatternItem p:s.getPatterns())if(p.hasPositionRange()){assertTrue(p.getStartPos()>=1);assertTrue(p.getEndPos()<=s.getLength());}
    }
    @Test public void boundedCollectorPreservesOriginalShortAsciiFindingsAndOrder() {
        Random random=new Random(234);String alphabet="abcd0123#";
        for(int example=0;example<100;example++){
            StringBuilder input=new StringBuilder();for(int i=0;i<30;i++)input.append(alphabet.charAt(random.nextInt(alphabet.length())));
            AnalysisSummary expected=ReferencePatternHunterEngine.analyze(input.toString()),actual=analyze(input.toString());
            assertEquals(input.toString(),keys(expected),keys(actual));
        }
    }
    private List<String> keys(AnalysisSummary summary){List<String> result=new ArrayList<>();for(PatternItem p:summary.getPatterns())result.add(p.getCategory()+"|"+p.getPriority()+"|"+p.getStartPos()+"|"+p.getEndPos()+"|"+p.getSnippet()+"|"+p.getDescriptionEn()+"|"+p.getDescriptionRu());return result;}
    @Test public void analysisSummaryIsAnImmutableSnapshot() {
        List<PatternItem> items=new ArrayList<>();items.add(new PatternItem(PatternCategory.RUNS,"a","а","a",1));
        AnalysisSummary s=new AnalysisSummary(1,1,0,"Latin","Латиница",items);items.clear();assertEquals(1,s.getPatterns().size());
        try{s.getPatterns().clear();fail("Mutable summary");}catch(UnsupportedOperationException expected){}
    }
    @Test public void repeatedAnalysisIsDeterministic() { String text="zyxzyxzyxabcdefabcdef";assertEquals(keys(analyze(text)),keys(analyze(text))); }
    @Test public void malformedUtf16DoesNotCrash() { assertEquals(3,analyze("x\uD800y").getLength()); }
}
