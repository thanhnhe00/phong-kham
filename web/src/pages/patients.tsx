import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { emptyDraft, PatientFields, toRequest } from '../components/patient-form';
import { Button, Card, Chip, ErrorNote, Field, Kv, Loading, PageHead, Table, Who } from '../components/ui';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { ageText, dmy, personLine, phone, timeDay } from '../lib/format';
import { useGet, useRun } from '../lib/hooks';
import { visitStatus } from '../lib/labels';
import type { Page, Patient, PatientDetail, Visit } from '../lib/types';

export function PatientsPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const sel = Number(params.get('id')) || null;
  const [text, setText] = useState(q);
  const { has } = useAuth();
  const canEdit = has('RECEPTIONIST', 'ADMIN');
  const [creating, setCreating] = useState(false);
  const list = useGet<Page<Patient>>(q ? `/patients?q=${encodeURIComponent(q)}&size=30` : '/patients?size=30');

  return (
    <div className="page">
      <PageHead title="Bệnh nhân" actions={canEdit && <Button icon="plus" onClick={() => setCreating(true)}>Tạo hồ sơ</Button>} />
      <div className="cols">
        <div className="wide">
          <Card title={q ? `Kết quả tìm “${q}”` : 'Hồ sơ mới cập nhật'} sub="Tìm theo tên, số điện thoại, mã bệnh nhân hoặc số giấy tờ" pad={false}
            right={<form onSubmit={(e) => { e.preventDefault(); setParams(text.trim() ? { q: text.trim() } : {}); }}><Field label="Tìm" type="search" value={text} onChange={(e) => setText(e.target.value)} style={{ width: 200 }} /></form>}>
            {list.isLoading ? <Loading /> : (
              <Table rows={list.data?.items ?? []} rowKey={(p) => p.id} selected={sel} onRow={(p) => { setCreating(false); setParams({ ...(q ? { q } : {}), id: String(p.id) }); }}
                empty="Không thấy hồ sơ nào."
                cols={[
                  { head: 'Mã', cell: (p) => <b>{p.patientCode}</b> },
                  { head: 'Họ tên', cell: (p) => <Who name={p.fullName} sub={[personLine(p.sex, p.dateOfBirth), phone(p.phone)].filter(Boolean).join(', ')} /> },
                  { head: 'Ngày sinh', cell: (p) => dmy(p.dateOfBirth) },
                ]} />
            )}
          </Card>
        </div>
        <div className="narrow">
          {creating ? <CreatePatient onDone={(p) => { setCreating(false); setParams({ q: p.patientCode, id: String(p.id) }); }} onCancel={() => setCreating(false)} />
            : sel ? <Detail key={sel} id={sel} canEdit={canEdit} /> : null}
        </div>
      </div>
    </div>
  );
}

function CreatePatient({ onDone, onCancel }: { onDone: (p: Patient) => void; onCancel: () => void }) {
  const [d, setD] = useState(emptyDraft());
  const [error, setError] = useState<string | null>(null);
  const { run, busy } = useRun();
  const save = () => {
    const r = toRequest(d);
    if (r.error) return setError(r.error);
    setError(null);
    let created: Patient | null = null;
    run('save', async () => { created = await api.post<Patient>('/patients', r.body); }, 'Đã tạo hồ sơ').then((ok) => ok && created && onDone(created));
  };
  return (
    <Card title="Tạo hồ sơ bệnh nhân" sheet>
      <PatientFields d={d} set={setD} />
      <ErrorNote text={error} />
      <div className="end"><Button kind="ghost" onClick={onCancel}>Thôi</Button><Button loading={busy === 'save'} onClick={save}>Tạo hồ sơ</Button></div>
    </Card>
  );
}

