package com.mapoker.interfaces.http;

import com.mapoker.application.HandHistoryService;
import com.mapoker.application.UserService;
import com.mapoker.application.UserTableHistoryService;
import com.mapoker.interfaces.http.dto.HandHistoryResponse;
import com.mapoker.interfaces.http.dto.LoginRequest;
import com.mapoker.interfaces.http.dto.RegisterRequest;
import com.mapoker.interfaces.http.dto.UserResponse;
import com.mapoker.interfaces.http.dto.UserTableHistoryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証関連エンドポイントを提供するコントローラー。
 *
 * <p>ユーザー登録・ログイン・ログアウトおよび認証済みユーザー情報の取得を担当する。
 * セッションは {@link HttpSession} で管理され、Spring Security の
 * {@link org.springframework.security.web.context.HttpSessionSecurityContextRepository}
 * を経由して永続化される。
 *
 * <p>ローカル開発プロファイル ({@code SPRING_PROFILES_ACTIVE=local}) では認証が無効化されるため、
 * {@code principal} が {@code null} になる場合がある。各エンドポイントで {@code null} チェックを
 * 行っているのはそのためである。
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final UserService userService;
    private final UserTableHistoryService userTableHistoryService;
    private final HandHistoryService handHistoryService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserService userService,
                          UserTableHistoryService userTableHistoryService,
                          HandHistoryService handHistoryService,
                          AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.userTableHistoryService = userTableHistoryService;
        this.handHistoryService = handHistoryService;
        this.authenticationManager = authenticationManager;
    }

    /**
     * 新規ユーザーを登録し、そのままセッションを確立して返す。
     *
     * <p>登録と同時にログイン状態になるため、クライアントは別途ログインリクエストを送る必要がない。
     *
     * @param req     ユーザー名とパスワードを含む登録リクエスト
     * @param request セッション生成に使用するHTTPリクエスト
     * @return 登録されたユーザーの {@link UserResponse}（HTTP 201）
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest request) {
        userService.register(req.username(), req.password());
        return createSessionAndReturn(req.username(), req.password(), request);
    }

    /**
     * 既存ユーザーを認証してセッションを確立する。
     *
     * @param req     ユーザー名とパスワードを含むログインリクエスト
     * @param request セッション生成に使用するHTTPリクエスト
     * @return 認証されたユーザーの {@link UserResponse}（HTTP 200）
     */
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        return createSessionAndReturn(req.username(), req.password(), request);
    }

    /**
     * 認証を実行してセッションを確立し、ユーザー情報を返す内部ヘルパー。
     *
     * <p>{@link SecurityContextHolder} に認証情報をセットした後、
     * セッションに {@code SPRING_SECURITY_CONTEXT_KEY} として格納することで
     * 後続リクエストでも認証状態が維持される。
     *
     * @param username   ユーザー名
     * @param rawPassword 平文パスワード
     * @param request    セッション生成に使用するHTTPリクエスト
     * @return ユーザーの {@link UserResponse}
     */
    private UserResponse createSessionAndReturn(String username, String rawPassword, HttpServletRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, rawPassword));
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
        var user = userService.getByUsername(username);
        return new UserResponse(user.id(), user.username());
    }

    /**
     * 現在ログイン中のユーザー情報を返す。
     *
     * <p>未認証の場合は HTTP 401 を返す。ローカルプロファイルでは {@code principal} が
     * {@code null} になり得るため、その場合も 401 を返す。
     *
     * @param principal Spring Security が注入する認証済みユーザー詳細。未認証時は {@code null}
     * @return 認証済みユーザーの {@link UserResponse}、または HTTP 401
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var user = userService.getByUsername(principal.getUsername());
        return ResponseEntity.ok(new UserResponse(user.id(), user.username()));
    }

    /**
     * 認証済みユーザーの直近テーブル参加履歴を返す。
     *
     * @param principal Spring Security が注入する認証済みユーザー詳細。未認証時は {@code null}
     * @return {@link UserTableHistoryResponse} のリスト、または HTTP 401
     */
    @GetMapping("/history")
    public ResponseEntity<java.util.List<UserTableHistoryResponse>> history(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userTableHistoryService.listRecent(principal.getUsername()).stream()
                .map(UserTableHistoryResponse::from)
                .toList());
    }

    /**
     * 認証済みユーザーの直近ハンド履歴を返す。
     *
     * @param principal Spring Security が注入する認証済みユーザー詳細。未認証時は {@code null}
     * @param limit     取得件数の上限（デフォルト 20）
     * @return {@link HandHistoryResponse} のリスト、または HTTP 401
     */
    @GetMapping("/hand-history")
    public ResponseEntity<java.util.List<HandHistoryResponse>> handHistory(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(handHistoryService.listRecentForUser(principal.getUsername(), limit).stream()
                .map(HandHistoryResponse::from)
                .toList());
    }

    /**
     * 現在のセッションを無効化してログアウトする。
     *
     * <p>セッションが存在しない場合は何もせず正常終了する。
     * {@link SecurityContextHolder} もクリアすることで、同一スレッド内での
     * 認証情報の残存を防ぐ。
     *
     * @param request セッション取得に使用するHTTPリクエスト
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
