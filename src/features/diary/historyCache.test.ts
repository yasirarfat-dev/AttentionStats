import NativeDiary from '../../platform/specs/NativeDiary';
import {
  cachedHistory,
  clearHistoryCache,
  readHistory,
  warmHistory,
} from './historyCache';
jest.mock('../../platform/specs/NativeDiary', () => ({
  __esModule: true,
  default: { getHistoryRange: jest.fn() },
}));
jest.mock('../../platform/diaryStatus', () => ({
  parseDiaryHistoryPage: (raw: string) => JSON.parse(raw),
}));
const native = NativeDiary!.getHistoryRange as jest.Mock;
beforeEach(() => {
  clearHistoryCache();
  native.mockReset();
});
test('launch warms all three distinct scopes and concurrent reads share one request', async () => {
  native.mockResolvedValue('{"fixture":true}');
  await warmHistory();
  expect(native.mock.calls).toEqual([
    ['', '', '', 7, 7],
    ['', '', '', 30, 30],
    ['', '', '', 30, 0],
  ]);
  expect(cachedHistory(['', '', '', 30, 0])).toEqual({ fixture: true });
  native.mockClear();
  await Promise.all([
    readHistory(['a', 'b', '', 7, 7]),
    readHistory(['a', 'b', '', 7, 7]),
  ]);
  expect(native).toHaveBeenCalledTimes(1);
});
test('invalidation rejects late pre-erase data and never repopulates the cache', async () => {
  let resolve!: (value: string) => void;
  native.mockImplementation(
    () =>
      new Promise<string>(done => {
        resolve = done;
      }),
  );
  const read = readHistory(['', '', '', 7, 7]);
  clearHistoryCache();
  resolve('{"oldHistory":true}');
  await expect(read).rejects.toThrow('History invalidated');
  expect(cachedHistory(['', '', '', 7, 7])).toBeNull();
});
test('failed warmup is retryable and does not cache failure as empty history', async () => {
  native
    .mockRejectedValueOnce(new Error('Busy'))
    .mockResolvedValue('{"retry":true}');
  await expect(readHistory(['', '', '', 7, 7])).rejects.toThrow('Busy');
  expect(cachedHistory(['', '', '', 7, 7])).toBeNull();
  await expect(readHistory(['', '', '', 7, 7])).resolves.toEqual({
    retry: true,
  });
});
