package vn.phongkham.clinical.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.clinical.dto.ClinicalDtos.AmendResultRequest;
import vn.phongkham.clinical.dto.ClinicalDtos.FileResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.ItemResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.OrderRequest;
import vn.phongkham.clinical.dto.ClinicalDtos.OrderResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.PerformRequest;
import vn.phongkham.clinical.dto.ClinicalDtos.PerformanceResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.ResultRequest;
import vn.phongkham.clinical.dto.ClinicalDtos.ResultResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.StoredFile;
import vn.phongkham.clinical.dto.ClinicalDtos.WorklistItem;
import vn.phongkham.clinical.model.ClinicalEnums.FileCategory;
import vn.phongkham.clinical.model.ClinicalEnums.FileVisibility;
import vn.phongkham.clinical.model.ClinicalEnums.ItemStatus;
import vn.phongkham.clinical.model.ClinicalEnums.OrderStatus;
import vn.phongkham.clinical.model.ClinicalEnums.PerformanceStatus;
import vn.phongkham.clinical.model.ClinicalEnums.ResultStatus;
import vn.phongkham.clinical.model.DocumentFile;
import vn.phongkham.clinical.model.ServiceOrder;
import vn.phongkham.clinical.model.ServiceOrderItem;
import vn.phongkham.clinical.model.ServicePerformance;
import vn.phongkham.clinical.model.ServiceResult;
import vn.phongkham.clinical.model.ServiceResultAmendment;
import vn.phongkham.clinical.repository.DocumentFileRepository;
import vn.phongkham.clinical.repository.ServiceOrderItemRepository;
import vn.phongkham.clinical.repository.ServiceOrderRepository;
import vn.phongkham.clinical.repository.ServicePerformanceRepository;
import vn.phongkham.clinical.repository.ServiceResultAmendmentRepository;
import vn.phongkham.clinical.repository.ServiceResultRepository;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.medicalservice.model.MedicalService;
import vn.phongkham.medicalservice.model.ServiceCategory;
import vn.phongkham.medicalservice.repository.MedicalServiceRepository;
import vn.phongkham.notification.service.NotificationService;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.repository.PatientRepository;
import vn.phongkham.schedule.service.ScheduleLookups;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.model.Visit;
import vn.phongkham.visit.repository.VisitRepository;
import vn.phongkham.visit.service.VisitAccess;

/**
 * UC24, UC25, UC29, UC30: bác sĩ chỉ định / bớt dịch vụ / ghi nhận bệnh nhân từ chối; kỹ thuật viên thực hiện,
 * nhập kết quả và tệp; bác sĩ duyệt kết quả (chỉ kết quả đã duyệt mới hiện cho bệnh nhân);
 * sửa kết quả đã duyệt phải tạo bản điều chỉnh và duyệt lại.
 */
@Service
public class ClinicalService {

    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository items;
    private final ServicePerformanceRepository performances;
    private final ServiceResultRepository results;
    private final ServiceResultAmendmentRepository amendments;
    private final DocumentFileRepository files;
    private final MedicalServiceRepository services;
    private final VisitRepository visits;
    private final PatientRepository patients;
    private final VisitAccess access;
    private final ScheduleLookups lookups;
    private final CurrentStaff currentStaff;
    private final NotificationService notifications;
    private final FileStorage storage;
    private final JsonMapper json;

    public ClinicalService(ServiceOrderRepository orders, ServiceOrderItemRepository items, ServicePerformanceRepository performances,
                           ServiceResultRepository results, ServiceResultAmendmentRepository amendments, DocumentFileRepository files,
                           MedicalServiceRepository services, VisitRepository visits, PatientRepository patients,
                           VisitAccess access, ScheduleLookups lookups, CurrentStaff currentStaff,
                           NotificationService notifications, FileStorage storage, JsonMapper json) {
        this.orders = orders;
        this.items = items;
        this.performances = performances;
        this.results = results;
        this.amendments = amendments;
        this.files = files;
        this.services = services;
        this.visits = visits;
        this.patients = patients;
        this.access = access;
        this.lookups = lookups;
        this.currentStaff = currentStaff;
        this.notifications = notifications;
        this.storage = storage;
        this.json = json;
    }

