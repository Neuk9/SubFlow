package com.subflow.global.security;

import com.subflow.global.auth.CurrentUserProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * demo 모드에서 인증이 없을 때 데모 소유자 인증을 주입한다.
 * oauth 모드에서는 등록하지 않는다.
 */
public class DemoAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            CurrentUserProvider.LOCAL_OWNER,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_DEMO"))));
        }
        filterChain.doFilter(request, response);
    }
}
