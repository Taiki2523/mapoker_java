package com.mapoker.interfaces.http;

import com.mapoker.application.GoogleAuthService;
import com.mapoker.application.HandHistoryService;
import com.mapoker.application.User;
import com.mapoker.application.UserService;
import com.mapoker.application.UserTableHistoryService;
import com.mapoker.interfaces.http.dto.GoogleAuthRequest;
import com.mapoker.interfaces.http.dto.HandHistoryResponse;
import com.mapoker.interfaces.http.dto.UpdateUsernameRequest;
import com.mapoker.interfaces.http.dto.UserResponse;
import com.mapoker.interfaces.http.dto.UserTableHistoryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 認証関連エンドポイントを提供するコントローラーです。
 *
 * <p>Google ID Token を受け取り、ユーザーを識別してセッションを確立します。
 * セッション principal には {@code public_id}（UUID）を使用します。
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final UserService userService;
    private final GoogleAuthService googleAuthService;
    private final UserTableHistoryService userTableHistoryService;
    private final HandHistoryService handHistoryService;

    public AuthController(UserService userService,
                          GoogleAuthService googleAuthService,
                          UserTableHistoryService userTableHistoryService,
                          HandHistoryService handHistoryService) {
        this.userService = userService;
        this.googleAuthService = googleAuthService;
        this.userTableHistoryService = userTableHistoryService;
        this.handHistoryService = handHistoryService;
    }

    /**
     * Google ID Token を検証してログインまたは新規登録します。
     *
     * @param req     ID Token を含むリクエスト
     * @param request セッション生成に使用する HTTP リクエスト
     * @return ユーザー情報
     */
    @PostMapping("/google")
    public UserResponse loginWithGoogle(@Valid @RequestBody GoogleAuthRequest req,
                                        HttpServletRequest request) {
        GoogleAuthService.LoginResult result = googleAuthService.loginWithGoogle(req.idToken());
        UserResponse response = result.isNewUser()
                ? UserResponse.fromNew(result.user())
                : UserResponse.from(result.user());
        return establishSessionWithResponse(result.user(), response, request);
    }

    /**
     * 現在ログイン中のユーザー情報を返します。
     *
     * @param principal 認証済みユーザー（principal = public_id）
     * @return ユーザー情報、または HTTP 401
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userService.getByPublicId(principal.getUsername());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * 認証済みユーザーの表示名を変更します。
     *
     * @param req       新しいユーザー名
     * @param principal 現在の認証済みユーザー
     * @return 更新後のユーザー情報、または HTTP 401 / 409
     */
    @PatchMapping("/me/username")
    public ResponseEntity<UserResponse> updateUsername(
            @Valid @RequestBody UpdateUsernameRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            User updated = userService.updateUsername(principal.getUsername(), req.newUsername());
            return ResponseEntity.ok(UserResponse.from(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * 認証済みユーザーの直近テーブル参加履歴を返します。
     */
    @GetMapping("/history")
    public ResponseEntity<List<UserTableHistoryResponse>> history(
            @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userService.getByPublicId(principal.getUsername());
        return ResponseEntity.ok(userTableHistoryService.listRecent(user.username()).stream()
                .map(UserTableHistoryResponse::from)
                .toList());
    }

    /**
     * 認証済みユーザーの直近ハンド履歴を返します。
     */
    @GetMapping("/hand-history")
    public ResponseEntity<List<HandHistoryResponse>> handHistory(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userService.getByPublicId(principal.getUsername());
        return ResponseEntity.ok(handHistoryService.listRecentForUser(user.username(), limit).stream()
                .map(HandHistoryResponse::from)
                .toList());
    }

    /**
     * 現在のセッションを無効化してログアウトします。
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    private UserResponse establishSession(User user, HttpServletRequest request) {
        return establishSessionWithResponse(user, UserResponse.from(user), request);
    }

    private UserResponse establishSessionWithResponse(User user, UserResponse response, HttpServletRequest request) {
        var userDetails = new org.springframework.security.core.userdetails.User(
                user.publicId(), "", List.of());
        var auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
        return response;
    }
}
