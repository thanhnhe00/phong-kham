// Kiểu dữ liệu theo các DTO của backend Spring Boot (vn.phongkham.*.dto). Thời điểm là chuỗi ISO UTC, ngày là "yyyy-MM-dd".

export type Role = 'PATIENT' | 'RECEPTIONIST' | 'NURSE' | 'DOCTOR' | 'ADMIN';

export interface Me {
  id: number;
  phone: string;
  email: string | null;
  roles: Role[];
  mustChangePassword: boolean;
  employeeId: number | null;
  fullName: string | null;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: Me;
}

export type Sex = 'MALE' | 'FEMALE' | 'OTHER' | 'UNKNOWN';
export type IdentityType = 'CCCD' | 'PASSPORT' | 'PERSONAL_ID' | 'OTHER' | 'NOT_PROVIDED';
export type Relationship = 'SELF' | 'PARENT' | 'LEGAL_GUARDIAN' | 'FAMILY_MEMBER' | 'OTHER';

export interface Patient {
  id: number;
  patientCode: string;
  fullName: string;
  dateOfBirth: string;
  ageInMonths: number | null;
  sex: Sex | null;
  identityType: IdentityType | null;
  identityNumber: string | null;
  phone: string | null;
  email: string | null;
  province: string | null;
}

export interface MyPatient {
  patient: Patient;
  relationship: Relationship;
}

export interface Allergy {
  id: number;
  allergen: string;
  reaction: string | null;
  severity: string | null;
}

export interface PatientDetail {
  patient: Patient;
  allergies: Allergy[];
}

export interface Specialty {
  id: number;
  code: string;
  name: string;
  description: string | null;
  consultationPrice: number | null;
}

export interface DoctorCard {
  id: number;
  fullName: string;
  academicTitle: string | null;
  specialties: { id: number; name: string; isPrimary: boolean }[];
  practiceSince: number | null;
  expertise: string | null;
}

export interface MedicalService {
  id: number;
  code: string;
  name: string;
  category: string;
  listPrice: number;
}

export interface Slot {
  id: number;
  start: string;
  end: string;
  status: string;
}

export interface OpenSession {
  sessionId: number;
  date: string;
  shiftName: string;
  doctorId: number;
  doctorName: string;
  specialtyId: number;
  specialtyName: string;
  roomCode: string;
  slots: Slot[];
}

export type AppointmentStatus =
  | 'PENDING_CONFIRMATION'
  | 'CONFIRMED'
  | 'NEEDS_RECONFIRMATION'
  | 'CHECKED_IN'
  | 'CANCELLED'
  | 'NO_SHOW';

export interface Appointment {
  id: number;
  code: string;
  status: AppointmentStatus;
  patientId: number;
  patientName: string;
  slotId: number;
  start: string;
  end: string;
  date: string;
  shiftName: string;
  doctorId: number;
  doctorName: string;
  specialtyId: number;
  specialtyName: string;
  roomCode: string;
  reason: string | null;
  services: { id: number; name: string }[];
  confirmedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  createdAt: string;
}

/** Yêu cầu xin đổi giờ / hủy lịch của bệnh nhân, lễ tân duyệt hoặc từ chối */
export interface ChangeRequest {
  id: number;
  appointmentId: number;
  type: 'RESCHEDULE' | 'CANCEL';
  requestedSlotId: number | null;
  requestedSlotStart: string | null;
  reason: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: string;
  handledAt: string | null;
}

export interface QrResponse {
  token: string;
  expiresAt: string;
}

export type VisitStatus = 'WAITING' | 'CALLED' | 'IN_PROGRESS' | 'AWAITING_RESULTS' | 'COMPLETED' | 'LEFT' | 'CANCELLED';

export interface Visit {
  id: number;
  code: string;
  source: 'APPOINTMENT' | 'WALK_IN';
  status: VisitStatus;
  queueNumber: number;
  patientId: number;
  patientName: string;
  sessionId: number;
  date: string;
  shiftName: string;
  doctorId: number;
  doctorName: string;
  specialtyId: number;
  specialtyName: string;
  roomCode: string;
  appointmentId: number | null;
  reason: string | null;
  checkedInAt: string | null;
  calledAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface MyQueueItem {
  visit: Visit;
  peopleAhead: number | null;
}

export interface Vitals {
  pulsePerMin: number | null;
  temperatureC: number | null;
  systolicBp: number | null;
  diastolicBp: number | null;
  respiratoryRate: number | null;
  spo2Percent: number | null;
  weightKg: number | null;
  heightCm: number | null;
  recordedAt: string;
}

export interface Diagnosis {
  icd10Code: string | null;
  name: string;
  type: 'PRIMARY' | 'SECONDARY' | 'DIFFERENTIAL';
}

export interface Emr {
  status: string;
  chiefComplaint: string | null;
  conclusion: string | null;
  treatmentPlan: string | null;
  advice: string | null;
  followUpDate: string | null;
  diagnoses: Diagnosis[];
  signedByName: string | null;
}

export interface ResultFile {
  id: number;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
}

export interface OrderItem {
  id: number;
  serviceName: string;
  category: string;
  performances: {
    id: number;
    status: string;
    result: { status: string; resultText: string | null; conclusion: string | null; files: ResultFile[] } | null;
  }[];
}

export interface Order {
  id: number;
  orderedAt: string;
  items: OrderItem[];
}

export interface PrescriptionItem {
  lineNo: number;
  medicineLabel: string;
  quantity: number;
  unit: string;
  dosePerTime: string;
  timesPerDay: number;
  route: string | null;
  timing: string | null;
  days: number;
  note: string | null;
}

export interface Prescription {
  id: number;
  code: string | null;
  status: string;
  advice: string | null;
  signedByName: string | null;
  signedAt: string | null;
  items: PrescriptionItem[];
}

export type InvoiceStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'REFUNDED' | 'CANCELLED';

export interface Invoice {
  id: number;
  code: string;
  visitId: number;
  totalAmount: number;
  paidAmount: number;
  remaining: number;
  status: InvoiceStatus;
  issuedAt: string | null;
  items: { id: number; type: string; description: string; quantity: number; unitPrice: number; lineTotal: number }[];
}

export interface VisitRecord {
  visit: Visit;
  vitals: Vitals[];
  emr: Emr | null;
  orders: Order[];
  prescriptions: Prescription[];
  invoice: Invoice | null;
}

export interface AppNotification {
  id: number;
  type: string;
  title: string;
  body: string;
  relatedEntityType: string | null;
  relatedEntityId: number | null;
  readAt: string | null;
  createdAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
