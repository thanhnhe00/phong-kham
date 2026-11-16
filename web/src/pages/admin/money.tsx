import { useState } from 'react';
import { Button, Card, Chip, Field, Kv, Loading, PageHead, Table, Tabs, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { dmy, timeDay, vnd, vnDate } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import { invoiceStatus, payMethod } from '../../lib/labels';
import type { Invoice, Page, Payment } from '../../lib/types';

type Tab = 'all' | 'counter' | 'app' | 'refund';
interface Tx { p: Payment; inv: Invoice }

export function MoneyPage() {
  const [date, setDate] = useState(vnDate());
  const [tab, setTab] = useState<Tab>('all');
  const [sel, setSel] = useState<number | null>(null);
  // shortcut: dựng sổ giao dịch từ hóa đơn trong ngày (mỗi hóa đơn kèm các lần thu/hoàn); khi dữ liệu lớn thì thêm API giao dịch riêng có phân trang
  const q = useGet<Page<Invoice>>(`/invoices?from=${date}&to=${date}&size=500`);
  const txs: Tx[] = (q.data?.items ?? []).flatMap((inv) => inv.payments.filter((p) => p.status === 'SUCCESS').map((p) => ({ p, inv })))
    .sort((a, b) => b.p.paidAt.localeCompare(a.p.paidAt));
  const match = (t: Tx) => tab === 'all' || (tab === 'refund' ? t.p.kind === 'REFUND' : t.p.kind === 'PAYMENT' && (tab === 'app') === (t.p.method === 'ONLINE_SIMULATED'));
  const rows = txs.filter(match);
  const collected = txs.reduce((n, t) => n + (t.p.kind === 'REFUND' ? 0 : t.p.amount), 0);
  const refunded = txs.reduce((n, t) => n + (t.p.kind === 'REFUND' ? t.p.amount : 0), 0);
  const cur = (q.data?.items ?? []).find((i) => i.id === sel) ?? rows[0]?.inv;

  return (
    <div className="page">
      <PageHead title="Thu chi" sub={<>Ngày {dmy(date)}: thu <b>{vnd(collected)}</b>, hoàn <b>{vnd(refunded)}</b>, thực thu <b>{vnd(collected - refunded)}</b>.</>}
        actions={<Field label="Ngày" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />} />
      <Tabs value={tab} onChange={setTab} items={[
        { key: 'all', label: `Tất cả (${txs.length})` }, { key: 'counter', label: 'Tại quầy' }, { key: 'app', label: 'Trên ứng dụng' }, { key: 'refund', label: 'Hoàn tiền' },
      ]} />
      <div className="cols">
        <div className="wide">
          <Card title="Giao dịch" sub="Thu tại quầy, thu trên ứng dụng và hoàn tiền" pad={false}>
            {q.isLoading ? <Loading /> : (
              <Table rows={rows} rowKey={(t) => t.p.id} selected={cur ? rows.find((t) => t.inv.id === cur.id)?.p.id : null} onRow={(t) => setSel(t.inv.id)} empty="Không có giao dịch."
                cols={[
                  { head: 'Lúc', cell: (t) => <b>{timeDay(t.p.paidAt)}</b> },
                  { head: 'Hóa đơn', cell: (t) => t.inv.code },
                  { head: 'Bệnh nhân', cell: (t) => <Who name={t.inv.patientName} sub={t.inv.patientCode} /> },
                  { head: 'Hình thức', cell: (t) => t.p.kind === 'REFUND' ? <Chip tone="red">Hoàn tiền</Chip> : payMethod[t.p.method] },
                  { head: 'Số tiền', right: true, cell: (t) => <b className={t.p.kind === 'REFUND' ? 'red' : undefined}>{t.p.kind === 'REFUND' ? '−' : ''}{vnd(t.p.amount)}</b> },
                ]} />
            )}
          </Card>
        </div>
        <div className="narrow">{cur && <Refund key={cur.id} inv={cur} />}</div>
      </div>
    </div>
  );
}

function Refund({ inv }: { inv: Invoice }) {
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const { run, busy } = useRun();
  const amt = Number(amount.replace(/\D/g, '')) || 0;
  return (
    <Card title={`Hóa đơn ${inv.code}`} right={<Chip tone={invoiceStatus[inv.status][1]}>{invoiceStatus[inv.status][0]}</Chip>} sheet>
      <Who name={inv.patientName} sub={`${inv.patientCode}, lượt khám ${inv.visitCode}`} />
      <div className="stack tight">
        <Kv k="Tổng hóa đơn" v={vnd(inv.totalAmount)} />
        <Kv k="Đã thu (sau hoàn)" v={vnd(inv.paidAmount)} />
        {inv.payments.filter((p) => p.status === 'SUCCESS').map((p) => (
          <Kv key={p.id} k={`${timeDay(p.paidAt)}, ${p.kind === 'REFUND' ? 'hoàn tiền' : payMethod[p.method]}${p.note ? `: ${p.note}` : ''}`} v={<span className={p.kind === 'REFUND' ? 'red' : undefined}>{p.kind === 'REFUND' ? '−' : ''}{vnd(p.amount)}</span>} />
        ))}
      </div>
      {inv.paidAmount > 0 && (
        <>
          <div className="row">
            <Field label="Số tiền hoàn" inputMode="numeric" suffix="đ" value={amount} onChange={(e) => setAmount(e.target.value)} />
            <Field label="Tối đa" value={String(inv.paidAmount)} suffix="đ" readOnly />
          </div>
          <Field label="Lý do" value={note} onChange={(e) => setNote(e.target.value)} placeholder="Ví dụ: bệnh nhân từ chối dịch vụ sau khi đã thu" />
          <div className="end">
            <Button kind="danger" disabled={amt <= 0 || amt > inv.paidAmount || !note.trim()} loading={busy === 'refund'}
              onClick={() => window.confirm(`Hoàn ${vnd(amt)} cho ${inv.patientName}?`) && run('refund', () => api.post(`/invoices/${inv.id}/refunds`, { amount: amt, note: note.trim() }), 'Đã hoàn tiền').then((ok) => ok && (setAmount(''), setNote('')))}>
              Hoàn tiền
            </Button>
          </div>
        </>
      )}
      <span className="muted small">Lưu người hoàn, thời điểm, lý do. Hóa đơn ở màn thu ngân và trên ứng dụng hiện thêm dòng hoàn tiền.</span>
    </Card>
  );
}
