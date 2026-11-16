import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import { useToast } from '../components/ui';
import { api, errorText } from './api';
import { vnDate } from './format';
import type { QueueView, Session } from './types';

/** Màn hình trong ngày làm mới định kỳ để số thứ tự, trạng thái luôn đúng */
export const LIVE_MS = 15_000;
// shortcut: hỏi lại định kỳ thay vì đẩy thời gian thực; khi backend có WebSocket/SSE thì bỏ hỏi lại.

export const useGet = <T>(path: string | null, live = false) =>
  useQuery({ queryKey: [path], queryFn: () => api.get<T>(path!), enabled: !!path, refetchInterval: live ? LIVE_MS : false });

/**
 * Chạy một thao tác ghi: khóa nút trong lúc chạy, báo lỗi bằng thông báo nhỏ, xong thì làm mới mọi dữ liệu.
 * Trả về true nếu thành công.
 */
export function useRun() {
  const qc = useQueryClient();
  const toast = useToast();
  const [busy, setBusy] = useState<string | null>(null);
  const run = useCallback(async (key: string, fn: () => Promise<unknown>, ok?: string) => {
    setBusy(key);
    try {
      await fn();
      await qc.invalidateQueries();
      if (ok) toast(ok);
      return true;
    } catch (e) {
      toast(errorText(e), true);
      return false;
    } finally {
      setBusy(null);
    }
  }, [qc, toast]);
  return { run, busy };
}

/** Các phiên khám hôm nay kèm hàng đợi của từng phiên */
export function useTodayQueues(mine = false) {
  const today = vnDate();
  const sessions = useGet<Session[]>(`/schedule/sessions${mine ? '/mine' : ''}?from=${today}&to=${today}`, true);
  const list = (sessions.data ?? []).filter((s) => s.status !== 'CANCELLED');
  const queues = useQuery({
    queryKey: ['queues', list.map((s) => s.id).join(',')],
    queryFn: () => Promise.all(list.map((s) => api.get<QueueView>(`/queue/sessions/${s.id}`))),
    enabled: list.length > 0,
    refetchInterval: LIVE_MS,
  });
  return {
    sessions: list,
    queues: queues.data ?? [],
    loading: sessions.isLoading || (list.length > 0 && queues.isLoading),
    error: sessions.error ?? queues.error,
    refetch: () => { sessions.refetch(); queues.refetch(); },
  };
}
