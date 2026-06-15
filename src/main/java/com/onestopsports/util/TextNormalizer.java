package com.onestopsports.util;

import java.text.Normalizer;

// Small helper for "accent-folding" text so search can match across diacritics.
//
// The problem this solves: our search used a plain case-insensitive LIKE, so
// typing "Dembele" would never match the stored name "Dembélé". By storing and
// querying a normalized form of every name (accents stripped, lower-cased) the
// two line up and the search matches.
//
// "Normalize" here means two steps:
//   1. Unicode NFD decomposition — splits an accented character like "é" into a
//      plain "e" plus a separate combining accent mark.
//   2. Strip the combining marks, leaving just the base letters, then lower-case.
//
// The same technique already lives inside ApiFootballService for player-name
// lookups; this class centralises it so the entities and search share one rule.
public final class TextNormalizer {

    // Utility class — never instantiated.
    private TextNormalizer() {}

    /**
     * Returns an accent-stripped, lower-cased form of the given text.
     * e.g. "Dembélé" → "dembele", "Atlético" → "atletico".
     * Returns null if the input is null so callers can store null straight through.
     */
    public static String normalize(String text) {
        if (text == null) return null;
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
    }
}
