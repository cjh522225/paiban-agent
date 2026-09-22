<template>
  <div class="dormitory-management">
    <div class="control-section">
      <div class="control-card">
        <div class="control-row">
          <div class="control-item">
            <label class="control-label">宿舍楼</label>
            <el-select v-model="selectedDormitory" placeholder="选择宿舍楼" class="control-select" @change="loadScheduleData">
              <el-option v-for="dorm in dormitories" :key="dorm.value" :label="dorm.label" :value="dorm.value" />
            </el-select>
          </div>
          <div class="control-item">
            <label class="control-label">周次</label>
            <el-select v-model="selectedWeek" placeholder="选择周次" class="control-select" @change="loadScheduleData">
              <el-option v-for="week in weekOptions" :key="week.value" :label="week.label" :value="week.value" />
            </el-select>
          </div>
          <div class="control-actions">
            <el-button type="success" @click="handleAutoScheduleThis"><el-icon><MagicStick /></el-icon>生成本楼排班</el-button>
            <el-button type="primary" :loading="autoRunning" :disabled="autoRunning" @click="handleFullAutoSchedule"><el-icon><MagicStick /></el-icon>生成全体宿舍排班</el-button>
            <el-button type="warning" @click="handleExport"><el-icon><Download /></el-icon>导出 Excel</el-button>
            <el-button type="danger" @click="handleClearSchedule"><el-icon><Delete /></el-icon>清空当前排班</el-button>
            <el-button :type="swapMode ? 'danger' : 'info'" @click="toggleSwapMode">
              <el-icon><RefreshLeft /></el-icon>{{ swapMode ? '退出互换' : '互换模式' }}
            </el-button>
            <el-button type="info" @click="openPermDialog">身份权限</el-button>
          </div>
        <div v-if="swapMode" class="swap-bar">
          <span v-if="swapSelection.length === 0" class="swap-hint">互换模式：点击两个<strong>有人</strong>的格子，再点"执行互换"</span>
          <template v-else>
            <span>已选：{{ swapSelection.map(s => s.label).join(' ↔ ') }}</span>
            <el-button v-if="swapSelection.length === 2" type="primary" size="small" @click="executeSwap">执行互换</el-button>
            <el-button size="small" @click="swapSelection = []">清空</el-button>
          </template>
        </div>
        </div>
      </div>
    </div>

    <div v-if="holidayTips.length" class="adj-tips">
      <el-tag v-for="t in holidayTips" :key="t" type="warning" size="small" style="margin-right:8px">{{ t }}</el-tag>
    </div>

    <div class="schedule-card" style="position:relative">
      <div class="table-container" :class="{ 'swap-mode': swapMode }" v-loading="tableLoading" element-loading-text="加载中..." element-loading-background="rgba(255,255,255,0.7)">
        <table class="schedule-table">
          <thead>
            <tr>
              <th class="col-date">日期</th>
              <th class="col-patrol">巡班人员</th>
              <th class="col-duty">坐班人员</th>
              <th class="col-light">敲灯人员</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="!tableLoading && !scheduleData.length">
              <tr><td colspan="4" class="empty-msg">当周暂无值班安排</td></tr>
            </template>
            <tr v-for="(day, index) in scheduleData" :key="index" class="schedule-row">
              <td class="cell-date">
                <div class="date-content"><span class="date-week">{{ day.weekDay }}</span><span class="date-day">{{ day.monthDay }}</span></div>
              </td>
              <td class="cell-patrol">
                <div class="staff-cell" :class="{ 'swap-target': isSwapSelected(day.patrolId) }" @click="clickDayCell(day, 'patrol')">
                  <span>{{ day.patrol || '-' }}</span>
                  <template v-if="!swapMode">
                    <el-button v-if="day.patrol" type="danger" link size="small" @click="removeStaff(index, 'patrol')"><el-icon><Close /></el-icon></el-button>
                    <el-button v-else type="primary" link size="small" @click="openEditDialog(index, 'patrol')"><el-icon><Plus /></el-icon>添加</el-button>
                  </template>
                </div>
              </td>
              <td class="cell-duty">
                <div class="staff-cell" :class="{ 'swap-target': isSwapSelected(day.dutyId) }" @click="clickDayCell(day, 'duty')">
                  <span>{{ day.duty || '-' }}</span>
                  <template v-if="!swapMode">
                    <el-button v-if="day.duty" type="danger" link size="small" @click="removeStaff(index, 'duty')"><el-icon><Close /></el-icon></el-button>
                    <el-button v-else type="primary" link size="small" @click="openEditDialog(index, 'duty')"><el-icon><Plus /></el-icon>添加</el-button>
                  </template>
                </div>
              </td>
              <td class="cell-light">
                <div class="light-group">
                  <span v-if="day.light1" class="light-item" :class="{ 'swap-target': isSwapSelected(day.light1Id) }" @click="clickDayCell(day, 'light1')">{{ day.light1 }}<el-button v-if="!swapMode" type="danger" link size="small" @click="removeStaff(index, 'light1')"><el-icon><Close /></el-icon></el-button></span>
                  <span v-if="day.light2" class="light-item" :class="{ 'swap-target': isSwapSelected(day.light2Id) }" @click="clickDayCell(day, 'light2')">{{ day.light2 }}<el-button v-if="!swapMode" type="danger" link size="small" @click="removeStaff(index, 'light2')"><el-icon><Close /></el-icon></el-button></span>
                  <span v-if="!day.light1 && !day.light2 && !swapMode"><el-button type="primary" link size="small" @click="openEditDialog(index, 'light')"><el-icon><Plus /></el-icon>添加</el-button></span>
                  <span v-if="day.light1 && !day.light2 && !swapMode"><el-button type="primary" link size="small" @click="openEditDialog(index, 'light2')"><el-icon><Plus /></el-icon>+1</el-button></span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <el-dialog v-model="showEditDialog" title="添加值班人员" width="480px">
      <el-form label-width="90px">
        <el-form-item label="值班日期">
          <span>{{ scheduleData[editForm.dayIndex]?.weekDay }} {{ scheduleData[editForm.dayIndex]?.monthDay }}</span>
        </el-form-item>
        <el-form-item label="值班岗位">
          <el-tag :type="getRoleType(editForm.roleType)">{{ getRoleName(editForm.roleType) }}</el-tag>
        </el-form-item>
        <el-form-item label="选择人员">
          <el-select v-model="editForm.staffId" filterable placeholder="输入姓名搜索" style="width: 100%">
            <el-option v-for="s in editEligibleStaff" :key="s.id" :label="s.realName" :value="s.id">
              <span>{{ s.realName }} - {{ s.count }}次</span>
              <el-tag v-if="s.tag" type="success" size="small" style="margin-left: 6px">{{ s.tag }}</el-tag>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmEditStaff">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPermDialog" title="身份权限设置（宿舍值班身份可顶岗位）" width="540px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px">对角（自身岗位）固定授权、不可取消；勾选某格后，该值班身份的人员即可顶对应岗位。</el-alert>
      <table style="width:100%;border-collapse:collapse;text-align:center">
        <thead><tr><th style="padding:8px;border:1px solid #ebeef5">身份 \ 岗位</th><th style="padding:8px;border:1px solid #ebeef5">巡班</th><th style="padding:8px;border:1px solid #ebeef5">坐班</th><th style="padding:8px;border:1px solid #ebeef5">敲灯</th></tr></thead>
        <tbody>
          <tr v-for="row in permRows" :key="row.identity">
            <td style="padding:8px;border:1px solid #ebeef5">{{ row.identity }}</td>
            <td style="padding:8px;border:1px solid #ebeef5"><el-checkbox v-model="row.allowPatrol" :disabled="row.identity === '巡班'" /></td>
            <td style="padding:8px;border:1px solid #ebeef5"><el-checkbox v-model="row.allowDuty" :disabled="row.identity === '坐班'" /></td>
            <td style="padding:8px;border:1px solid #ebeef5"><el-checkbox v-model="row.allowKnock" :disabled="row.identity === '敲灯'" /></td>
          </tr>
        </tbody>
      </table>
      <template #footer>
        <el-button @click="showPermDialog = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="savePerm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Download, Plus, Close, RefreshLeft } from '@element-plus/icons-vue'
