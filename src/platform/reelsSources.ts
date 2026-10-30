/** Supported short-video sources. Order is display order. */
export const reelsSources = {
  instagram: {
    name: 'Instagram',
    observation: 'Instagram Reels',
    mark: 'IG',
    packageName: 'com.instagram.android',
    color: '#E1306C',
  },
  facebook: {
    name: 'Facebook',
    observation: 'Facebook Reels',
    mark: 'f',
    packageName: 'com.facebook.katana',
    color: '#1877F2',
  },
  youtube: {
    name: 'YouTube',
    observation: 'YouTube Shorts',
    mark: 'YT',
    packageName: 'com.google.android.youtube',
    color: '#FF0033',
  },
  tiktok: {
    name: 'TikTok',
    observation: 'TikTok videos',
    mark: 'TT',
    packageName: 'com.zhiliaoapp.musically',
    color: '#25F4EE',
  },
  snapchat: {
    name: 'Snapchat',
    observation: 'Snapchat Spotlight',
    mark: 'SC',
    packageName: 'com.snapchat.android',
    color: '#FFFC00',
  },
} as const;

export const reelsSourceKeys = [
  'instagram',
  'facebook',
  'youtube',
  'tiktok',
  'snapchat',
] as const;

export type ReelsSourceKey = (typeof reelsSourceKeys)[number];

/** Disclosure revision the native layer requires before any source may be observed. */
export const NOTICE_REVISION = 6;
