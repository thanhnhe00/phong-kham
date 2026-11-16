package vn.phongkham.room.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.common.Patterns;
import vn.phongkham.room.dto.RoomDtos.RoomRequest;
import vn.phongkham.room.dto.RoomDtos.RoomResponse;
import vn.phongkham.room.service.RoomService;

@Tag(name = "Phòng khám")
@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService service;

    public RoomController(RoomService service) {
        this.service = service;
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping
    public List<RoomResponse> list() {
        return service.list();
    }

    @PreAuthorize(Patterns.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@Valid @RequestBody RoomRequest req) {
        return service.create(req);
    }

    @PreAuthorize(Patterns.ADMIN)
    @PutMapping("/{id}")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest req) {
        return service.update(id, req);
    }
}