import { getDormitoryList } from '@/api/dormitory'
import { autoScheduleDormitory, getScheduleList, exportDormitoryExcel, addSchedule, deleteSchedule, batchDeleteSchedule, swapSchedules } from '@/api/schedule'
import request from '@/utils/request'
import { getDormitoryWeekRange } from '@/utils/date'

const dormitories = ref<any[]>([])

// ===== 身份权限设置（宿舍值班身份 → 可顶岗位 矩阵）=====
const showPermDialog = ref(false)
const permSaving = ref(false)
const IDENTITIES = ['巡班', '坐班', '敲灯']
const permRows = ref<{ identity: string; allowPatrol: boolean; allowDuty: boolean; allowKnock: boolean }[]>([])

async function openPermDialog() {
  try {
    const res = await request.get('/dorm-identity-permission')
    const list = (res.data || []) as any[]
    permRows.value = IDENTITIES.map((identity: string) => {
      const row = list.find((x: any) => x.identity === identity)
      return {
        identity,
        allowPatrol: !!(row && row.allowPatrol) || identity === '巡班',
        allowDuty: !!(row && row.allowDuty) || identity === '坐班',
        allowKnock: !!(row && row.allowKnock) || identity === '敲灯',
      }
    })
    showPermDialog.value = true
  } catch (e: any) { ElMessage.error(e.message || '加载身份权限失败') }
}
async function savePerm() {
  permSaving.value = true
  try {
    const payload = permRows.value.map((r) => ({
      identity: r.identity,
      allowPatrol: r.allowPatrol ? 1 : 0,
      allowDuty: r.allowDuty ? 1 : 0,
      allowKnock: r.allowKnock ? 1 : 0,
    }))
    const res = await request.put('/dorm-identity-permission', payload)
    if (res.code === 200) {
      ElMessage.success('身份权限已保存')
      showPermDialog.value = false
      await loadStaffCounts()
    }
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { permSaving.value = false }
}
const weekOptions = computed(() => {
  const total = semesterConfig.value?.totalWeeks || 18
  return Array.from({ length: total }, (_, i) => ({ label: `第${i + 1}周`, value: i + 1 }))
})
const selectedDormitory = ref('')
const selectedWeek = ref(1)

// ========== 手动互换模式 ==========
const swapMode = ref(false)
const swapSelection = ref<{ scheduleId: number; label: string }[]>([])

function toggleSwapMode() {
  swapMode.value = !swapMode.value
  swapSelection.value = []
}

function isSwapSelected(scheduleId: number) {
  return swapMode.value && swapSelection.value.some(s => s.scheduleId === scheduleId)
}

function clickDayCell(day: any, role: string) {
  if (!swapMode.value) return
  const idMap: Record<string, string> = { patrol: 'patrolId', duty: 'dutyId', light1: 'light1Id', light2: 'light2Id' }
  const nameMap: Record<string, string> = { patrol: 'patrol', duty: 'duty', light1: 'light1', light2: 'light2' }
  const roleLabelMap: Record<string, string> = { patrol: '巡班', duty: '坐班', light1: '敲灯', light2: '敲灯2' }
  const scheduleId = (day as any)[idMap[role]!]
  const name = (day as any)[nameMap[role]!]
  if (!scheduleId || !name) return
  const idx = swapSelection.value.findIndex(s => s.scheduleId === scheduleId)
  if (idx >= 0) { swapSelection.value.splice(idx, 1); return }
  if (swapSelection.value.length >= 2) { ElMessage.warning('最多选两个班次'); return }
  swapSelection.value.push({ scheduleId, label: `${day.weekDay} ${roleLabelMap[role]} ${name}` })
}

async function executeSwap() {
  if (swapSelection.value.length !== 2) { ElMessage.warning('请选择两个班次'); return }
  const [a, b] = swapSelection.value
  if (!a || !b) return
  const ok = await ElMessageBox.confirm(`确认互换？\n「${a.label}」 ↔ 「${b.label}」`, '执行互换', {
    type: 'warning', confirmButtonText: '确认互换', cancelButtonText: '取消',
  }).catch(() => false)
  if (!ok) return
  // 失败（如身份/性别不符）时保留选中格子，方便管理员重选
  const r = await swapSchedules({ scheduleIdA: a.scheduleId, scheduleIdB: b.scheduleId, weekNumber: selectedWeek.value }).catch(() => null)
  if (r && r.code === 200) {
    ElMessage.success('互换成功')
    swapSelection.value = []
    swapMode.value = false
    await loadScheduleData()
  }
}
const showEditDialog = ref(false)
const scheduleData = ref<any[]>([])
const staffWithCounts = ref<any[]>([])

const editForm = ref({ dayIndex: 0, roleType: 'patrol', staffId: null as number | null })
const semesterConfig = ref<any>(null)
const tableLoading = ref(false)
const holidays = ref<any[]>([])
const adjustments = ref<any[]>([])
const holidayTips = computed(() => {
  const tips: string[] = []
  const range = getWeekDateRange()
  for (const h of holidays.value || []) {
    if (h.startDate <= range.end && h.endDate >= range.start) tips.push(`${h.name} ${h.startDate}~${h.endDate} 放假`)
  }
  for (const a of adjustments.value || []) {
    if (a.startDate <= range.end && a.endDate >= range.start) {
      tips.push(`调休 ${a.startDate}~${a.endDate}${a.note ? `（${a.note}）` : ''}`)
      for (const mk of a.makeups || []) {
        tips.push(`${mk.makeupDate} 补班(补 ${mk.replacedDate})`)
      }
    }
  }
  return tips
})
async function loadHolidaysAdjustments() {
  const [h, a] = await Promise.all([request.get('/holidays'), request.get('/duty-adjustments')])
  if (h.code === 200) holidays.value = h.data || []
  if (a.code === 200) adjustments.value = a.data || []
}
const loadingTimer = ref(null as ReturnType<typeof setTimeout> | null)

const getRoleNameMap: Record<string, string> = { patrol: '巡班', duty: '坐班', light: '敲灯', light2: '敲灯' }
const weekLabels = ['周日', '周一', '周二', '周三', '周四']
const dutyRoleMap: Record<string, string> = { patrol: '巡班', duty: '坐班', light: '敲灯', light2: '敲灯' }

const editEligibleStaff = computed(() => {
  const roleKey = dutyRoleMap[editForm.value.roleType] || '敲灯'
  const dorm = dormitories.value.find(d => d.value === selectedDormitory.value)
  const dormGender = dorm?.gender as string | undefined
  const day = scheduleData.value[editForm.value.dayIndex]
  // 当天该楼已排班的人（同天不重复）
  const dayUsedIds = new Set<number>()
  if (day) {
    for (const id of [day.patrolId, day.dutyId, day.light1Id, day.light2Id]) {
      if (id != null) dayUsedIds.add(id)
    }
  }
  const dateStr = day?.date as string | undefined
  const isOnLeave = (s: any) => (s.leaveRanges || []).some((r: any) => dateStr && r.start <= dateStr && dateStr <= r.end)
  const multiOf = (s: any) => (s.multiDuty || []).find((m: any) => selectedWeek.value >= m.weekStart && selectedWeek.value <= m.weekEnd)
  return staffWithCounts.value
    .filter((s: any) => {
      // 身份权限：该值班身份是否被授权顶当前岗位(roleKey∈巡班/坐班/敲灯)
      return (s.allowedSlots || []).includes(roleKey)
    })
    .filter((s: any) => {
      if (roleKey === '巡班') return true // 巡班不分性别
      return !dormGender || s.gender === dormGender
    })
    .filter((s: any) => !isOnLeave(s) && !dayUsedIds.has(s.id))
    .sort((a: any, b: any) => {
      const ma = multiOf(a) ? 0 : 1
      const mb = multiOf(b) ? 0 : 1
      if (ma !== mb) return ma - mb
      return (a.totalCount || 0) - (b.totalCount || 0)
    })
    .map((s: any) => {
      const m = multiOf(s)
      const tag = m ? `多排${m.weekStart}-${m.weekEnd}周` : ''
      return { id: s.id, realName: s.realName, count: s.totalCount || 0, tag }
    })
})

const totalDays = computed(() => scheduleData.value.length)

async function loadDormitories() {
  const res = await getDormitoryList({ status: 1 })
  if (res.code === 200 && Array.isArray(res.data)) {
    dormitories.value = res.data.map((d: any) => ({ label: `${d.name}(${d.gender || '-'})`, value: String(d.id), gender: d.gender }))
    if (!selectedDormitory.value) selectedDormitory.value = dormitories.value[0]?.value || ''
  }
}

async function loadStaffCounts() {
  const res = await request.get('/users/staff-with-counts', { params: { type: 'dormitory' } })
  if (res.code === 200) staffWithCounts.value = res.data || []
}

function fmtDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function getSemesterStart() {
  return semesterConfig.value?.startDate || '2026-03-09'
}
function getWeekDateRange() {
  // 宿舍排班固定周日~周四，不随开学日调整而变（开学日任意星期均可）
  const range = getDormitoryWeekRange(getSemesterStart(), selectedWeek.value)
  // 调休影响：
  //   ① 调休"开始前一天"落在本周 → 本周=调休周，范围换成[开始前一天~结束前一天]
  //   ② 本周起点仍在调休周排班结束(结束前一天)之前 → 本周后移到调休结束后的下一个周日~周四
  for (const a of adjustments.value || []) {
    if (!a.startDate || !a.endDate) continue
    const end = new Date(a.endDate + 'T00:00:00')
    const s = new Date(a.startDate + 'T00:00:00'); s.setDate(s.getDate() - 1)
    const e = new Date(end); e.setDate(e.getDate() - 1)
    const adjStart = fmtDate(s); const adjEnd = fmtDate(e)
    if (adjStart >= range.start && adjStart <= range.end) {
      return { start: adjStart, end: adjEnd }
    }
    // 只有调休周"占用本周开头"（调休开始前一天在本周之前、且调休周排班还没结束）才后移
    if (adjStart < range.start && adjEnd >= range.start) {
      const nextSun = new Date(end)
      nextSun.setDate(nextSun.getDate() + ((7 - end.getDay()) % 7 || 7))
      const nextThu = new Date(nextSun); nextThu.setDate(nextThu.getDate() + 4)
      return { start: fmtDate(nextSun), end: fmtDate(nextThu) }
    }
  }
  return range
}

function startLoading() {
  tableLoading.value = false
  if (loadingTimer.value) clearTimeout(loadingTimer.value)
  loadingTimer.value = setTimeout(() => { tableLoading.value = true }, 250)
}
function stopLoading() {
  if (loadingTimer.value) { clearTimeout(loadingTimer.value); loadingTimer.value = null }
  tableLoading.value = false
}

async function loadScheduleData() {
  startLoading()
  try {
    const range = getWeekDateRange()
    const res = await getScheduleList({ type: 'dormitory', locationId: selectedDormitory.value, startDate: range.start, endDate: range.end })
    if (res.code === 200 && res.data) {
      const dateMap = new Map<string, any>()
      res.data.forEach((item: any) => {
        if (!item.dutyDate) return
        if (!dateMap.has(item.dutyDate)) {
          const d = new Date(item.dutyDate)
          dateMap.set(item.dutyDate, { date: item.dutyDate, weekDay: ['周日','周一','周二','周三','周四','周五','周六'][d.getDay()], monthDay: `${d.getMonth()+1}/${d.getDate()}`, patrol: null, duty: null, light1: null, light2: null, patrolId: null, dutyId: null, light1Id: null, light2Id: null })
        }
        const day = dateMap.get(item.dutyDate)
        if (item.timeSlot?.includes('巡')) { day.patrol = item.userName; day.patrolId = item.id }
        else if (item.timeSlot?.includes('坐')) { day.duty = item.userName; day.dutyId = item.id }
        else if (item.timeSlot?.includes('敲')) {
          if (!day.light1) { day.light1 = item.userName; day.light1Id = item.id }
          else if (!day.light2) { day.light2 = item.userName; day.light2Id = item.id }
        }
      })
      scheduleData.value = [...dateMap.values()].sort((a, b) => a.date.localeCompare(b.date))
      if (scheduleData.value.length === 0) {
        scheduleData.value = getEmptyWeekDays()
      }
      ElMessage({ message: '加载成功', type: 'success', duration: 1200, showClose: false })
    }
  } catch { scheduleData.value = getEmptyWeekDays() } finally { stopLoading() }
}

// 生成本周空占位数据，确保手动添加按钮始终可见
function getEmptyWeekDays() {
  const range = getWeekDateRange()
  const days: any[] = []
  const start = new Date(range.start + 'T00:00:00')
  const end = new Date(range.end + 'T00:00:00')
  for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
    // 宿舍排班包含周日(晚班)，仅周六不排
    if (d.getDay() === 6) continue
    days.push({
      date: fmtDate(d),
      weekDay: ['周日','周一','周二','周三','周四','周五','周六'][d.getDay()],
      monthDay: `${d.getMonth()+1}/${d.getDate()}`,
      patrol: null, duty: null, light1: null, light2: null,
      patrolId: null, dutyId: null, light1Id: null, light2Id: null,
    })
  }
  return days
}

