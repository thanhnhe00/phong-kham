import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { addDays, vnDate } from './format';
import { api } from './api';
import type {
  AppNotification,
  Appointment,
  ChangeRequest,
  DoctorCard,
  MedicalService,
  MyPatient,
  MyQueueItem,
  OpenSession,
  Page,
  PatientDetail,
  QrResponse,
  Specialty,
  Visit,
  VisitRecord,
} from './types';

/** Đang trong ngày khám thì làm mới thường xuyên để số thứ tự, trạng thái luôn đúng. */
export const LIVE_MS = 15_000;
// shortcut: cập nhật bằng hỏi lại định kỳ, chưa có đẩy thời gian thực; khi backend có WebSocket/push thì bỏ hỏi lại.

export const qk = {
  patients: ['patients'] as const,
  patient: (id: number) => ['patient', id] as const,
  appointments: ['appointments'] as const,
  appointment: (id: number) => ['appointment', id] as const,
  queue: ['queue'] as const,
  visits: (patientId: number) => ['visits', patientId] as const,
  record: (visitId: number) => ['record', visitId] as const,
  notifications: ['notifications'] as const,
};

export const usePatients = (enabled = true) =>
  useQuery({ queryKey: qk.patients, queryFn: () => api.get<MyPatient[]>('/me/patients'), enabled });

export const usePatientDetail = (id: number | null) =>
  useQuery({ queryKey: qk.patient(id ?? 0), queryFn: () => api.get<PatientDetail>(`/me/patients/${id}`), enabled: !!id });

export const useAppointments = (live = false) =>
  useQuery({
    queryKey: qk.appointments,
    queryFn: () => api.get<Appointment[]>('/me/appointments'),
    refetchInterval: live ? LIVE_MS : false,
  });

export const useAppointment = (id: number) =>
  useQuery({ queryKey: qk.appointment(id), queryFn: () => api.get<Appointment>(`/me/appointments/${id}`) });

/** Yêu cầu đổi/hủy đã gửi cho một lịch, mới nhất trước */
export const useChangeRequests = (appointmentId: number) =>
  useQuery({
    queryKey: [...qk.appointment(appointmentId), 'change-requests'],
    queryFn: () => api.get<ChangeRequest[]>(`/me/appointments/${appointmentId}/change-requests`),
  });

/** /me/queue chỉ trả lượt khám hôm nay: có lượt nào thì hỏi lại theo nhịp trong ngày khám, không thì mỗi phút */
export const useQueue = (live = false) =>
  useQuery({
    queryKey: qk.queue,
    queryFn: () => api.get<MyQueueItem[]>('/me/queue'),
    refetchInterval: (q) => (live || q.state.data?.length ? LIVE_MS : 60_000),
  });

export const useVisits = (patientId: number | null) =>
  useQuery({
    queryKey: qk.visits(patientId ?? 0),
    queryFn: () => api.get<Visit[]>(`/me/visits?patientId=${patientId}`),
    enabled: !!patientId,
  });

export const fetchRecord = (visitId: number) => api.get<VisitRecord>(`/me/visits/${visitId}`);

export const useRecord = (visitId: number, live = false) =>
  useQuery({ queryKey: qk.record(visitId), queryFn: () => fetchRecord(visitId), refetchInterval: live ? LIVE_MS : false });

/** Hồ sơ chi tiết của vài lượt khám gần nhất (để biết hóa đơn còn nợ, lịch tái khám, đơn thuốc). */
// shortcut: gọi từng lượt khám vì backend chưa có API tổng hợp hóa đơn/đơn thuốc theo bệnh nhân; nếu lịch sử dài thì thêm API danh sách.
export function useRecentRecords(visits: Visit[] | undefined, limit = 5, live = false) {
  const ids = (visits ?? []).filter((v) => v.status !== 'CANCELLED' && v.status !== 'LEFT').slice(0, limit).map((v) => v.id);
  const results = useQueries({
    queries: ids.map((id) => ({ queryKey: qk.record(id), queryFn: () => fetchRecord(id), refetchInterval: live ? LIVE_MS : (false as const) })),
  });
  return {
    records: results.map((r) => r.data).filter((r): r is VisitRecord => !!r),
    loading: results.some((r) => r.isLoading),
  };
}

export const useNotifications = () =>
  useQuery({
    queryKey: qk.notifications,
    queryFn: () => api.get<Page<AppNotification>>('/me/notifications?size=50'),
    refetchInterval: 60_000,
  });

export const useSpecialties = () => useQuery({ queryKey: ['specialties'], queryFn: () => api.get<Specialty[]>('/specialties'), staleTime: 300_000 });

export const useDoctors = (specialtyId: number | null) =>
  useQuery({
    queryKey: ['doctors', specialtyId],
    queryFn: () => api.get<DoctorCard[]>(`/doctors?specialtyId=${specialtyId}`),
    enabled: !!specialtyId,
    staleTime: 300_000,
  });

export const useLabServices = () =>
  useQuery({ queryKey: ['services', 'LAB'], queryFn: () => api.get<MedicalService[]>('/services?category=LAB'), staleTime: 300_000 });

/** Slot trống của một bác sĩ trong 30 ngày tới (backend cho tối đa 31 ngày) */
export const useOpenSlots = (doctorId: number | null) =>
  useQuery({
    queryKey: ['slots', doctorId],
    queryFn: () => {
      const from = vnDate();
      return api.get<OpenSession[]>(`/slots?doctorId=${doctorId}&from=${from}&to=${addDays(from, 30)}`);
    },
    enabled: !!doctorId,
  });

export const fetchQr = (appointmentId: number) => api.get<QrResponse>(`/me/appointments/${appointmentId}/qr`);

/** Sau thao tác ghi thì làm mới mọi dữ liệu của bệnh nhân. */
export function useInvalidateAll() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries();
}

export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.post(`/me/notifications/${id}/read`),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.notifications }),
  });
}
