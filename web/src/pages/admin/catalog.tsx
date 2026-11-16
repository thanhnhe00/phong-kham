import { useState } from 'react';
import { Button, Card, Check, Chip, Field, Loading, Note, PageHead, Select, Table, Tabs } from '../../components/ui';
import { api } from '../../lib/api';
import { vnd } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import { category } from '../../lib/labels';
import type { MedicalService, Medicine, Page, Room, Specialty } from '../../lib/types';

type Tab = 'services' | 'specialties' | 'medicines' | 'rooms';
const active = (on: boolean) => <Chip tone={on ? 'teal' : 'grey'}>{on ? 'Đang dùng' : 'Ngừng'}</Chip>;

export function CatalogPage() {
  const [tab, setTab] = useState<Tab>('services');
  const services = useGet<MedicalService[]>('/services/all');
  const specs = useGet<Specialty[]>('/specialties/all');
  const meds = useGet<Page<Medicine>>('/medicines?includeInactive=true&size=500');
  const rooms = useGet<Room[]>('/rooms');
  const [sel, setSel] = useState<number | 'new' | null>(null);
  const change = (t: Tab) => { setTab(t); setSel(null); };

  return (
    <div className="page">
      <PageHead title="Danh mục" sub="Ngừng dùng thay vì xóa, để giữ đúng lịch sử hóa đơn và đơn thuốc"
        actions={<Button icon="plus" onClick={() => setSel('new')}>Thêm {({ services: 'dịch vụ', specialties: 'chuyên khoa', medicines: 'thuốc', rooms: 'phòng' })[tab]}</Button>} />
      <Tabs value={tab} onChange={change} items={[
        { key: 'services', label: `Dịch vụ (${services.data?.length ?? 0})` }, { key: 'specialties', label: `Chuyên khoa (${specs.data?.length ?? 0})` },
        { key: 'medicines', label: `Thuốc (${meds.data?.total ?? 0})` }, { key: 'rooms', label: `Phòng (${rooms.data?.length ?? 0})` },
      ]} />
      <div className="cols">
        <div className="wide">
          <Card title={({ services: 'Dịch vụ và giá', specialties: 'Chuyên khoa', medicines: 'Danh mục thuốc', rooms: 'Phòng' })[tab]} pad={false}>
            {tab === 'services' && (services.isLoading ? <Loading /> : (
              <Table rows={services.data ?? []} rowKey={(s) => s.id} selected={typeof sel === 'number' ? sel : null} onRow={(s) => setSel(s.id)}
                cols={[
                  { head: 'Mã', cell: (s) => <b>{s.code}</b> }, { head: 'Tên dịch vụ', cell: (s) => s.name }, { head: 'Loại', cell: (s) => category[s.category] },
                  { head: 'Giá', right: true, cell: (s) => <b style={{ whiteSpace: 'nowrap' }}>{vnd(s.listPrice)}</b> },
                  { head: 'Thời gian', cell: (s) => s.estimatedMinutes ? `${s.estimatedMinutes} phút` : '' }, { head: 'Trạng thái', cell: (s) => active(s.isActive) },
                ]} />
            ))}
            {tab === 'specialties' && (
              <Table rows={specs.data ?? []} rowKey={(s) => s.id} selected={typeof sel === 'number' ? sel : null} onRow={(s) => setSel(s.id)}
                cols={[{ head: 'Mã', cell: (s) => <b>{s.code}</b> }, { head: 'Tên', cell: (s) => s.name }, { head: 'Phí khám', right: true, cell: (s) => vnd(s.consultationPrice) }, { head: 'Trạng thái', cell: (s) => active(s.isActive) }]} />
            )}
            {tab === 'medicines' && (
              <Table rows={meds.data?.items ?? []} rowKey={(m) => m.id} selected={typeof sel === 'number' ? sel : null} onRow={(m) => setSel(m.id)}
                cols={[{ head: 'Thuốc', cell: (m) => <b>{m.label}</b> }, { head: 'Đơn vị', cell: (m) => m.unit }, { head: 'Nhóm', cell: (m) => drugClass[m.drugClass] }, { head: 'Trạng thái', cell: (m) => active(m.isActive) }]} />
            )}
            {tab === 'rooms' && (
              <Table rows={rooms.data ?? []} rowKey={(r) => r.id} selected={typeof sel === 'number' ? sel : null} onRow={(r) => setSel(r.id)}
                cols={[{ head: 'Mã', cell: (r) => <b>{r.code}</b> }, { head: 'Tên', cell: (r) => r.name }, { head: 'Trạng thái', cell: (r) => active(r.isActive) }]} />
            )}
          </Card>
        </div>
        <div className="narrow">
          {sel != null && tab === 'services' && <ServiceForm key={String(sel)} s={sel === 'new' ? undefined : services.data?.find((x) => x.id === sel)} done={() => setSel(null)} />}
          {sel != null && tab === 'specialties' && <SpecialtyForm key={String(sel)} s={sel === 'new' ? undefined : specs.data?.find((x) => x.id === sel)} services={services.data ?? []} done={() => setSel(null)} />}
          {sel != null && tab === 'medicines' && <MedicineForm key={String(sel)} m={sel === 'new' ? undefined : meds.data?.items.find((x) => x.id === sel)} done={() => setSel(null)} />}
          {sel != null && tab === 'rooms' && <RoomForm key={String(sel)} r={sel === 'new' ? undefined : rooms.data?.find((x) => x.id === sel)} done={() => setSel(null)} />}
        </div>
      </div>
    </div>
  );
}