function getDormitoryName(v: string) { return dormitories.value.find(d => d.value === v)?.label || v }
function getRoleType(r: string) { return { patrol: 'warning', duty: 'success', light: 'primary' }[r] || 'info' }
function getRoleName(r: string) { return getRoleNameMap[r] || r }

function openEditDialog(idx: number, role: string) {
  editForm.value = { dayIndex: idx, roleType: role, staffId: null }
  showEditDialog.value = true
}

async function confirmEditStaff() {
  if (!editForm.value.staffId) return
  const s = staffWithCounts.value.find((x: any) => x.id === editForm.value.staffId)
  if (!s) return
  const day = scheduleData.value[editForm.value.dayIndex]
  const r = editForm.value.roleType
  const timeSlotMap: Record<string, string> = { patrol: '巡班', duty: '坐班', light: '敲灯', light2: '敲灯' }
  const dormId = selectedDormitory.value
  const dormName = getDormitoryName(dormId)
  try {
    await addSchedule({
      type: 'dormitory',
      locationId: dormId,
      locationName: dormName,
      userId: s.id,
      userName: s.realName,
      dutyDate: day.date,
      timeSlot: timeSlotMap[r],
    })
    await loadScheduleData(); await loadStaffCounts()
    ElMessage.success('添加成功')
  } catch (e) { ElMessage.error('添加失败') }
  showEditDialog.value = false
}

