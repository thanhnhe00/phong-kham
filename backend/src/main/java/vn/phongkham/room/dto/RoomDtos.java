package vn.phongkham.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.phongkham.room.model.Room;

public final class RoomDtos {

    private RoomDtos() {
    }

    public record RoomRequest(
            @NotBlank(message = "Vui lòng nhập mã phòng") @Size(max = 30, message = "Mã phòng tối đa 30 ký tự") String code,
            @NotBlank(message = "Vui lòng nhập tên phòng") @Size(max = 120, message = "Tên phòng tối đa 120 ký tự") String name,
            Long specialtyId,
            Boolean isActive) {
    }

    public record RoomResponse(Long id, String code, String name, Long specialtyId, boolean isActive) {

        public static RoomResponse of(Room r) {
            return new RoomResponse(r.getId(), r.getCode(), r.getName(), r.getSpecialtyId(), r.isActive());
        }
    }
}
