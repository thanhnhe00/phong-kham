import { useQueryClient } from '@tanstack/react-query';
import { Redirect, router } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { Band, Steps, Tiles } from '@/components/home';
import { Icon } from '@/components/icon';
import { T } from '@/components/text';
import { Avatar, Button, Card, Loading, LoadError, RowList, Screen, SectionTitle, Tag, Ticket, type ListRowProps } from '@/components/ui';
import { api, errorText } from '@/lib/api';
import { useBooking } from '@/lib/booking';
import { apptWhen, dayLabel, dm, relativeDay, time, vnd } from '@/lib/format';
import { homeMain, homeTodos, NO_SHOW_MINUTES, visitSteps, type FollowUp, type Todo } from '@/lib/home-state';
import { appointmentStatus, managerRole } from '@/lib/labels';
import { useProfile } from '@/lib/profile';
import { useAppointments, useMarkRead, useNotifications, useQueue, useRecentRecords, useVisits } from '@/lib/queries';
import { colors } from '@/lib/theme';
import type { Appointment, MyQueueItem, VisitRecord } from '@/lib/types';

export default function Home() {
  const { current, loading: loadingProfiles } = useProfile();
  const { draft } = useBooking();
  const patientId = current?.patient.id ?? null;
  const queue = useQueue();
  // Có lượt khám hôm nay (kể cả đã khám xong, vì hóa đơn, kết quả còn đổi) thì hỏi lại thường xuyên để mọi thứ luôn mới
  const today = (queue.data ?? []).some((q) => q.visit.patientId === patientId);
  const appts = useAppointments(today);
  const visits = useVisits(patientId);
  const { records, loading: loadingRecords } = useRecentRecords(visits.data, 5, today);
  const notes = useNotifications();

  if (loadingProfiles) return <Loading />;
  if (!current) return <Redirect href="/tao-ho-so" />;
  if (draft.resume && draft.slot) return <Redirect href="/dat-lich/xac-nhan" />;
  if (appts.error || queue.error) {
    return (
      <Screen>
        <LoadError message={errorText(appts.error ?? queue.error)} onRetry={() => { appts.refetch(); queue.refetch(); }} />
      </Screen>
    );
  }
  if (appts.isLoading || queue.isLoading || visits.isLoading || loadingRecords) return <Loading />;

  const input = { patientId: current.patient.id, appointments: appts.data ?? [], queue: queue.data ?? [], records, notifications: notes.data?.items ?? [] };
  const main = homeMain(input);
  const todos = homeTodos(input, main);
  const note = managerRole[current.relationship];

  return (
    <Screen>
      <StatusBar style="light" />
      {main.kind === 'visit' ? (
        <VisitMain item={main.item} record={main.record} note={note} />
      ) : main.kind === 'today' ? (
        <TodayMain appointment={main.appointment} late={main.lateMinutes} note={note} />
      ) : main.kind === 'upcoming' ? (
        <>
          <Band label="Lịch hẹn sắp tới" link={main.count > 1 ? `Tất cả (${main.count})` : undefined} onLink={() => router.push('/lich-hen')} note={note} />
          <UpcomingTicket a={main.appointment} />
        </>
      ) : (
        <>
          <Band label="Lịch hẹn sắp tới" note={note} />
          <EmptyMain name={current.patient.fullName} followUp={main.followUp} />
        </>
      )}
      {todos.length > 0 ? (
        <>
          <SectionTitle title="Việc cần làm" />
          <TodoList todos={todos} />
        </>
      ) : null}
      <Tiles />
    </Screen>
  );
}

function DoctorLine({ name, sub, struck }: { name: string; sub: string; struck?: boolean }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1 }}>
      <Avatar name={name} size={40} />
      <View style={{ flex: 1 }}>
        <T weight={700} style={struck ? { textDecorationLine: 'line-through' } : undefined}>
          {name.startsWith('BS') || name.includes('.BS') ? name : `BS ${name}`}
        </T>
        <T size={13} color={colors.muted}>
          {sub}
        </T>
      </View>
    </View>
  );
}

function WhenTop({ when, rel, status }: { when: string; rel: string; status: { text: string; kind: 'teal' | 'amber' | 'red' | 'grey' | 'sky' } }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10 }}>
      <View style={{ flex: 1 }}>
        <T size={13} color={colors.muted}>
          {rel}
        </T>
        <T size={22} weight={800} tabular style={{ letterSpacing: -0.4 }}>
          {when}
        </T>
      </View>
      <Tag text={status.text} kind={status.kind} />
    </View>
  );
}

