export interface AchievementResponse {
  readonly key: string;
  readonly name: string;
  readonly description: string;
  readonly xpReward: number;
  readonly unlockedAt: string | null;
  readonly progress: number;
}
