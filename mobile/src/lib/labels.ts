import type { AppointmentStatus, Relationship } from './types';

export type TagKind = 'teal' | 'amber' | 'red' | 'grey' | 'sky';

export const appointmentStatus: Record<AppointmentStatus, { text: string; kind: TagKind }> = {
  PENDING_CONFIRMATION: { text: 'Chờ xác nhận', kind: 'amber' },
  CONFIRMED: { text: 'Đã xác nhận', kind: 'teal' },
  NEEDS_RECONFIRMATION: { text: 'Cần xác nhận lại', kind: 'amber' },
  CHECKED_IN: { text: 'Đã check-in', kind: 'teal' },
  CANCELLED: { text: 'Đã hủy', kind: 'grey' },
  NO_SHOW: { text: 'Bỏ lỡ', kind: 'red' },
};

/** Người có hồ sơ là ai của chủ tài khoản */
export const relationshipLabel: Record<Relationship, string> = {
  SELF: 'Bản thân',
  PARENT: 'Con',
  LEGAL_GUARDIAN: 'Người được giám hộ',
  FAMILY_MEMBER: 'Người thân',
  OTHER: 'Khác',
};

/** Vai trò của chủ tài khoản đối với hồ sơ (câu hiển thị ở đầu trang chủ) */
export const managerRole: Record<Relationship, string | null> = {
  SELF: null,
  PARENT: 'Bạn quản lý hồ sơ này với vai trò cha mẹ',
  LEGAL_GUARDIAN: 'Bạn quản lý hồ sơ này với vai trò người giám hộ',
  FAMILY_MEMBER: 'Bạn quản lý hồ sơ này với vai trò người thân',
  OTHER: 'Bạn được phép quản lý hồ sơ này',
};

export const relationshipChoices: { value: Relationship; label: string }[] = [
  { value: 'PARENT', label: 'Con của tôi' },
  { value: 'LEGAL_GUARDIAN', label: 'Tôi là người giám hộ' },
  { value: 'FAMILY_MEMBER', label: 'Người thân' },
  { value: 'OTHER', label: 'Khác' },
];

export const sexLabel = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác', UNKNOWN: 'Không rõ' } as const;