/** A1 (đã xác nhận / chờ xác nhận) và A3 (đổi bác sĩ, chờ lễ tân gọi) */
function UpcomingTicket({ a }: { a: Appointment }) {
  const reconfirm = a.status === 'NEEDS_RECONFIRMATION';
  const pending = a.status === 'PENDING_CONFIRMATION';
  const open = () => router.push(`/lich-hen/${a.id}`);
  return (
    <Ticket
      onPress={open}
      edge={reconfirm ? colors.amberLine : undefined}
      top={<WhenTop when={apptWhen(a.start)} rel={relativeDay(a.date)} status={appointmentStatus[a.status]} />}
      bottom={
        <>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <DoctorLine name={a.doctorName} sub={`${a.specialtyName}, phòng ${a.roomCode}`} />
            <Icon name="right" color={colors.muted} />
          </View>
          {reconfirm ? (
            <>
              <T size={13} color={colors.amberInk}>
                Lịch khám có thay đổi bác sĩ. Lễ tân sẽ gọi để hỏi ý bạn: đồng ý thì giữ nguyên giờ, không đồng ý thì xếp ngày khác.
              </T>
              <Button title="Chọn ngày khác" kind="ghost" height={46} onPress={() => router.push(`/doi-lich/${a.id}`)} />
            </>
          ) : pending ? (
            <T size={13} color={colors.amberInk}>
              Lễ tân sẽ gọi cho bạn để xác nhận lịch này.
            </T>
          ) : null}
        </>
      }
    />
  );
}

