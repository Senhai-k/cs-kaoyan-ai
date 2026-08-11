export const PROVINCE_OPTIONS = [
  '北京', '天津', '河北', '山西', '内蒙古',
  '辽宁', '吉林', '黑龙江',
  '上海', '江苏', '浙江', '安徽', '福建', '江西', '山东',
  '河南', '湖北', '湖南',
  '广东', '广西', '海南',
  '重庆', '四川', '贵州', '云南', '西藏',
  '陕西', '甘肃', '青海', '宁夏', '新疆'
] as const;

export type Province = typeof PROVINCE_OPTIONS[number];

const PROVINCE_SET = new Set<string>(PROVINCE_OPTIONS);

export function isProvince(value: unknown): value is Province {
  return typeof value === 'string' && PROVINCE_SET.has(value);
}

export function provinceToRegion(province: string): string {
  if (['北京', '天津', '河北', '山西', '内蒙古'].includes(province)) return '华北';
  if (['辽宁', '吉林', '黑龙江'].includes(province)) return '东北';
  if (['上海', '江苏', '浙江', '安徽', '福建', '江西', '山东'].includes(province)) return '华东';
  if (['河南', '湖北', '湖南'].includes(province)) return '华中';
  if (['广东', '广西', '海南'].includes(province)) return '华南';
  if (['重庆', '四川', '贵州', '云南', '西藏'].includes(province)) return '西南';
  if (['陕西', '甘肃', '青海', '宁夏', '新疆'].includes(province)) return '西北';
  return '';
}
