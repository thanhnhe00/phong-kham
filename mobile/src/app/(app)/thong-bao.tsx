import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { Pressable, View } from 'react-native';
import { Icon, type IconName } from '@/components/icon';
import { T } from '@/components/text';
import { BackHeader, Card, Loading, LoadError, Screen } from '@/components/ui';
import { api, errorText } from '@/lib/api';
import { dm, time, vnDate } from '@/lib/format';
import { qk, useMarkRead, useNotifications } from '@/lib/queries';
import { colors } from '@/lib/theme';
import type { AppNotification } from '@/lib/types';

const ICON: Record<string, IconName> = {
  APPOINTMENT_CONFIRMED: 'check',
  APPOINTMENT_RECONFIRM: 'alert',
  APPOINTMENT_CANCELLED: 'x',
  REMINDER: 'clock',
  CALLED: 'bell',
  RESULTS_READY: 'flask',
  VISIT_COMPLETED: 'doc',
};

export default function Notifications() {
  const q = useNotifications();
  const qc = useQueryClient();
  const markRead = useMarkRead();
  const items = q.data?.items ?? [];
  const today = vnDate();
  const groups = [
    { title: 'Hôm nay', list: items.filter((n) => vnDate(n.createdAt) === today) },
    { title: 'Trước đó', list: items.filter((n) => vnDate(n.createdAt) !== today) },
  ].filter((g) => g.list.length);

  const open = (n: AppNotification) => {
    if (!n.readAt) markRead.mutate(n.id);
    if (n.relatedEntityType === 'APPOINTMENT' && n.relatedEntityId) router.push(`/lich-hen/${n.relatedEntityId}`);
    else if (n.relatedEntityType === 'VISIT' && n.relatedEntityId) router.push(`/luot-kham/${n.relatedEntityId}`);
  };
  const readAll = async () => {
    await api.post('/me/notifications/read-all').catch(() => undefined);
    await qc.invalidateQueries({ queryKey: qk.notifications });
  };

  return (
    <Screen>
      <BackHeader
        title="Thông báo"
        right={
          items.some((n) => !n.readAt) ? (
            <Pressable onPress={readAll} accessibilityRole="button" hitSlop={10}>
              <T size={13} weight={600} color={colors.brand}>
                Đọc hết
              </T>
            </Pressable>
          ) : undefined
        }
      />
      {q.isLoading ? (
        <Loading />
      ) : q.error ? (
        <LoadError message={errorText(q.error)} onRetry={() => q.refetch()} />
      ) : items.length === 0 ? (
        <Card>
          <T weight={600}>Chưa có thông báo</T>
          <T size={13} color={colors.muted}>
            Xác nhận lịch, nhắc lịch, gọi vào khám và kết quả xét nghiệm sẽ hiện ở đây.
          </T>
        </Card>
      ) : (
        <View style={{ backgroundColor: colors.card, borderRadius: 18, overflow: 'hidden' }}>
          {groups.map((g, gi) => (
            <View key={g.title}>
              <T size={13} weight={600} color={colors.muted} style={{ paddingHorizontal: 16, paddingVertical: 12, borderTopWidth: gi ? 1 : 0, borderTopColor: '#EEF2F1' }}>
                {g.title}
              </T>
              {g.list.map((n) => (
                <Pressable
                  key={n.id}
                  onPress={() => open(n)}
                  accessibilityRole="button"
                  accessibilityLabel={`${n.readAt ? '' : 'Chưa đọc. '}${n.title}. ${n.body}`}
                  style={{ flexDirection: 'row', gap: 12, padding: 16, borderTopWidth: 1, borderTopColor: '#EEF2F1', backgroundColor: n.readAt ? colors.card : '#F3FAF8' }}
                >
                  <View style={{ width: 40, height: 40, borderRadius: 10, backgroundColor: colors.soft, alignItems: 'center', justifyContent: 'center' }}>
                    <Icon name={ICON[n.type] ?? 'bell'} color={colors.brand} />
                  </View>
                  <View style={{ flex: 1, gap: 3 }}>
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 8 }}>
                      <T weight={n.readAt ? 500 : 700} style={{ flex: 1 }}>
                        {n.title}
                      </T>
                      <T size={12} color={colors.muted}>
                        {vnDate(n.createdAt) === today ? time(n.createdAt) : dm(vnDate(n.createdAt))}
                      </T>
                    </View>
                    <T size={13} color={colors.muted}>
                      {n.body}
                    </T>
                  </View>
                  {n.readAt ? null : <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: colors.brand, marginTop: 6 }} />}
                </Pressable>
              ))}
            </View>
          ))}
        </View>
      )}
    </Screen>
  );
}
