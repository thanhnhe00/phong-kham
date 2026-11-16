import type { OrderItem, Performance } from './types';

/** Giai đoạn của một mục chỉ định cận lâm sàng */
export type Stage = 'todo' | 'entering' | 'returned' | 'review' | 'done' | 'refused';

/** Lần thực hiện còn hiệu lực gần nhất */
export const lastPerformance = (item: OrderItem): Performance | undefined =>
  [...item.performances].filter((p) => p.status === 'DONE').sort((a, b) => a.performedAt.localeCompare(b.performedAt)).pop();

export function stageOf(item: OrderItem): Stage {
  if (item.status === 'REFUSED' || item.status === 'CANCELLED') return 'refused';
  const p = lastPerformance(item);
  if (!p) return 'todo';
  const r = p.result;
  if (!r) return 'entering';
  if (r.status === 'DRAFT') return r.returnNote ? 'returned' : 'entering';
  if (r.status === 'SUBMITTED') return 'review';
  return 'done';
}

export const stageLabel: Record<Stage, string> = {
  todo: 'Chờ làm', entering: 'Đang nhập kết quả', returned: 'Bị trả lại', review: 'Chờ bác sĩ duyệt', done: 'Đã duyệt', refused: 'Không làm',
};
