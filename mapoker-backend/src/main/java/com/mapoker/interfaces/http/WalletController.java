package com.mapoker.interfaces.http;

import com.mapoker.application.auth.UserService;
import com.mapoker.application.wallet.WalletEntry;
import com.mapoker.application.wallet.WalletService;
import com.mapoker.interfaces.http.dto.AdminGrantRequest;
import com.mapoker.interfaces.http.dto.WalletLedgerResponse;
import com.mapoker.interfaces.http.dto.WalletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Spring の {@code @RestController} としてウォレット参照とボーナス請求 API を提供するコントローラです。
 */
@RestController
@Profile("postgresql")
@RequestMapping("/v1/wallet")
public class WalletController {

    private static final int MAX_LEDGER_LIMIT = 100;

    private final WalletService walletService;
    private final UserService userService;

    public WalletController(WalletService walletService, UserService userService) {
        this.walletService = walletService;
        this.userService = userService;
    }

    /**
     * 認証ユーザーのウォレット情報を取得します。
     *
     * @param principal 認証済みユーザー
     * @return ウォレット応答
     */
    @GetMapping
    public ResponseEntity<WalletResponse> getWallet(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(loadWalletResponse(resolveUsername(principal)));
    }

    /**
     * 認証ユーザーの台帳履歴を取得します。
     *
     * @param principal 認証済みユーザー
     * @param limit     取得件数（最大 100）
     * @return 台帳履歴応答
     */
    @GetMapping("/ledger")
    public ResponseEntity<List<WalletLedgerResponse>> getLedger(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_LEDGER_LIMIT));
        return ResponseEntity.ok(walletService.getLedger(resolveUsername(principal), normalizedLimit).stream()
                .map(WalletLedgerResponse::from)
                .toList());
    }

    /**
     * 日次ボーナスを請求します。
     *
     * @param principal 認証済みユーザー
     * @return 更新後のウォレット応答
     */
    @PostMapping("/daily-bonus")
    public ResponseEntity<WalletResponse> claimDailyBonus(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = resolveUsername(principal);
        walletService.claimDailyBonus(username);
        return ResponseEntity.ok(loadWalletResponse(username));
    }

    /** principal.getUsername() は publicId（UUID）を返す。そのまま wallet に渡す。 */
    private String resolveUsername(UserDetails principal) {
        return principal.getUsername();
    }

    private WalletResponse loadWalletResponse(String publicId) {
        WalletEntry walletEntry = walletService.getBalance(publicId);
        return WalletResponse.from(walletEntry, walletService.getNextClaimTimes(publicId));
    }
}

/**
 * Spring の {@code @RestController} として管理者向けウォレット付与 API を提供するコントローラです。
 */
@RestController
@Profile("postgresql")
@RequestMapping("/v1/admin")
class AdminWalletController {

    private final WalletService walletService;

    private final UserService userService;

    private final com.mapoker.application.ports.UserRepository userRepository;

    AdminWalletController(WalletService walletService, UserService userService,
                          com.mapoker.application.ports.UserRepository userRepository) {
        this.walletService = walletService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /**
     * 管理者権限でウォレット残高を付与します。
     *
     * @param principal 認証済みユーザー
     * @param request   付与リクエスト
     * @return 更新後のウォレット応答
     */
    @PostMapping("/wallet/grants")
    public ResponseEntity<WalletResponse> grantWalletBalance(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AdminGrantRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String adminUsername = userService.getByPublicId(principal.getUsername()).username();
        walletService.adminGrant(adminUsername, request.targetUsername(), request.amount());
        // 対象ユーザーの publicId で残高を取得
        String targetPublicId = userRepository.findByUsername(request.targetUsername())
                .map(com.mapoker.application.auth.User::publicId)
                .orElse(null);
        if (targetPublicId == null) {
            return ResponseEntity.ok(WalletResponse.from(
                    new com.mapoker.application.wallet.WalletEntry(request.targetUsername(), 0L, null),
                    new WalletService.NextClaimTimes(null)));
        }
        return ResponseEntity.ok(WalletResponse.from(
                walletService.getBalance(targetPublicId),
                walletService.getNextClaimTimes(targetPublicId)));
    }
}
