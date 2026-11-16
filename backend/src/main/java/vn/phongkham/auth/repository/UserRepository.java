package vn.phongkham.auth.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByRolesContaining(Role role);

    /**
     * Không truyền null cho tham số chuỗi/enum: PostgreSQL không xác định được kiểu của null.
     * q = "" là không lọc; anyRole = true là không lọc vai trò.
     */
    @Query("""
            select u from User u
            where (:anyRole = true or :role member of u.roles)
              and (:q = '' or u.phone like concat('%', :q, '%'))
            order by u.id
            """)
    Page<User> search(@Param("anyRole") boolean anyRole, @Param("role") Role role, @Param("q") String q, Pageable pageable);

    default Page<User> search(Role role, String q, Pageable pageable) {
        return search(role == null, role == null ? Role.PATIENT : role, q, pageable);
    }
}
