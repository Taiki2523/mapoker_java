package com.mapoker.interfaces.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * アプリケーションバージョンを返すエンドポイント。
 */
@RestController
@RequestMapping("/v1/version")
public class VersionController {

    private final String version;

    public VersionController(@Value("${spring.application.version:unknown}") String version) {
        this.version = version;
    }

    @GetMapping
    public Map<String, String> getVersion() {
        return Map.of("version", version);
    }
}
