import NativeDiary from '../../platform/specs/NativeDiary';
import { HistoryReadError } from '../../platform/historyFailure';
import { parseDiaryHistoryPage } from '../../platform/diaryStatus';
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
  parseDiaryHistoryPage: jest.fn((raw: string) => JSON.parse(raw)),
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
    .mockRejectedValueOnce({ code: 'BUSY', message: 'private' })
    .mockResolvedValue('{"retry":true}');
  await expect(readHistory(['', '', '', 7, 7])).rejects.toMatchObject({
    code: 'native_busy',
  });
  expect(cachedHistory(['', '', '', 7, 7])).toBeNull();
  await expect(readHistory(['', '', '', 7, 7])).resolves.toEqual({
    retry: true,
  });
});

test('invalidated pending read cannot delete a replacement request for the same page', async () => {
  let resolveOld!: (raw: string) => void;
  let resolveNew!: (raw: string) => void;
  native
    .mockImplementationOnce(
      () =>
        new Promise(done => {
          resolveOld = done;
        }),
    )
    .mockImplementationOnce(
      () =>
        new Promise(done => {
          resolveNew = done;
        }),
    );
  const args = ['', '', '', 7, 7] as [string, string, string, 7, 7];
  const old = readHistory(args);
  clearHistoryCache();
  const replacement = readHistory(args);
  resolveOld('{"old":true}');
  await expect(old).rejects.toMatchObject({ code: 'invalidated' });
  expect(readHistory(args)).toBe(replacement);
  resolveNew('{"new":true}');
  await replacement;
  expect(cachedHistory(args)).toEqual({ new: true });
  expect(native).toHaveBeenCalledTimes(2);
});

test('validation failures are not recategorized as native and retain only same-page stale data', async () => {
  native.mockResolvedValue('{"retained":true}');
  const args = ['', '', '', 30, 0] as [string, string, string, 30, 0];
  await readHistory(args);
  jest
    .mocked(parseDiaryHistoryPage)
    .mockImplementationOnce(
      jest.requireActual('../../platform/diaryStatus').parseDiaryHistoryPage,
    );
  native.mockResolvedValue('malformed');
  await expect(readHistory(args)).rejects.toMatchObject({ code: 'json' });
  expect(cachedHistory(args)).toEqual({ retained: true });
  expect(cachedHistory(['', '', '2026-10-01', 30, 0])).toBeNull();
  native.mockRejectedValueOnce({ code: 'INTERRUPTED', message: 'private' });
  await expect(readHistory(args)).rejects.toBeInstanceOf(HistoryReadError);
  native.mockResolvedValue('{"fresh":true}');
  await expect(readHistory(args)).resolves.toEqual({ fresh: true });
});
