import { useState } from 'react';
import { addDays, dayLabel, time, vnDate } from '../lib/format';
import { useGet } from '../lib/hooks';
import type { DoctorCard, OpenSession, Slot, Specialty } from '../lib/types';
import { Loading, Select } from './ui';

export interface PickedSlot { id: number; start: string; doctorName: string }

/** Chọn giờ trống của một bác sĩ trong 14 ngày tới, gom theo ngày */
export function SlotPicker({ doctorId, value, onChange }: { doctorId: number | null; value: PickedSlot | null; onChange: (s: PickedSlot) => void }) {
  const from = vnDate();
  const q = useGet<OpenSession[]>(doctorId ? `/slots?doctorId=${doctorId}&from=${from}&to=${addDays(from, 14)}` : null);
  const [day, setDay] = useState<string | null>(null);
  if (!doctorId) return null;
  if (q.isLoading) return <Loading />;
  const now = Date.now();
  const byDay = new Map<string, { s: OpenSession; slot: Slot }[]>();
  for (const s of q.data ?? []) {
    for (const slot of s.slots) {
      if (slot.status !== 'AVAILABLE' || Date.parse(slot.start) < now) continue;
      const d = vnDate(slot.start);
      byDay.set(d, [...(byDay.get(d) ?? []), { s, slot }]);
    }
  }
  const days = [...byDay.keys()].sort();
  if (days.length === 0) return <span className="muted small">Bác sĩ chưa có giờ trống trong 14 ngày tới.</span>;
  const cur = day && byDay.has(day) ? day : days[0];
  return (
    <div className="stack tight">
      <Select label="Ngày" value={cur} onChange={(e) => setDay(e.target.value)}>
        {days.map((d) => <option key={d} value={d}>{dayLabel(d)} ({byDay.get(d)!.length} giờ trống)</option>)}
      </Select>
      <div className="seg" role="radiogroup" aria-label="Giờ khám">
        {byDay.get(cur)!.map(({ s, slot }) => (
          <button key={slot.id} type="button" role="radio" aria-checked={value?.id === slot.id}
            onClick={() => onChange({ id: slot.id, start: slot.start, doctorName: s.doctorName })}>{time(slot.start)}</button>
        ))}
      </div>
    </div>
  );
}

/** Chọn chuyên khoa rồi bác sĩ */
export function DoctorPicker({ value, onChange }: { value: number | null; onChange: (doctorId: number | null) => void }) {
  const specs = useGet<Specialty[]>('/specialties');
  const [spec, setSpec] = useState<number | null>(null);
  const docs = useGet<DoctorCard[]>(spec ? `/doctors?specialtyId=${spec}` : null);
  return (
    <div className="row">
      <Select label="Chuyên khoa" value={spec ?? ''} onChange={(e) => { setSpec(Number(e.target.value) || null); onChange(null); }}>
        <option value="">Chọn chuyên khoa</option>
        {specs.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
      </Select>
      <Select label="Bác sĩ" value={value ?? ''} onChange={(e) => onChange(Number(e.target.value) || null)} disabled={!spec}>
        <option value="">Chọn bác sĩ</option>
        {docs.data?.map((d) => <option key={d.id} value={d.id}>{d.academicTitle ? `${d.academicTitle} ` : 'BS '}{d.fullName}</option>)}
      </Select>
    </div>
  );
}
