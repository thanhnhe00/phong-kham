package vn.phongkham.auth.security;

import java.util.EnumSet;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import vn.phongkham.auth.model.User;
import vn.phongkham.auth.model.UserStatus;
import vn.phongkham.auth.repository.UserRepository;

/**
 * Kiểm tra token với DB ở mỗi request: tài khoản bị khóa hoặc đã đổi mật khẩu (token_version tăng)
 * thì token cũ hết hiệu lực ngay, không phải chờ hết hạn.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public JwtAuthConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = users.findById(Long.valueOf(jwt.getSubject())).orElse(null);
        Number version = jwt.getClaim("ver");
        if (user == null || user.getStatus() != UserStatus.ACTIVE || version == null
                || version.intValue() != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Phiên đăng nhập đã hết hiệu lực");
        }
        var principal = new AuthUser(user.getId(),
                user.getRoles().isEmpty() ? EnumSet.noneOf(vn.phongkham.auth.model.Role.class) : EnumSet.copyOf(user.getRoles()),
                user.isMustChangePassword());
        var authorities = user.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}
