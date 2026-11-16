package vn.phongkham.staff.service;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.User;
import vn.phongkham.auth.model.UserStatus;
import vn.phongkham.auth.repository.UserRepository;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.auth.service.TemporaryPasswords;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.specialty.repository.SpecialtyRepository;
import vn.phongkham.staff.dto.StaffDtos.CreateStaffRequest;
import vn.phongkham.staff.dto.StaffDtos.CreatedStaffResponse;
import vn.phongkham.staff.dto.StaffDtos.DoctorProfileRequest;
import vn.phongkham.staff.dto.StaffDtos.DoctorProfileResponse;
import vn.phongkham.staff.dto.StaffDtos.HistoryRequest;
import vn.phongkham.staff.dto.StaffDtos.HistoryResponse;
import vn.phongkham.staff.dto.StaffDtos.StaffResponse;
import vn.phongkham.staff.dto.StaffDtos.UpdateStaffRequest;
import vn.phongkham.staff.model.DoctorHistory;
import vn.phongkham.staff.model.DoctorProfile;
import vn.phongkham.staff.model.Employee;
import vn.phongkham.staff.model.EmployeeSpecialty;
import vn.phongkham.staff.model.StaffEnums.EmploymentStatus;
import vn.phongkham.staff.repository.DoctorHistoryRepository;
import vn.phongkham.staff.repository.DoctorProfileRepository;
import vn.phongkham.staff.repository.EmployeeRepository;
import vn.phongkham.staff.repository.EmployeeSpecialtyRepository;

/** UC31: quản lý nhân viên, vai trò, chuyên khoa và hồ sơ hành nghề của bác sĩ. */
@Service
public class StaffService {

    private final EmployeeRepository employees;
    private final UserRepository users;
    private final EmployeeSpecialtyRepository links;
    private final SpecialtyRepository specialties;
    private final DoctorProfileRepository profiles;
    private final DoctorHistoryRepository history;
    private final StaffSpecialties staffSpecialties;
    private final PasswordEncoder encoder;

