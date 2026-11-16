import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../../components/icons';
import { Button, Card, Loading, PageHead, Table, Who, useToast } from '../../components/ui';
import { errorText, openFile } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { time, timeDay } from '../../lib/format';
import { useGet } from '../../lib/hooks';
import type { WorkItem } from '../../lib/types';
import { lastPerformance, stageOf } from '../../lib/work';
import { ReviewButtons } from './exam-orders';

export function ReviewPage() {
  const { me } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const q = useGet<WorkItem[]>('/order-items/worklist', true);
  const rows = (q.data ?? []).filter((w) => w.doctorId === me?.employeeId && stageOf(w.item) === 'review');
  const [sel, setSel] = useState<number | null>(null);
  const cur = rows.find((w) => w.item.id === sel) ?? rows[0];
  const r = cur ? lastPerformance(cur.item)?.result : null;

  return (
    <div className="page">
      <PageHead title="Duyệt kết quả" sub="Kết quả điều dưỡng đã gửi cho bệnh nhân của bạn. Bệnh nhân chỉ xem được sau khi bạn duyệt" />
      <div className="cols">
        <div className="wide">
          <Card title="Chờ duyệt" pad={false}>
            {q.isLoading ? <Loading /> : (
              <Table rows={rows} rowKey={(w) => w.item.id} selected={cur?.item.id} onRow={(w) => setSel(w.item.id)} empty="Không có kết quả nào chờ duyệt."
                cols={[
                  { head: 'Bệnh nhân', cell: (w) => <Who name={w.patientName} sub={w.patientCode} /> },
                  { head: 'Xét nghiệm', cell: (w) => w.item.serviceName },
                  { head: 'Gửi lúc', cell: (w) => time(lastPerformance(w.item)?.result?.enteredAt ?? w.orderedAt) },
                ]} />
            )}
          </Card>
        </div>
        <div className="narrow">
          {cur && r && (
            <Card title={cur.item.serviceName} sub={`${cur.patientName}, ${r.enteredByName} gửi lúc ${timeDay(r.enteredAt)}`} sheet key={cur.item.id}>
              {r.resultText && <p style={{ whiteSpace: 'pre-wrap' }}>{r.resultText}</p>}
              {r.conclusion && <p><span className="muted">Nhận xét: </span><b>{r.conclusion}</b></p>}
              {r.files.map((f) => (
                <div key={f.id} className="between">
                  <span className="wrap"><Icon name="doc" />{f.fileName}</span>
                  <button type="button" className="link" onClick={() => openFile(f.id).catch((e) => toast(errorText(e), true))}>Mở tệp</button>
                </div>
              ))}
              <ReviewButtons resultId={r.id} />
              <Button kind="ghost" sm onClick={() => nav(`/bac-si/kham/${cur.visitId}`)}>Mở phiếu khám</Button>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
