import { useState } from 'react';
import { Button, Card, Chip, Field, Kv, Loading, PageHead, Seg, Table, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { timeDay, vnd } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import { invoiceStatus, payMethod } from '../../lib/labels';
import type { Invoice, Page } from '../../lib/types';

type Method = 'CASH' | 'BANK_TRANSFER' | 'OTHER';

export function CashierPage() {
  const unpaid = useGet<Page<Invoice>>('/invoices?status=UNPAID&size=100', true);
  const partial = useGet<Page<Invoice>>('/invoices?status=PARTIALLY_PAID&size=100', true);
  const rows = [...(unpaid.data?.items ?? []), ...(partial.data?.items ?? [])].sort((a, b) => b.issuedAt.localeCompare(a.issuedAt));
  const [sel, setSel] = useState<number | null>(null);
  const cur = rows.find((i) => i.id === sel) ?? rows[0];

  return (
    <div className="page">
      <PageHead title="Thu ngân" sub="Mỗi lượt khám một hóa đơn: phí khám và các dịch vụ đã làm. Bệnh nhân cũng trả được trên ứng dụng" />
      <div className="cols">
        <div className="wide no-print">
          <Card title="Chờ thanh toán" pad={false}>
            {unpaid.isLoading || partial.isLoading ? <Loading /> : (
              <Table rows={rows} rowKey={(i) => i.id} selected={cur?.id} onRow={(i) => setSel(i.id)} empty="Không còn hóa đơn nào chờ thu."
                cols={[
                  { head: 'Hóa đơn', cell: (i) => <b>{i.code}</b> },
                  { head: 'Bệnh nhân', cell: (i) => <Who name={i.patientName} sub={`${i.patientCode}, lập lúc ${timeDay(i.issuedAt)}`} /> },
                  { head: 'Tổng tiền', right: true, cell: (i) => vnd(i.totalAmount) },
                  { head: 'Còn phải trả', right: true, cell: (i) => <b className={i.status === 'PARTIALLY_PAID' ? 'amber' : undefined}>{vnd(i.remaining)}</b> },
                ]} />
            )}
          </Card>
        </div>
        <div className="narrow">{cur && <Pay key={cur.id} inv={cur} />}</div>
      </div>
    </div>
  );
}

function Pay({ inv }: { inv: Invoice }) {
  const [method, setMethod] = useState<Method>('CASH');
  const [amount, setAmount] = useState(String(inv.remaining));
  const [given, setGiven] = useState('');
  const { run, busy } = useRun();
  const amt = Number(amount.replace(/\D/g, '')) || 0;
  const cash = Number(given.replace(/\D/g, '')) || 0;
  const payments = inv.payments.filter((p) => p.status === 'SUCCESS');

  return (
    <>
      <Card title={`Hóa đơn ${inv.code}`} sheet right={<Chip tone={invoiceStatus[inv.status][1]}>{invoiceStatus[inv.status][0]}</Chip>}>
        <Who name={inv.patientName} sub={`${inv.patientCode}, lượt khám ${inv.visitCode}`} />
        <div className="stack tight">
          {inv.items.map((l) => <Kv key={l.id} k={`${l.description}${l.quantity > 1 ? ` × ${l.quantity}` : ''}`} v={vnd(l.lineTotal)} />)}
        </div>
        <div className="total num"><span>Còn phải trả</span><span>{vnd(inv.remaining)}</span></div>
        {payments.length > 0 && (
          <div className="stack tight">
            <span className="b small">Các lần thu</span>
            {payments.map((p) => (
              <Kv key={p.id} k={`${timeDay(p.paidAt)}, ${p.kind === 'REFUND' ? 'hoàn tiền' : payMethod[p.method]}`} v={<span className={p.kind === 'REFUND' ? 'red' : undefined}>{p.kind === 'REFUND' ? '−' : ''}{vnd(p.amount)}</span>} />
            ))}
          </div>
        )}
        <div className="no-print stack">
          <div className="field"><span>Hình thức</span>
            <Seg label="Hình thức" value={method} onChange={setMethod} items={[{ key: 'CASH', label: 'Tiền mặt' }, { key: 'BANK_TRANSFER', label: 'Chuyển khoản' }, { key: 'OTHER', label: 'Khác' }]} />
          </div>
          <div className="row">
            <Field label="Số tiền thu" inputMode="numeric" suffix="đ" value={amount} onChange={(e) => setAmount(e.target.value)} />
            {method === 'CASH' && <Field label="Khách đưa" inputMode="numeric" suffix="đ" value={given} onChange={(e) => setGiven(e.target.value)} />}
          </div>
          {method === 'CASH' && cash > 0 && <Kv k="Trả lại khách" v={<b>{cash >= amt ? vnd(cash - amt) : 'Khách đưa chưa đủ'}</b>} />}
          <div className="end">
            <Button kind="ghost" icon="print" onClick={() => window.print()}>In hóa đơn</Button>
            <Button icon="check" disabled={amt <= 0 || amt > inv.remaining} loading={busy === 'pay'}
              onClick={() => run('pay', () => api.post(`/invoices/${inv.id}/payments`, { method, amount: amt }), `Đã thu ${vnd(amt)}`)}>Thu {vnd(amt)}</Button>
          </div>
          <span className="muted small">Không thu quá số còn phải trả. Hoàn tiền do quản lý làm và hiện lại ở đây.</span>
        </div>
      </Card>
    </>
  );
}
