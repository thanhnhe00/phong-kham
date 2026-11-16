import { addDays, dm, mondayOf, vnDate } from '../lib/format';
import { Button } from './ui';

export function WeekNav({ week, setWeek }: { week: string; setWeek: (w: string) => void }) {
  return (
    <div className="wrap">
      <Button kind="ghost" icon="left" aria-label="Tuần trước" onClick={() => setWeek(addDays(week, -7))} />
      <Button kind="ghost" icon="cal" onClick={() => setWeek(mondayOf(vnDate()))}>Tuần {dm(week)} – {dm(addDays(week, 6))}</Button>
      <Button kind="ghost" icon="right" aria-label="Tuần sau" onClick={() => setWeek(addDays(week, 7))} />
    </div>
  );
}
