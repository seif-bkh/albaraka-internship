package com.albaraka.backend.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wording rule that survived the removal of the second engine: a gap refusal is
 * replaced by the bank's own sentence, a policy refusal is not.
 */
class RefusalHandlingTests {

    private final RefusalHandling handling = new RefusalHandling();

    @Test
    void replacesProseForAGapRefusal() {
        assertThat(handling.replacesEngineProse("RAGLAB_REFUSED_INSUFFICIENT_EVIDENCE", false)).isTrue();
        // A refusal with no machine code is treated as a gap: the customer must not
        // read the engine's operator-facing wording either way.
        assertThat(handling.replacesEngineProse("RAGLAB_REFUSED", false)).isTrue();
    }

    @Test
    void keepsAPolicyRefusal() {
        assertThat(handling.replacesEngineProse("RAGLAB_REFUSED_OUT_OF_SCOPE", false)).isFalse();
        assertThat(handling.replacesEngineProse("RAGLAB_REFUSED_PRIVATE_OR_LIVE_REQUEST", false)).isFalse();
    }

    @Test
    void neverTouchesAGroundedAnswer() {
        assertThat(handling.replacesEngineProse("RAGLAB_ANSWERED", true)).isFalse();
        assertThat(handling.replacesEngineProse("RAGLAB_REFUSED_OUT_OF_SCOPE", true)).isFalse();
    }

    @Test
    void finalReasonsMatchCaseInsensitively() {
        assertThat(RefusalHandling.isGapRefusal("RAGLAB_REFUSED_OUT_OF_SCOPE")).isFalse();
        assertThat(RefusalHandling.isGapRefusal("RAGLAB_REFUSED_Out_Of_Scope")).isFalse();
        assertThat(RefusalHandling.isGapRefusal("RAGLAB_REFUSED_INSUFFICIENT_EVIDENCE")).isTrue();
        assertThat(RefusalHandling.isGapRefusal(null)).isTrue();
    }
}
