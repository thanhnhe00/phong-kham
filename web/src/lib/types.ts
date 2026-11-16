// Kiểu dữ liệu trả về từ backend (theo Swagger /api/v3/api-docs), chỉ giữ trường web dùng.
export type Role = 'PATIENT' | 'RECEPTIONIST' | 'NURSE' | 'DOCTOR' | 'ADMIN';
export type StaffRole = Exclude<Role, 'PATIENT'>;

export interface Page<T> { items: T[]; page: number; size: number; total: number }

export interface Me { id: number; phone: string; roles: Role[]; mustChangePassword: boolean; employeeId: number | null; fullName: string | null }
export interface AuthResponse { token: string; user: Me }

export type Sex = 'MALE' | 'FEMALE' | 'OTHER' | 'UNKNOWN';
export interface Patient {
  id: number; patientCode: string; fullName: string; dateOfBirth: string; ageInMonths: number; sex: Sex;
  identityType: string | null; identityNumber: string | null; phone: string | null; addressLine: string | null;
}
export interface Allergy { id: number; allergen: string; reaction: string | null; severity: string | null }
export interface Representative { id: number; fullName: string; relationship: string; phone: string | null }
export interface PatientDetail { patient: Patient; allergies: Allergy[]; representatives: Representative[]; linkedAccounts: number }

export type AppointmentStatus = 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'NEEDS_RECONFIRMATION' | 'CHECKED_IN' | 'CANCELLED' | 'NO_SHOW';
export interface Appointment {
  id: number; code: string; status: AppointmentStatus; channel: 'APP_REQUEST' | 'PHONE' | 'COUNTER';
  patientId: number; patientCode: string; patientName: string; patientPhone: string | null;
  slotId: number; start: string; end: string; sessionId: number; date: string; shiftName: string;
  doctorId: number; doctorName: string; specialtyId: number; specialtyName: string; roomCode: string;
  reason: string | null; services: { id: number; name: string }[]; createdAt: string;
}
export interface ConfirmItem { appointment: Appointment; noAnswerCount: number; maxAttemptsReached: boolean; recentNoShows: number }
export interface Contact { id: number; result: string; purpose: string; note: string | null; employeeName: string | null; contactedAt: string }
export interface AppointmentChange {
  id: number; appointmentId: number; type: 'RESCHEDULE' | 'CANCEL'; requestedSlotId: number | null; requestedSlotStart: string | null;
  reason: string | null; status: 'PENDING' | 'APPROVED' | 'REJECTED'; createdAt: string; handledAt: string | null;
}

export type VisitStatus = 'WAITING' | 'CALLED' | 'IN_PROGRESS' | 'AWAITING_RESULTS' | 'COMPLETED' | 'LEFT' | 'CANCELLED';
export interface Visit {
  id: number; code: string; source: 'APPOINTMENT' | 'WALK_IN'; status: VisitStatus; queueNumber: number;
  queueGroup: 'ON_TIME' | 'LATE' | 'WALK_IN'; patientId: number; patientCode: string; patientName: string;
  patientDateOfBirth: string; sessionId: number; date: string; shiftName: string; doctorId: number; doctorName: string;
  specialtyId: number; specialtyName: string; roomCode: string; appointmentId: number | null; reason: string | null;
  companionName: string | null; checkedInAt: string; calledAt: string | null; startedAt: string | null; completedAt: string | null;
}
export interface Vitals {
  id: number; pulsePerMin: number | null; temperatureC: number | null; systolicBp: number | null; diastolicBp: number | null;
  respiratoryRate: number | null; spo2Percent: number | null; weightKg: number | null; heightCm: number | null;
  recordedByName: string | null; recordedAt: string;
}
export interface WaitingItem { position: number; visit: Visit; vitals: Vitals | null }
export interface QueueView { sessionId: number; doctorName: string; roomCode: string; current: Visit[]; waiting: WaitingItem[]; awaitingResults: Visit[]; done: number }

export interface Session {
  id: number; date: string; shiftName: string; startsAt: string; endsAt: string; status: 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'CANCELLED';
  doctorId: number; doctorName: string; specialtyId: number; specialtyName: string; roomId: number; roomCode: string;
  walkInCapacity: number; totalSlots: number; availableSlots: number;
}
export interface Slot { id: number; start: string; end: string; status: 'AVAILABLE' | 'HELD' | 'BOOKED' | 'BLOCKED' }
export interface OpenSession { sessionId: number; date: string; shiftName: string; doctorId: number; doctorName: string; roomCode: string; slots: Slot[] }

