import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useAuth } from './auth';
import { usePatients } from './queries';
import { KEYS, storage } from './storage';
import type { MyPatient } from './types';

/** Hồ sơ bệnh nhân đang xem (bản thân hoặc người thân). Nhớ lựa chọn cho lần mở app sau. */
interface ProfileState {
  patients: MyPatient[];
  current: MyPatient | null;
  loading: boolean;
  select: (patientId: number) => void;
}

const ProfileContext = createContext<ProfileState | null>(null);

export function ProfileProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { data, isLoading } = usePatients(!!user);
  const [selected, setSelected] = useState<number | null>(null);

  useEffect(() => {
    storage.get(KEYS.patient).then((v) => v && setSelected(Number(v)));
  }, []);

  const select = useCallback((id: number) => {
    setSelected(id);
    void storage.set(KEYS.patient, String(id));
  }, []);

  const patients = useMemo(() => data ?? [], [data]);
  const current =
    patients.find((p) => p.patient.id === selected) ?? patients.find((p) => p.relationship === 'SELF') ?? patients[0] ?? null;

  const value = useMemo(() => ({ patients, current, loading: isLoading, select }), [patients, current, isLoading, select]);
  return <ProfileContext.Provider value={value}>{children}</ProfileContext.Provider>;
}

export function useProfile() {
  const ctx = useContext(ProfileContext);
  if (!ctx) throw new Error('useProfile phải nằm trong ProfileProvider');
  return ctx;
}
