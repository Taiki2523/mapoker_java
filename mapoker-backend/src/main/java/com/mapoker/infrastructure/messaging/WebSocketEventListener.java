package com.mapoker.infrastructure.messaging;

import com.mapoker.application.table.TableMembershipService;
import com.mapoker.application.auth.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * WebSocket セッションの接続・切断イベントを処理するコンポーネントです。
 *
 * <p>切断時は 30 秒の猶予を設けた後、テーブルから自動退席します。
 * 猶予期間内に再接続（再購読）した場合はキャンセルします。
 */
@Component
public class WebSocketEventListener {

    private static final long DISCONNECT_GRACE_MS = 30_000;

    /** sessionId → [tableId, username] */
    private final Map<String, String[]> sessionToTable = new ConcurrentHashMap<>();
    /** sessionId → pending leave task */
    private final Map<String, ScheduledFuture<?>> pendingLeaves = new ConcurrentHashMap<>();

    private final ObjectProvider<TableMembershipService> tableServiceProvider;
    private final ObjectProvider<UserService> userServiceProvider;
    private final ThreadPoolTaskScheduler scheduler;

    public WebSocketEventListener(ObjectProvider<TableMembershipService> tableServiceProvider,
                                  ObjectProvider<UserService> userServiceProvider) {
        this.tableServiceProvider = tableServiceProvider;
        this.userServiceProvider = userServiceProvider;
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(2);
        this.scheduler.setThreadNamePrefix("ws-disconnect-");
        this.scheduler.initialize();
    }

    /**
     * ゲーム topic への購読を検知し、sessionId とテーブル・ユーザー情報を記録します。
     *
     * <p>再接続時は保留中の退席タスクをキャンセルします。
     */
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        if (destination == null || sessionId == null) return;

        // /topic/tables/{tableId}/game
        if (!destination.matches("/topic/tables/[^/]+/game")) return;

        String[] parts = destination.split("/");
        if (parts.length < 5) return;
        String tableId = parts[3];

        String principalName = event.getUser() != null ? event.getUser().getName() : null;
        if (principalName == null) return;

        UserService userService = userServiceProvider.getIfAvailable();
        if (userService == null) return;
        String username;
        try {
            username = userService.getByPublicId(principalName).username();
        } catch (Exception e) {
            return;
        }

        sessionToTable.put(sessionId, new String[]{ tableId, username });

        // 再接続: 保留中の退席をキャンセル
        ScheduledFuture<?> pending = pendingLeaves.remove(sessionId);
        if (pending != null) pending.cancel(false);
    }

    /**
     * WebSocket 切断を検知し、{@value DISCONNECT_GRACE_MS}ms 後に自動退席を実行します。
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) return;

        String[] info = sessionToTable.get(sessionId);
        if (info == null) return;

        String tableId = info[0];
        String username = info[1];

        ScheduledFuture<?> task = scheduler.schedule(() -> {
            sessionToTable.remove(sessionId);
            pendingLeaves.remove(sessionId);
            TableMembershipService tableService = tableServiceProvider.getIfAvailable();
            if (tableService == null) return;
            try {
                tableService.leave(tableId, username, null);
            } catch (Exception ignored) {}
        }, Instant.now().plusMillis(DISCONNECT_GRACE_MS));

        pendingLeaves.put(sessionId, task);
    }
}
