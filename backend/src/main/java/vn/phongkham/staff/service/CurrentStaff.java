package vn.phongkham.staff.service;

import org.springframework.stereotype.Component;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.staff.model.Employee;
import vn.phongkham.staff.repository.EmployeeRepository;

/** Hồ sơ nhân viên của tài khoản đang đăng nhập (người thao tác được ghi vào created_by, confirmed_by...) */
@Component
public class CurrentStaff {

    private final EmployeeRepository employees;

    public CurrentStaff(EmployeeRepository employees) {
        this.employees = employees;
    }

    public Employee of(AuthUser user) {
        return employees.findByUserId(user.id())
                .orElseThrow(() -> ApiException.forbidden("Tài khoản không gắn với hồ sơ nhân viên"));
    }

    public Long idOf(AuthUser user) {
        return of(user).getId();
    }
}
