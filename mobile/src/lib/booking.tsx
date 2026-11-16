import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import type { Slot } from './types';

/** Thông tin đặt lịch giữ qua 3 bước: khám cho ai, chuyên khoa, bác sĩ, giờ, lý do, xét nghiệm mong muốn. */
export interface BookingDraft {
  patientId: number | null;
  specialtyId: number | null;
  specialtyName: string;
  price: number | null;
  doctorId: number | null;
  doctorName: string;
  slot: (Slot & { date: string; roomCode: string; shiftName: string }) | null;
  reason: string;
  serviceIds: number[];
  /** Đặt lịch tái khám: ngày bác sĩ hẹn, để chọn sẵn trên lịch */
  followUpDate: string | null;
  /** Khách chọn giờ xong mới đăng nhập: đăng nhập xong quay lại bước xác nhận */
  resume: boolean;
}

const empty: BookingDraft = {
  patientId: null,
  specialtyId: null,
  specialtyName: '',
  price: null,
  doctorId: null,
  doctorName: '',
  slot: null,
  reason: '',
  serviceIds: [],
  followUpDate: null,
  resume: false,
};

interface BookingState {
  draft: BookingDraft;
  update: (patch: Partial<BookingDraft>) => void;
  reset: (start?: Partial<BookingDraft>) => void;
}

const BookingContext = createContext<BookingState | null>(null);

export function BookingProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<BookingDraft>(empty);
  const value = useMemo(
    () => ({
      draft,
      update: (patch: Partial<BookingDraft>) => setDraft((d) => ({ ...d, ...patch })),
      reset: (start: Partial<BookingDraft> = {}) => setDraft({ ...empty, ...start }),
    }),
    [draft],
  );
  return <BookingContext.Provider value={value}>{children}</BookingContext.Provider>;
}

export function useBooking() {
  const ctx = useContext(BookingContext);
  if (!ctx) throw new Error('useBooking phải nằm trong BookingProvider');
  return ctx;
}
