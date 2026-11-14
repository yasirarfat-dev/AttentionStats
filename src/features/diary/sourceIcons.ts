import { reelsSources, type ReelsSourceKey } from '../../platform';

/** Source rows can combine editions; package-history rows always keep their exact package icon. */
export function iconPackages(packageName: string): string[] {
  return packageName === reelsSources.tiktok.packageName
    ? [packageName, 'com.ss.android.ugc.trill', 'com.zhiliaoapp.musically.go']
    : [packageName];
}

export function sourceIcon(
  icons: Record<string, string>,
  key: ReelsSourceKey,
): string | undefined {
  return iconPackages(reelsSources[key].packageName)
    .map(pkg => icons[pkg])
    .find(Boolean);
}
