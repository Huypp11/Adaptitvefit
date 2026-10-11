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
export const MISSED_REASONS: Option[] = [
  { value: 'NO_TIME', label: 'Không có thời gian' },
  { value: 'TOO_TIRED', label: 'Quá mệt' },
  { value: 'TOO_DIFFICULT', label: 'Bài tập quá khó' },
  { value: 'DISLIKE', label: 'Không thích bài tập' },
  { value: 'UNEXPECTED_WORK', label: 'Có việc đột xuất' },
  { value: 'OTHER', label: 'Lý do khác' },
];

export function labelOf(options: Option[], value: string | null | undefined): string {
  if (!value) return '-';
  return options.find((o) => o.value === value)?.label ?? value;
}

export const STATUS_LABEL: Record<string, string> = {
  STARTED: 'Đang tập',
  COMPLETED: 'Đã hoàn thành',
  ABANDONED: 'Đã huỷ',
};

export const STRATEGY_LABEL: Record<string, string> = {
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
