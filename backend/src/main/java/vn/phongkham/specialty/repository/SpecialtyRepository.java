package vn.phongkham.specialty.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.specialty.model.Specialty;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    List<Specialty> findByIsActiveTrueOrderByNameAsc();

    List<Specialty> findAllByOrderByNameAsc();
}
