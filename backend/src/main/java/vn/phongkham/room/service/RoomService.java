package vn.phongkham.room.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.room.dto.RoomDtos.RoomRequest;
import vn.phongkham.room.dto.RoomDtos.RoomResponse;
import vn.phongkham.room.model.Room;
import vn.phongkham.room.repository.RoomRepository;
import vn.phongkham.specialty.repository.SpecialtyRepository;

/** UC31: danh mục phòng khám. */
@Service
public class RoomService {

    private final RoomRepository repo;
    private final SpecialtyRepository specialties;

    public RoomService(RoomRepository repo, SpecialtyRepository specialties) {
        this.repo = repo;
        this.specialties = specialties;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list() {
        return repo.findAllByOrderByCodeAsc().stream().map(RoomResponse::of).toList();
    }

    @Transactional
    public RoomResponse create(RoomRequest req) {
        Room r = new Room();
        apply(r, req);
        return RoomResponse.of(repo.saveAndFlush(r));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest req) {
        Room r = repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng"));
        apply(r, req);
        return RoomResponse.of(repo.saveAndFlush(r));
    }

    private void apply(Room r, RoomRequest req) {
        if (req.specialtyId() != null && !specialties.existsById(req.specialtyId())) {
            throw ApiException.badRequest("Chuyên khoa không tồn tại");
        }
        r.setCode(req.code().trim());
        r.setName(req.name().trim());
        r.setSpecialtyId(req.specialtyId());
        if (req.isActive() != null) {
            r.setActive(req.isActive());
        }
    }
}
