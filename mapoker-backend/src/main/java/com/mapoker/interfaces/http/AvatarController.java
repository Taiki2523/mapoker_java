package com.mapoker.interfaces.http;

import com.mapoker.application.auth.User;
import com.mapoker.application.ports.UserRepository;
import com.mapoker.application.auth.UserService;
import com.mapoker.interfaces.http.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * アバター画像のアップロードと配信を担当するコントローラーです。
 */
@RestController
public class AvatarController {

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024L;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final Path storagePath;
    private final UserService userService;
    private final UserRepository userRepository;

    public AvatarController(
            @Value("${avatar.storage-path:/data/avatars}") String storagePath,
            UserService userService,
            UserRepository userRepository) {
        this.storagePath = Paths.get(storagePath).toAbsolutePath().normalize();
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /**
     * アバター画像を配信します（認証不要）。
     *
     * @param filename ファイル名（例: {@code publicId.jpg}）
     * @return 画像リソース、または 404
     */
    @GetMapping("/v1/avatars/{filename:.+}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        Path filePath = storagePath.resolve(filename).normalize();
        if (!filePath.startsWith(storagePath)) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = filename.endsWith(".png") ? "image/png" : "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS))
                .body(resource);
    }

    /**
     * アバター画像をアップロードします（JPEG/PNG、最大 2MB）。
     *
     * @param principal 認証済みユーザー
     * @param file      アップロードするファイル（{@code multipart/form-data} の {@code file} フィールド）
     * @return 更新後のユーザー情報、またはエラー応答
     */
    @PostMapping(value = "/v1/auth/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadAvatar(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam("file") MultipartFile file) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        User user = userService.getByPublicId(principal.getUsername());
        String ext = "image/png".equals(contentType) ? ".png" : ".jpg";
        String filename = user.publicId() + ext;

        try {
            Files.createDirectories(storagePath);
            Path dest = storagePath.resolve(filename);
            try (var in = file.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }

            // 旧拡張子のファイルを削除
            String oldExt = ext.equals(".png") ? ".jpg" : ".png";
            Files.deleteIfExists(storagePath.resolve(user.publicId() + oldExt));

            String avatarUrl = "/api/v1/avatars/" + filename;
            userRepository.updateAvatarUrl(user.publicId(), avatarUrl);
            User updated = userService.getByPublicId(user.publicId());
            return ResponseEntity.ok(UserResponse.from(updated));
        } catch (IOException e) {
            throw new RuntimeException("Avatar upload failed: " + e.getMessage(), e);
        }
    }
}