const drugClass: Record<Medicine['drugClass'], string> = { NORMAL: 'Thường', ANTIBIOTIC: 'Kháng sinh', ADDICTIVE: 'Gây nghiện', PSYCHOTROPIC: 'Hướng thần', PRECURSOR: 'Tiền chất', TOXIC: 'Thuốc độc' };

function Save({ busy, onSave, done, disabled }: { busy: boolean; onSave: () => void; done: () => void; disabled?: boolean }) {
  return <div className="end"><Button kind="ghost" onClick={done}>Thôi</Button><Button loading={busy} disabled={disabled} onClick={onSave}>Lưu</Button></div>;
}

function ServiceForm({ s, done }: { s?: MedicalService; done: () => void }) {
  const [f, setF] = useState({ code: s?.code ?? '', name: s?.name ?? '', category: s?.category ?? 'LAB', price: String(s?.listPrice ?? ''), minutes: String(s?.estimatedMinutes ?? ''), isActive: s?.isActive ?? true });
  const { run, busy } = useRun();
  const up = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const body = { code: f.code.trim(), name: f.name.trim(), category: f.category, listPrice: Number(f.price.replace(/\D/g, '')), estimatedMinutes: Number(f.minutes) || null, isActive: f.isActive };
  return (
    <Card title={s ? `Sửa: ${s.name}` : 'Thêm dịch vụ'} sheet>
      <div className="row"><Field label="Mã" value={f.code} onChange={up('code')} /><Select label="Loại" value={f.category} onChange={up('category')}>{Object.entries(category).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</Select></div>
      <Field label="Tên dịch vụ" value={f.name} onChange={up('name')} />
      <div className="row"><Field label="Giá" inputMode="numeric" suffix="đ" value={f.price} onChange={up('price')} /><Field label="Thời gian làm" inputMode="numeric" suffix="phút" value={f.minutes} onChange={up('minutes')} /></div>
      {s && <Note tone="teal">Giá mới chỉ áp dụng cho chỉ định từ lúc lưu. Chỉ định cũ giữ giá lúc bác sĩ chỉ định.</Note>}
      <Check label="Đang dùng" checked={f.isActive} onChange={(isActive) => setF({ ...f, isActive })} />
      <Save busy={busy === 'save'} done={done} disabled={!body.code || !body.name || !body.listPrice}
        onSave={() => run('save', () => (s ? api.put(`/services/${s.id}`, body) : api.post('/services', body)), 'Đã lưu dịch vụ').then((ok) => ok && done())} />
    </Card>
  );
}

function SpecialtyForm({ s, services, done }: { s?: Specialty; services: MedicalService[]; done: () => void }) {
  const [f, setF] = useState({ code: s?.code ?? '', name: s?.name ?? '', description: s?.description ?? '', consultationServiceId: String(s?.consultationServiceId ?? ''), isActive: s?.isActive ?? true });
  const { run, busy } = useRun();
  const up = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const body = { code: f.code.trim(), name: f.name.trim(), description: f.description.trim() || null, consultationServiceId: Number(f.consultationServiceId) || null, isActive: f.isActive };
  return (
    <Card title={s ? `Sửa: ${s.name}` : 'Thêm chuyên khoa'} sheet>
      <div className="row"><Field label="Mã" value={f.code} onChange={up('code')} /><Field label="Tên" value={f.name} onChange={up('name')} /></div>
      <Select label="Dịch vụ khám (phí khám)" value={f.consultationServiceId} onChange={up('consultationServiceId')}>
        <option value="">Chọn</option>{services.filter((x) => x.category === 'CONSULTATION').map((x) => <option key={x.id} value={x.id}>{x.name}, {vnd(x.listPrice)}</option>)}
      </Select>
      <Field label="Mô tả" value={f.description} onChange={up('description')} />
      <Check label="Đang dùng" checked={f.isActive} onChange={(isActive) => setF({ ...f, isActive })} />
      <Save busy={busy === 'save'} done={done} disabled={!body.code || !body.name}
        onSave={() => run('save', () => (s ? api.put(`/specialties/${s.id}`, body) : api.post('/specialties', body)), 'Đã lưu chuyên khoa').then((ok) => ok && done())} />
    </Card>
  );
}

function MedicineForm({ m, done }: { m?: Medicine; done: () => void }) {
  const [f, setF] = useState({ activeIngredient: m?.activeIngredient ?? '', brandName: m?.brandName ?? '', strength: m?.strength ?? '', dosageForm: m?.dosageForm ?? '', defaultRoute: m?.defaultRoute ?? 'Uống', unit: m?.unit ?? 'viên', drugClass: m?.drugClass ?? 'NORMAL', isActive: m?.isActive ?? true });
  const { run, busy } = useRun();
  const up = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const body = Object.fromEntries(Object.entries(f).map(([k, v]) => [k, typeof v === 'string' ? v.trim() || null : v]));
  return (
    <Card title={m ? `Sửa: ${m.label}` : 'Thêm thuốc'} sheet>
      <div className="row"><Field label="Hoạt chất" value={f.activeIngredient} onChange={up('activeIngredient')} /><Field label="Tên thương mại" value={f.brandName} onChange={up('brandName')} /></div>
      <div className="row"><Field label="Hàm lượng" value={f.strength} onChange={up('strength')} placeholder="500mg" /><Field label="Dạng bào chế" value={f.dosageForm} onChange={up('dosageForm')} placeholder="Viên nén" /></div>
      <div className="row"><Field label="Đơn vị" value={f.unit} onChange={up('unit')} /><Field label="Đường dùng" value={f.defaultRoute} onChange={up('defaultRoute')} />
        <Select label="Nhóm" value={f.drugClass} onChange={up('drugClass')}>{Object.entries(drugClass).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</Select></div>
      <Check label="Đang dùng" checked={f.isActive} onChange={(isActive) => setF({ ...f, isActive })} />
      <Save busy={busy === 'save'} done={done} disabled={!f.activeIngredient.trim() || !f.unit.trim()}
        onSave={() => run('save', () => (m ? api.put(`/medicines/${m.id}`, body) : api.post('/medicines', body)), 'Đã lưu thuốc').then((ok) => ok && done())} />
    </Card>
  );
}

function RoomForm({ r, done }: { r?: Room; done: () => void }) {
  const [f, setF] = useState({ code: r?.code ?? '', name: r?.name ?? '', isActive: r?.isActive ?? true });
  const { run, busy } = useRun();
  const body = { code: f.code.trim(), name: f.name.trim(), specialtyId: r?.specialtyId ?? null, isActive: f.isActive };
  return (
    <Card title={r ? `Sửa: ${r.code}` : 'Thêm phòng'} sheet>
      <div className="row"><Field label="Mã phòng" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} /><Field label="Tên" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></div>
      <Check label="Đang dùng" checked={f.isActive} onChange={(isActive) => setF({ ...f, isActive })} />
      <Save busy={busy === 'save'} done={done} disabled={!body.code || !body.name}
        onSave={() => run('save', () => (r ? api.put(`/rooms/${r.id}`, body) : api.post('/rooms', body)), 'Đã lưu phòng').then((ok) => ok && done())} />
    </Card>
  );
}
