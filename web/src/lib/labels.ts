import type { Tone } from '../components/ui';
import type { AppointmentStatus, Invoice, ScheduleChange, ServiceCategory, StaffRole, VisitStatus } from './types';

export const apptStatus: Record<AppointmentStatus, [string, Tone]> = {
  PENDING_CONFIRMATION: ['Chờ xác nhận', 'amber'],
  CONFIRMED: ['Đã xác nhận', 'teal'],
  NEEDS_RECONFIRMATION: ['Cần báo đổi lịch', 'amber'],
  CHECKED_IN: ['Đã đến', 'teal'],
  CANCELLED: ['Đã hủy', 'grey'],
  NO_SHOW: ['Vắng mặt', 'red'],
};

export const visitStatus: Record<VisitStatus, [string, Tone]> = {
  WAITING: ['Đang chờ', 'grey'],
  CALLED: ['Đã gọi', 'amber'],
  IN_PROGRESS: ['Đang khám', 'teal'],
  AWAITING_RESULTS: ['Đi xét nghiệm', 'blue'],
  COMPLETED: ['Khám xong', 'teal'],
  LEFT: ['Bỏ về', 'red'],
  CANCELLED: ['Đã hủy', 'grey'],
};

export const invoiceStatus: Record<Invoice['status'], [string, Tone]> = {
  UNPAID: ['Chưa thu', 'amber'],
  PARTIALLY_PAID: ['Thu một phần', 'amber'],
  PAID: ['Đã thu đủ', 'teal'],
  REFUNDED: ['Đã hoàn', 'grey'],
  CANCELLED: ['Đã hủy', 'grey'],
};

export const payMethod: Record<string, string> = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản', ONLINE_SIMULATED: 'Trên ứng dụng', OTHER: 'Khác' };

export const category: Record<ServiceCategory, string> = {
  CONSULTATION: 'Khám', LAB: 'Xét nghiệm', IMAGING: 'Chẩn đoán hình ảnh', ULTRASOUND: 'Siêu âm', PROCEDURE: 'Thủ thuật', OTHER: 'Khác',
};

export const roleLabel: Record<StaffRole | 'PATIENT', string> = { RECEPTIONIST: 'Lễ tân', NURSE: 'Điều dưỡng', DOCTOR: 'Bác sĩ', ADMIN: 'Quản lý', PATIENT: 'Bệnh nhân' };

export const queueGroup = { ON_TIME: 'Có hẹn', LATE: 'Có hẹn, trễ', WALK_IN: 'Không hẹn' } as const;

export const changeStatus: Record<ScheduleChange['status'], [string, 'amber' | 'teal' | 'red' | 'grey']> = {
  PENDING_COLLEAGUE: ['Chờ bác sĩ được nhờ đồng ý', 'amber'], PENDING_MANAGER: ['Chờ quản lý duyệt', 'amber'],
  APPROVED: ['Đã duyệt', 'teal'], REJECTED: ['Không được duyệt', 'red'], WITHDRAWN: ['Đã rút', 'grey'],
};
export const changeType = { LEAVE: 'Xin nghỉ', REPLACEMENT: 'Nhờ khám thay', SWAP: 'Đổi ca' } as const;
