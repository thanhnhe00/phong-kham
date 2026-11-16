import { useMemo, useState } from 'react';
import { Pressable, View } from 'react-native';
import { firstOpenDay, monthCells, shiftMonth, slotsByDay, todayMonth } from '@/lib/calendar';
import { dayLabel, time, vnDate } from '@/lib/format';
import { useOpenSlots } from '@/lib/queries';
import { colors } from '@/lib/theme';
import type { Slot } from '@/lib/types';
import { Icon } from './icon';
import { T } from './text';
import { Card, Loading, LoadError, Pill, Segmented } from './ui';

export type PickedSlot = Slot & { date: string; roomCode: string; shiftName: string };

const WEEK = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

/** Lịch tháng có chấm báo ngày còn chỗ, chọn buổi rồi chọn giờ. Dùng cho đặt lịch và xin đổi giờ. */
export function SlotPicker({
  doctorId,
  value,
  onChange,
  preferDate,
}: {
  doctorId: number;
  value: PickedSlot | null;
  onChange: (s: PickedSlot | null) => void;
  preferDate?: string | null;
}) {
  const q = useOpenSlots(doctorId);
  const byDay = useMemo(() => slotsByDay(q.data ?? []), [q.data]);
  const [picked, setDay] = useState<string | null>(value?.date ?? null);
  const [pickedMonth, setMonth] = useState<string | null>(null);
  const [shift, setShift] = useState(0);
  // Chọn sẵn: ngày bác sĩ hẹn tái khám nếu còn chỗ, không thì ngày gần nhất còn chỗ
  const day = picked ?? (preferDate && byDay.has(preferDate) ? preferDate : firstOpenDay(byDay));
  const month = pickedMonth ?? (day ?? vnDate()).slice(0, 7);

  if (q.isLoading) return <Loading />;
  if (q.error) return <LoadError message="Không tải được lịch trống của bác sĩ." onRetry={() => q.refetch()} />;
  if (byDay.size === 0) {
    return (
      <Card>
        <T weight={600}>Bác sĩ chưa còn giờ trống trong 30 ngày tới</T>
        <T size={13} color={colors.muted}>
          Bạn chọn bác sĩ khác cùng chuyên khoa, hoặc gọi phòng khám để được xếp lịch.
        </T>
      </Card>
    );
  }

  const today = vnDate();
  const sessions = day ? byDay.get(day) ?? [] : [];
  const session = sessions[Math.min(shift, sessions.length - 1)];
  const minMonth = todayMonth();

  return (
    <View style={{ gap: 14 }}>
      <View style={{ backgroundColor: colors.card, borderRadius: 18, padding: 12, gap: 6 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 4 }}>
          <T weight={700}>Tháng {Number(month.slice(5))}, {month.slice(0, 4)}</T>
          <View style={{ flexDirection: 'row', gap: 6 }}>
            <NavBtn label="Tháng trước" icon="left" disabled={month <= minMonth} onPress={() => setMonth(shiftMonth(month, -1))} />
            <NavBtn label="Tháng sau" icon="right" onPress={() => setMonth(shiftMonth(month, 1))} />
          </View>
        </View>
        <View style={{ flexDirection: 'row' }}>
          {WEEK.map((w) => (
            <T key={w} size={12} color={colors.muted} center style={{ flex: 1 }}>
              {w}
            </T>
          ))}
        </View>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
          {monthCells(month).map((d, i) => {
            if (!d) return <View key={`e${i}`} style={{ width: `${100 / 7}%`, height: 38 }} />;
            const open = byDay.has(d);
            const on = d === day;
            const why = d < today ? 'đã qua' : open ? 'còn chỗ' : 'không còn chỗ';
            return (
              <View key={d} style={{ width: `${100 / 7}%`, height: 38, padding: 2 }}>
                <Pressable
                  disabled={!open}
                  accessibilityRole="button"
                  accessibilityLabel={`Ngày ${Number(d.slice(8))}, ${on ? 'đã chọn' : why}`}
                  accessibilityState={{ selected: on, disabled: !open }}
                  onPress={() => {
                    setDay(d);
                    setShift(0);
                    onChange(null);
                  }}
                  style={{ flex: 1, borderRadius: 10, alignItems: 'center', justifyContent: 'center', backgroundColor: on ? colors.brand : 'transparent' }}
                >
                  <T weight={open ? 600 : 400} color={on ? '#FFFFFF' : open ? colors.ink : colors.disabled}>
                    {Number(d.slice(8))}
                  </T>
                  {open && !on ? <View style={{ position: 'absolute', bottom: 3, width: 4, height: 4, borderRadius: 2, backgroundColor: colors.brand }} /> : null}
                </Pressable>
              </View>
            );
          })}
        </View>
      </View>

      {day && session ? (
        <View style={{ gap: 10 }}>
          {sessions.length > 1 ? <Segmented items={sessions.map((x) => `Buổi ${x.shiftName.toLowerCase()}`)} value={shift} onChange={(i) => { setShift(i); onChange(null); }} /> : null}
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' }}>
            <T weight={700}>
              Buổi {session.shiftName.toLowerCase()} {dayLabel(day)}
            </T>
            <T size={12} color={colors.muted}>
              Còn {session.slots.length} giờ trống
            </T>
          </View>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {session.slots.map((sl) => (
              <View key={sl.id} style={{ width: '23%', flexGrow: 0 }}>
                <Pill
                  text={time(sl.start)}
                  on={value?.id === sl.id}
                  onPress={() => onChange({ ...sl, date: day, roomCode: session.roomCode, shiftName: session.shiftName })}
                />
              </View>
            ))}
          </View>
        </View>
      ) : null}
    </View>
  );
}

function NavBtn({ label, icon, onPress, disabled }: { label: string; icon: 'left' | 'right'; onPress: () => void; disabled?: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={disabled}
      onPress={onPress}
      style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center', opacity: disabled ? 0.4 : 1 }}
    >
      <Icon name={icon} size={16} />
    </Pressable>
  );
}