    @Transactional
    public OrderResponse order(AuthUser me, Long visitId, OrderRequest req) {
        access.forDoctor(me, visitId, VisitAccess.EXAMINING);
        Map<Long, MedicalService> byId = services.findAllById(req.items().stream().map(i -> i.serviceId()).distinct().toList())
                .stream().collect(Collectors.toMap(MedicalService::getId, Function.identity()));
        for (var i : req.items()) {
            MedicalService s = byId.get(i.serviceId());
            if (s == null || !s.isActive()) {
                throw ApiException.badRequest("Có dịch vụ không tồn tại hoặc đã ngừng áp dụng");
            }
            if (s.getCategory() == ServiceCategory.CONSULTATION) {
                throw ApiException.badRequest("Phí khám đã tính theo chuyên khoa, không chỉ định thêm");
            }
        }
        ServiceOrder o = new ServiceOrder();
        o.setVisitId(visitId);
        o.setOrderedBy(currentStaff.idOf(me));
        o.setNote(req.note());
        orders.save(o);
        items.saveAll(req.items().stream().map(i -> {
            ServiceOrderItem x = new ServiceOrderItem();
            x.setServiceOrderId(o.getId());
            x.setServiceId(i.serviceId());
            x.setRequestedQuantity((short) (i.quantity() == null ? 1 : i.quantity()));
            x.setUnitPrice(byId.get(i.serviceId()).getListPrice()); // chốt giá lúc chỉ định
            return x;
        }).toList());
        return toOrders(List.of(o), false).getFirst();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> forVisit(Long visitId, boolean patientView) {
        return toOrders(orders.findByVisitIdOrderByOrderedAt(visitId), patientView);
    }

    /** Bệnh nhân từ chối: ghi nhận lý do, không thực hiện, không tính tiền */
    @Transactional
    public ItemResponse refuse(AuthUser me, Long itemId, String note) {
        ServiceOrderItem x = loadItemForDoctor(me, itemId);
        requireStatus(x, ItemStatus.ORDERED, "Chỉ ghi nhận từ chối khi dịch vụ chưa thực hiện");
        x.setStatus(ItemStatus.REFUSED);
        x.setRefusalNote(note.trim());
        refreshOrder(x.getServiceOrderId());
        return item(x);
    }

    /** Bác sĩ bớt dịch vụ trước khi thực hiện */
    @Transactional
    public ItemResponse cancel(AuthUser me, Long itemId) {
        ServiceOrderItem x = loadItemForDoctor(me, itemId);
        requireStatus(x, ItemStatus.ORDERED, "Chỉ bớt được dịch vụ chưa thực hiện");
        x.setStatus(ItemStatus.CANCELLED);
        refreshOrder(x.getServiceOrderId());
        return item(x);
    }

    /**
     * Việc của điều dưỡng/bác sĩ: mục chỉ định của lượt khám còn mở mà chưa có kết quả được duyệt
     * (chờ làm, đã làm nhưng kết quả còn nháp/bị trả lại/chờ duyệt). category = null: mọi loại.
     */
    @Transactional(readOnly = true)
    public List<WorklistItem> worklist(ServiceCategory category) {
        List<ServiceOrderItem> open = items.findOfOpenVisits(vn.phongkham.visit.model.VisitEnums.VisitStatus.OPEN,
                java.util.EnumSet.of(ItemStatus.ORDERED, ItemStatus.IN_PROGRESS, ItemStatus.PERFORMED));
        Map<Long, ItemResponse> responses = toItems(open, false).stream().collect(Collectors.toMap(ItemResponse::id, Function.identity()));
        List<ServiceOrderItem> pending = open.stream().filter(x -> {
            ItemResponse r = responses.get(x.getId());
            if (category != null && r.category() != category) {
                return false;
            }
            return x.getStatus() != ItemStatus.PERFORMED || r.performances().stream()
                    .anyMatch(pf -> pf.result() == null || !ResultStatus.VISIBLE.contains(pf.result().status()));
        }).toList();
        Map<Long, ServiceOrder> orderById = orders.findAllById(pending.stream().map(ServiceOrderItem::getServiceOrderId).distinct().toList())
                .stream().collect(Collectors.toMap(ServiceOrder::getId, Function.identity()));
        Map<Long, Visit> visitById = visits.findAllById(orderById.values().stream().map(ServiceOrder::getVisitId).distinct().toList())
                .stream().collect(Collectors.toMap(Visit::getId, Function.identity()));
        Map<Long, Patient> patientById = patients.findAllById(visitById.values().stream().map(Visit::getPatientId).distinct().toList())
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, String> doctors = lookups.employeeNames(visitById.values().stream().map(Visit::getDoctorId).distinct().toList());
        return pending.stream().map(x -> {
            ServiceOrder o = orderById.get(x.getServiceOrderId());
            Visit v = visitById.get(o.getVisitId());
            Patient p = patientById.get(v.getPatientId());
            return new WorklistItem(responses.get(x.getId()), v.getId(), v.getVisitCode(), p.getId(), p.getPatientCode(),
                    p.getFullName(), v.getDoctorId(), doctors.get(v.getDoctorId()), o.getOrderedAt());
        }).toList();
    }

    /** Kỹ thuật viên ghi nhận một lần thực hiện; đủ số lượng chỉ định thì mục chuyển "đã thực hiện" */
    @Transactional
    public PerformanceResponse perform(AuthUser me, Long itemId, PerformRequest req) {
        ServiceOrderItem x = items.findById(itemId).orElseThrow(() -> ApiException.notFound("Không tìm thấy dịch vụ được chỉ định"));
        if (!ItemStatus.PENDING.contains(x.getStatus())) {
            throw ApiException.badRequest("Dịch vụ không còn chờ thực hiện");
        }
        ServicePerformance p = new ServicePerformance();
        p.setOrderItemId(itemId);
        p.setPerformedBy(currentStaff.idOf(me));
        p.setQuantity((short) (req.quantity() == null ? 1 : req.quantity()));
        p.setNote(req.note());
        performances.save(p);
        int done = performances.findByOrderItemId(itemId).stream().filter(y -> y.getStatus() == PerformanceStatus.DONE)
                .mapToInt(y -> y.getQuantity()).sum();
        x.setStatus(done >= x.getRequestedQuantity() ? ItemStatus.PERFORMED : ItemStatus.IN_PROGRESS);
        if (x.getAssignedTo() == null) {
            x.setAssignedTo(p.getPerformedBy());
        }
        refreshOrder(x.getServiceOrderId());
        return toPerformances(List.of(p), false).getFirst();
    }

    @Transactional
    public ResultResponse saveResult(AuthUser me, Long performanceId, ResultRequest req) {
        performances.findById(performanceId).orElseThrow(() -> ApiException.notFound("Không tìm thấy lần thực hiện"));
        ServiceResult r = results.findByPerformanceId(performanceId).orElseGet(() -> {
            ServiceResult n = new ServiceResult();
            n.setPerformanceId(performanceId);
            return n;
        });
        if (ResultStatus.VISIBLE.contains(r.getStatus())) {
            throw ApiException.conflict("Kết quả đã được bác sĩ duyệt, hãy lập bản điều chỉnh");
        }
        r.setResultText(req.resultText());
        r.setConclusion(req.conclusion());
        r.setEnteredBy(currentStaff.idOf(me));
        r.setEnteredAt(Instant.now());
        r.setStatus(Boolean.TRUE.equals(req.submit()) ? ResultStatus.SUBMITTED : ResultStatus.DRAFT);
        if (r.getStatus() == ResultStatus.SUBMITTED) {
            r.setReturnNote(null);
        }
        results.save(r);
        return toResults(List.of(r), false).getFirst();
    }

    /** Bác sĩ của lượt khám duyệt; từ lúc này bệnh nhân xem được kết quả và tệp */
    @Transactional
    public ResultResponse review(AuthUser me, Long resultId) {
        ServiceResult r = results.findById(resultId).orElseThrow(() -> ApiException.notFound("Không tìm thấy kết quả"));
        Visit v = visitOfResult(r);
        access.forDoctor(me, v.getId(), Set.of());
        if (r.getStatus() != ResultStatus.SUBMITTED) {
            throw ApiException.badRequest("Kết quả chưa được gửi duyệt hoặc đã duyệt");
        }
        Long doctorId = currentStaff.idOf(me);
        r.setReviewedBy(doctorId);
        r.setReviewedAt(Instant.now());
        r.setStatus(r.getVersion() > 1 ? ResultStatus.AMENDED : ResultStatus.REVIEWED);
        amendments.findByResultIdOrderByFromVersion(r.getId()).stream().filter(a -> a.getReconfirmedAt() == null)
                .forEach(a -> {
                    a.setReconfirmedBy(doctorId);
                    a.setReconfirmedAt(Instant.now());
                });
        notifications.toPatient(v.getPatientId(), "RESULTS_READY", "Đã có kết quả cận lâm sàng",
                "Kết quả của lượt khám " + v.getVisitCode() + " đã được bác sĩ duyệt.", "VISIT", v.getId());
        return toResults(List.of(r), false).getFirst();
    }

    /** Bác sĩ trả kết quả chờ duyệt về cho điều dưỡng sửa, kèm lý do */
    @Transactional
    public ResultResponse returnToNurse(AuthUser me, Long resultId, String reason) {
        ServiceResult r = results.findById(resultId).orElseThrow(() -> ApiException.notFound("Không tìm thấy kết quả"));
        access.forDoctor(me, visitOfResult(r).getId(), Set.of());
        if (r.getStatus() != ResultStatus.SUBMITTED) {
            throw ApiException.badRequest("Chỉ trả lại kết quả đang chờ duyệt");
        }
        r.setStatus(ResultStatus.DRAFT);
        r.setReturnNote(reason.trim());
        return toResults(List.of(r), false).getFirst();
    }

    /** Sửa kết quả đã duyệt: lưu bản cũ, tăng phiên bản, ẩn với bệnh nhân cho tới khi bác sĩ duyệt lại */
    @Transactional
    public ResultResponse amend(AuthUser me, Long resultId, AmendResultRequest req) {
        ServiceResult r = results.findById(resultId).orElseThrow(() -> ApiException.notFound("Không tìm thấy kết quả"));
        if (!ResultStatus.VISIBLE.contains(r.getStatus())) {
            throw ApiException.badRequest("Kết quả chưa duyệt, hãy sửa trực tiếp");
        }
        ServiceResultAmendment a = new ServiceResultAmendment();
        a.setResultId(r.getId());
        a.setFromVersion(r.getVersion());
        a.setPreviousSnapshot(json.writeValueAsString(Map.of(
                "resultText", String.valueOf(r.getResultText()), "conclusion", String.valueOf(r.getConclusion()),
                "reviewedBy", String.valueOf(r.getReviewedBy()), "reviewedAt", String.valueOf(r.getReviewedAt()))));
        a.setReason(req.reason().trim());
        a.setAmendedBy(currentStaff.idOf(me));
        amendments.save(a);
        r.setResultText(req.resultText());
        r.setConclusion(req.conclusion());
        r.setVersion(r.getVersion() + 1);
        r.setStatus(ResultStatus.SUBMITTED);
        r.setEnteredBy(a.getAmendedBy());
        r.setEnteredAt(Instant.now());
        return toResults(List.of(r), false).getFirst();
    }

    @Transactional
    public FileResponse attach(AuthUser me, Long resultId, String fileName, byte[] content) {
        ServiceResult r = results.findById(resultId).orElseThrow(() -> ApiException.notFound("Không tìm thấy kết quả"));
        Visit v = visitOfResult(r);
        String mime = FileStorage.detectMime(content);
        DocumentFile f = new DocumentFile();
        f.setPatientId(v.getPatientId());
        f.setVisitId(v.getId());
        f.setServiceResultId(r.getId());
        f.setUploadedBy(currentStaff.idOf(me));
        f.setFileName(fileName == null || fileName.isBlank() ? "ket-qua" : fileName.trim());
        f.setMimeType(mime);
        f.setSizeBytes((long) content.length);
        f.setSha256(sha256(content));
        f.setCategory(mime.equals("application/pdf") ? FileCategory.LAB_FILE : FileCategory.IMAGING);
        f.setVisibility(FileVisibility.PATIENT_VISIBLE); // vẫn chỉ hiện khi kết quả đã được duyệt
        f.setStorageKey(storage.save(content));
        files.save(f);
        return toFile(f);
    }

    /** Nhân viên tải tệp */
    @Transactional(readOnly = true)
    public StoredFile download(Long fileId) {
        DocumentFile f = files.findById(fileId).orElseThrow(() -> ApiException.notFound("Không tìm thấy tệp"));
        return new StoredFile(f.getFileName(), f.getMimeType(), storage.load(f.getStorageKey()));
    }

    /** Bệnh nhân tải tệp: phải là hồ sơ mình quản lý, tệp cho phép bệnh nhân xem và kết quả đã được duyệt */
    @Transactional(readOnly = true)
    public StoredFile downloadForPatient(AuthUser me, Long fileId) {
        DocumentFile f = files.findById(fileId).orElseThrow(() -> ApiException.notFound("Không tìm thấy tệp"));
        access.forPatient(me, f.getVisitId());
        boolean resultVisible = f.getServiceResultId() == null
                || results.findById(f.getServiceResultId()).map(r -> ResultStatus.VISIBLE.contains(r.getStatus())).orElse(false);
        if (f.getVisibility() == FileVisibility.STAFF_ONLY || !resultVisible) {
            throw ApiException.notFound("Không tìm thấy tệp");
        }
        return new StoredFile(f.getFileName(), f.getMimeType(), storage.load(f.getStorageKey()));
    }

    /** Dùng khi kết thúc lượt khám: còn dịch vụ chưa thực hiện / kết quả chưa duyệt thì chưa kết thúc được */
    @Transactional(readOnly = true)
    public String pendingWork(Long visitId) {
        List<ServiceOrderItem> list = itemsOfVisit(visitId);
        if (list.stream().anyMatch(x -> ItemStatus.PENDING.contains(x.getStatus()))) {
            return "Còn dịch vụ chưa thực hiện: hãy chờ kết quả, bớt dịch vụ hoặc ghi nhận bệnh nhân từ chối";
        }
        var perfIds = performances.findByOrderItemIdIn(list.stream().map(ServiceOrderItem::getId).toList()).stream()
                .filter(p -> p.getStatus() == PerformanceStatus.DONE).map(ServicePerformance::getId).toList();
        var res = results.findByPerformanceIdIn(perfIds);
        if (res.size() < perfIds.size() || res.stream().anyMatch(r -> !ResultStatus.VISIBLE.contains(r.getStatus()))) {
            return "Còn kết quả cận lâm sàng chưa nhập hoặc chưa được bác sĩ duyệt";
        }
        return null;
    }

    /** Các dịch vụ đã thực hiện của lượt khám (để lập hóa đơn): mục chỉ định + số lượng đã làm */
    @Transactional(readOnly = true)
    public Map<ServiceOrderItem, Integer> performedItems(Long visitId) {
        List<ServiceOrderItem> list = itemsOfVisit(visitId).stream().filter(x -> x.getStatus() == ItemStatus.PERFORMED).toList();
        Map<Long, Integer> done = performances.findByOrderItemIdIn(list.stream().map(ServiceOrderItem::getId).toList()).stream()
                .filter(p -> p.getStatus() == PerformanceStatus.DONE)
                .collect(Collectors.groupingBy(ServicePerformance::getOrderItemId, Collectors.summingInt(p -> p.getQuantity())));
        Map<ServiceOrderItem, Integer> result = new LinkedHashMap<>();
        list.forEach(x -> result.put(x, done.getOrDefault(x.getId(), 0)));
        return result;
    }

    public boolean hasPendingItems(Long visitId) {
        return itemsOfVisit(visitId).stream().anyMatch(x -> ItemStatus.PENDING.contains(x.getStatus()));
    }

    private List<ServiceOrderItem> itemsOfVisit(Long visitId) {
        var orderIds = orders.findByVisitIdOrderByOrderedAt(visitId).stream().map(ServiceOrder::getId).toList();
        return orderIds.isEmpty() ? List.of() : items.findByServiceOrderIdIn(orderIds);
    }

    private ServiceOrderItem loadItemForDoctor(AuthUser me, Long itemId) {
        ServiceOrderItem x = items.findById(itemId).orElseThrow(() -> ApiException.notFound("Không tìm thấy dịch vụ được chỉ định"));
        Long visitId = orders.findById(x.getServiceOrderId()).orElseThrow().getVisitId();
        access.forDoctor(me, visitId, VisitAccess.EXAMINING);
        return x;
    }

    private static void requireStatus(ServiceOrderItem x, ItemStatus s, String message) {
        if (x.getStatus() != s) {
            throw ApiException.badRequest(message);
        }
    }

    /** Trạng thái phiếu: mọi mục đã xong (làm/từ chối/bớt) = COMPLETED; có mục đã làm = PARTIALLY_DONE */
    private void refreshOrder(Long orderId) {
        ServiceOrder o = orders.findById(orderId).orElseThrow();
        List<ServiceOrderItem> list = items.findByServiceOrderId(orderId);
        boolean allClosed = list.stream().noneMatch(x -> ItemStatus.PENDING.contains(x.getStatus()));
        boolean anyPerformed = list.stream().anyMatch(x -> x.getStatus() == ItemStatus.PERFORMED || x.getStatus() == ItemStatus.IN_PROGRESS);
        boolean allDropped = list.stream().allMatch(x -> x.getStatus() == ItemStatus.CANCELLED || x.getStatus() == ItemStatus.REFUSED);
        o.setStatus(allDropped ? OrderStatus.CANCELLED : allClosed ? OrderStatus.COMPLETED
                : anyPerformed ? OrderStatus.PARTIALLY_DONE : OrderStatus.OPEN);
    }

    private Visit visitOfResult(ServiceResult r) {
        ServicePerformance p = performances.findById(r.getPerformanceId()).orElseThrow();
        ServiceOrderItem x = items.findById(p.getOrderItemId()).orElseThrow();
        return access.load(orders.findById(x.getServiceOrderId()).orElseThrow().getVisitId());
    }

    // ---------- chuyển sang DTO (patientView: ẩn kết quả chưa duyệt và tệp chỉ dành cho nhân viên) ----------

    private ItemResponse item(ServiceOrderItem x) {
        return toItems(List.of(x), false).getFirst();
    }

    private List<OrderResponse> toOrders(List<ServiceOrder> list, boolean patientView) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ItemResponse>> byOrder = toItems(items.findByServiceOrderIdIn(list.stream().map(ServiceOrder::getId).toList()), patientView)
                .stream().collect(Collectors.groupingBy(ItemResponse::orderId));
        Map<Long, String> names = lookups.employeeNames(list.stream().map(ServiceOrder::getOrderedBy).distinct().toList());
        return list.stream().map(o -> new OrderResponse(o.getId(), o.getVisitId(), o.getOrderedBy(), names.get(o.getOrderedBy()),
                o.getOrderedAt(), o.getNote(), o.getStatus(), byOrder.getOrDefault(o.getId(), List.of()))).toList();
    }

