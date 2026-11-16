package vn.phongkham.room.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.room.model.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByOrderByCodeAsc();
}