async function removeStaff(idx: number, role: string) {
  const day = scheduleData.value[idx]
  const idMap: Record<string, string> = { patrol: 'patrolId', duty: 'dutyId', light1: 'light1Id', light2: 'light2Id' }
  const nameMap: Record<string, string> = { patrol: 'patrol', duty: 'duty', light1: 'light1', light2: 'light2' }
  const scheduleId = (day as any)[idMap[role]!]
  if (scheduleId) {
    try { await deleteSchedule(Number(scheduleId)) } catch {}
  }
  await loadScheduleData()
  ElMessage.success('已移除')
}

async function handleAutoScheduleThis() {
  if (autoRunning.value) return // B修复：运行中禁止连点
  const range = getWeekDateRange()
  autoRunning.value = true
  try {
    const res = await autoScheduleDormitory({
      dormitoryIds: selectedDormitory.value ? [parseInt(selectedDormitory.value)] : undefined as any,
      startDate: range.start,
      endDate: range.end,
      semesterStart: getSemesterStart(),
    })
    if (res.code === 200) {
      ElMessage.success(`排班完成，共 ${res.data?.totalSchedules || 0} 条`)
      await Promise.all([loadScheduleData(), loadStaffCounts()])
    }
  } catch (e: any) { ElMessage.error(e.message || '排班失败') }
  finally { autoRunning.value = false }
}

