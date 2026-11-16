package vn.phongkham.schedule.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.schedule.model.ClinicClosure;

public interface ClinicClosureRepository extends JpaRepository<ClinicClosure, LocalDate> {

    List<ClinicClosure> findByClosedDateBetweenOrderByClosedDate(LocalDate from, LocalDate to);
}
