package com.mapoker.interfaces.http;

import com.mapoker.application.WalletEntry;
import com.mapoker.application.WalletService;
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

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
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
        return ResponseEntity.ok(loadWalletResponse(principal.getUsername()));
    }

    /**
     * 認証ユーザーの台帳履歴を取得します。
     *
     * @param principal 認証済みユーザー
     * @param limit 取得件数
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
        return ResponseEntity.ok(walletService.getLedger(principal.getUsername(), normalizedLimit).stream()
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
        String username = principal.getUsername();
        walletService.claimDailyBonus(username);
        return ResponseEntity.ok(loadWalletResponse(username));
    }

    /**
     * 救済ボーナスを請求します。
     *
     * @param principal 認証済みユーザー
     * @return 更新後のウォレット応答
     */
    @PostMapping("/recovery")
    public ResponseEntity<WalletResponse> claimRecovery(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = principal.getUsername();
        walletService.claimRecovery(username);
        return ResponseEntity.ok(loadWalletResponse(username));
    }

    private WalletResponse loadWalletResponse(String username) {
        WalletEntry walletEntry = walletService.getBalance(username);
        return WalletResponse.from(walletEntry, walletService.getNextClaimTimes(username));
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

    AdminWalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * 管理者権限でウォレット残高を付与します。
     *
     * @param principal 認証済みユーザー
     * @param request 付与リクエスト
     * @return 更新後のウォレット応答
     */
    @PostMapping("/wallet/grants")
    public ResponseEntity<WalletResponse> grantWalletBalance(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AdminGrantRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        walletService.adminGrant(principal.getUsername(), request.targetUsername(), request.amount());
        return ResponseEntity.ok(WalletResponse.from(
                walletService.getBalance(request.targetUsername()),
                walletService.getNextClaimTimes(request.targetUsername())));
    }
}