const autoRunning = ref(false)
async function handleFullAutoSchedule() {
  if (autoRunning.value) return // B修复：运行中禁止连点
  const range = getWeekDateRange()
  autoRunning.value = true
  try {
    const res = await autoScheduleDormitory({ startDate: range.start, endDate: range.end, semesterStart: getSemesterStart() })
    if (res.code === 200) {
      const r = res.data
      let msg = `全体宿舍排班完成，${r.totalSchedules} 条`
      if (r.warnings?.length) msg += `，${r.warnings.length} 条警告`
      ElMessage.success(msg)
      await Promise.all([loadScheduleData(), loadStaffCounts()])
    }
  } catch (e: any) { ElMessage.error(e.message || '排班失败') }
  finally { autoRunning.value = false }
}

// 导出当前选中周、所有宿舍楼的排班记录（不依赖单栋楼选择）
async function handleExport() {
  try {
    const range = getWeekDateRange()
    const r = await exportDormitoryExcel({ startDate: range.start, endDate: range.end, week: selectedWeek.value }) as any
    const blob = new Blob([r.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob)
    a.download = `第${selectedWeek.value}周宿舍值班安排.xlsx`; a.click()
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

// 只清空当前选中周的排班，不动其他周的历史
async function handleClearSchedule() {
  if (!selectedDormitory.value) { ElMessage.warning('请先选择宿舍楼'); return }
  try {
    await ElMessageBox.confirm(
      `确定要清空「${getDormitoryName(selectedDormitory.value)}」第${selectedWeek.value}周的排班数据吗？此操作不可恢复。`,
      '清空排班确认', { confirmButtonText: '确定清空', cancelButtonText: '取消', type: 'warning' }
    )
    const range = getWeekDateRange()
    await batchDeleteSchedule({ type: 'dormitory', locationId: selectedDormitory.value, startDate: range.start, endDate: range.end })
    ElMessage.success('已清空')
    await Promise.all([loadScheduleData(), loadStaffCounts()])
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('清空失败')
  }
}

async function loadSemesterConfig() {
  const res = await request.get('/semester')
  if (res.code === 200) semesterConfig.value = res.data
}
onMounted(async () => {
  await Promise.all([loadDormitories(), loadStaffCounts(), loadSemesterConfig(), loadHolidaysAdjustments()])
  const start = new Date(getSemesterStart() + 'T00:00:00')
  const now = new Date()
  const wk = Math.floor((now.getTime() - start.getTime()) / (7 * 24 * 60 * 60 * 1000)) + 1
  const max = semesterConfig.value?.totalWeeks || 18
  selectedWeek.value = wk > 0 ? Math.min(wk, max) : max
  await loadScheduleData()
})
onActivated(async () => { await loadSemesterConfig(); await loadScheduleData() })
</script>

<style scoped>
.dormitory-management { display: flex; flex-direction: column; gap: 20px; height: 100%; overflow: hidden; }
.control-section { flex-shrink: 0; }
.control-card { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 2px 8px rgba(0,0,0,.06); }
.control-row { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; }
.control-item { display: flex; flex-direction: column; gap: 6px; }
.control-label { font-size: 12px; font-weight: 500; color: #374151; }
.control-select { width: 160px; }
.control-actions { margin-left: auto; display: flex; gap: 8px; }
.schedule-card { flex: 1; background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,.06); overflow: hidden; display: flex; flex-direction: column; }
.card-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #e5e7eb; }
.card-title { font-size: 16px; font-weight: 600; color: #1f2937; }
.table-container { flex: 1; overflow: auto; }
.schedule-table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.schedule-table thead { position: sticky; top: 0; z-index: 10; background: linear-gradient(135deg, #e0f2fe 0%, #bae6fd 100%); border-bottom: 2px solid #7dd3fc; }
.schedule-table th { padding: 12px 10px; text-align: left; font-weight: 600; font-size: 14px; color: #0369a1; }
.col-date { width: 100px; } .col-patrol { width: 150px; } .col-duty { width: 150px; } .col-light { width: 220px; }
.schedule-table tbody tr { border-bottom: 1px solid #e5e7eb; transition: background .2s; }
.schedule-table tbody tr:hover { background: #f0f9ff; }
.schedule-table td { padding: 12px; font-size: 14px; vertical-align: middle; }
.date-content { display: flex; flex-direction: column; gap: 4px; }
.date-week { color: #1f2937; font-size: 14px; font-weight: 600; }
.date-day { color: #6b7280; font-size: 13px; }
.staff-cell { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 6px 10px; background: #f1f5f9; border-radius: 6px; font-size: 13px; color: #374151; }
.light-group { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
.light-item { display: inline-flex; align-items: center; gap: 4px; padding: 4px 8px; background: #f1f5f9; border-radius: 6px; font-size: 13px; color: #374151; }

.empty-msg { text-align: center; padding: 40px 0; color: #9ca3af; font-size: 15px; }

/* ========== 手动互换模式 ========== */
.swap-bar { display: flex; align-items: center; gap: 12px; padding: 8px 16px; margin-top: 10px; background: #fef3c7; border: 1px solid #fcd34d; border-radius: 8px; font-size: 13px; color: #92400e; }
.swap-hint { color: #92400e; }
.swap-mode .staff-cell, .swap-mode .light-item { cursor: pointer; }
.swap-target { outline: 2px solid #3b82f6; outline-offset: 1px; background: #dbeafe !important; box-shadow: 0 0 0 4px rgba(59,130,246,.15); }

/* 放假/调休提示 */
.adj-tips { display: flex; flex-wrap: wrap; gap: 8px; flex-shrink: 0; }
</style>
