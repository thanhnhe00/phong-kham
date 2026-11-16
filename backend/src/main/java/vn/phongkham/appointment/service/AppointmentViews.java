package vn.phongkham.appointment.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.phongkham.appointment.dto.AppointmentDtos.AppointmentResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.ServiceRef;
import vn.phongkham.appointment.model.Appointment;
import vn.phongkham.appointment.model.AppointmentServiceItem;
import vn.phongkham.appointment.repository.AppointmentServiceItemRepository;
import vn.phongkham.medicalservice.model.MedicalService;
import vn.phongkham.medicalservice.repository.MedicalServiceRepository;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.repository.PatientRepository;
import vn.phongkham.schedule.model.AppointmentSlot;
import vn.phongkham.schedule.model.WorkSession;
import vn.phongkham.schedule.repository.AppointmentSlotRepository;
import vn.phongkham.schedule.repository.WorkSessionRepository;
import vn.phongkham.schedule.service.ScheduleLookups;

/** Ghép lịch hẹn với slot, phiên khám, bệnh nhân, bác sĩ, dịch vụ để trả về (gom theo lô, tránh N+1). */
@Component
public class AppointmentViews {

    private final AppointmentSlotRepository slots;
    private final WorkSessionRepository sessions;
    private final PatientRepository patients;
    private final AppointmentServiceItemRepository serviceItems;
    private final MedicalServiceRepository services;
    private final ScheduleLookups lookups;

    public AppointmentViews(AppointmentSlotRepository slots, WorkSessionRepository sessions, PatientRepository patients,
                            AppointmentServiceItemRepository serviceItems, MedicalServiceRepository services,
                            ScheduleLookups lookups) {
        this.slots = slots;
        this.sessions = sessions;
        this.patients = patients;
        this.serviceItems = serviceItems;
        this.services = services;
        this.lookups = lookups;
    }

    public AppointmentResponse of(Appointment a) {
        return of(List.of(a)).getFirst();
    }

    public List<AppointmentResponse> of(List<Appointment> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, AppointmentSlot> slotById = byId(slots.findAllById(list.stream().map(Appointment::getSlotId).distinct().toList()),
                AppointmentSlot::getId);
        Map<Long, WorkSession> sessionById = byId(sessions.findAllById(slotById.values().stream()
                .map(AppointmentSlot::getSessionId).distinct().toList()), WorkSession::getId);
        Map<Long, Patient> patientById = byId(patients.findAllById(list.stream().map(Appointment::getPatientId).distinct().toList()),
                Patient::getId);
        var sessionList = sessionById.values();
        Map<Long, String> doctors = lookups.employeeNames(sessionList.stream().map(WorkSession::getDoctorId).distinct().toList());
        Map<Long, String> specs = lookups.specialtyNames(sessionList.stream().map(WorkSession::getSpecialtyId).distinct().toList());
        Map<Long, String> rooms = lookups.roomCodes(sessionList.stream().map(WorkSession::getRoomId).distinct().toList());
        List<AppointmentServiceItem> items = serviceItems.findByIdAppointmentIdIn(list.stream().map(Appointment::getId).toList());
        Map<Long, String> serviceNames = services.findAllById(items.stream().map(i -> i.getId().getServiceId()).distinct().toList())
                .stream().collect(Collectors.toMap(MedicalService::getId, MedicalService::getName));
        Map<Long, List<ServiceRef>> servicesByAppointment = items.stream().collect(Collectors.groupingBy(
                i -> i.getId().getAppointmentId(),
                Collectors.mapping(i -> new ServiceRef(i.getId().getServiceId(), serviceNames.get(i.getId().getServiceId())),
                        Collectors.toList())));
        return list.stream().map(a -> {
            AppointmentSlot sl = slotById.get(a.getSlotId());
            WorkSession s = sessionById.get(sl.getSessionId());
            Patient p = patientById.get(a.getPatientId());
            return new AppointmentResponse(a.getId(), a.getAppointmentCode(), a.getStatus(), a.getChannel(), p.getId(),
                    p.getPatientCode(), p.getFullName(), p.getPhone(), sl.getId(), sl.getSlotStart(), sl.getSlotEnd(), s.getId(),
                    s.getSessionDate(), s.getShiftName(), s.getDoctorId(), doctors.get(s.getDoctorId()), s.getSpecialtyId(),
                    specs.get(s.getSpecialtyId()), rooms.get(s.getRoomId()), a.getReason(),
                    servicesByAppointment.getOrDefault(a.getId(), List.of()), a.getConfirmedAt(), a.getCancelledAt(),
                    a.getCancelReason(), a.getCreatedAt());
        }).toList();
    }

    private static <T> Map<Long, T> byId(List<T> list, Function<T, Long> id) {
        return list.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
