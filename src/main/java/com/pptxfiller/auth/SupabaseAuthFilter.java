package com.pptxfiller.auth;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Servlet filter that validates every request to /api/** against Supabase.
 *
 * <p>The client must send the Supabase JWT in the Authorization header:
 * <pre>Authorization: Bearer &lt;supabase-jwt&gt;</pre>
 *
 * <p>The filter calls Supabase's /auth/v1/user endpoint with that token.
 * If Supabase returns 200 the request passes through; otherwise 401 is returned.
 *
 * <p>No JWT library is needed — Supabase validates the token for us.
 */
@Component
public class SupabaseAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthFilter.class);

    private final RestTemplate rest = new RestTemplate();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.publishable-key}")
    private String supabasePublishableKey;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Only guard /api/** paths
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            unauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String token = authHeader.substring(7).trim();

        if (!isValidSupabaseToken(token)) {
            unauthorized(response, "Invalid or expired Supabase token");
            return;
        }

        chain.doFilter(req, res);
    }

    /**
     * Calls Supabase GET /auth/v1/user with the client's JWT.
     * Returns true only when Supabase responds with HTTP 200.
     */
    private boolean isValidSupabaseToken(String jwt) {
        String url = supabaseUrl + "/auth/v1/user";

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabasePublishableKey);
        headers.setBearerAuth(jwt);

        try {
            ResponseEntity<Map> response = rest.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (RestClientException ex) {
            log.warn("Supabase auth check failed: {}", ex.getMessage());
            return false;
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
