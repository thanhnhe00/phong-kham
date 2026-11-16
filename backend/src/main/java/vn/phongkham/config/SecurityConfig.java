package vn.phongkham.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import vn.phongkham.auth.security.JsonErrorHandlers;
import vn.phongkham.auth.security.JwtAuthConverter;
import vn.phongkham.auth.security.MustChangePasswordFilter;

/**
 * Mặc định mọi API cần đăng nhập (Bearer JWT). Route công khai liệt kê ở đây;
 * phân quyền theo vai trò đặt bằng @PreAuthorize trên controller.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final SecretKey jwtKey;

    public SecurityConfig(@Value("${app.jwt.secret:}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Thiếu biến môi trường JWT_SECRET (tối thiểu 32 ký tự)");
        }
        this.jwtKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthConverter converter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // API stateless, không dùng cookie phiên
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/docs", "/docs/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/phone-codes", "/auth/phone-codes/check", "/auth/register",
                                "/auth/login", "/auth/password/reset").permitAll()
                        // cổng SMS tự xác thực bằng token / chữ ký riêng (SmsGatewayAuth)
                        .requestMatchers(HttpMethod.POST, "/sms/inbound").permitAll()
                        .requestMatchers(HttpMethod.GET, "/specialties", "/services", "/doctors", "/doctors/*", "/slots").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(JsonErrorHandlers.UNAUTHORIZED))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(JsonErrorHandlers.UNAUTHORIZED)
                        .accessDeniedHandler(JsonErrorHandlers.FORBIDDEN))
                .addFilterAfter(new MustChangePasswordFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
