package vn.phongkham.staff.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.staff.model.DoctorProfile;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {
}
