export interface Option { value: string; label: string }

export const FITNESS_LEVELS: Option[] = [
  { value: 'BEGINNER', label: 'Mới bắt đầu' },
  { value: 'INTERMEDIATE', label: 'Trung bình' },
  { value: 'ADVANCED', label: 'Đã tập lâu' },
];
export const GOALS: Option[] = [
  { value: 'STAY_ACTIVE', label: 'Duy trì vận động' },
  { value: 'LOSE_WEIGHT', label: 'Giảm cân' },
  { value: 'BUILD_MUSCLE', label: 'Tăng cơ' },
  { value: 'ENDURANCE', label: 'Tăng sức bền' },
];
export const EXERCISE_GOALS: Option[] = [...GOALS, { value: 'RECOVERY', label: 'Phục hồi' }];
export const LOCATIONS: Option[] = [
  { value: 'HOME', label: 'Ở nhà' },
  { value: 'GYM', label: 'Phòng tập' },
  { value: 'OUTDOOR', label: 'Ngoài trời' },
];
export const EXERCISE_LOCATIONS: Option[] = [...LOCATIONS, { value: 'ANY', label: 'Linh hoạt' }];
export const INTENSITIES: Option[] = [
  { value: 'LOW', label: 'Nhẹ' },
  { value: 'MEDIUM', label: 'Vừa' },
  { value: 'HIGH', label: 'Nặng' },
];
export const MAX_EXERCISE_INTENSITIES: Option[] = [
  { value: 'LOW', label: 'Nhẹ – chỉ vận động nhẹ nhàng' },
  { value: 'MEDIUM', label: 'Vừa – có thể tập mức trung bình' },
  { value: 'HIGH', label: 'Nặng – chấp nhận cường độ cao' },
];
export const MISSED_REASONS: Option[] = [
  { value: 'NO_TIME', label: 'Không có thời gian' },
  { value: 'TOO_TIRED', label: 'Quá mệt' },
  { value: 'TOO_DIFFICULT', label: 'Bài tập quá khó' },
  { value: 'DISLIKE', label: 'Không thích bài tập' },
  { value: 'UNEXPECTED_WORK', label: 'Có việc đột xuất' },
  { value: 'OTHER', label: 'Lý do khác' },
];
export const FEEDBACK_REASONS: Option[] = [
  ...MISSED_REASONS,
  { value: 'NO_EQUIPMENT', label: 'Thiếu dụng cụ' },
];
export const ABANDON_REASONS: Option[] = [
  { value: 'TOO_TIRED', label: 'Quá mệt' },
  { value: 'NO_EQUIPMENT', label: 'Thiếu dụng cụ' },
  { value: 'UNEXPECTED_WORK', label: 'Đột xuất' },
];

export function labelOf(options: Option[], value: string | null | undefined): string {
  if (!value) return '-';
  return options.find((o) => o.value === value)?.label ?? value;
}

export const STATUS_LABEL: Record<string, string> = {
  STARTED: 'Đang tập',
  COMPLETED: 'Đã hoàn thành',
  ABANDONED: 'Đã huỷ',
  INVALID_WORKOUT: 'Chưa đủ thời lượng',
};

export const STRATEGY_LABEL: Record<string, string> = {
  DONE: 'Đã tập xong',
  NORMAL: 'Giữ kế hoạch',
  REDUCE: 'Giảm tải',
  RECOVERY: 'Phục hồi',
  RESTART: 'Khởi động lại',
  CHALLENGE: 'Tăng thử thách',
};

export const RISK_LABEL: Record<string, string> = { LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao' };

export const EQUIPMENT_OPTIONS: Option[] = [
  { value: 'yoga_mat', label: 'Thảm tập' },
  { value: 'jump_rope', label: 'Dây nhảy' },
  { value: 'bicycle', label: 'Xe đạp' },
  { value: 'stable_step', label: 'Bậc hoặc ghế/bàn chắc chắn' },
];

export const BREAKDOWN_LABEL: Record<string, string> = {
  time: 'Thời gian', energy: 'Năng lượng', goal: 'Mục tiêu',
  difficulty: 'Độ khó', preference: 'Nơi tập và sở thích', recovery: 'Phục hồi',
};

/** Tinh trang thuc hien cua mot goi y (nguoi dung tu bao cao). */
export const RECOMMENDATION_STATUSES: Option[] = [
  { value: 'COMPLETED', label: 'Đã hoàn thành gợi ý' },
  { value: 'PARTIAL', label: 'Đã thực hiện một phần' },
  { value: 'NOT_DONE', label: 'Chưa thực hiện' },
  { value: 'NOT_SUITABLE', label: 'Gợi ý không phù hợp' },
];

export const RECOMMENDATION_REASONS: Option[] = [
  { value: 'NO_TIME', label: 'Thiếu thời gian' },
  { value: 'TOO_TIRED', label: 'Mệt mỏi' },
  { value: 'NO_EQUIPMENT', label: 'Không có dụng cụ' },
  { value: 'NOT_SUITABLE', label: 'Bài tập không phù hợp' },
  { value: 'OTHER', label: 'Lý do khác' },
];

/** Cau giai thich cu (da luu trong snapshot truoc khi doi cach noi) duoc hien bang loi dung su that. */
const LEGACY_REASONS: Record<string, string> = {
  'Không có dấu hiệu cần điều chỉnh': 'Chưa có quy tắc điều chỉnh nào được kích hoạt với trạng thái hôm nay',
  'Không có quy tắc nào cần điều chỉnh': 'Chưa có quy tắc điều chỉnh nào được kích hoạt với trạng thái hôm nay',
};
export const modernReason = (text: string): string => LEGACY_REASONS[text] ?? text;

/** Snapshot cu co the con ma ky thuat (HOME, MEDIUM...) trong cau; doi sang tieng Viet khi hien thi. */
export function prettyReason(text: string): string {
  return text
    .replace(/\bHOME\b/g, 'ở nhà').replace(/\bGYM\b/g, 'phòng tập').replace(/\bOUTDOOR\b/g, 'ngoài trời')
    .replace(/\bLOW\b/g, 'nhẹ').replace(/\bMEDIUM\b/g, 'vừa').replace(/\bHIGH\b/g, 'nặng');
}
