/** 把 Date 格式化为 yyyy-MM-dd */
export function fmtDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * 计算"第 N 周"对应的日历周周一（周一为一周起点）。
 *
 * 无论开学日落在星期几，都先把锚点归位到它所在日历周的周一，
 * 再据此推出宿舍(周日~周四)、办公室(周一~周五)的固定排班区间，
 * 保证排班日期不随开学日调整而变化。
 *
 * 例：9 月 1 日（周二）开学，第 1 周周一 = 8 月 31 日，
 *     宿舍范围 = 8/30(周日) ~ 9/3(周四)，包含开学前一天的周一晚班。
 */
export function getWeekMonday(semesterStart: string, week: number): Date {
  const base = new Date(semesterStart + 'T00:00:00')
  const anchor = new Date(base)
  anchor.setDate(anchor.getDate() + (week - 1) * 7)
  // getDay(): 0=周日 ... 6=周六；(dow+6)%7 即"离上周一过去几天"
  anchor.setDate(anchor.getDate() - ((anchor.getDay() + 6) % 7))
  return anchor
}

/**
 * 宿舍排班周范围（晚班：学生前一晚返校，一直住到周四晚）。
 *
 * - 开学周（第 1 周）：从【开学前一天】排到【该周周四】。
 *   例：9/1 周二开学 → 8/31(周一)~9/3(周四)，8/30(周日)不排；
 *       若周日开学 → 前一天是周六，则周六~周四。
 * - 第 2 周起：恢复【周日~周四】，从第 1 周周四起每周往后推，保证周区间不重叠。
 */
export function getDormitoryWeekRange(semesterStart: string, week: number): { start: string; end: string } {
  const base = new Date(semesterStart + 'T00:00:00')
  // 开学周首个周四：开学日当天或之后最近的周四
  const firstThu = new Date(base)
  firstThu.setDate(firstThu.getDate() + ((4 - base.getDay() + 7) % 7))

  if (week <= 1) {
    const start = new Date(base)
    start.setDate(start.getDate() - 1) // 开学前一天
    return { start: fmtDate(start), end: fmtDate(firstThu) }
  }

  // 第 2 周起：周日 = 第 1 周周四 + (N-2)*7 + 3；周四 = 第 1 周周四 + (N-2)*7 + 7
  const offset = (week - 2) * 7
  const start = new Date(firstThu)
  start.setDate(start.getDate() + offset + 3)
  const end = new Date(firstThu)
  end.setDate(end.getDate() + offset + 7)
  return { start: fmtDate(start), end: fmtDate(end) }
}

/** 办公室排班周范围：周一 ~ 周五 */
export function getOfficeWeekRange(semesterStart: string, week: number): { start: string; end: string } {
  const monday = getWeekMonday(semesterStart, week)
  const end = new Date(monday)
  end.setDate(end.getDate() + 4)
  return { start: fmtDate(monday), end: fmtDate(end) }
}
