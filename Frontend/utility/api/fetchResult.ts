/** Discriminated result for client fetch modules (not an HTTP status wrapper). */
export type FetchResult<T> = { kind: "ok"; data: T } | { kind: "error" };
