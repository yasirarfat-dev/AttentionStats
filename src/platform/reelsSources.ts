export const reelsSources = {
  instagram: {
    name: 'Instagram',
    observation: 'Instagram Reels',
    mark: 'IG',
    noticeRevision: 5,
  },
  facebook: {
    name: 'Facebook',
    observation: 'Facebook Reels',
    mark: 'f',
    noticeRevision: 5,
  },
  youtube: {
    name: 'YouTube Shorts',
    observation: 'YouTube Shorts',
    mark: 'YT',
    noticeRevision: 3,
  },
  tiktok: {
    name: 'TikTok',
    observation: 'TikTok videos',
    mark: 'TT',
    noticeRevision: 4,
  },
} as const;

export const reelsSourceKeys = [
  'instagram',
  'facebook',
  'youtube',
  'tiktok',
] as const;
