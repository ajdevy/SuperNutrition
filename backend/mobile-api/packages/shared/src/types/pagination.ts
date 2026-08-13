/** Cursor pagination (docs/06 §6.4). Offset pagination drifts on insert. */
export interface PageRequest {
  readonly limit: number;
  readonly cursor?: string;
}

export interface PageInfo {
  readonly nextCursor: string | null;
  readonly hasMore: boolean;
}

export interface Page<T> {
  readonly data: readonly T[];
  readonly page: PageInfo;
}
