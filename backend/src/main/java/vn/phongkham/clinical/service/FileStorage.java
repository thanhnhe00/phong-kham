package vn.phongkham.clinical.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.phongkham.common.exception.ApiException;

/**
 * Lưu tệp kết quả ra ổ đĩa (thư mục app.storage.dir); DB chỉ giữ storage_key.
 * shortcut: lưu ổ đĩa cục bộ của server; khi triển khai nhiều máy chủ thì chuyển sang object storage (S3/MinIO).
 */
@Component
public class FileStorage {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final Path root;

    public FileStorage(@Value("${app.storage.dir:./data/files}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    /** Nhận diện loại tệp theo nội dung (không tin tên tệp/Content-Type do client gửi); chỉ nhận JPG, PNG, PDF */
    public static String detectMime(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        throw ApiException.badRequest("Chỉ nhận tệp ảnh JPG, PNG hoặc PDF");
    }

    public String save(byte[] content) {
        if (content.length == 0) {
            throw ApiException.badRequest("Tệp rỗng");
        }
        if (content.length > MAX_BYTES) {
            throw ApiException.badRequest("Tệp tối đa 10 MB");
        }
        LocalDate d = LocalDate.now();
        String key = "%d/%02d/%s".formatted(d.getYear(), d.getMonthValue(), UUID.randomUUID());
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được tệp", e);
        }
        return key;
    }

    public byte[] load(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw ApiException.notFound("Không tìm thấy nội dung tệp");
        }
    }

    /** Chặn storage_key chứa ".." thoát ra ngoài thư mục lưu trữ */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.badRequest("Đường dẫn tệp không hợp lệ");
        }
        return p;
    }
}
