package vn.phongkham.clinical.model;

import java.util.EnumSet;
import java.util.Set;

/** Ánh xạ các kiểu liệt kê của chỉ định / kết quả / tệp trong DB */
public final class ClinicalEnums {

    private ClinicalEnums() {
    }

    public enum OrderStatus { OPEN, PARTIALLY_DONE, COMPLETED, CANCELLED }

    public enum ItemStatus {
        ORDERED, IN_PROGRESS, PERFORMED, REFUSED, CANCELLED;

        public static final Set<ItemStatus> PENDING = EnumSet.of(ORDERED, IN_PROGRESS);
    }

    public enum PerformanceStatus { DONE, VOIDED }

    public enum ResultStatus {
        DRAFT, SUBMITTED, REVIEWED, AMENDED;

        /** Bệnh nhân chỉ thấy kết quả bác sĩ đã duyệt */
        public static final Set<ResultStatus> VISIBLE = EnumSet.of(REVIEWED, AMENDED);
    }

    public enum FileCategory { EMR_ATTACHMENT, SCAN, PHOTO, LAB_FILE, IMAGING, ULTRASOUND, AVATAR, OTHER }

    public enum FileVisibility { STAFF_ONLY, PATIENT_VISIBLE, PUBLIC }
}