    private List<ItemResponse> toItems(List<ServiceOrderItem> list, boolean patientView) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, MedicalService> svc = services.findAllById(list.stream().map(ServiceOrderItem::getServiceId).distinct().toList())
                .stream().collect(Collectors.toMap(MedicalService::getId, Function.identity()));
        Map<Long, List<PerformanceResponse>> perf = toPerformances(
                performances.findByOrderItemIdIn(list.stream().map(ServiceOrderItem::getId).toList()), patientView)
                .stream().collect(Collectors.groupingBy(PerformanceResponse::orderItemId));
        return list.stream().map(x -> new ItemResponse(x.getId(), x.getServiceOrderId(), x.getServiceId(),
                svc.get(x.getServiceId()).getName(), svc.get(x.getServiceId()).getCategory(), x.getRequestedQuantity(),
                x.getUnitPrice(), x.getStatus(), x.getRefusalNote(), perf.getOrDefault(x.getId(), List.of()))).toList();
    }

    private List<PerformanceResponse> toPerformances(List<ServicePerformance> list, boolean patientView) {
        Map<Long, ResultResponse> byPerf = toResults(results.findByPerformanceIdIn(list.stream().map(ServicePerformance::getId).toList()),
                patientView).stream().collect(Collectors.toMap(ResultResponse::performanceId, Function.identity()));
        Map<Long, String> names = lookups.employeeNames(list.stream().map(ServicePerformance::getPerformedBy).distinct().toList());
        return list.stream().map(p -> new PerformanceResponse(p.getId(), p.getOrderItemId(), p.getPerformedBy(), names.get(p.getPerformedBy()),
                p.getPerformedAt(), p.getQuantity(), p.getStatus(), p.getNote(), byPerf.get(p.getId()))).toList();
    }

    private List<ResultResponse> toResults(List<ServiceResult> list, boolean patientView) {
        List<ServiceResult> shown = patientView ? list.stream().filter(r -> ResultStatus.VISIBLE.contains(r.getStatus())).toList() : list;
        if (shown.isEmpty()) {
            return List.of();
        }
        Map<Long, List<FileResponse>> fileMap = files.findByServiceResultIdIn(shown.stream().map(ServiceResult::getId).toList()).stream()
                .filter(f -> !patientView || f.getVisibility() != FileVisibility.STAFF_ONLY)
                .collect(Collectors.groupingBy(DocumentFile::getServiceResultId, Collectors.mapping(ClinicalService::toFile, Collectors.toList())));
        Map<Long, String> names = lookups.employeeNames(shown.stream().flatMap(r -> Stream.of(r.getEnteredBy(), r.getReviewedBy()))
                .filter(java.util.Objects::nonNull).distinct().toList());
        return shown.stream().map(r -> new ResultResponse(r.getId(), r.getPerformanceId(), r.getStatus(), r.getVersion(),
                r.getResultText(), r.getConclusion(), r.getEnteredBy(), names.get(r.getEnteredBy()), r.getEnteredAt(),
                r.getReviewedBy(), r.getReviewedBy() == null ? null : names.get(r.getReviewedBy()), r.getReviewedAt(),
                patientView ? null : r.getReturnNote(), fileMap.getOrDefault(r.getId(), List.of()))).toList();
    }

    private static FileResponse toFile(DocumentFile f) {
        return new FileResponse(f.getId(), f.getFileName(), f.getMimeType(), f.getSizeBytes(), f.getCategory(), f.getCreatedAt());
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
