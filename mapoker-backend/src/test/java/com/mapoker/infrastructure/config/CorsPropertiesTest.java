package com.mapoker.infrastructure.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CorsPropertiesTest {
    @Test
    void emptyOriginsDoNotFallBackToWildcard() {
        assertThat(new CorsProperties(List.of(), null, null, true).allowedOriginPatterns())
                .containsExactly("http://localhost:3000");
    }

    @Test
    void credentialedWildcardIsRejected() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*"), null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
