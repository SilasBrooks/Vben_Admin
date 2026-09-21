import { $t } from '#/locales';

/**
 * 聊天场景相对时间格式化：
 * < 1 分钟 → 刚刚；< 1 小时 → N 分钟前；今天内更早 → N 小时前；
 * 昨天/前天 → 「昨天 HH:mm」「前天 HH:mm」；
 * 其他 → 具体日期（同年 MM-DD HH:mm，跨年 YYYY-MM-DD HH:mm）。
 * 入参为后端 LocalDateTime 字符串（无时区，按本地时间解析）。
 */
export function formatRelativeTime(value: string): string {
  if (!value) return '';
  const date = new Date(value);
  const time = date.getTime();
  if (Number.isNaN(time)) {
    return value.split('.')[0]?.replace('T', ' ') ?? value;
  }

  const minute = 60_000;
  const hour = 3_600_000;
  const day = 86_400_000;
  const diff = Date.now() - time;
  const pad = (n: number) => String(n).padStart(2, '0');
  const hm = `${pad(date.getHours())}:${pad(date.getMinutes())}`;

  if (diff < minute) {
    return $t('im.time.justNow');
  }
  if (diff < hour) {
    return $t('im.time.minutesAgo', [Math.floor(diff / minute)]);
  }

  // 以本地自然日 0 点为界判断今天/昨天/前天
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const todayStart = startOfToday.getTime();
  if (time >= todayStart) {
    return $t('im.time.hoursAgo', [Math.floor(diff / hour)]);
  }
  if (time >= todayStart - day) {
    return `${$t('im.time.yesterday')} ${hm}`;
  }
  if (time >= todayStart - 2 * day) {
    return `${$t('im.time.dayBeforeYesterday')} ${hm}`;
  }

  const sameYear = date.getFullYear() === new Date().getFullYear();
  const md = `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return sameYear ? `${md} ${hm}` : `${date.getFullYear()}-${md} ${hm}`;
}
