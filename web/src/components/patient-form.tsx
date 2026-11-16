import { useState } from 'react';
import { api, errorText } from '../lib/api';
import { dmy, parseDmy, personLine, phone as fmtPhone } from '../lib/format';
import type { Page, Patient, Sex } from '../lib/types';
import { Button, ErrorNote, Field, Note, Seg, Select, Who } from './ui';

export interface PatientDraft {
  fullName: string; dob: string; sex: Sex; phone: string; identityType: string; identityNumber: string; addressLine: string;
}
export const emptyDraft = (p?: Partial<Patient>): PatientDraft => ({
  fullName: p?.fullName ?? '', dob: p?.dateOfBirth ? dmy(p.dateOfBirth) : '', sex: p?.sex ?? 'FEMALE', phone: p?.phone ?? '',
  identityType: p?.identityType && p.identityType !== 'NOT_PROVIDED' ? p.identityType : 'CCCD', identityNumber: p?.identityNumber ?? '',
  addressLine: p?.addressLine ?? '',
});

/** Kiểm tra form, trả về body gửi backend hoặc thông báo lỗi */
export function toRequest(d: PatientDraft): { body?: Record<string, unknown>; error?: string } {
  if (!d.fullName.trim()) return { error: 'Vui lòng nhập họ và tên.' };
  const dob = parseDmy(d.dob);
  if (!dob) return { error: 'Ngày sinh viết theo dạng ngày/tháng/năm, ví dụ 15/03/1985.' };
  const id = d.identityNumber.replace(/\s/g, '');
  if (id && d.identityType === 'CCCD' && !/^\d{12}$/.test(id)) return { error: 'Số CCCD gồm đúng 12 chữ số.' };
  return {
    body: {
      fullName: d.fullName.trim(), dateOfBirth: dob, sex: d.sex, phone: d.phone.replace(/\s/g, '') || null,
      identityType: id ? d.identityType : 'NOT_PROVIDED', identityNumber: id || null, addressLine: d.addressLine.trim() || null,
    },
  };
}

export function PatientFields({ d, set }: { d: PatientDraft; set: (d: PatientDraft) => void }) {
  const up = (k: keyof PatientDraft) => (e: { target: { value: string } }) => set({ ...d, [k]: e.target.value });
  return (
    <>
      <div className="row">
        <Field label="Họ và tên" value={d.fullName} onChange={up('fullName')} style={{ flexBasis: 200 }} />
        <Field label="Số điện thoại" inputMode="tel" value={d.phone} onChange={up('phone')} />
      </div>
      <div className="row">
        <Field label="Ngày sinh (ngày/tháng/năm)" placeholder="15/03/1985" value={d.dob} onChange={up('dob')} />
        <div className="field"><span>Giới tính</span>
          <Seg label="Giới tính" value={d.sex} onChange={(sex) => set({ ...d, sex })} items={[{ key: 'FEMALE', label: 'Nữ' }, { key: 'MALE', label: 'Nam' }]} />
        </div>
      </div>
      <div className="row">
        <Select label="Loại giấy tờ" value={d.identityType} onChange={up('identityType')}>
          <option value="CCCD">CCCD</option><option value="PASSPORT">Hộ chiếu</option><option value="PERSONAL_ID">Mã định danh</option><option value="OTHER">Khác</option>
        </Select>
        <Field label="Số giấy tờ (không bắt buộc)" inputMode="numeric" value={d.identityNumber} onChange={up('identityNumber')} />
      </div>
      <Field label="Địa chỉ" value={d.addressLine} onChange={up('addressLine')} />
    </>
  );
}

/**
 * Tìm hồ sơ theo tên/số điện thoại/mã; không thấy thì tạo mới (có kiểm tra trùng trước khi tạo).
 */
