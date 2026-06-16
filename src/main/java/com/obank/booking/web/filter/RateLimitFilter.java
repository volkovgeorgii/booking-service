package com.obank.booking.web.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern IP_PATTERN =
            Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$|^[\\da-fA-F:]+$");

    private final Bucket globalBucket;
    private final Bandwidth perIpBandwidth;

    private final Cache<String, Bucket> ipBuckets;

    public RateLimitFilter(
            @Value("${booking.rate.global-capacity:50000}") int globalCapacity,
            @Value("${booking.rate.per-ip-capacity:500}") int perIpCapacity) {

        this.globalBucket = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(globalCapacity)
                        .refillGreedy(globalCapacity, Duration.ofSeconds(10))
                        .build())
                .build();

        this.perIpBandwidth = Bandwidth.builder()
                .capacity(perIpCapacity)
                .refillGreedy(perIpCapacity, Duration.ofSeconds(10))
                .build();

        this.ipBuckets = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(Duration.ofMinutes(5))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        ConsumptionProbe globalProbe = globalBucket.tryConsumeAndReturnRemaining(1);
        if (!globalProbe.isConsumed()) {
            reject(response, globalProbe.getNanosToWaitForRefill(), "Global rate limit exceeded");
            return;
        }

        String ip = resolveClientIp(request);
        Bucket ipBucket = ipBuckets.get(ip, k -> Bucket.builder().addLimit(perIpBandwidth).build());
        ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
        if (!ipProbe.isConsumed()) {
            reject(response, ipProbe.getNanosToWaitForRefill(), "Per-IP rate limit exceeded");
            return;
        }

        response.addHeader("X-Rate-Limit-Remaining", String.valueOf(ipProbe.getRemainingTokens()));
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, long nanosToWait, String reason)
            throws IOException {
        long retryAfter = Math.max(1, nanosToWait / 1_000_000_000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader("Retry-After", String.valueOf(retryAfter));
        response.getWriter().write("""
                {"type":"urn:booking:rate-limit","title":"Too Many Requests","status":429,"detail":"%s","retryAfterSeconds":%d}
                """.formatted(reason, retryAfter).strip());
    }

    String resolveClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            String trimmed = realIp.strip();
            if (trimmed.length() <= 45 && IP_PATTERN.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return request.getRemoteAddr();
    }
}
