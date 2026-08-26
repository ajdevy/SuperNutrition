export interface CoachMessageResponse {
  readonly type: string;
  readonly messageKey: string;
  readonly params: Readonly<Record<string, unknown>>;
  readonly createdAt: string;
}
