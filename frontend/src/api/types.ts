export type Role = 'USER' | 'ADMIN';

export interface AuthResponse { token: string; email: string; role: Role }

export interface Profile {
  age: number; heightCm: number; weightKg: number; fitnessLevel: string; goal: string;
  availableMinutes: number; preferredWorkout: string | null; location: string; equipment: string | null;
  maxExerciseIntensity: 'LOW' | 'MEDIUM' | 'HIGH';
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

export type WorkoutStatus = 'STARTED' | 'COMPLETED' | 'ABANDONED' | 'INVALID_WORKOUT';

export interface WorkoutItem {
  exerciseId: number; name: string; orderNo: number; plannedMin: number;
  intensity: string; instructions: string | null; met: number | null;
  steps: string[]; media: string[]; tips: string | null;
}

export interface Workout {
  id: number; status: WorkoutStatus; interventionCode: string | null;
  startedAt: string; finishedAt: string | null; exercises: WorkoutItem[]; notCompletedReason: string | null;
}

export type WorkoutSummary = Omit<Workout, 'exercises' | 'notCompletedReason'>;

export interface FeedbackInput {
  sessionId: number; completed: boolean; difficulty?: number; fatigue?: number;
  satisfaction?: number; liked?: boolean; notCompletedReason?: string;
}
export type RecommendationFeedbackStatus = 'COMPLETED' | 'PARTIAL' | 'NOT_DONE' | 'NOT_SUITABLE';
export interface RecommendationFeedbackInput {
  status: RecommendationFeedbackStatus;
  suitabilityRating?: number;
  difficultyRating?: number;
  reason?: string;
  comment?: string;
}
export interface RecommendationFeedback {
  id: number;
  recommendationId: number;
  status: RecommendationFeedbackStatus;
  suitabilityRating: number | null;
  difficultyRating: number | null;
  reason: string | null;
  comment: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface FeedbackRecord {
  id: number;
  sessionId: number | null;
  completed: boolean | null;
  difficulty: number | null;
  fatigue: number | null;
  satisfaction: number | null;
  liked: boolean | null;
  notCompletedReason: string | null;
  createdAt: string;
  updatedAt: string;
  recommendationId: number | null;
  recommendationStatus: RecommendationFeedbackStatus | null;
  suitabilityRating: number | null;
  recommendationDifficulty: number | null;
  recommendationReason: string | null;
  recommendationComment: string | null;
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
export interface RecommendationState {
  sleepHours: number; stress: number; energy: number; freeTimeMin: number; missedDays: number;
  workoutFrequency7d: number; riskLevel: string; riskSource: string;
  exercisedToday: boolean; exercisedMinutesToday: number | null;
}
export interface Recommendation {
  recommendationId: number | null;
  trackingId: string;
  mode: 'CHECKIN' | 'PROFILE_FALLBACK';
  interactionStatus: 'GENERATED' | 'STARTED' | 'COMPLETED' | 'ABANDONED' | 'INVALID_WORKOUT';
  feedback: RecommendationFeedback | null;
  createdAt: string;
  strategy: { code: string; description: string | null; reasons: string[] };
  state: RecommendationState | null;
  risk: RiskInfo | null;
  recommendation: RecommendationResult;
}

export interface RecommendationDataConsent {
  trackingEnabled: boolean;
  policyVersion: string;
  changedAt: string | null;
  storedEventCount: number;
}

export type RecommendationEventType = 'DISPLAYED' | 'SKIPPED';
export interface RecommendationEventInput {
  eventType: RecommendationEventType;
  recommendationId: number | null;
  trackingId: string;
  idempotencyKey: string;
  recommendationMode: Recommendation['mode'];
}

export interface RecommendationAnalytics {
  windowDays: number;
  usersWithCurrentConsent: number;
  displayedCheckinJourneys: number;
  displayedFallbackJourneys: number;
  matureJourneys: number;
  completedValidWithin24Hours: number;
  completionRateWithin24Hours: number | null;
  matureCheckinJourneys: number;
  completedCheckinWithin24Hours: number;
  completionRateCheckinWithin24Hours: number | null;
  matureFallbackJourneys: number;
  completedFallbackWithin24Hours: number;
  completionRateFallbackWithin24Hours: number | null;
  skippedJourneys: number;
  startedJourneys: number;
  abandonedJourneys: number;
  invalidWorkoutJourneys: number;
  journeysWithoutFeedback: number;
  journeysStillWithinObservationWindow: number;
  lifecycleEventsWithoutStart: number;
  journeysWithoutCompletionEvent: number;
  note: string;
}

export interface Intervention {
  id: number; code: string; priority: number; description: string | null; active: boolean;
  conditionExpr: string | null; paramsExpr: string | null;
}
export interface InterventionInput {
  priority: number; conditionExpr: string; paramsExpr?: string; description?: string; active: boolean;
}

export interface RiskFactor {
  feature: string; label: string; value: number; reference: number;
  direction: 'increase' | 'decrease'; impactPp: number; text: string;
}
export interface RiskInfo {
  source: 'model' | 'history_rule'; predictionId: number | null; riskScore: number | null; riskLevel: string;
  baseRisk: number | null; modelVersion: string | null; algorithm: string | null; demoModel: boolean;
  summary: string | null; factors: RiskFactor[]; otherImpactPp: number | null; notes: string[];
}
export interface RiskPoint { date: string; riskScore: number; riskLevel: string }