export interface FileInfo { id: number; fileName: string; mimeType: string; sizeBytes: number; createdAt: string }
export type ResultStatus = 'DRAFT' | 'SUBMITTED' | 'REVIEWED' | 'AMENDED';
export interface Result {
  id: number; performanceId: number; status: ResultStatus; version: number; resultText: string | null; conclusion: string | null;
  enteredByName: string | null; enteredAt: string; reviewedByName: string | null; reviewedAt: string | null; returnNote: string | null; files: FileInfo[];
}
export interface Performance { id: number; orderItemId: number; performedByName: string | null; performedAt: string; status: 'DONE' | 'VOIDED'; result: Result | null }
export type ServiceCategory = 'CONSULTATION' | 'LAB' | 'IMAGING' | 'ULTRASOUND' | 'PROCEDURE' | 'OTHER';
export interface OrderItem {
  id: number; serviceId: number; serviceName: string; category: ServiceCategory; quantity: number; unitPrice: number;
  status: 'ORDERED' | 'IN_PROGRESS' | 'PERFORMED' | 'REFUSED' | 'CANCELLED'; refusalNote: string | null; performances: Performance[];
}
export interface Order { id: number; visitId: number; orderedByName: string; orderedAt: string; status: string; items: OrderItem[] }
export interface WorkItem { item: OrderItem; visitId: number; visitCode: string; patientId: number; patientCode: string; patientName: string; doctorId: number; doctorName: string; orderedAt: string }

export interface Diagnosis { icd10Code: string | null; name: string; type: 'PRIMARY' | 'SECONDARY' | 'DIFFERENTIAL' }
export interface Emr {
  id: number; status: 'DRAFT' | 'SIGNED' | 'AMENDED'; version: number; chiefComplaint: string | null; presentIllness: string | null;
  organExam: string | null; conclusion: string | null; treatmentPlan: string | null; advice: string | null; followUpDate: string | null;
  diagnoses: Diagnosis[]; signedByName: string | null; signedAt: string | null;
}
export type DrugClass = 'NORMAL' | 'ADDICTIVE' | 'PSYCHOTROPIC' | 'PRECURSOR' | 'TOXIC' | 'ANTIBIOTIC';
export interface RxItem { id: number; medicineId: number; medicineLabel: string; drugClass: DrugClass; quantity: number; unit: string; dosePerTime: string; timesPerDay: number; route: string | null; timing: string | null; days: number; note: string | null }
export interface Prescription {
  id: number; code: string | null; visitId: number; status: 'DRAFT' | 'SIGNED' | 'REPLACED' | 'CANCELLED'; diagnosisText: string | null;
  advice: string | null; signedByName: string | null; prescriberLicenseNo: string | null; signedAt: string | null; allergyAck: string | null; items: RxItem[];
}
export interface Medicine { id: number; activeIngredient: string; brandName: string | null; strength: string | null; dosageForm: string | null; defaultRoute: string | null; unit: string; drugClass: DrugClass; isActive: boolean; label: string }

export interface Payment { id: number; code: string; kind: 'PAYMENT' | 'REFUND'; method: 'CASH' | 'BANK_TRANSFER' | 'ONLINE_SIMULATED' | 'OTHER'; amount: number; status: string; receivedBy: number | null; paidAt: string; note: string | null }
export interface InvoiceLine { id: number; type: string; description: string; quantity: number; unitPrice: number; lineTotal: number }
export interface Invoice {
  id: number; code: string; visitId: number; visitCode: string; patientId: number; patientCode: string; patientName: string;
  totalAmount: number; paidAmount: number; remaining: number; status: 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'REFUNDED' | 'CANCELLED';
  issuedAt: string; items: InvoiceLine[]; payments: Payment[];
}
export interface VisitRecord { visit: Visit; vitals: Vitals[]; emr: Emr | null; orders: Order[]; prescriptions: Prescription[]; invoice: Invoice | null }

export interface Specialty { id: number; code: string; name: string; description: string | null; isActive: boolean; consultationServiceId: number; consultationPrice: number }
export interface MedicalService { id: number; code: string; name: string; category: ServiceCategory; description: string | null; listPrice: number; estimatedMinutes: number | null; isActive: boolean }
export interface Room { id: number; code: string; name: string; specialtyId: number | null; isActive: boolean }
export interface Staff {
  id: number; employeeCode: string; fullName: string; jobTitle: string | null; employmentStatus: string; userId: number; phone: string;
  roles: Role[]; accountStatus: 'PENDING' | 'ACTIVE' | 'LOCKED' | 'DISABLED'; specialties: { id: number; name: string; isPrimary: boolean }[];
}
export interface ScheduleChange {
  id: number; sessionId: number; sessionDate: string; shiftName: string; type: 'LEAVE' | 'REPLACEMENT' | 'SWAP'; requestedBy: number;
  requesterName: string; replacementDoctorId: number | null; replacementName: string | null; reason: string;
  status: 'PENDING_COLLEAGUE' | 'PENDING_MANAGER' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'; managerNote: string | null; createdAt: string;
}
export interface DoctorCard { id: number; fullName: string; academicTitle: string | null; specialties: { id: number; name: string }[] }
export interface Summary {
  from: string; to: string; visits: number; completedVisits: number; walkIns: number; noShows: number; cancelledAppointments: number;
  billed: number; collected: number; refunded: number; unpaid: number;
  byDay: { date: string; visits: number; collected: number }[];
  byDoctor: { doctorId: number; doctorName: string; visits: number; completed: number; billed: number }[];
  byService: { serviceId: number; serviceName: string; quantity: number; amount: number }[];
}
export interface Feedback { id: number; patientId: number; visitId: number | null; category: string | null; content: string; status: 'NEW' | 'IN_REVIEW' | 'RESOLVED' | 'CLOSED'; handledAt: string | null; createdAt: string }
