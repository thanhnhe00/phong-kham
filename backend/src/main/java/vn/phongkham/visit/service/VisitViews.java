package vn.phongkham.visit.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.repository.PatientRepository;
import vn.phongkham.schedule.model.WorkSession;
import vn.phongkham.schedule.repository.WorkSessionRepository;
import vn.phongkham.schedule.service.ScheduleLookups;
import vn.phongkham.visit.dto.VisitDtos.VisitResponse;
import vn.phongkham.visit.model.Visit;

/** Ghép lượt khám với bệnh nhân, phiên khám, bác sĩ, phòng (gom theo lô) */
@Component
public class VisitViews {

    private final PatientRepository patients;
    private final WorkSessionRepository sessions;
    private final ScheduleLookups lookups;

    public VisitViews(PatientRepository patients, WorkSessionRepository sessions, ScheduleLookups lookups) {
        this.patients = patients;
        this.sessions = sessions;
        this.lookups = lookups;
    }

    public VisitResponse of(Visit v) {
        return of(List.of(v)).getFirst();
    }

    public List<VisitResponse> of(List<Visit> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Patient> patientById = patients.findAllById(list.stream().map(Visit::getPatientId).distinct().toList())
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, WorkSession> sessionById = sessions.findAllById(list.stream().map(Visit::getSessionId).distinct().toList())
                .stream().collect(Collectors.toMap(WorkSession::getId, Function.identity()));
        Map<Long, String> doctors = lookups.employeeNames(list.stream().map(Visit::getDoctorId).distinct().toList());
        Map<Long, String> specs = lookups.specialtyNames(sessionById.values().stream().map(WorkSession::getSpecialtyId).distinct().toList());
        Map<Long, String> rooms = lookups.roomCodes(sessionById.values().stream().map(WorkSession::getRoomId).distinct().toList());
        return list.stream().map(v -> {
            Patient p = patientById.get(v.getPatientId());
            WorkSession s = sessionById.get(v.getSessionId());
            return new VisitResponse(v.getId(), v.getVisitCode(), v.getSource(), v.getStatus(), v.getQueueNumber(),
                    v.getQueueGroup(), p.getId(), p.getPatientCode(), p.getFullName(), p.getDateOfBirth(), s.getId(),
                    s.getSessionDate(), s.getShiftName(), v.getDoctorId(), doctors.get(v.getDoctorId()), s.getSpecialtyId(),
                    specs.get(s.getSpecialtyId()), rooms.get(s.getRoomId()), v.getAppointmentId(), v.getReason(),
                    v.getCompanionName(), v.getCheckedInAt(), v.getCalledAt(), v.getStartedAt(), v.getCompletedAt());
        }).toList();
    }
}
