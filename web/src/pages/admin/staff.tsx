import { useState } from 'react';
import { Button, Card, Check, Chip, ErrorNote, Field, Kv, Loading, Note, PageHead, Select, Table, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { phone } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import { roleLabel } from '../../lib/labels';
import type { Page, Specialty, Staff, StaffRole } from '../../lib/types';

const ROLES: StaffRole[] = ['RECEPTIONIST', 'NURSE', 'DOCTOR', 'ADMIN'];
const account: Record<Staff['accountStatus'], [string, 'teal' | 'amber' | 'red' | 'grey']> = {
  ACTIVE: ['Đang dùng', 'teal'], PENDING: ['Phải đổi mật khẩu tạm', 'amber'], LOCKED: ['Đã khóa', 'red'], DISABLED: ['Ngừng dùng', 'grey'],
};

export function StaffPage() {
  const q = useGet<Page<Staff>>('/staff?size=200');
  const [sel, setSel] = useState<number | 'new' | null>(null);
  const [temp, setTemp] = useState<{ id: number; pw: string } | null>(null);
  const rows = q.data?.items ?? [];
  const cur = rows.find((s) => s.id === sel);

  return (
    <div className="page">
      <PageHead title="Nhân viên" actions={<Button icon="plus" onClick={() => setSel('new')}>Thêm nhân viên</Button>} />
      <div className="cols">
        <div className="wide">
          <Card title={`Nhân viên (${rows.length})`} sub="Một người có thể giữ nhiều vai trò" pad={false}>
            {q.isLoading ? <Loading /> : (
              <Table rows={rows} rowKey={(s) => s.id} selected={typeof sel === 'number' ? sel : null} onRow={(s) => setSel(s.id)}
                cols={[
                  { head: 'Họ tên', cell: (s) => <Who name={s.fullName} sub={`${s.employeeCode}, ${phone(s.phone)}`} /> },
                  { head: 'Vai trò', cell: (s) => <div className="wrap">{s.roles.filter((r) => r !== 'PATIENT').map((r) => <Chip key={r} tone="teal">{roleLabel[r]}</Chip>)}</div> },
                  { head: 'Chuyên khoa', cell: (s) => s.specialties.map((x) => x.name).join(', ') || '—' },
                  { head: 'Tài khoản', cell: (s) => <Chip tone={account[s.accountStatus][1]}>{account[s.accountStatus][0]}</Chip> },
                ]} />
            )}
          </Card>
        </div>
        <div className="narrow">
          {sel === 'new' && <CreateStaff onCreated={(id, pw) => { setSel(id); setTemp({ id, pw }); }} />}
          {cur && <EditStaff key={cur.id} s={cur} temp={temp?.id === cur.id ? temp.pw : null} onTemp={(pw) => setTemp({ id: cur.id, pw })} />}
        </div>
      </div>
    </div>
  );
}

function RoleChecks({ roles, set }: { roles: StaffRole[]; set: (r: StaffRole[]) => void }) {
  return (
    <div className="field"><span>Vai trò</span>
      <div className="wrap" style={{ gap: 14 }}>
        {ROLES.map((r) => <Check key={r} label={roleLabel[r]} checked={roles.includes(r)} onChange={(on) => set(on ? [...roles, r] : roles.filter((x) => x !== r))} />)}
      </div>
    </div>
  );
}

function CreateStaff({ onCreated }: { onCreated: (id: number, pw: string) => void }) {
  const specs = useGet<Specialty[]>('/specialties');
  const [f, setF] = useState({ fullName: '', phone: '', jobTitle: '', specialtyId: '', license: '', title: '' });
  const [roles, setRoles] = useState<StaffRole[]>([]);
  const [error, setError] = useState<string | null>(null);
  const { run, busy } = useRun();
  const doctor = roles.includes('DOCTOR');
  const up = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const save = () => {
    if (!f.fullName.trim() || !/^0\d{9}$/.test(f.phone.replace(/\s/g, ''))) return setError('Nhập họ tên và số điện thoại 10 số.');
    if (!roles.length) return setError('Chọn ít nhất một vai trò.');
    if (doctor && (!f.specialtyId || !f.license.trim())) return setError('Bác sĩ cần chuyên khoa và số chứng chỉ hành nghề.');
    setError(null);
    run('save', async () => {
      const r = await api.post<{ staff: Staff; temporaryPassword: string }>('/staff', {
        fullName: f.fullName.trim(), phone: f.phone.replace(/\s/g, ''), jobTitle: f.jobTitle.trim() || null, roles,
        specialtyIds: doctor ? [Number(f.specialtyId)] : [], primarySpecialtyId: doctor ? Number(f.specialtyId) : null,
      });
      if (doctor) await api.put(`/staff/${r.staff.id}/doctor-profile`, { practiceLicenseNo: f.license.trim(), academicTitle: f.title.trim() || null, isPublic: true });
      onCreated(r.staff.id, r.temporaryPassword);
    }, 'Đã tạo tài khoản nhân viên');
  };
  return (
    <Card title="Thêm nhân viên" sheet>
      <div className="row"><Field label="Họ và tên" value={f.fullName} onChange={up('fullName')} /><Field label="Số điện thoại" inputMode="tel" value={f.phone} onChange={up('phone')} /></div>
      <Field label="Chức danh" value={f.jobTitle} onChange={up('jobTitle')} placeholder="Ví dụ: Điều dưỡng trưởng" />
      <RoleChecks roles={roles} set={setRoles} />
      {doctor && (
        <>
          <Select label="Chuyên khoa" value={f.specialtyId} onChange={up('specialtyId')}>
            <option value="">Chọn</option>{specs.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </Select>
          <div className="row"><Field label="Học hàm, học vị" value={f.title} onChange={up('title')} placeholder="ThS.BS" /><Field label="Số chứng chỉ hành nghề" value={f.license} onChange={up('license')} /></div>
        </>
      )}
      <ErrorNote text={error} />
      <div className="end"><Button loading={busy === 'save'} onClick={save}>Tạo tài khoản</Button></div>
      <span className="muted small">Hệ thống cấp mật khẩu tạm; nhân viên phải đổi khi đăng nhập lần đầu.</span>
    </Card>
  );
}

function EditStaff({ s, temp, onTemp }: { s: Staff; temp: string | null; onTemp: (pw: string) => void }) {
  const { me } = useAuth();
  const [roles, setRoles] = useState<StaffRole[]>(s.roles.filter((r): r is StaffRole => r !== 'PATIENT'));
  const { run, busy } = useRun();
  const self = me?.id === s.userId;
  const changed = roles.slice().sort().join() !== s.roles.filter((r) => r !== 'PATIENT').sort().join();
  return (
    <Card title={s.fullName} sub={s.jobTitle ?? s.employeeCode} sheet>
      <div className="stack tight">
        <Kv k="Số điện thoại" v={phone(s.phone)} />
        <Kv k="Mã nhân viên" v={s.employeeCode} />
        <Kv k="Tài khoản" v={<Chip tone={account[s.accountStatus][1]}>{account[s.accountStatus][0]}</Chip>} />
        {s.specialties.length > 0 && <Kv k="Chuyên khoa" v={s.specialties.map((x) => x.name).join(', ')} />}
      </div>
      <RoleChecks roles={roles} set={setRoles} />
      {temp && <Note>Mật khẩu tạm: <b className="num" style={{ letterSpacing: '0.08em' }}>{temp}</b>. Chỉ hiện một lần, hãy đưa trực tiếp cho nhân viên. Lần đầu đăng nhập họ phải đổi mật khẩu.</Note>}
      <div className="end">
        <Button kind="ghost" sm loading={busy === 'reset'} onClick={() => run('reset', async () => onTemp((await api.post<{ temporaryPassword: string }>(`/admin/users/${s.userId}/reset-password`)).temporaryPassword), 'Đã cấp mật khẩu tạm')}>Đặt lại mật khẩu</Button>
        {s.accountStatus === 'LOCKED' ? (
          <Button kind="outline" sm loading={busy === 'lock'} onClick={() => run('lock', () => api.patch(`/admin/users/${s.userId}/status`, { status: 'ACTIVE' }), 'Đã mở khóa')}>Mở khóa</Button>
        ) : (
          <Button kind="danger" sm disabled={self} loading={busy === 'lock'} onClick={() => window.confirm(`Khóa tài khoản ${s.fullName}?`) && run('lock', () => api.patch(`/admin/users/${s.userId}/status`, { status: 'LOCKED' }), 'Đã khóa tài khoản')}>Khóa tài khoản</Button>
        )}
        <Button sm disabled={!changed || roles.length === 0} loading={busy === 'save'} onClick={() => run('save', () => api.put(`/staff/${s.id}`, {
          fullName: s.fullName, jobTitle: s.jobTitle, employmentStatus: s.employmentStatus, roles,
          specialtyIds: s.specialties.map((x) => x.id), primarySpecialtyId: s.specialties.find((x) => x.isPrimary)?.id ?? s.specialties[0]?.id ?? null,
        }), 'Đã lưu vai trò')}>Lưu vai trò</Button>
      </div>
      <span className="muted small">Người giữ nhiều vai trò thấy menu của các vai đó; mỗi thao tác đều lưu tên người làm và thời điểm. Khóa tài khoản thì người đó bị đăng xuất ngay trên mọi máy.</span>
    </Card>
  );
}