function Detail({ id, canEdit }: { id: number; canEdit: boolean }) {
  const { has } = useAuth();
  const nav = useNavigate();
  const q = useGet<PatientDetail>(`/patients/${id}`);
  const clinical = has('DOCTOR', 'NURSE', 'ADMIN');
  const visits = useGet<Visit[]>(clinical ? `/patients/${id}/visits` : null);
  const { run, busy } = useRun();
  const [editing, setEditing] = useState(false);
  const [d, setD] = useState(emptyDraft());
  const [error, setError] = useState<string | null>(null);
  const [code, setCode] = useState<{ code: string; expiresAt: string } | null>(null);
  const [allergen, setAllergen] = useState('');
  const [reaction, setReaction] = useState('');

  if (q.isLoading || !q.data) return <Loading />;
  const { patient: p, allergies, representatives, linkedAccounts } = q.data;

  if (editing) {
    return (
      <Card title={`Sửa hồ sơ ${p.patientCode}`} sheet>
        <PatientFields d={d} set={setD} />
        <ErrorNote text={error} />
        <div className="end">
          <Button kind="ghost" onClick={() => setEditing(false)}>Thôi</Button>
          <Button loading={busy === 'save'} onClick={() => {
            const r = toRequest(d);
            if (r.error) return setError(r.error);
            run('save', () => api.put(`/patients/${p.id}`, r.body), 'Đã lưu hồ sơ').then((ok) => ok && setEditing(false));
          }}>Lưu</Button>
        </div>
      </Card>
    );
  }

  return (
    <>
      <Card title={`${p.fullName}, ${p.patientCode}`} sheet
        right={canEdit && <Button kind="ghost" sm onClick={() => { setD(emptyDraft(p)); setError(null); setEditing(true); }}>Sửa hồ sơ</Button>}>
        <div className="stack tight">
          <Kv k="Ngày sinh" v={`${dmy(p.dateOfBirth)} (${ageText(p.dateOfBirth)})`} />
          <Kv k="Giới tính" v={personLine(p.sex, null) || 'Chưa ghi'} />
          <Kv k="Số điện thoại" v={phone(p.phone) || 'Chưa có'} />
          <Kv k="Giấy tờ" v={p.identityNumber ? `${p.identityType === 'CCCD' ? 'CCCD' : 'Giấy tờ'} ${p.identityNumber}` : 'Chưa có'} />
          {p.addressLine && <Kv k="Địa chỉ" v={p.addressLine} />}
          {representatives.map((r) => <Kv key={r.id} k="Người đại diện" v={`${r.fullName}, ${r.relationship}${r.phone ? `, ${phone(r.phone)}` : ''}`} />)}
          <Kv k="Ứng dụng" v={linkedAccounts ? <Chip tone="teal">Đã liên kết {linkedAccounts} tài khoản</Chip> : <Chip>Chưa liên kết</Chip>} />
        </div>
      </Card>
      <Card title="Dị ứng">
        {allergies.length === 0 ? <span className="muted">Chưa ghi nhận dị ứng.</span> : (
          <div className="wrap">{allergies.map((a) => <Chip key={a.id} tone="red">{a.allergen}{a.reaction ? `: ${a.reaction}` : ''}</Chip>)}</div>
        )}
        {has('RECEPTIONIST', 'NURSE', 'DOCTOR') && (
          <div className="row" style={{ alignItems: 'flex-end' }}>
            <Field label="Chất gây dị ứng" value={allergen} onChange={(e) => setAllergen(e.target.value)} placeholder="Ví dụ: Penicillin" />
            <Field label="Biểu hiện" value={reaction} onChange={(e) => setReaction(e.target.value)} placeholder="Nổi mẩn" />
            <Button kind="ghost" sm style={{ flex: '0 0 auto' }} disabled={!allergen.trim()} loading={busy === 'al'}
              onClick={() => run('al', () => api.post(`/patients/${p.id}/allergies`, { allergen: allergen.trim(), reaction: reaction.trim() || null }), 'Đã thêm dị ứng').then((ok) => ok && (setAllergen(''), setReaction('')))}>Thêm</Button>
          </div>
        )}
      </Card>
      {canEdit && (
        <Card title="Liên kết với ứng dụng">
          <span className="muted small">Người nhà muốn xem hồ sơ trên ứng dụng: đối chiếu giấy tờ, rồi đọc mã này cho họ nhập ở mục Liên kết hồ sơ.</span>
          {code ? (
            <div className="between" style={{ background: 'var(--soft-2)', borderRadius: 10, padding: '14px 16px' }}>
              <span className="code-big num">{code.code.replace(/(.{4})(.{4})/, '$1 $2')}</span>
              <Chip tone="amber">Hết hạn {timeDay(code.expiresAt)}</Chip>
            </div>
          ) : null}
          <div className="between">
            <span className="muted small">Mã dùng một lần, hạn 24 giờ. Tạo mã mới thì mã cũ hết hiệu lực.</span>
            <Button kind="outline" sm loading={busy === 'code'} onClick={() => run('code', async () => setCode(await api.post(`/patients/${p.id}/link-code`)))}>Tạo mã liên kết</Button>
          </div>
        </Card>
      )}
      {clinical && (
        <Card title="Lịch sử khám" pad={false}>
          <Table rows={visits.data ?? []} rowKey={(v) => v.id} empty="Chưa có lượt khám nào."
            onRow={has('DOCTOR') ? (v) => nav(`/bac-si/kham/${v.id}`) : undefined}
            cols={[
              { head: 'Ngày', cell: (v) => dmy(v.date) },
              { head: 'Bác sĩ', cell: (v) => <Who name={`BS ${v.doctorName}`} sub={v.specialtyName} /> },
              { head: 'Trạng thái', cell: (v) => <Chip tone={visitStatus[v.status][1]}>{visitStatus[v.status][0]}</Chip> },
            ]} />
        </Card>
      )}
    </>
  );
}
