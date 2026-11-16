import { useState } from 'react';
import { ResultEditor } from '../../components/result-editor';
import { Card, Chip, Loading, Note, PageHead, Table, Tabs, Who } from '../../components/ui';
import { time } from '../../lib/format';
import { useGet } from '../../lib/hooks';
import type { WorkItem } from '../../lib/types';
import { stageLabel, stageOf, type Stage } from '../../lib/work';

type Tab = 'todo' | 'returned' | 'review';
const inTab = (t: Tab, s: Stage) => (t === 'todo' ? s === 'todo' || s === 'entering' : s === t);

export function LabPage() {
  const q = useGet<WorkItem[]>('/order-items/worklist?category=LAB', true);
  const [tab, setTab] = useState<Tab>('todo');
  const [sel, setSel] = useState<number | null>(null);
  const all = q.data ?? [];
  const count = (t: Tab) => all.filter((w) => inTab(t, stageOf(w.item))).length;
  const rows = all.filter((w) => inTab(tab, stageOf(w.item)));
  const cur = rows.find((w) => w.item.id === sel) ?? rows[0];
  const returned = all.filter((w) => stageOf(w.item) === 'returned');

  return (
    <div className="page">
      <PageHead title="Xét nghiệm cần làm" sub="Điều dưỡng làm xét nghiệm. Siêu âm, X-quang, nội soi do bác sĩ làm và nhập kết quả" />
      <Tabs value={tab} onChange={(t) => { setTab(t); setSel(null); }}
        items={[{ key: 'todo', label: `Chờ làm (${count('todo')})` }, { key: 'returned', label: `Bị trả lại (${count('returned')})` }, { key: 'review', label: `Chờ bác sĩ duyệt (${count('review')})` }]} />
      {tab !== 'returned' && returned.length > 0 && (
        <Note><b>{returned.length} kết quả bị bác sĩ trả lại.</b> Mở tab Bị trả lại để sửa và gửi lại.</Note>
      )}
      <div className="cols">
        <div className="wide">
          <Card title="Bác sĩ đã chỉ định" sub="Theo giờ bác sĩ chỉ định" pad={false}>
            {q.isLoading ? <Loading /> : (
              <Table rows={rows} rowKey={(w) => w.item.id} selected={cur?.item.id} onRow={(w) => setSel(w.item.id)} empty="Không có việc nào."
                cols={[
                  { head: 'Bệnh nhân', cell: (w) => <Who name={w.patientName} sub={w.patientCode} /> },
                  { head: 'Xét nghiệm', cell: (w) => w.item.serviceName },
                  { head: 'Bác sĩ', cell: (w) => <Who name={`BS ${w.doctorName}`} sub={`Chỉ định ${time(w.orderedAt)}`} /> },
                  { head: 'Trạng thái', cell: (w) => { const s = stageOf(w.item); return <Chip tone={s === 'returned' ? 'red' : s === 'review' ? 'blue' : s === 'entering' ? 'teal' : 'amber'}>{stageLabel[s]}</Chip>; } },
                ]} />
            )}
          </Card>
        </div>
        <div className="narrow">
          {cur && (
            <Card title={`Kết quả: ${cur.item.serviceName}`} sub={`${cur.patientName}, ${cur.patientCode}`} sheet key={cur.item.id}>
              <ResultEditor item={cur.item} />
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
