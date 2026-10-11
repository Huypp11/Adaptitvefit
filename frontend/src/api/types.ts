export type Role = 'USER' | 'ADMIN';

export interface AuthResponse { token: string; email: string; role: Role }

export interface Profile {
  age: number; heightCm: number; weightKg: number; fitnessLevel: string; goal: string;
  availableMinutes: number; preferredWorkout: string | null; location: string; equipment: string | null;
}

export interface Checkin {
  id: number; checkinDate: string; sleepHours: number; stress: number; energy: number; mood: number;
  freeTimeMin: number; motivation: number; exercisedToday: boolean; durationMin: number | null;
  difficulty: number | null; missedReason: string | null;
}

export interface CheckinInput {
  sleepHours: number; stress: number; energy: number; mood: number; freeTimeMin: number;
  motivation: number; exercisedToday: boolean; durationMin?: number; difficulty?: number; missedReason?: string;
}

export interface Exercise {
  id: number; name: string; description: string | null; durationMin: number; durationMax: number;
  difficulty: number; intensity: string; equipment: string | null; location: string;
  muscleGroup: string | null; goal: string | null; energyRequirement: number; recoverySuitable: boolean;
  met: number | null; steps: string[]; media: string[]; tips: string | null; instructions: string | null;
  active: boolean; tags: string[];
}

export interface ExerciseInput {
  name: string; description?: string; durationMin: number; durationMax: number; difficulty: number;
  intensity: string; equipment?: string; location: string; muscleGroup?: string; goal?: string;
  energyRequirement: number; recoverySuitable: boolean; met?: number; steps: string[]; media: string[];
  tips?: string; instructions?: string; active: boolean; tags: string[];
}

export interface PageResponse<T> {
  content: T[]; page: number; size: number; totalElements: number; totalPages: number;
}

export type WorkoutStatus = 'STARTED' | 'COMPLETED' | 'ABANDONED';

export interface WorkoutItem {
  exerciseId: number; name: string; orderNo: number; plannedMin: number;
  intensity: string; instructions: string | null; met: number | null;
  steps: string[]; media: string[]; tips: string | null;
}

export interface Workout {
  id: number; status: WorkoutStatus; interventionCode: string | null;
  startedAt: string; finishedAt: string | null; exercises: WorkoutItem[];
}

export type WorkoutSummary = Omit<Workout, 'exercises'>;

export interface FeedbackInput {
  sessionId: number; completed: boolean; difficulty?: number; fatigue?: number;
  satisfaction?: number; liked?: boolean; notCompletedReason?: string;
}

export interface Breakdown {
  time: number; energy: number; goal: number; difficulty: number; preference: number; recovery: number;
}
export interface ScoredExercise {
  exerciseId: number; name: string; plannedMin: number; score: number; breakdown: Breakdown; reasons: string[];
}
export interface PlanItem { exerciseId: number; name: string; plannedMin: number }
export interface RecommendationResult {
  budgetMinutes: number; items: ScoredExercise[]; plan: PlanItem[]; excluded: Record<string, number>; message: string | null;
}
export interface Recommendation {
  strategy: { code: string; description: string | null; reasons: string[] };
  state: {
    sleepHours: number; stress: number; energy: number; freeTimeMin: number; missedDays: number;
    workoutFrequency7d: number; riskLevel: string; riskSource: string;
  };
  recommendation: RecommendationResult;
}

export interface Intervention {
  id: number; code: string; priority: number; description: string | null; active: boolean;
  conditionExpr: string | null; paramsExpr: string | null;
}
export interface InterventionInput {
  priority: number; conditionExpr: string; paramsExpr?: string; description?: string; active: boolean;
}
