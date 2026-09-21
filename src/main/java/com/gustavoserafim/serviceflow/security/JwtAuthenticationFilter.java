package com.gustavoserafim.serviceflow.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que roda ANTES dos controllers em toda requisição.
 *
 * Fluxo: lê "Authorization: Bearer <token>" -> valida o token -> carrega o
 * usuário -> coloca a autenticação no SecurityContext ("quem está logado
 * nesta requisição"). O restante do Spring Security e o @PreAuthorize
 * consultam esse contexto para decidir se libera.
 *
 * Se o token faltar ou for inválido, o filtro NÃO barra nada: apenas não
 * autentica. Quem responde 401 é o Spring Security mais adiante, se a rota
 * exigir login. Assim rotas públicas (login) continuam funcionando.
 *
 * Esta classe NÃO é @Component de propósito: como bean, o Spring Boot a
 * registraria como filtro do servidor uma segunda vez (rodaria em duplicado).
 * Ela é instanciada manualmente no SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                JwtService.TokenClaims claims = jwtService.parse(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(claims.subject());

                // Só autentica se o usuário estiver ativo E o token tiver a versão
                // vigente: troca de senha ou reuso de refresh token sobem a versão
                // e derrubam todos os tokens anteriores.
                if (userDetails.isEnabled()
                        && userDetails instanceof AppUserDetails appUser
                        && appUser.getTokenVersion() == claims.tokenVersion()) {
                    UsernamePasswordAuthenticationToken authentication =
                            UsernamePasswordAuthenticationToken.authenticated(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                // Token adulterado, expirado, malformado ou de usuário removido:
                // segue sem autenticar -> a rota protegida responderá 401.
                logger.debug("Token JWT rejeitado: " + ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
