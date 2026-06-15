package com.onestopsports.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Verifies the accent-folding used to make global search accent-insensitive.
// These are the exact cases the QA sweep flagged: a user typing an un-accented
// query should match a stored name that carries diacritics.
class TextNormalizerTest {

    @Test
    void stripsAccentsAndLowercases() {
        // The headline case — "Dembele" (what a user types) must normalize to the
        // same value as the stored "Dembélé".
        assertThat(TextNormalizer.normalize("Dembélé")).isEqualTo("dembele");
        assertThat(TextNormalizer.normalize("Dembele")).isEqualTo("dembele");
    }

    @Test
    void normalizesClubNamesWithAccents() {
        assertThat(TextNormalizer.normalize("Atlético")).isEqualTo("atletico");
        assertThat(TextNormalizer.normalize("Atletico")).isEqualTo("atletico");
    }

    @Test
    void handlesVariousDiacritics() {
        // A spread of accents across different base letters
        assertThat(TextNormalizer.normalize("Müller")).isEqualTo("muller");
        assertThat(TextNormalizer.normalize("Håland")).isEqualTo("haland");
        assertThat(TextNormalizer.normalize("Çağlar")).isEqualTo("caglar");
    }

    @Test
    void leavesPlainAsciiUntouchedExceptForCase() {
        assertThat(TextNormalizer.normalize("Manchester City")).isEqualTo("manchester city");
    }

    @Test
    void returnsNullForNullInput() {
        // Null in → null out, so callers can store it straight through.
        assertThat(TextNormalizer.normalize(null)).isNull();
    }
}