export function PatientPicker({ value, onChange }: { value: Patient | null; onChange: (p: Patient | null) => void }) {
  const [q, setQ] = useState('');
  const [found, setFound] = useState<Patient[] | null>(null);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState(emptyDraft());
  const [dupes, setDupes] = useState<Patient[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (value) {
    return (
      <div className="between" style={{ border: '1px solid var(--line)', borderRadius: 10, padding: '10px 12px' }}>
        <Who name={`${value.fullName}, ${value.patientCode}`} sub={[personLine(value.sex, value.dateOfBirth), fmtPhone(value.phone)].filter(Boolean).join(', ')} />
        <button type="button" className="link" onClick={() => onChange(null)}>Đổi người</button>
      </div>
    );
  }

  const search = async () => {
    if (!q.trim()) return;
    setBusy(true);
    setError(null);
    try {
      setFound((await api.get<Page<Patient>>(`/patients?q=${encodeURIComponent(q.trim())}&size=8`)).items);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  const create = async (force = false) => {
    const r = toRequest(draft);
    if (r.error) return setError(r.error);
    setBusy(true);
    setError(null);
    try {
      if (!force) {
        const b = r.body!;
        const params = new URLSearchParams({ fullName: String(b.fullName), dateOfBirth: String(b.dateOfBirth) });
        if (b.phone) params.set('phone', String(b.phone));
        if (b.identityNumber) params.set('identityNumber', String(b.identityNumber));
        const d = await api.get<Patient[]>(`/patients/possible-duplicates?${params}`);
        if (d.length) return setDupes(d);
      }
      onChange(await api.post<Patient>('/patients', r.body));
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  if (creating) {
    return (
      <div className="stack">
        <PatientFields d={draft} set={setDraft} />
        {dupes && dupes.length > 0 && (
          <Note tone="teal" icon="doc">
            Có thể đã có hồ sơ:{' '}
            {dupes.map((p) => (
              <span key={p.id}><b>{p.fullName}</b>, sinh {dmy(p.dateOfBirth)}, {p.patientCode}{' '}
                <button type="button" className="link" onClick={() => onChange(p)}>Dùng hồ sơ này</button>. </span>
            ))}
            Không phải thì <button type="button" className="link" onClick={() => create(true)}>vẫn tạo hồ sơ mới</button>.
          </Note>
        )}
        <ErrorNote text={error} />
        <div className="end">
          <Button kind="ghost" sm onClick={() => setCreating(false)}>Quay lại tìm</Button>
          <Button sm loading={busy} onClick={() => create()}>Tạo hồ sơ</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="stack tight">
      <div className="wrap" style={{ flexWrap: 'nowrap' }}>
        <Field label="Tìm hồ sơ: tên, số điện thoại hoặc mã BN" value={q} onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), search())} style={{ flex: 1 }} className="grow" />
        <Button kind="ghost" style={{ alignSelf: 'flex-end' }} onClick={search} loading={busy} icon="search">Tìm</Button>
      </div>
      <ErrorNote text={error} />
      {found && found.length === 0 && <span className="muted small">Không thấy hồ sơ nào.</span>}
      {found?.map((p) => (
        <button key={p.id} type="button" className="between" onClick={() => onChange(p)}
          style={{ border: '1px solid var(--line)', borderRadius: 10, padding: '8px 12px', background: '#fff', cursor: 'pointer', textAlign: 'left' }}>
          <Who name={`${p.fullName}, ${p.patientCode}`} sub={[personLine(p.sex, p.dateOfBirth), fmtPhone(p.phone)].filter(Boolean).join(', ')} />
          <span className="link">Chọn</span>
        </button>
      ))}
      <button type="button" className="link" style={{ alignSelf: 'flex-start' }} onClick={() => { setCreating(true); setDraft(emptyDraft({ fullName: /\d/.test(q) ? '' : q, phone: /^\d[\d\s]+$/.test(q) ? q : '' })); }}>
        + Tạo hồ sơ mới
      </button>
    </div>
  );
}
