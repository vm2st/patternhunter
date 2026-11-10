package com.vm2st.patternhunter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.AbstractList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PatternHunterEngine {

    private static final String VOWELS_LATIN = "aeiouy";
    private static final String VOWELS_CYRILLIC = "аеёиоуыэюя";
    // Bounds also used by the input field; never silently analyze only a prefix.
    public static final int MAX_INPUT_UTF16_LENGTH = 16384;
    static final int MAX_PREVIEW_TOKENS = 96;
    private static final String CYRILLIC_ALPHABET = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final int MAX_PATTERNS_OUTPUT = 150;

    public static AnalysisSummary analyze(String input) {
        checkCancelled();
        if (input != null && input.length() > MAX_INPUT_UTF16_LENGTH) {
            throw new IllegalArgumentException("Input exceeds " + MAX_INPUT_UTF16_LENGTH + " UTF-16 units");
        }
        if (input == null || input.isEmpty()) {
            return new AnalysisSummary(0, 0, 0.0, "Empty", "Пусто", Collections.<PatternItem>emptyList());
        }

        // Tokenize into full Unicode code points so emojis and surrogate pairs are never split
        final String[] tokens = tokenizeToCodePoints(input);
        final int n = tokens.length;
        if (n == 0) {
            return new AnalysisSummary(0, 0, 0.0, "Empty", "Пусто", Collections.<PatternItem>emptyList());
        }

        final BoundedItems items = new BoundedItems();

        // 1. Structural & Domain Classification
        DomainInfo domain = classifyDomain(tokens);
        double entropy = calculateShannonEntropy(tokens);

        // 2. Runs of Identical Characters (e.g., "999" in 278179499912, "🚀🚀🚀")
        findIdenticalRuns(tokens, items);

        // 3. Boundary & Symmetry (e.g., "2 at start and end" in 278179499912)
        findBoundaryAndSymmetry(tokens, items);

        // 4. Symmetric Index Pairs (e.g., "o after first and before last" in goofhfigperos)
        findSymmetricPairs(tokens, items);

        // 5. Sandwiches (e.g., "fhf" in goofhfigperos, "949" in 278179499912, "🚀★🚀")
        findSandwiches(tokens, items);

        // 6. Arithmetic digit and alphabetic progressions
        findProgressions(tokens, items);

        // 7. Palindromes and Reflections
        findPalindromesAndReversals(tokens, items);

        // 8. Alternations (Vowels/Consonants, Letters/Digits, Parity)
        findAlternations(tokens, items);

        // 9. Tandem Repeats (WW, WWW)
        findTandemRepeats(tokens, items);

        // 10. Frequency, Dominance, Periodicity & Pair Distances
        findFrequencyAndPeriodicity(tokens, items);

        // Keep only the best unique results while searching, not after building a huge list.
        List<PatternItem> filtered = items.sorted();

        return new AnalysisSummary(n, domain.uniqueCount, entropy, domain.nameEn, domain.nameRu, filtered);
    }

    public static String[] tokenizeToCodePoints(String s) {
        if (s == null || s.isEmpty()) return new String[0];
        List<String> tokens = new ArrayList<>();
        int len = s.length();
        int i = 0;
        while (i < len) {
            checkCancelled();
            int codePoint = s.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            tokens.add(s.substring(i, i + charCount));
            i += charCount;
        }
        return tokens.toArray(new String[0]);
    }

    private static String joinTokens(String[] tokens, int start, int end) {
        StringBuilder sb = new StringBuilder();
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(tokens.length, end);
        for (int i = safeStart; i < safeEnd; i++) {
            checkCancelled();
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    private static void findIdenticalRuns(String[] tokens, List<PatternItem> items) {
        final int n = tokens.length;
        int i = 0;
        while (i < n) {
            checkCancelled();
            int j = i;
            while (j < n && tokens[j].equals(tokens[i])) {
                checkCancelled();
                j++;
            }
            int count = j - i;
            if (count >= 2) {
                String token = tokens[i];
                String sub = previewTokens(tokens, i, j);
                int startPos = i + 1;
                int endPos = j;

                double midpoint = (i + j - 1) / 2.0;
                double ratio = n > 1 ? midpoint / (n - 1) : 0.5;

                String posEn;
                String posRu;

                if (i == 0) {
                    posEn = String.format(Locale.US, "at the start (positions 1–%d)", endPos);
                    posRu = String.format(Locale.US, "в начале строки (позиции 1–%d)", endPos);
                } else if (j == n) {
                    posEn = String.format(Locale.US, "at the end (positions %d–%d)", startPos, endPos);
                    posRu = String.format(Locale.US, "в конце строки (позиции %d–%d)", startPos, endPos);
                } else if (ratio >= 0.20 && ratio <= 0.80) {
                    posEn = String.format(Locale.US, "in the middle of the string (positions %d–%d)", startPos, endPos);
                    posRu = String.format(Locale.US, "в середине строки (позиции %d–%d)", startPos, endPos);
                } else if (ratio < 0.20) {
                    posEn = String.format(Locale.US, "in the first third (positions %d–%d)", startPos, endPos);
                    posRu = String.format(Locale.US, "в первой трети строки (позиции %d–%d)", startPos, endPos);
                } else {
                    posEn = String.format(Locale.US, "in the last third (positions %d–%d)", startPos, endPos);
                    posRu = String.format(Locale.US, "в последней трети строки (позиции %d–%d)", startPos, endPos);
                }

                String countWordEn = countToWordEn(count);
                String descEn = String.format(Locale.US, "%s consecutive '%s's ('%s') %s", countWordEn, token, sub, posEn);
                String descRu = formatRussianRun(count, token, sub, posRu);

                int priority = 90 + Math.min(count * 5, 25);
                items.add(new PatternItem(PatternCategory.RUNS, descEn, descRu, sub, startPos, endPos, priority));
            }
            i = j;
        }
    }

    private static String formatRussianRun(int count, String token, String sub, String posRu) {
        String countWord;
        String symbolWord;

        int rem100 = count % 100;
        int rem10 = count % 10;

        if (count == 2) {
            countWord = "Два";
            symbolWord = "символа";
        } else if (count == 3) {
            countWord = "Три";
            symbolWord = "символа";
        } else if (count == 4) {
            countWord = "Четыре";
            symbolWord = "символа";
        } else if (rem100 >= 11 && rem100 <= 19) {
            countWord = String.valueOf(count);
            symbolWord = "символов";
        } else if (rem10 == 1) {
            countWord = String.valueOf(count);
            symbolWord = "символ";
        } else if (rem10 >= 2 && rem10 <= 4) {
            countWord = String.valueOf(count);
            symbolWord = "символа";
        } else {
            countWord = String.valueOf(count);
            symbolWord = "символов";
        }

        return String.format(Locale.US, "%s подряд %s '%s' ('%s') %s", countWord, symbolWord, token, sub, posRu);
    }

    private static void findBoundaryAndSymmetry(String[] tokens, List<PatternItem> items) {
        final int n = tokens.length;
        if (n < 2) return;

        // Start and end match
        if (tokens[0].equals(tokens[n - 1])) {
            String token = tokens[0];
            String descEn = String.format(Locale.US, "Matching boundary characters: string starts and ends with '%s'", token);
            String descRu = String.format(Locale.US, "Одинаковые символы на границах: в начале и в конце '%s'", token);
            items.add(new PatternItem(PatternCategory.BOUNDARY, descEn, descRu, String.format(Locale.US, "%s...%s", token, token), 1, n, 95));
        }

        // Matching prefix & suffix of length k >= 2
        int maxK = Math.min(n / 2, 50);
        for (int k = 2; k <= maxK; k++) {
            checkCancelled();
            String prefix = joinTokens(tokens, 0, k);
            String suffix = joinTokens(tokens, n - k, n);
            if (prefix.equals(suffix)) {
                String descEn = String.format(Locale.US, "Identical prefix and suffix of length %d: '%s' at both ends", k, prefix);
                String descRu = String.format(Locale.US, "Одинаковый префикс и суффикс длины %d: '%s' на обоих концах", k, prefix);
                items.add(new PatternItem(PatternCategory.BOUNDARY, descEn, descRu, prefix, 1, n, 88 + k));
            }
        }

        // Center element or central pair
        if (n >= 3 && n % 2 == 1) {
            int mid = n / 2;
            String midToken = tokens[mid];
            String descEn = String.format(Locale.US, "Center of string is '%s' at position %d", midToken, mid + 1);
            String descRu = String.format(Locale.US, "Центральный символ строки: '%s' (позиция %d)", midToken, mid + 1);
            items.add(new PatternItem(PatternCategory.BOUNDARY, descEn, descRu, midToken, mid + 1, mid + 1, 60));
        } else if (n >= 4 && n % 2 == 0) {
            int m1 = n / 2 - 1;
            int m2 = n / 2;
            String midPair = joinTokens(tokens, m1, m2 + 1);
            String descEn = String.format(Locale.US, "Central pair of symbols: '%s' (positions %d–%d)", midPair, m1 + 1, m2 + 1);
            String descRu = String.format(Locale.US, "Центральная пара символов: '%s' (позиции %d–%d)", midPair, m1 + 1, m2 + 1);
            items.add(new PatternItem(PatternCategory.BOUNDARY, descEn, descRu, midPair, m1 + 1, m2 + 1, 55));
        }
    }

    private static void findSymmetricPairs(String[] tokens, List<PatternItem> items) {
        final int n = tokens.length;
        if (n < 4) return;

        int limit = Math.min(n / 2, 20);
        for (int k = 1; k < limit; k++) {
            checkCancelled();
            if (tokens[k].equals(tokens[n - 1 - k])) {
                String token = tokens[k];
                int p1 = k + 1;
                int p2 = n - k;

                String descEn;
                String descRu;

                if (k == 1) {
                    descEn = String.format(Locale.US, "Symmetric pair: right after the first character (pos 2) and before the last (pos %d) is '%s'", p2, token);
                    descRu = String.format(Locale.US, "Симметричная пара: сразу после первой буквы (поз. 2) и перед последней (поз. %d) стоит '%s'", p2, token);
                } else {
                    descEn = String.format(Locale.US, "Symmetric mirror pair at distance %d from edges: positions %d and %d are both '%s'", k, p1, p2, token);
                    descRu = String.format(Locale.US, "Симметричная пара на расстоянии %d от краёв: позиции %d и %d — это '%s'", k, p1, p2, token);
                }

                items.add(new PatternItem(PatternCategory.BOUNDARY, descEn, descRu, String.format(Locale.US, "%s (pos %d & %d)", token, p1, p2), p1, p2, 85 - k));
            }
        }
    }

    private static void findSandwiches(String[] tokens, List<PatternItem> items) {
        final int n = tokens.length;
        if (n < 3) return;

        // XYX sandwiches
        for (int i = 0; i < n - 2; i++) {
            checkCancelled();
            if (tokens[i].equals(tokens[i + 2]) && !tokens[i].equals(tokens[i + 1])) {
                String x = tokens[i];
                String y = tokens[i + 1];
                String sub = joinTokens(tokens, i, i + 3);

                String descEn = String.format(Locale.US, "Sandwich structure '%s': two '%s's (pos %d and %d) with '%s' in between (pos %d)", sub, x, i + 1, i + 3, y, i + 2);
                String descRu = String.format(Locale.US, "Сэндвич-структура '%s': две буквы '%s' (поз. %d и %d), между которыми находится '%s' (поз. %d)", sub, x, i + 1, i + 3, y, i + 2);

                items.add(new PatternItem(PatternCategory.SANDWICH, descEn, descRu, sub, i + 1, i + 3, 82));
            }
        }

        // Wide sandwiches: X Y1 Y2 X (length 4)
        for (int i = 0; i < n - 3; i++) {
            checkCancelled();
            if (tokens[i].equals(tokens[i + 3]) && !tokens[i].equals(tokens[i + 1]) && !tokens[i].equals(tokens[i + 2])) {
                String x = tokens[i];
                String inner = joinTokens(tokens, i + 1, i + 3);
                String sub = joinTokens(tokens, i, i + 4);

                String descEn = String.format(Locale.US, "Wide sandwich '%s': two '%s's (pos %d and %d) enclosing '%s'", sub, x, i + 1, i + 4, inner);
                String descRu = String.format(Locale.US, "Широкий сэндвич '%s': две '%s' (поз. %d и %d) обрамляют фрагмент '%s'", sub, x, i + 1, i + 4, inner);

                items.add(new PatternItem(PatternCategory.SANDWICH, descEn, descRu, sub, i + 1, i + 4, 75));
            }
        }

        // Symmetrical 5-char sandwich: X Y Z Y X (e.g. 94149, abcba, 🚀★🔥★🚀)
        for (int i = 0; i < n - 4; i++) {
            checkCancelled();
            if (tokens[i].equals(tokens[i + 4]) && tokens[i + 1].equals(tokens[i + 3]) && !tokens[i].equals(tokens[i + 1])) {
                String sub = joinTokens(tokens, i, i + 5);
                String descEn = String.format(Locale.US, "Symmetrical double sandwich '%s' (positions %d–%d)", sub, i + 1, i + 5);
                String descRu = String.format(Locale.US, "Симметричный двойной сэндвич '%s' (позиции %d–%d)", sub, i + 1, i + 5);
                items.add(new PatternItem(PatternCategory.SANDWICH, descEn, descRu, sub, i + 1, i + 5, 87));
            }
        }
    }

    private static void findProgressions(String[] tokens, List<PatternItem> items) {
        final int n = tokens.length;
        if (n < 2) return;

        // Digits: ascending +1
        int i = 0;
        while (i < n) {
            checkCancelled();
            if (isAsciiDigit(tokens[i])) {
                int j = i;
                while (j + 1 < n && isAsciiDigit(tokens[j + 1]) && getAsciiDigitVal(tokens[j + 1]) == getAsciiDigitVal(tokens[j]) + 1) {
                    checkCancelled();
                    j++;
                }
                int len = j - i + 1;
                if (len >= 2 && (n <= 12 || len >= 3)) {
                    String sub = joinTokens(tokens, i, j + 1);
                    String descEn = String.format(Locale.US, "Ascending digit run: '%s' (step +1, positions %d–%d)", sub, i + 1, j + 1);
                    String descRu = String.format(Locale.US, "Возрастающая последовательность цифр: '%s' (шаг +1, позиции %d–%d)", sub, i + 1, j + 1);
                    items.add(new PatternItem(PatternCategory.PROGRESSION, descEn, descRu, sub, i + 1, j + 1, 70 + len * 3));
                }

                // Digits: descending -1
                j = i;
                while (j + 1 < n && isAsciiDigit(tokens[j + 1]) && getAsciiDigitVal(tokens[j + 1]) == getAsciiDigitVal(tokens[j]) - 1) {
                    checkCancelled();
                    j++;
                }
                len = j - i + 1;
                if (len >= 2 && (n <= 12 || len >= 3)) {
                    String sub = joinTokens(tokens, i, j + 1);
                    String descEn = String.format(Locale.US, "Descending digit run: '%s' (step -1, positions %d–%d)", sub, i + 1, j + 1);
                    String descRu = String.format(Locale.US, "Убывающая последовательность цифр: '%s' (шаг -1, позиции %d–%d)", sub, i + 1, j + 1);
                    items.add(new PatternItem(PatternCategory.PROGRESSION, descEn, descRu, sub, i + 1, j + 1, 70 + len * 3));
                }

                // Digits: step +/- 2 (e.g. 02468, 13579)
                for (int step : new int[]{2, -2}) {
                    checkCancelled();
                    j = i;
                    while (j + 1 < n && isAsciiDigit(tokens[j + 1]) && getAsciiDigitVal(tokens[j + 1]) == getAsciiDigitVal(tokens[j]) + step) {
                        checkCancelled();
                        j++;
                    }
                    len = j - i + 1;
                    if (len >= 3) {
                        String sub = joinTokens(tokens, i, j + 1);
                        String descEn = String.format(Locale.US, "Arithmetic digit progression: '%s' (step %+d, positions %d–%d)", sub, step, i + 1, j + 1);
                        String descRu = String.format(Locale.US, "Арифметическая прогрессия цифр: '%s' (шаг %+d, позиции %d–%d)", sub, step, i + 1, j + 1);
                        items.add(new PatternItem(PatternCategory.PROGRESSION, descEn, descRu, sub, i + 1, j + 1, 75 + len * 2));
                    }
                }
            }

            // Alphabetical: Latin or Cyrillic (Script-bounded strictly)
            if (isAlphabetLetter(tokens[i])) {
                // Ascending letters
                int j = i;
                while (j + 1 < n && isSameAlphabetScript(tokens[j], tokens[j + 1]) && getAlphabetRank(tokens[j + 1]) == getAlphabetRank(tokens[j]) + 1) {
                    checkCancelled();
                    j++;
                }
                int len = j - i + 1;
                if (len >= 3) {
                    String sub = joinTokens(tokens, i, j + 1);
                    String descEn = String.format(Locale.US, "Alphabetical ascending run: '%s' (positions %d–%d)", sub, i + 1, j + 1);
                    String descRu = String.format(Locale.US, "Алфавитная возрастающая цепочка: '%s' (позиции %d–%d)", sub, i + 1, j + 1);
                    items.add(new PatternItem(PatternCategory.PROGRESSION, descEn, descRu, sub, i + 1, j + 1, 72 + len * 3));
                }

                // Descending letters
                j = i;
                while (j + 1 < n && isSameAlphabetScript(tokens[j], tokens[j + 1]) && getAlphabetRank(tokens[j + 1]) == getAlphabetRank(tokens[j]) - 1) {
                    checkCancelled();
                    j++;
                }
                len = j - i + 1;
                if (len >= 3) {
                    String sub = joinTokens(tokens, i, j + 1);
                    String descEn = String.format(Locale.US, "Alphabetical descending run: '%s' (positions %d–%d)", sub, i + 1, j + 1);
                    String descRu = String.format(Locale.US, "Алфавитная убывающая цепочка: '%s' (позиции %d–%d)", sub, i + 1, j + 1);
                    items.add(new PatternItem(PatternCategory.PROGRESSION, descEn, descRu, sub, i + 1, j + 1, 72 + len * 3));
                }
            }
            i++;
        }
    }

    private static void findPalindromesAndReversals(String[] tokens, List<PatternItem> items) {
        final int n = tokens.length;
        if (n < 3) return;

        // Full palindrome
        if (isPalindrome(tokens, 0, n - 1)) {
            String fullStr = previewTokens(tokens, 0, n);
            String descEn = String.format(Locale.US, "Entire string is a perfect palindrome of length %d!", n);
            String descRu = String.format(Locale.US, "Вся строка является совершенным палиндромом длины %d!", n);
            items.add(new PatternItem(PatternCategory.PALINDROME, descEn, descRu, fullStr, 1, n, 100));
            return;
        }

        int searchLimit = n;

        // Substring palindromes (length >= 3)
        for (int len = Math.min(searchLimit - 1, 10); len >= 3; len--) {
            checkCancelled();
            for (int st = 0; st <= searchLimit - len; st++) {
                checkCancelled();
                int en = st + len - 1;
                if (isPalindrome(tokens, st, en)) {
                    // Check if it is not just identical chars
                    boolean allSame = true;
                    for (int k = st + 1; k <= en; k++) {
                        checkCancelled();
                        if (!tokens[k].equals(tokens[st])) {
                            allSame = false;
                            break;
                        }
                    }
                    if (!allSame) {
                        String sub = previewTokens(tokens, st, en + 1);
                        String descEn = String.format(Locale.US, "Palindromic substring of length %d: '%s' (positions %d–%d)", len, sub, st + 1, en + 1);
                        String descRu = String.format(Locale.US, "Палиндромная подстрока длины %d: '%s' (позиции %d–%d)", len, sub, st + 1, en + 1);
                        items.add(new PatternItem(PatternCategory.PALINDROME, descEn, descRu, sub, st + 1, en + 1, 80 + len * 2));
                    }
                }
            }
        }

        // Exact reverse-pair lookup. At each length, later than the first 150 pairs cannot
        // enter the final top 150 (same priority and nondecreasing start positions).
        if (searchLimit >= 6) {
            for (int wlen = 3; wlen <= Math.min(6, searchLimit / 2); wlen++) {
                checkCancelled();
                Map<String, List<Integer>> index = new HashMap<>();
                for (int pos = 0; pos <= searchLimit - wlen; pos++) {
                    checkCancelled();
                    String key = joinTokens(tokens, pos, pos + wlen);
                    List<Integer> positions = index.get(key);
                    if (positions == null) { positions = new ArrayList<>(); index.put(key, positions); }
                    positions.add(pos);
                }
                int emitted = 0;
                for (int p1 = 0; p1 <= searchLimit - 2 * wlen && emitted < MAX_PATTERNS_OUTPUT; p1++) {
                    checkCancelled();
                    String sub = joinTokens(tokens, p1, p1 + wlen);
                    String rev = reverseJoined(tokens, p1, p1 + wlen);
                    if (sub.equals(rev)) continue;
                    List<Integer> positions = index.get(rev);
                    if (positions == null) continue;
                    int start = Collections.binarySearch(positions, p1 + wlen);
                    if (start < 0) start = -start - 1;
                    for (int k = start; k < positions.size() && emitted < MAX_PATTERNS_OUTPUT; k++) {
                        checkCancelled();
                        int p2 = positions.get(k);
                        String descEn = String.format(Locale.US, "Reversed mirror pair: '%s' (pos %d–%d) and '%s' (pos %d–%d)", sub, p1 + 1, p1 + wlen, rev, p2 + 1, p2 + wlen);
                        String descRu = String.format(Locale.US, "Зеркальная пара подстрок: '%s' (поз. %d–%d) и '%s' (поз. %d–%d)", sub, p1 + 1, p1 + wlen, rev, p2 + 1, p2 + wlen);
                        items.add(new PatternItem(PatternCategory.PALINDROME, descEn, descRu, String.format(Locale.US, "%s ⇄ %s", sub, rev), p1 + 1, p2 + wlen, 78));
                        emitted++;
                    }
                }
            }
        }
    }

    private static void findAlternations(String[] tokens, List<PatternItem> items) {
        final int n = tokens.length;
        if (n < 4) return;

        // Vowels and Consonants alternation (e.g. "peros" -> CVCVC)
        int st = 0;
        while (st < n) {
            checkCancelled();
            if (isPhoneticLetter(tokens[st])) {
                int en = st;
                while (en + 1 < n && isPhoneticLetter(tokens[en + 1])) {
                    checkCancelled();
                    boolean curVowel = isVowelToken(tokens[en]);
                    boolean nextVowel = isVowelToken(tokens[en + 1]);
                    if ((curVowel && !nextVowel) || (!curVowel && nextVowel)) {
                        en++;
                    } else {
                        break;
                    }
                }
                int len = en - st + 1;
                if (len >= 4) {
                    String sub = previewTokens(tokens, st, en + 1);
                    StringBuilder pattern = new StringBuilder();
                    for (int k = st; k <= Math.min(en, st + MAX_PREVIEW_TOKENS - 1); k++) {
                        checkCancelled();
                        pattern.append(isVowelToken(tokens[k]) ? "V" : "C");
                    }
                    if (len > MAX_PREVIEW_TOKENS) pattern.append("…");
                    String descEn = String.format(Locale.US, "Alternating consonants & vowels (%s): '%s' (positions %d–%d)", pattern.toString(), sub, st + 1, en + 1);
                    String descRu = String.format(Locale.US, "Чередование согласных и гласных (%s): '%s' (позиции %d–%d)", pattern.toString(), sub, st + 1, en + 1);
                    items.add(new PatternItem(PatternCategory.ALTERNATION, descEn, descRu, sub, st + 1, en + 1, 74 + len));
                    st = en;
                }
            }
            st++;
        }

        // Letter & Digit alternation (e.g. a1b2c3)
        st = 0;
        while (st < n) {
            checkCancelled();
            int en = st;
            while (en + 1 < n) {
                checkCancelled();
                boolean curL = isLetterToken(tokens[en]);
                boolean curD = isAsciiDigit(tokens[en]);
                boolean nextL = isLetterToken(tokens[en + 1]);
                boolean nextD = isAsciiDigit(tokens[en + 1]);
                if ((curL && nextD) || (curD && nextL)) {
                    en++;
                } else {
                    break;
                }
            }
            int len = en - st + 1;
            if (len >= 4) {
                String sub = previewTokens(tokens, st, en + 1);
                String descEn = String.format(Locale.US, "Alternating letters & digits: '%s' (positions %d–%d)", sub, st + 1, en + 1);
                String descRu = String.format(Locale.US, "Строгое чередование букв и цифр: '%s' (позиции %d–%d)", sub, st + 1, en + 1);
                items.add(new PatternItem(PatternCategory.ALTERNATION, descEn, descRu, sub, st + 1, en + 1, 76 + len));
                st = en;
            }
            st++;
        }

        // Parity alternation in digits (even-odd-even-odd)
        st = 0;
        while (st < n) {
            checkCancelled();
            if (isAsciiDigit(tokens[st])) {
                int en = st;
                while (en + 1 < n && isAsciiDigit(tokens[en + 1])) {
                    checkCancelled();
                    int p1 = getAsciiDigitVal(tokens[en]) % 2;
                    int p2 = getAsciiDigitVal(tokens[en + 1]) % 2;
                    if (p1 != p2) {
                        en++;
                    } else {
                        break;
                    }
                }
                int len = en - st + 1;
                if (len >= 4) {
                    String sub = previewTokens(tokens, st, en + 1);
                    String descEn = String.format(Locale.US, "Alternating even/odd digits: '%s' (positions %d–%d)", sub, st + 1, en + 1);
                    String descRu = String.format(Locale.US, "Чередование чётных и нечётных цифр: '%s' (позиции %d–%d)", sub, st + 1, en + 1);
                    items.add(new PatternItem(PatternCategory.ALTERNATION, descEn, descRu, sub, st + 1, en + 1, 72 + len));
                    st = en;
                }
            }
            st++;
        }
    }

    private static void findTandemRepeats(String[] tokens, List<PatternItem> items) {
        final int n = tokens.length;
        if (n < 4) return;

        for (int wlen = 2; wlen <= Math.min(n / 2, 20); wlen++) {
            checkCancelled();
            for (int st = 0; st <= n - 2 * wlen; st++) {
                checkCancelled();
                String w1 = joinTokens(tokens, st, st + wlen);
                String w2 = joinTokens(tokens, st + wlen, st + 2 * wlen);
                if (w1.equals(w2)) {
                    // Check for triple (WWW)
                    int count = 2;
                    while (st + (count + 1) * wlen <= n && joinTokens(tokens, st + count * wlen, st + (count + 1) * wlen).equals(w1)) {
                        checkCancelled();
                        count++;
                    }
                    String fullRepeat = previewTokens(tokens, st, st + count * wlen);
                    String descEn = String.format(Locale.US, "Tandem repeat: '%s' repeated %d times ('%s', positions %d–%d)", w1, count, fullRepeat, st + 1, st + count * wlen);
                    String descRu = String.format(Locale.US, "Тандемный повтор: блок '%s' повторяется %d раз подряд ('%s', позиции %d–%d)", w1, count, fullRepeat, st + 1, st + count * wlen);
                    items.add(new PatternItem(PatternCategory.RUNS, descEn, descRu, fullRepeat, st + 1, st + count * wlen, 85 + count * 3));
                    st += (count - 1) * wlen; // Skip ahead
                }
            }
        }
    }

    private static void findFrequencyAndPeriodicity(String[] tokens, List<PatternItem> items) {
        final int n = tokens.length;
        Map<String, List<Integer>> posMap = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            checkCancelled();
            String token = tokens[i];
            List<Integer> list = posMap.get(token);
            if (list == null) {
                list = new ArrayList<>();
                posMap.put(token, list);
            }
            list.add(i);
        }

        // Most frequent character
        String mostFrequentToken = "";
        int maxCount = 0;
        for (Map.Entry<String, List<Integer>> entry : posMap.entrySet()) {
            checkCancelled();
            if (entry.getValue().size() > maxCount) {
                maxCount = entry.getValue().size();
                mostFrequentToken = entry.getKey();
            }
        }

        if (maxCount >= 3 || (maxCount >= 2 && n <= 5)) {
            double pct = ((double) maxCount / n) * 100.0;
            String descEn = String.format(Locale.US, "Dominant symbol '%s' appears %d times (%.1f%% of total string)", mostFrequentToken, maxCount, pct);
            String descRu = String.format(Locale.US, "Доминирующий символ: '%s' встречается %d раз (%.1f%% всей строки)", mostFrequentToken, maxCount, pct);
            items.add(new PatternItem(PatternCategory.FREQUENCY, descEn, descRu, String.format(Locale.US, "'%s' x%d", mostFrequentToken, maxCount), 68));
        }

        // Strict periodicity or Pair Distances
        for (Map.Entry<String, List<Integer>> entry : posMap.entrySet()) {
            checkCancelled();
            String token = entry.getKey();
            List<Integer> positions = entry.getValue();

            if (positions.size() >= 3) {
                int firstDiff = positions.get(1) - positions.get(0);
                boolean isPeriodic = true;
                for (int k = 1; k < positions.size() - 1; k++) {
                    checkCancelled();
                    if (positions.get(k + 1) - positions.get(k) != firstDiff) {
                        isPeriodic = false;
                        break;
                    }
                }
                if (isPeriodic && firstDiff > 1) {
                    String descEn = String.format(Locale.US, "Strict periodicity: symbol '%s' appears %d times at constant interval of %d symbols", token, positions.size(), firstDiff);
                    String descRu = String.format(Locale.US, "Строгая периодичность: символ '%s' повторяется %d раз с точным шагом в %d симв.", token, positions.size(), firstDiff);
                    items.add(new PatternItem(PatternCategory.PERIODICITY, descEn, descRu, String.format(Locale.US, "'%s' step %d", token, firstDiff), 84));
                }
            } else if (positions.size() == 2) {
                int p1 = positions.get(0) + 1;
                int p2 = positions.get(1) + 1;
                int dist = positions.get(1) - positions.get(0);
                if (dist > 2) {
                    String descEn = String.format(Locale.US, "Pair occurrence: symbol '%s' appears exactly twice at positions %d and %d (distance %d)", token, p1, p2, dist);
                    String descRu = String.format(Locale.US, "Парное появление: символ '%s' встречается ровно 2 раза на позициях %d и %d (расстояние %d)", token, p1, p2, dist);
                    items.add(new PatternItem(PatternCategory.PERIODICITY, descEn, descRu, String.format(Locale.US, "'%s' dist %d", token, dist), p1, p2, 65));
                }
            }
        }
    }

    private static boolean isPalindrome(String[] tokens, int start, int end) {
        while (start < end) {
            checkCancelled();
            if (!tokens[start].equals(tokens[end])) {
                return false;
            }
            start++;
            end--;
        }
        return true;
    }

    private static String reverseJoined(String[] tokens, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = end - 1; i >= start; i--) {
            checkCancelled();
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    private static boolean isAsciiDigit(String token) {
        if (token == null || token.length() != 1) return false;
        char c = token.charAt(0);
        return c >= '0' && c <= '9';
    }

    private static int getAsciiDigitVal(String token) {
        if (token == null || token.isEmpty()) return 0;
        return token.charAt(0) - '0';
    }

    private static boolean isAlphabetLetter(String token) {
        if (token == null || token.length() != 1) return false;
        char c = Character.toLowerCase(token.charAt(0));
        return isAsciiLatin(token.charAt(0)) || CYRILLIC_ALPHABET.indexOf(c) >= 0;
    }

    private static boolean isAsciiLatin(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isPhoneticLetter(String token) {
        if (!isAlphabetLetter(token)) return false;
        char c = Character.toLowerCase(token.charAt(0));
        return c != 'ь' && c != 'ъ';
    }

    private static boolean isSameAlphabetScript(String t1, String t2) {
        if (!isAlphabetLetter(t1) || !isAlphabetLetter(t2)) return false;
        char c1 = t1.charAt(0);
        char c2 = t2.charAt(0);
        boolean latin1 = (c1 >= 'a' && c1 <= 'z') || (c1 >= 'A' && c1 <= 'Z');
        boolean latin2 = (c2 >= 'a' && c2 <= 'z') || (c2 >= 'A' && c2 <= 'Z');
        if (latin1 != latin2) return false;
        boolean upper1 = Character.isUpperCase(c1);
        boolean upper2 = Character.isUpperCase(c2);
        return upper1 == upper2;
    }

    private static int getAlphabetRank(String token) {
        if (token == null || token.isEmpty()) return 0;
        char c = Character.toLowerCase(token.charAt(0));
        if (c >= 'a' && c <= 'z') {
            return c - 'a';
        }
        int cyrillicRank = CYRILLIC_ALPHABET.indexOf(c);
        if (cyrillicRank >= 0) return cyrillicRank;
        return (int) c;
    }

    private static boolean isLetterToken(String token) {
        if (token == null || token.isEmpty()) return false;
        int cp = token.codePointAt(0);
        return Character.isLetter(cp);
    }

    private static boolean isVowelToken(String token) {
        if (token == null || token.isEmpty()) return false;
        String lower = token.toLowerCase(Locale.ROOT);
        return VOWELS_LATIN.contains(lower) || VOWELS_CYRILLIC.contains(lower);
    }

    private static double calculateShannonEntropy(String[] tokens) {
        final int n = tokens.length;
        if (n == 0) return 0.0;
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            checkCancelled();
            Integer cur = counts.get(token);
            counts.put(token, cur == null ? 1 : cur + 1);
        }
        double entropy = 0.0;
        for (int count : counts.values()) {
            checkCancelled();
            double p = (double) count / n;
            entropy -= p * (Math.log(p) / Math.log(2.0));
        }
        return entropy;
    }

    private static DomainInfo classifyDomain(String[] tokens) {
        boolean allDigits = true;
        boolean allBinary = true;
        boolean allHex = true;
        boolean allLatin = true;
        boolean allCyrillic = true;
        boolean allAlphanumeric = true;

        Set<String> uniqueSet = new HashSet<>();

        for (String token : tokens) {
            checkCancelled();
            uniqueSet.add(token);
            int cp = token.codePointAt(0);

            if (!isAsciiDigit(token)) {
                allDigits = false;
                allBinary = false;
            } else if (!token.equals("0") && !token.equals("1")) {
                allBinary = false;
            }

            String lc = token.toLowerCase(Locale.ROOT);
            if (!isAsciiDigit(token)) {
                if (token.length() != 1 || (lc.charAt(0) < 'a' || lc.charAt(0) > 'f')) {
                    allHex = false;
                }
            }

            if (token.length() != 1 || !isAsciiLatin(token.charAt(0))) {
                allLatin = false;
            }

            if (token.length() != 1 || !((lc.charAt(0) >= 'а' && lc.charAt(0) <= 'я') || lc.charAt(0) == 'ё')) {
                allCyrillic = false;
            }

            if (!Character.isLetterOrDigit(cp)) {
                allAlphanumeric = false;
            }
        }

        String en;
        String ru;

        if (allBinary) {
            en = "Pure Binary (0, 1)";
            ru = "Бинарный код (0, 1)";
        } else if (allDigits) {
            en = "Pure Decimal Digits (0–9)";
            ru = "Только десятичные цифры (0–9)";
        } else if (allHex && !allLatin) {
            en = "Hexadecimal (0–9, a–f)";
            ru = "Шестнадцатеричный код (0–9, a–f)";
        } else if (allLatin) {
            en = "Latin Alphabetic (a–z)";
            ru = "Латинские буквы (a–z)";
        } else if (allCyrillic) {
            en = "Cyrillic Alphabetic (а–я)";
            ru = "Кириллические буквы (а–я)";
        } else if (allAlphanumeric) {
            en = "Alphanumeric (Letters & Digits)";
            ru = "Буквы и цифры";
        } else {
            en = "Mixed Symbols / Unicode";
            ru = "Смешанные символы / Юникод";
        }

        return new DomainInfo(uniqueSet.size(), en, ru);
    }

    private static String countToWordEn(int count) {
        switch (count) {
            case 2: return "Two";
            case 3: return "Three";
            case 4: return "Four";
            case 5: return "Five";
            case 6: return "Six";
            case 7: return "Seven";
            case 8: return "Eight";
            case 9: return "Nine";
            default: return String.valueOf(count);
        }
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Analysis cancelled");
    }

    private static String previewTokens(String[] tokens, int start, int end) {
        int safeEnd = Math.min(tokens.length, end);
        int previewEnd = Math.min(safeEnd, start + MAX_PREVIEW_TOKENS);
        return joinTokens(tokens, start, previewEnd) + (previewEnd < safeEnd ? "…" : "");
    }

    private static String resultKey(PatternItem item) {
        return item.getCategory().name() + "|" + item.getSnippet() + "|" + item.getStartPos() + "|" + item.getEndPos() + "|" + item.getDescriptionEn();
    }

    private static final class Ranked {
        final PatternItem item;
        final long order;
        final String key;
        Ranked(PatternItem item, long order, String key) { this.item = item; this.order = order; this.key = key; }
    }

    private static int compareRank(Ranked a, Ranked b) {
        int comparison = a.item.compareTo(b.item);
        return comparison != 0 ? comparison : Long.compare(a.order, b.order);
    }

    private static final class BoundedItems extends AbstractList<PatternItem> {
        private final PriorityQueue<Ranked> heap = new PriorityQueue<>(MAX_PATTERNS_OUTPUT,
                new Comparator<Ranked>() { @Override public int compare(Ranked a, Ranked b) { return compareRank(b, a); } });
        private final Set<String> keys = new HashSet<>();
        private long sequence;
        @Override public boolean add(PatternItem item) {
            Ranked candidate = new Ranked(item, sequence++, null);
            if (heap.size() >= MAX_PATTERNS_OUTPUT && compareRank(candidate, heap.peek()) >= 0) return false;
            String key = resultKey(item);
            if (!keys.add(key)) return false;
            if (heap.size() >= MAX_PATTERNS_OUTPUT) keys.remove(heap.remove().key);
            heap.add(new Ranked(item, candidate.order, key));
            return true;
        }
        @Override public int size() { return heap.size(); }
        @Override public PatternItem get(int index) { return sorted().get(index); }
        @Override public Iterator<PatternItem> iterator() { return sorted().iterator(); }
        List<PatternItem> sorted() {
            List<Ranked> ranked = new ArrayList<>(heap);
            Collections.sort(ranked, new Comparator<Ranked>() { @Override public int compare(Ranked a, Ranked b) { return compareRank(a, b); } });
            List<PatternItem> result = new ArrayList<>(ranked.size());
            for (Ranked entry : ranked) result.add(entry.item);
            return result;
        }
    }

    private static class DomainInfo {
        final int uniqueCount;
        final String nameEn;
        final String nameRu;

        DomainInfo(int uniqueCount, String nameEn, String nameRu) {
            this.uniqueCount = uniqueCount;
            this.nameEn = nameEn;
            this.nameRu = nameRu;
        }
    }
}
