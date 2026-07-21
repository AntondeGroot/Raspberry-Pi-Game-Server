package ADG.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/perform-login")
                .failureUrl("/login.html?error")
                .permitAll()
                // On successful login: set a long-lived hint cookie so the lobby can show the
                // admin a "Login" shortcut even after the Spring Security session expires.
                // The cookie is NOT HttpOnly so that GWT can read it client-side.
                .successHandler((req, res, auth) -> {
                    Cookie hint = new Cookie("admin_hint", "1");
                    hint.setMaxAge(400 * 24 * 60 * 60); // 400 days
                    hint.setPath("/");
                    hint.setHttpOnly(false);
                    hint.setSecure(req.isSecure());
                    res.addCookie(hint);
                    res.sendRedirect("/");
                })
            )
            .logout(logout -> logout
                // POST-only (Spring Security default) to prevent logout CSRF. The lobby's
                // Logout button POSTs to /logout via XHR, then reloads the lobby.
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .httpBasic(basic -> {})
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // Return JSON 401 for API calls so GWT can detect unauthenticated state
                    // without the browser following a redirect to /login.html.
                    String accept = request.getHeader("Accept");
                    if (accept != null && accept.contains("application/json")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"authenticated\":false}");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login.html");
                    }
                })
            );
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${admin.password-hash}") String passwordHash) {
        UserDetails admin = User.withUsername("admin")
                .password(passwordHash)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}