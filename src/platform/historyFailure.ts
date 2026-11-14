export type HistoryFailureCode =
  | 'native_busy'
  | 'native_interrupted'
  | 'native_storage_unavailable'
  | 'native_invalid_argument'
  | 'native_unavailable'
  | 'native_unknown'
  | 'payload'
  | 'json'
  | 'validation'
  | 'invalidated'
  | 'unexpected';

/** Fixed tokens only: never retain a native rejection, payload, parser issue or cause. */
export class HistoryReadError extends Error {
  constructor(readonly code: HistoryFailureCode) {
    super(
      code === 'invalidated'
        ? 'History invalidated'
        : 'History page unavailable',
    );
    this.name = 'HistoryReadError';
  }
}

export function nativeHistoryFailure(error: unknown): HistoryReadError {
  let code: unknown;
  try {
    if (typeof error === 'object' && error !== null && 'code' in error)
      code = error.code;
  } catch {
    // Even an unexpected accessor cannot expose the original error.
  }
  switch (code) {
    case 'BUSY':
      return new HistoryReadError('native_busy');
    case 'INTERRUPTED':
      return new HistoryReadError('native_interrupted');
    case 'STORAGE_UNAVAILABLE':
      return new HistoryReadError('native_storage_unavailable');
    case 'INVALID_ARGUMENT':
      return new HistoryReadError('native_invalid_argument');
    default:
      return new HistoryReadError('native_unknown');
  }
}

export const historyFailureCode = (error: unknown): HistoryFailureCode =>
  error instanceof HistoryReadError ? error.code : 'unexpected';
