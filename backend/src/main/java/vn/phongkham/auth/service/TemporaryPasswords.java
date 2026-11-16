package vn.phongkham.auth.service;

import java.security.SecureRandom;

/** Mật khẩu tạm cho tài khoản nhân viên mới / đặt lại mật khẩu (bỏ ký tự dễ nhầm như 0/O, 1/l). */
public final class TemporaryPasswords {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
