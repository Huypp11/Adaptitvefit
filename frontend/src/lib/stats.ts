import type { WorkoutSummary } from '../api/types';
import { addDays, datePart, toISODate } from './dates';

export interface WorkoutStats {
  completed: number;
  abandoned: number;
  completionRate: number | null;
  completedLast7Days: number;
  totalMinutes: number;
  streak: number;
}

/** Thong ke mo ta tu lich su workout that. Khong suy dien nguyen nhan. */
export function computeWorkoutStats(items: WorkoutSummary[], today = new Date()): WorkoutStats {
  const completedItems = items.filter((w) => w.status === 'COMPLETED');
  const abandoned = items.filter((w) => w.status === 'ABANDONED').length;
  const finished = completedItems.length + abandoned;

  const since = toISODate(addDays(today, -6));
  const completedLast7Days = completedItems.filter(
    (w) => w.finishedAt && datePart(w.finishedAt) >= since,
  ).length;

  const totalMinutes = completedItems.reduce((sum, w) => {
    if (!w.finishedAt) return sum;
    const ms = new Date(w.finishedAt).getTime() - new Date(w.startedAt).getTime();
    return sum + Math.max(0, Math.round(ms / 60000));
  }, 0);

  const days = new Set(completedItems.filter((w) => w.finishedAt).map((w) => datePart(w.finishedAt!)));
  let cursor = days.has(toISODate(today)) ? today : addDays(today, -1);
  let streak = 0;
  while (days.has(toISODate(cursor))) {
    streak += 1;
    cursor = addDays(cursor, -1);
  }

  return {
    completed: completedItems.length,
    abandoned,
    completionRate: finished === 0 ? null : Math.round((completedItems.length / finished) * 100),
    completedLast7Days,
    totalMinutes,
    streak,
  };
}
