// Uoc tinh calo = MET x can nang (kg) x thoi gian (gio). Chi la uoc tinh, sai lech theo tung nguoi.
const FALLBACK_MET: Record<string, number> = { LOW: 2.5, MEDIUM: 4.5, HIGH: 7 };

export function metFor(met: number | null | undefined, intensity: string): number {
  return met ?? FALLBACK_MET[intensity] ?? 4;
}

export function estimateKcal(met: number, weightKg: number, minutes: number): number {
  return Math.round(met * weightKg * (minutes / 60));
}

export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, totalSeconds);
  const m = Math.floor(s / 60);
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}
