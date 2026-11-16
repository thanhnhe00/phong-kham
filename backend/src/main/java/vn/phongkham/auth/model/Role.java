package vn.phongkham.auth.model;

import java.util.EnumSet;
import java.util.Set;

/** Ánh xạ kiểu role_code trong DB */
public enum Role {
    PATIENT, RECEPTIONIST, NURSE, DOCTOR, ADMIN;

    public static final Set<Role> STAFF = EnumSet.of(RECEPTIONIST, NURSE, DOCTOR, ADMIN);
    /** Chỉ admin được cấp các vai trò này */
}
