package vn.phongkham.patient.service;

import java.time.Period;
import vn.phongkham.common.VnTime;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.patient.dto.PatientDtos.AllergyResponse;
import vn.phongkham.patient.dto.PatientDtos.PatientRequest;
import vn.phongkham.patient.dto.PatientDtos.PatientResponse;
import vn.phongkham.patient.dto.PatientDtos.RepresentativeResponse;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.model.PatientAllergy;
import vn.phongkham.patient.model.PatientEnums.IdentityType;
import vn.phongkham.patient.model.PatientEnums.Sex;
import vn.phongkham.patient.model.PatientRepresentative;

/** Chuyển đổi DTO <-> entity và chuẩn hóa dữ liệu hồ sơ bệnh nhân */
final class PatientMapper {

    private PatientMapper() {
    }

    /** Áp dụng hồ sơ đầy đủ; kiểm tra giấy tờ: có số thì phải chọn loại, CCCD phải đủ 12 số */
    static void apply(Patient p, PatientRequest r) {
        String number = blankToNull(r.identityNumber());
        IdentityType type = r.identityType() == null ? IdentityType.NOT_PROVIDED : r.identityType();
        if (number == null) {
            type = IdentityType.NOT_PROVIDED;
        } else if (type == IdentityType.NOT_PROVIDED) {
            throw ApiException.badRequest("Vui lòng chọn loại giấy tờ tùy thân");
        } else if (type == IdentityType.CCCD && !number.matches("\\d{12}")) {
            throw ApiException.badRequest("Số CCCD phải gồm 12 chữ số");
        }
        p.setFullName(r.fullName().trim());
        p.setDateOfBirth(r.dateOfBirth());
        p.setSex(r.sex() == null ? Sex.UNKNOWN : r.sex());
        p.setIdentityType(type);
        p.setIdentityNumber(number);
        p.setPhone(blankToNull(r.phone()));
        p.setEmail(blankToNull(r.email()));
        p.setAddressLine(blankToNull(r.addressLine()));
        p.setWard(blankToNull(r.ward()));
        p.setProvince(blankToNull(r.province()));
        p.setOccupation(blankToNull(r.occupation()));
        p.setEthnicity(blankToNull(r.ethnicity()));
        if (blankToNull(r.nationality()) != null) {
            p.setNationality(r.nationality().trim());
        }
    }

    static PatientResponse toResponse(Patient p) {
        Period age = Period.between(p.getDateOfBirth(), VnTime.today());
        return new PatientResponse(p.getId(), p.getPatientCode(), p.getFullName(), p.getDateOfBirth(),
                (int) age.toTotalMonths(), p.getSex(), p.getIdentityType(), p.getIdentityNumber(), p.getPhone(), p.getEmail(),
                p.getAddressLine(), p.getWard(), p.getProvince(), p.getOccupation(), p.getEthnicity(), p.getNationality());
    }

    static AllergyResponse toAllergy(PatientAllergy a) {
        return new AllergyResponse(a.getId(), a.getAllergen(), a.getReaction(), a.getSeverity(), a.getRecordedAt());
    }

    static RepresentativeResponse toRepresentative(PatientRepresentative r) {
        return new RepresentativeResponse(r.getId(), r.getFullName(), r.getRelationship(), r.getPhone(), r.getIdentityNumber(),
                r.getAuthorityNote(), r.getVerifiedAt());
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
