package vn.phongkham.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Định dạng lỗi chung của mọi API: message để hiển thị, errors = lỗi theo từng trường (nếu có). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, Map<String, String> errors) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, null);
    }
}
