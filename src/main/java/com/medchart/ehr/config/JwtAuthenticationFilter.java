package com.medchart.ehr.config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SecurityException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String VALIDATION_FAILURE_METRIC = "jwt.validation.failures";

    static final String REASON_EXPIRED = "expired";
    static final String REASON_MALFORMED = "malformed";
    static final String REASON_UNSUPPORTED = "unsupported";
    static final String REASON_INVALID_SIGNATURE = "invalid_signature";
    static final String REASON_UNKNOWN_USER = "unknown_user";
    static final String REASON_REJECTED = "rejected";
    static final String REASON_ERROR = "error";

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                if (tokenProvider.validateToken(jwt, loadUserByUsername(jwt))) {
                    String username = tokenProvider.getUsernameFromToken(jwt);

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    countFailure(REASON_REJECTED);
                    logger.warn("Rejected JWT for request; authentication not established");
                }
            }
        } catch (Exception ex) {
            countFailure(reasonFor(ex));
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private void countFailure(String reason) {
        meterRegistry.counter(VALIDATION_FAILURE_METRIC, "reason", reason).increment();
    }

    static String reasonFor(Exception ex) {
        if (ex instanceof ExpiredJwtException) {
            return REASON_EXPIRED;
        }
        if (ex instanceof SecurityException) {
            return REASON_INVALID_SIGNATURE;
        }
        if (ex instanceof MalformedJwtException || ex instanceof IllegalArgumentException) {
            return REASON_MALFORMED;
        }
        if (ex instanceof UnsupportedJwtException) {
            return REASON_UNSUPPORTED;
        }
        if (ex instanceof UsernameNotFoundException) {
            return REASON_UNKNOWN_USER;
        }
        return REASON_ERROR;
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private UserDetails loadUserByUsername(String jwt) {
        String username = tokenProvider.getUsernameFromToken(jwt);
        return userDetailsService.loadUserByUsername(username);
    }
}
