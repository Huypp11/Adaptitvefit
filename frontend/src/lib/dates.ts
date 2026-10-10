export function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function addDays(d: Date, n: number): Date {
  const c = new Date(d);
  c.setDate(c.getDate() + n);
  return c;
}

const WEEKDAYS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
export const weekdayLabel = (d: Date) => WEEKDAYS[d.getDay()];

/** Backend tra LocalDateTime khong co mui gio; lay phan ngay theo gio may chu. */
export const datePart = (iso: string) => iso.slice(0, 10);

export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

export function formatDateLong(d: Date): string {
  return d.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long' });
}

export function greeting(d: Date): string {
  const h = d.getHours();
  if (h < 11) return 'Chào buổi sáng';
  if (h < 14) return 'Chào buổi trưa';
  if (h < 18) return 'Chào buổi chiều';
  return 'Chào buổi tối';
}