/** A2: chưa có lịch, có thể đang đến hạn tái khám */
function EmptyMain({ name, followUp }: { name: string; followUp: FollowUp | null }) {
  const { reset } = useBooking();
  const given = name.trim().split(/\s+/).pop();
  const rebook = () => {
    if (followUp) reset({ specialtyId: followUp.specialtyId, doctorId: followUp.doctorId, doctorName: followUp.doctorName, followUpDate: followUp.date });
    router.push(followUp ? '/dat-lich/gio' : '/dat-lich');
  };
  return (
    <View style={{ backgroundColor: colors.card, borderRadius: 20, borderWidth: 2, borderStyle: 'dashed', borderColor: '#C3D6CD', padding: 18, gap: 12 }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <T size={13} color={colors.muted}>
          Chưa có lịch nào
        </T>
        {followUp ? <Tag text="Đến hạn tái khám" kind="amber" /> : null}
      </View>
      <T size={20} weight={800}>
        {followUp ? `Tái khám ngày ${dm(followUp.date)}` : `${given} chưa có lịch khám`}
      </T>
      <T size={13} color={colors.muted}>
        {followUp
          ? `BS ${followUp.doctorName} hẹn kiểm tra lại sau lần khám ngày ${dm(followUp.visitDate)}.`
          : 'Đặt trước để không phải chờ lâu khi đến phòng khám.'}
      </T>
      <Button title={followUp ? 'Đặt lịch tái khám' : 'Đặt lịch khám'} onPress={rebook} height={46} />
      {followUp ? (
        <Pressable onPress={() => router.push('/dat-lich')} accessibilityRole="button" style={{ alignSelf: 'center' }}>
          <T size={13} weight={600} color={colors.brand}>
            Đặt lịch khám khác
          </T>
        </Pressable>
      ) : null}
    </View>
  );
}

/** A4: hôm nay có lịch, chưa check-in */
function TodayMain({ appointment: a, late, note }: { appointment: Appointment; late: number; note: string | null }) {
  const confirmed = a.status === 'CONFIRMED';
  const holdUntil = time(new Date(Date.parse(a.start) + NO_SHOW_MINUTES * 60_000).toISOString());
  return (
    <>
      <Band label="Lịch khám hôm nay" note={note} />
      <Ticket
        top={
          <>
            <WhenTop when={`${time(a.start)}, phòng ${a.roomCode}`} rel="Giờ hẹn" status={late > 0 ? { text: `Trễ ${late} phút`, kind: 'amber' } : appointmentStatus[a.status]} />
            <DoctorLine name={a.doctorName} sub={a.specialtyName} />
          </>
        }
        bottom={
          confirmed ? (
            <Button title="Mở mã check-in" icon="qr" height={54} onPress={() => router.push(`/ma-checkin/${a.id}`)} />
          ) : (
            <T size={13} color={colors.amberInk}>
              Lịch này chưa được lễ tân xác nhận. Khi đến, bạn đọc số điện thoại tại quầy để được check-in.
            </T>
          )
        }
      />
      {late > 0 ? (
        <View accessibilityRole="alert" style={{ backgroundColor: colors.amberBg, borderRadius: 18, padding: 14, gap: 6 }}>
          <T weight={700} color={colors.amberInk}>
            Bạn vẫn được khám
          </T>
          <T size={13} color={colors.amberInk}>
            Người đến trễ được xếp sau người đến đúng giờ. Phòng khám giữ lịch đến {holdUntil}, sau đó lịch chuyển thành bỏ lỡ và bạn đăng ký khám tại quầy.
          </T>
        </View>
      ) : (
        <Card gap={6}>
          <T weight={700}>Khi đến phòng khám</T>
          <T size={13} color={colors.muted}>
            Đưa mã check-in cho lễ tân quét. Mang theo CCCD và giấy tờ khám cũ nếu có.
          </T>
        </Card>
      )}
    </>
  );
}

/** A5–A7: đã check-in hôm nay */
function VisitMain({ item, record, note }: { item: MyQueueItem; record: VisitRecord | null; note: string | null }) {
  const v = item.visit;
  const steps = visitSteps(record, item, time);
  if (v.status === 'COMPLETED') return <DoneMain item={item} record={record} note={note} steps={steps} />;
  if (v.status === 'AWAITING_RESULTS') {
    const tests = (record?.orders ?? []).flatMap((o) => o.items).map((i) => i.serviceName);
    return (
      <>
        <Band label="Lượt khám hôm nay" note={note} />
        <Ticket
          top={
            <>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <T size={13} color={colors.muted}>
                  Bác sĩ chỉ định
                </T>
                <Tag text="Đang làm" kind="sky" />
              </View>
              <T size={24} weight={800}>
                {tests.join(', ') || 'Xét nghiệm theo chỉ định'}
              </T>
              <T size={13} color={colors.muted}>
                Làm theo hướng dẫn của nhân viên phòng xét nghiệm.
              </T>
            </>
          }
          bottom={
            <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
              <View>
                <T size={13} color={colors.muted}>
                  Khi có kết quả
                </T>
                <T weight={700}>Quay lại phòng {v.roomCode}</T>
              </View>
              <View style={{ alignItems: 'flex-end' }}>
                <T size={13} color={colors.muted}>
                  Số của bạn
                </T>
                <T weight={700}>Vẫn là {v.queueNumber}</T>
              </View>
            </View>
          }
        />
        <Steps steps={steps} />
      </>
    );
  }
  const called = v.status === 'CALLED';
  const inRoom = v.status === 'IN_PROGRESS';
  const right = called ? 'Đến lượt bạn' : inRoom ? 'Đang khám' : item.peopleAhead === 0 ? 'Sắp đến lượt' : `Còn ${item.peopleAhead ?? '?'} người`;
  return (
    <>
      <Band label="Lượt khám hôm nay" note={note} />
      <Ticket
        edge={called ? colors.brand : undefined}
        top={
          <>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
              <T size={13} color={colors.muted}>
                Check-in lúc {v.checkedInAt ? time(v.checkedInAt) : ''}
              </T>
              <Tag text={called ? 'Mời vào phòng' : inRoom ? 'Đang khám' : 'Đang chờ khám'} kind="teal" />
            </View>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' }}>
              <View>
                <T size={13} color={colors.muted}>
                  Số thứ tự
                </T>
                <T size={88} weight={800} color={colors.brandDark} tabular style={{ lineHeight: 92, letterSpacing: -3 }} testID="queue-number">
                  {v.queueNumber}
                </T>
              </View>
              <View style={{ alignItems: 'flex-end', paddingBottom: 10 }}>
                <T size={20} weight={800}>
                  {right}
                </T>
                {!called && !inRoom && item.peopleAhead ? (
                  <T size={13} color={colors.muted}>
                    trước bạn
                  </T>
                ) : null}
              </View>
            </View>
          </>
        }
        bottom={
          <View>
            <T weight={700}>Phòng {v.roomCode}</T>
            <T size={13} color={colors.muted}>
              BS {v.doctorName}
            </T>
          </View>
        }
      />
      <Steps steps={steps} />
    </>
  );
}

/** A7: khám xong, hóa đơn chung của lượt khám, kết luận và tái khám */
function DoneMain({ item, record, note, steps }: { item: MyQueueItem; record: VisitRecord | null; note: string | null; steps: ReturnType<typeof visitSteps> }) {
  const qc = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inv = record?.invoice;
  const pay = async () => {
    if (!inv) return;
    setBusy(true);
    setError(null);
    try {
      await api.post(`/me/invoices/${inv.id}/pay`);
      await qc.invalidateQueries();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  const diag = record?.emr?.diagnoses.find((d) => d.type === 'PRIMARY')?.name ?? record?.emr?.conclusion;
  const rx = record?.prescriptions.filter((p) => p.status !== 'REPLACED') ?? [];
  const rows: ListRowProps[] = [
    {
      icon: 'doc',
      tint: 'mint',
      title: diag ?? 'Kết luận khám',
      sub: rx.length ? `Kết luận và đơn thuốc ${rx.reduce((n, p) => n + p.items.length, 0)} loại` : 'Xem kết luận của bác sĩ',
      onPress: () => router.push(`/luot-kham/${item.visit.id}`),
    },
  ];
  return (
    <>
      <Band label="Lượt khám hôm nay" note={note} />
      {inv ? (
        <Ticket
          top={
            <>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <T weight={700}>Hóa đơn hôm nay</T>
                <Tag text={inv.status === 'PAID' ? 'Đã thanh toán' : 'Chưa thanh toán'} kind={inv.status === 'PAID' ? 'teal' : 'amber'} />
              </View>
              {inv.items.map((it) => (
                <View key={it.id} style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                  <T color={colors.muted} style={{ flex: 1 }}>
                    {it.description}
                  </T>
                  <T tabular>{vnd(it.lineTotal)}</T>
                </View>
              ))}
            </>
          }
          bottom={
            <>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <T size={18} weight={800}>
                  {inv.remaining > 0 ? 'Còn phải trả' : 'Tổng cộng'}
                </T>
                <T size={18} weight={800} tabular testID="invoice-remaining">
                  {vnd(inv.remaining > 0 ? inv.remaining : inv.totalAmount)}
                </T>
              </View>
              {inv.remaining > 0 ? (
                <>
                  <Button title="Trả trên app" onPress={pay} loading={busy} height={46} />
                  <T size={12} color={colors.muted}>
                    Hoặc trả tại quầy thu ngân. Phòng khám chỉ kê đơn, thuốc mua ở nhà thuốc.
                  </T>
                </>
              ) : null}
            </>
          }
        />
      ) : (
        <Card>
          <T>Hóa đơn đang được lập, vui lòng chờ trong giây lát.</T>
        </Card>
      )}
      {error ? (
        <T size={13} color={colors.red}>
          {error}
        </T>
      ) : null}
      <RowList rows={rows} />
      {inv?.status !== 'PAID' ? <Steps steps={steps} /> : null}
    </>
  );
}

function TodoList({ todos }: { todos: Todo[] }) {
  const markRead = useMarkRead();
  const { reset } = useBooking();
  const rows: ListRowProps[] = todos.map((t) => {
    if (t.kind === 'pay')
      return {
        icon: 'card',
        tint: 'honey',
        title: `Hóa đơn ${vnd(t.amount)}`,
        sub: `Lần khám ngày ${dm(t.visitDate)}`,
        tag: { text: 'Cần thanh toán', kind: 'amber' },
        action: 'Thanh toán',
        onPress: () => router.push(`/luot-kham/${t.visitId}`),
      };
    if (t.kind === 'followUp')
      return {
        icon: 'clock',
        tint: 'honey',
        title: `Tái khám ${dayLabel(t.followUp.date)}`,
        sub: `BS ${t.followUp.doctorName} hẹn kiểm tra lại`,
        tag: { text: 'Cần đặt lịch', kind: 'amber' },
        action: 'Đặt lịch',
        onPress: () => {
          reset({ specialtyId: t.followUp.specialtyId, doctorId: t.followUp.doctorId, doctorName: t.followUp.doctorName, followUpDate: t.followUp.date });
          router.push('/dat-lich/gio');
        },
      };
    return {
      icon: 'flask',
      tint: 'peach',
      title: t.title,
      sub: t.body,
      tag: { text: 'Kết quả mới', kind: 'sky' },
      onPress: () => {
        markRead.mutate(t.notificationId);
        if (t.visitId) router.push(`/luot-kham/${t.visitId}`);
      },
    };
  });
  return <RowList rows={rows} />;
}
