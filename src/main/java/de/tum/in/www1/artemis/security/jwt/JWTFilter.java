package de.tum.in.www1.artemis.security.jwt;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Filters incoming requests and installs a Spring Security principal if a header corresponding to a valid user is found.
 */
public class JWTFilter extends GenericFilterBean {

    public static final String JWT_COOKIE_NAME = "jwt";

    private final TokenProvider tokenProvider;

    public JWTFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        Cookie jwtCookie = WebUtils.getCookie(httpServletRequest, JWT_COOKIE_NAME);
        if (isJwtCookieValid(this.tokenProvider, jwtCookie)) {
            Authentication authentication = this.tokenProvider.getAuthentication(jwtCookie.getValue());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    /**
     * Checks if the cookie containing the jwt is valid
     *
     * @param tokenProvider  the artemis token provider used to generate and validate jwt's
     * @param jwtCookie the cookie containing the jwt
     * @return true if the jwt is valid, false if missing or invalid
     */
    public static boolean isJwtCookieValid(TokenProvider tokenProvider, Cookie jwtCookie) {
        if (jwtCookie == null) {
            return false;
        }
        String jwt = jwtCookie.getValue();
        return StringUtils.isNotBlank(jwt) && tokenProvider.validateTokenForAuthority(jwt);
    }
}
