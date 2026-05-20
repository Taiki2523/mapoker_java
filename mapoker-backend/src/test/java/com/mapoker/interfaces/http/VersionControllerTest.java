package com.mapoker.interfaces.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VersionController の単体テスト。
 */
class VersionControllerTest {

    @Test
    void getVersionReturnsInjectedVersion() {
        var controller = new VersionController("1.2.3");
        assertThat(controller.getVersion()).containsEntry("version", "1.2.3");
    }

    @Test
    void getVersionReturnsUnknownWhenNotConfigured() {
        var controller = new VersionController("unknown");
        assertThat(controller.getVersion()).containsEntry("version", "unknown");
    }
}
