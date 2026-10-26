// Shared semantic palette for the preview and its contrast verification.
export default {
  light: {
    canvas:'#F6F5EF', surface:'#FFFEF9', surfaceAlt:'#ECEFE6',
    text:'#243C32', muted:'#56645B', accent:'#305B48',
    onAccent:'#FFFFFF', tint:'#E0EADB', border:'#79867B',
    lavender:'#EAE5F0', onLavender:'#554769',
    caution:'#80501D', cautionSurface:'#F6EAD5',
    danger:'#A03431', focus:'#7051A2',
  },
  dark: {
    canvas:'#17231F', surface:'#202F29', surfaceAlt:'#2B3C32',
    text:'#F2F1E8', muted:'#BAC8BD', accent:'#BED6AF',
    onAccent:'#203326', tint:'#314635', border:'#8B9D90',
    lavender:'#383342', onLavender:'#D9CAEE',
    caution:'#EDCA91', cautionSurface:'#423625',
    danger:'#FFBCB2', focus:'#CAB3EB',
  },
  motionMs:{ feedback:120, reveal:180, navigation:220 },
  spacingDp:[4,8,12,16,24,32,48],
  minTargetDp:48,
};
