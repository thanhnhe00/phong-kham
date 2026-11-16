package vn.phongkham.common;

/** Biểu thức kiểm tra dùng chung cho @Pattern ở các DTO. */
public final class Patterns {

    /** Số điện thoại di động VN: 10 số, bắt đầu bằng 0 */
    public static final String PHONE = "^0\\d{9}$";
    public static final String PHONE_MESSAGE = "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0";

    /** Quyền dùng trong @PreAuthorize: mọi nhân viên (không gồm bệnh nhân) */
    public static final String ANY_STAFF =
            "hasAnyRole('RECEPTIONIST','NURSE','DOCTOR','ADMIN')";
    public static final String ADMIN = "hasRole('ADMIN')";

    private Patterns() {
    }
}
