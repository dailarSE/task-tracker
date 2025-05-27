package com.example.tasktracker.backend.test.util;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.jwt.JwtIssuer;
import com.example.tasktracker.backend.security.jwt.JwtKeyService;
import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.user.entity.User;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public class TestJwtUtil {
    private final JwtProperties baseAppJwtProperties;
    private final Clock baseAppClock;
    private final JwtKeyService jwtKeyService; // Используем один и тот же KeyService

    public TestJwtUtil(JwtProperties baseAppJwtProperties, Clock baseAppClock) {
        this.baseAppJwtProperties = baseAppJwtProperties;
        this.baseAppClock = baseAppClock;
        // Создаем JwtKeyService один раз на основе базовых properties
        this.jwtKeyService = new JwtKeyService(baseAppJwtProperties);
    }

    public String generateValidToken(User user) {
        AppUserDetails appUserDetails = new AppUserDetails(user);
        Authentication authentication = new TestingAuthenticationToken(appUserDetails, null, appUserDetails.getAuthorities());
        // Используем общий jwtKeyService и baseAppClock
        JwtIssuer realIssuer = new JwtIssuer(baseAppJwtProperties, jwtKeyService, baseAppClock);
        return realIssuer.generateToken(authentication);
    }

    public String generateExpiredToken(User user, Duration tokenLifetime, Duration issuedAgo) {
        AppUserDetails appUserDetails = new AppUserDetails(user);
        Authentication authentication = new TestingAuthenticationToken(appUserDetails, null, appUserDetails.getAuthorities());

        Instant issueAt = Instant.now(baseAppClock).minus(issuedAgo);
        Clock fixedIssueClock = Clock.fixed(issueAt, ZoneOffset.UTC);

        // Создаем кастомные properties только для времени жизни, ключ и остальное берем из базовых
        JwtProperties customProps = new JwtProperties();
        customProps.setSecretKey(baseAppJwtProperties.getSecretKey()); // Ключ тот же!
        customProps.setExpirationMs(tokenLifetime.toMillis()); // Новое время жизни
        customProps.setEmailClaimKey(baseAppJwtProperties.getEmailClaimKey());
        customProps.setAuthoritiesClaimKey(baseAppJwtProperties.getAuthoritiesClaimKey());

        // JwtIssuer с кастомным временем жизни и временем выдачи, но тем же ключом
        JwtIssuer customIssuer = new JwtIssuer(customProps, this.jwtKeyService, fixedIssueClock);
        return customIssuer.generateToken(authentication);
    }
}