    public StaffService(EmployeeRepository employees, UserRepository users, EmployeeSpecialtyRepository links,
                        SpecialtyRepository specialties, DoctorProfileRepository profiles, DoctorHistoryRepository history,
                        StaffSpecialties staffSpecialties, PasswordEncoder encoder) {
        this.employees = employees;
        this.users = users;
        this.links = links;
        this.specialties = specialties;
        this.profiles = profiles;
        this.history = history;
        this.staffSpecialties = staffSpecialties;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffResponse> list(Role role, EmploymentStatus status, String q, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        Page<Employee> page = employees.search(role, status, query, pageable);
        List<StaffResponse> items = toResponses(page.getContent());
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public StaffResponse get(Long id) {
        return toResponses(List.of(load(id))).getFirst();
    }

    @Transactional
    public CreatedStaffResponse create(AuthUser actor, CreateStaffRequest req) {
        checkStaffRoles(req.roles());
        checkDoctorHasSpecialty(req.roles(), req.specialtyIds());
        if (users.existsByPhone(req.phone())) {
            throw ApiException.conflict("Số điện thoại đã có tài khoản");
        }
        String temp = TemporaryPasswords.generate();
        User u = new User();
        u.setPhone(req.phone());
        u.setEmail(blankToNull(req.email()));
        u.setPasswordHash(encoder.encode(temp));
        u.setStatus(UserStatus.ACTIVE);
        u.setMustChangePassword(true);
        u.setRoles(EnumSet.copyOf(req.roles()));
        users.save(u);

        Employee e = new Employee();
        e.setUserId(u.getId());
        e.setEmployeeCode("TMP-" + UUID.randomUUID()); // đổi thành NV<id> ngay bên dưới khi đã có id
        e.setFullName(req.fullName().trim());
        e.setJobTitle(req.jobTitle());
        e.setHiredAt(req.hiredAt());
        employees.saveAndFlush(e);
        e.setEmployeeCode("NV%05d".formatted(e.getId()));
        replaceSpecialties(e.getId(), req.specialtyIds(), req.primarySpecialtyId());
        employees.flush();
        return new CreatedStaffResponse(toResponses(List.of(e)).getFirst(), temp);
    }

    @Transactional
    public StaffResponse update(AuthUser actor, Long id, UpdateStaffRequest req) {
        Employee e = load(id);
        User u = users.findById(e.getUserId()).orElseThrow();
        checkStaffRoles(req.roles());
        checkDoctorHasSpecialty(req.roles(), req.specialtyIds());
        if (u.getId().equals(actor.id()) && actor.has(Role.ADMIN) && !req.roles().contains(Role.ADMIN)) {
            throw ApiException.badRequest("Không thể tự bỏ vai trò quản trị của mình");
        }
        e.setFullName(req.fullName().trim());
        e.setJobTitle(req.jobTitle());
        e.setEmploymentStatus(req.employmentStatus());
        e.setHiredAt(req.hiredAt());
        u.setEmail(blankToNull(req.email()));
        u.getRoles().clear();
        u.getRoles().addAll(req.roles());
        replaceSpecialties(e.getId(), req.specialtyIds(), req.primarySpecialtyId());
        users.flush();
        return toResponses(List.of(e)).getFirst();
    }

    @Transactional
    public DoctorProfileResponse saveDoctorProfile(Long employeeId, DoctorProfileRequest req) {
        Employee e = load(employeeId);
        User u = users.findById(e.getUserId()).orElseThrow();
        if (!u.getRoles().contains(Role.DOCTOR)) {
            throw ApiException.badRequest("Nhân viên chưa có vai trò bác sĩ");
        }
        DoctorProfile p = profiles.findById(employeeId).orElseGet(() -> {
            DoctorProfile n = new DoctorProfile();
            n.setEmployeeId(employeeId);
            return n;
        });
        p.setAcademicTitle(req.academicTitle());
        p.setPracticeLicenseNo(req.practiceLicenseNo().trim());
        p.setPracticeScope(req.practiceScope());
        p.setPrescriberCode(blankToNull(req.prescriberCode()));
        p.setPracticeSince(req.practiceSince() == null ? null : req.practiceSince().shortValue());
        p.setBio(req.bio());
        p.setExpertise(req.expertise());
        if (req.isPublic() != null) {
            p.setPublic(req.isPublic());
        }
        return toProfile(profiles.saveAndFlush(p));
    }

    @Transactional(readOnly = true)
    public DoctorProfileResponse getDoctorProfile(Long employeeId) {
        return toProfile(profiles.findById(employeeId)
                .orElseThrow(() -> ApiException.notFound("Nhân viên chưa có hồ sơ bác sĩ")));
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> listHistory(Long employeeId) {
        return history.findByEmployeeIdOrderByKindAscFromYearDesc(employeeId).stream().map(StaffService::toHistory).toList();
    }

    @Transactional
    public HistoryResponse addHistory(Long employeeId, HistoryRequest req) {
        if (!profiles.existsById(employeeId)) {
            throw ApiException.badRequest("Cần tạo hồ sơ bác sĩ trước");
        }
        if (req.toYear() != null && req.toYear() < req.fromYear()) {
            throw ApiException.badRequest("Năm kết thúc phải sau năm bắt đầu");
        }
        DoctorHistory h = new DoctorHistory();
        h.setEmployeeId(employeeId);
        h.setKind(req.kind());
        h.setFromYear(req.fromYear().shortValue());
        h.setToYear(req.toYear() == null ? null : req.toYear().shortValue());
        h.setTitle(req.title().trim());
        h.setPlace(req.place().trim());
        return toHistory(history.save(h));
    }

    @Transactional
    public void deleteHistory(Long employeeId, Long historyId) {
        history.delete(history.findByIdAndEmployeeId(historyId, employeeId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy mục quá trình")));
    }

    private static void checkStaffRoles(Set<Role> wanted) {
        if (!Role.STAFF.containsAll(wanted)) {
            throw ApiException.badRequest("Vai trò nhân viên không hợp lệ");
        }
    }

    private static void checkDoctorHasSpecialty(Set<Role> roles, List<Long> specialtyIds) {
        if (roles.contains(Role.DOCTOR) && (specialtyIds == null || specialtyIds.isEmpty())) {
            throw ApiException.badRequest("Bác sĩ phải có ít nhất một chuyên khoa");
        }
    }

    private void replaceSpecialties(Long employeeId, List<Long> ids, Long primaryId) {
        Set<Long> unique = ids == null ? Set.of() : new LinkedHashSet<>(ids);
        if (!unique.isEmpty() && specialties.findAllById(unique).size() != unique.size()) {
            throw ApiException.badRequest("Có chuyên khoa không tồn tại");
        }
        if (primaryId != null && !unique.contains(primaryId)) {
            throw ApiException.badRequest("Chuyên khoa chính phải nằm trong danh sách chuyên khoa");
        }
        Long primary = primaryId != null ? primaryId : unique.stream().findFirst().orElse(null);
        links.deleteAllOfEmployee(employeeId);
        links.saveAll(unique.stream().map(sid -> new EmployeeSpecialty(employeeId, sid, sid.equals(primary))).toList());
    }

    private Employee load(Long id) {
        return employees.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
    }

    private List<StaffResponse> toResponses(List<Employee> list) {
        var ids = list.stream().map(Employee::getId).toList();
        Map<Long, User> userById = users.findAllById(list.stream().map(Employee::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        var specs = staffSpecialties.of(ids);
        Set<Long> withProfile = profiles.findAllById(ids).stream().map(DoctorProfile::getEmployeeId).collect(Collectors.toSet());
        return list.stream().map(e -> {
            User u = userById.get(e.getUserId());
            return new StaffResponse(e.getId(), e.getEmployeeCode(), e.getFullName(), e.getJobTitle(), e.getEmploymentStatus(),
                    e.getHiredAt(), u.getId(), u.getPhone(), u.getEmail(), Set.copyOf(u.getRoles()), u.getStatus(),
                    specs.getOrDefault(e.getId(), List.of()), withProfile.contains(e.getId()));
        }).toList();
    }

    static DoctorProfileResponse toProfile(DoctorProfile p) {
        return new DoctorProfileResponse(p.getEmployeeId(), p.getAcademicTitle(), p.getPracticeLicenseNo(), p.getPracticeScope(),
                p.getPrescriberCode(), p.getPracticeSince() == null ? null : p.getPracticeSince().intValue(),
                p.getBio(), p.getExpertise(), p.isPublic());
    }

    static HistoryResponse toHistory(DoctorHistory h) {
        return new HistoryResponse(h.getId(), h.getKind(), h.getFromYear().intValue(),
                h.getToYear() == null ? null : h.getToYear().intValue(), h.getTitle(), h.getPlace());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
