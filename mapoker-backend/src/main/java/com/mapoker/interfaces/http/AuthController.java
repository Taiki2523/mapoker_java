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

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest request) {
        userService.register(req.username(), req.password());
        return createSessionAndReturn(req.username(), req.password(), request);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        return createSessionAndReturn(req.username(), req.password(), request);
    }

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

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var user = userService.getByUsername(principal.getUsername());
        return ResponseEntity.ok(new UserResponse(user.id(), user.username()));
    }

    @GetMapping("/history")
    public ResponseEntity<java.util.List<UserTableHistoryResponse>> history(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userTableHistoryService.listRecent(principal.getUsername()).stream()
                .map(UserTableHistoryResponse::from)
                .toList());
    }

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
