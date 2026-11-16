package vn.phongkham.medicine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.medicine.model.Medicine;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    /** q tìm theo hoạt chất hoặc tên thương mại, không phân biệt hoa thường; q = "" là không lọc */
    @Query("""
            select m from Medicine m
            where (:includeInactive = true or m.isActive = true)
              and (:q = ''
                   or lower(m.activeIngredient) like lower(concat('%', :q, '%'))
                   or lower(m.brandName) like lower(concat('%', :q, '%')))
            order by m.activeIngredient, m.strength
            """)
    Page<Medicine> search(@Param("q") String q, @Param("includeInactive") boolean includeInactive, Pageable pageable);
}
