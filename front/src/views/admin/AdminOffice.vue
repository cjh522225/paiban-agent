<template>
  <div class="office-management">
    <!-- 操作控制区 -->
    <div class="control-section">
      <div class="control-card">
        <div class="control-row">
          <div class="control-item">
            <label class="control-label">办公室</label>
            <el-select v-model="selectedOffice" placeholder="选择办公室" class="control-select" @change="loadScheduleData">
              <el-option v-for="office in offices" :key="office.value" :label="office.label" :value="office.value" />
            </el-select>
          </div>

          <div class="control-item">
            <label class="control-label">周次</label>
            <el-select v-model="selectedWeek" placeholder="选择周次" class="control-select" @change="loadScheduleData">
              <el-option v-for="week in weekOptions" :key="week.value" :label="week.label" :value="week.value" />
            </el-select>
          </div>

          <div class="control-actions">
            <el-button type="success" @click="handleAutoScheduleThis"><el-icon><MagicStick /></el-icon>生成本办公室排班</el-button>
            <el-button type="primary" :loading="autoRunning" :disabled="autoRunning" @click="handleFullAutoSchedule"><el-icon><MagicStick /></el-icon>生成全体办公室排班</el-button>
            <el-button type="warning" @click="handleExport">
              <el-icon><Download /></el-icon>导出 Excel
            </el-button>
            <el-button type="danger" @click="handleClearSchedule"><el-icon><Delete /></el-icon>清空当前排班</el-button>
            <el-button :type="swapMode ? 'danger' : 'info'" @click="toggleSwapMode">
              <el-icon><RefreshLeft /></el-icon>{{ swapMode ? '退出互换' : '互换模式' }}
            </el-button>
          </div>
        </div>
        <div v-if="swapMode" class="swap-bar">
          <span v-if="swapSelection.length === 0" class="swap-hint">互换模式：点击两个<strong>有人</strong>的名字，再点"执行互换"</span>
          <template v-else>
            <span>已选：{{ swapSelection.map(s => s.label).join(' ↔ ') }}</span>
            <el-button v-if="swapSelection.length === 2" type="primary" size="small" @click="executeSwap">执行互换</el-button>
            <el-button size="small" @click="swapSelection = []">清空</el-button>
          </template>
        </div>
      </div>
    </div>

    <div v-if="holidayTips.length" class="adj-tips">
      <el-tag v-for="t in holidayTips" :key="t" type="warning" size="small" style="margin-right:8px">{{ t }}</el-tag>
    </div>

    <!-- 排班表格 -->
    <div class="schedule-card" style="position:relative">

      <div class="table-container" :class="{ 'swap-mode': swapMode }" v-loading="tableLoading" element-loading-text="加载中..." element-loading-background="rgba(255,255,255,0.7)">
        <table class="schedule-table" v-if="tableLoading || totalSlots > 0">
          <thead>
            <tr>
              <th class="col-time">时间段</th>
              <th v-for="(col, dayIndex) in columns" :key="dayIndex" class="col-day">{{ colHeader(col) }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(slot, slotIndex) in timeSlots" :key="slotIndex" class="schedule-row">
              <td class="cell-time">
                <div class="time-content">
                  <span class="time-label">{{ slot.label }}</span>
                  <span class="time-period">{{ slot.period }}</span>
                </div>
              </td>
              <td v-for="(col, dayIndex) in columns" :key="dayIndex" class="cell-duty">
                <div class="duty-cell" :class="{ 'has-duty': getDutyStaff(slotIndex, dayIndex).length > 0 }">
                  <div v-for="(staff, staffIdx) in getDutyStaff(slotIndex, dayIndex)" :key="staffIdx"
                    class="staff-item" :class="{ 'swap-target': isSwapSelected(getScheduleId(slotIndex, dayIndex, staff)) }"
                    @click="clickOfficeStaff(slotIndex, dayIndex, staff)">
                    <span>{{ staff }}</span>
                    <template v-if="!swapMode">
                      <el-button type="danger" link size="small" @click="removeStaff(slotIndex, dayIndex, staff)">
                        <el-icon>
                          <Close />
                        </el-icon>
                      </el-button>
                    </template>
                  </div>
                </div>
                <div v-if="getDutyStaff(slotIndex, dayIndex).length === 0 && !swapMode" class="empty-duty">
                  <el-button type="primary" link @click="addStaff(slotIndex, dayIndex)">
                    <el-icon>
                      <Plus />
                    </el-icon>
                    添加
                  </el-button>
                </div>
                <div v-else-if="!swapMode && getDutyStaff(slotIndex, dayIndex).length < slotCapacity" class="duty-actions">
                  <el-button type="primary" link size="small" @click="addStaff(slotIndex, dayIndex)">
                    <el-icon>
                      <Plus />
                    </el-icon>
                  </el-button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="!tableLoading" class="empty-msg">当周暂无值班安排</div>
      </div>
    </div>

    <!-- 添加人员对话框 -->
    <el-dialog v-model="showAddDialog" title="添加值班人员" width="480px" :close-on-click-modal="false">
      <el-form ref="addFormRef" :model="addForm" label-width="90px" label-position="left">
        <el-form-item label="值班时段">
          <span>{{ timeSlots[addForm.slotIndex]?.label }} - {{ addDayLabel }}</span>
        </el-form-item>

        <el-form-item label="选择人员" prop="staffIds">
          <el-select v-model="addForm.staffIds" multiple filterable placeholder="输入姓名搜索" style="width: 100%">
            <el-option v-for="s in addableStaff" :key="s.id" :label="s.realName" :value="s.id">
              <span>{{ s.realName }} - {{ s.count }}次</span>
              <el-tag v-if="s.tag" type="success" size="small" style="margin-left: 6px">{{ s.tag }}</el-tag>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddStaff">确定</el-button>
      </template>
    </el-dialog>


  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MagicStick,
  Download,
  Plus,
  Close,
  RefreshLeft,
} from '@element-plus/icons-vue'
import { getOfficeList } from '@/api/office'
import request from '@/utils/request'
import { autoScheduleOffice, getScheduleList, exportOfficeExcel, addSchedule, deleteSchedule, batchDeleteSchedule, swapSchedules } from '@/api/schedule'
import { getTimeSlotList } from '@/api/timeSlot'
import { getOfficeWeekRange as getOfficeWeekRangeUtil } from '@/utils/date'

const offices = ref<any[]>([])

const weekOptions = computed(() => {
  const total = semesterConfig.value?.totalWeeks || 18;
  return Array.from({ length: total }, (_, i) => ({
    label: `第${i + 1}周`,
    value: i + 1,
  }));
})

/** 后端 week-columns 接口返回的单列结构：该周实际要显示的排班列（含调休补班列） */
interface OfficeColumn {
  date: string          // 'yyyy-MM-dd'
  weekday: string       // 中文：周一~周日
  isMakeup: boolean     // 是否补班日
  makeupLabel: string | null  // 补班显示文本，如 '周二(9/29)'，非补班为 null
  effectiveDow: number  // 匹配空闲用星期 1-7（补班日取"补的那天"）
  effectiveParity: string // 'odd' | 'even'
}

// 该周要显示的排班列（由后端接口权威给出）
const columns = ref<OfficeColumn[]>([])

// 列标题/显示文案工具
function colMonthDay(date: string) {
  const parts = date.split('-')
  return `${parseInt(parts[1] || '0', 10)}/${parseInt(parts[2] || '0', 10)}`
}
function colHeader(col: OfficeColumn) {
  const md = colMonthDay(col.date)
  if (col.isMakeup && col.makeupLabel) return `${col.weekday}（${md}）-补课${col.makeupLabel}`
  return `${col.weekday}（${md}）`
}

const selectedOffice = ref('')
const selectedWeek = ref(1)
const showAddDialog = ref(false)
const loading = ref(false)
const semesterConfig = ref<any>(null)
const holidays = ref<any[]>([])
const adjustments = ref<any[]>([])
const holidayTips = computed(() => {
  const tips: string[] = []
  const range = getOfficeWeekRange()
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
const tableLoading = ref(false)
const loadingTimer = ref(null as ReturnType<typeof setTimeout> | null)
const timeSlots = ref<any[]>([])
// 每节课值班人数（可配置，默认4；满额时手动添加隐藏）
const slotCapacity = ref(4)
async function loadOfficeCap() {
  try { const res = await request.get('/office-schedule-config'); if (res.code === 200) slotCapacity.value = res.data?.slotCapacity ?? 4 } catch {}
}

// 添加人员表单
const addFormRef = ref()
const addForm = ref({
  slotIndex: 0,
  dayIndex: 0,
  staffIds: [] as number[],
})

// 自动排班配置
// 模拟人员列表
const availableStaff = ref<any[]>([])
const staffWithCounts = ref<any[]>([])

// 添加弹窗里"值班时段"列的展示文案（如 周一（10/12） 或 周六（10/17）-补课周二(9/29)）
const addDayLabel = computed(() => {
  const col = columns.value[addForm.value.dayIndex]
  return col ? colHeader(col) : ''
})

// 手动添加可选人员：排除当日请假、该时段无空闲（空闲按 columns 列的 effectiveDow/effectiveParity 匹配），多排通过者优先显示+打标签
const addableStaff = computed(() => {
  const { slotIndex, dayIndex } = addForm.value
  const col = columns.value[dayIndex]
  if (!col) return []
  const slot = timeSlots.value[slotIndex]
  const slotId = slot?.id as number | undefined
  const dow = col.effectiveDow // 补班日也按"补的那天"的星期匹配空闲
  const parity = col.effectiveParity
  // 所选日期
  const dateStr = col.date
  const multiOf = (s: any) => (s.multiDuty || []).find((m: any) => selectedWeek.value >= m.weekStart && selectedWeek.value <= m.weekEnd)
  return staffWithCounts.value
    .filter((s: any) => {
      // 排除当日请假
      if ((s.leaveRanges || []).some((r: any) => dateStr && r.start <= dateStr && dateStr <= r.end)) return false
      // 必须在该时段设置过空闲（且单双周匹配）
      const av = (s.availability || []).find((a: any) => a.dayOfWeek === dow && a.timeSlotId === slotId)
      if (!av) return false
      if (av.weekParity !== 'both' && av.weekParity !== parity) return false
      return true
    })
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

// 模拟值班数据
const dutyData = ref<Record<number, Record<number, string[]>>>({})
const dutyIdMap = ref<Record<string, number>>({})

// ========== 手动互换模式 ==========
const swapMode = ref(false)
const swapSelection = ref<{ scheduleId: number; label: string }[]>([])

function toggleSwapMode() {
  swapMode.value = !swapMode.value
  swapSelection.value = []
}

function getScheduleId(slotIndex: number, dayIndex: number, staff: string) {
  return dutyIdMap.value[`${slotIndex}-${dayIndex}-${staff}`]
}

function isSwapSelected(scheduleId: number | undefined) {
  return swapMode.value && scheduleId != null && swapSelection.value.some(s => s.scheduleId === scheduleId)
}

function clickOfficeStaff(slotIndex: number, dayIndex: number, staff: string) {
  if (!swapMode.value) return
  const scheduleId = getScheduleId(slotIndex, dayIndex, staff)
  if (!scheduleId) return
  const idx = swapSelection.value.findIndex(s => s.scheduleId === scheduleId)
  if (idx >= 0) { swapSelection.value.splice(idx, 1); return }
  if (swapSelection.value.length >= 2) { ElMessage.warning('最多选两个班次'); return }
  const col = columns.value[dayIndex]
  const dayLabel = col ? colHeader(col) : `第${dayIndex + 1}列`
  swapSelection.value.push({
    scheduleId,
    label: `${dayLabel} ${timeSlots.value[slotIndex]?.label || ''} ${staff}`,
  })
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

// 获取指定时间段和日期的值班人员
const getDutyStaff = (slotIndex: number, dayIndex: number) => {
  return dutyData.value[slotIndex]?.[dayIndex] || []
}

// 获取办公室名称
const getOfficeName = (value: string) => {
  const office = offices.value.find(o => o.id == value || o.value === value)
  return office?.name || office?.label || value
}

// 总值班时段数
const totalSlots = computed(() => {
  let count = 0
  for (let slot = 0; slot < timeSlots.value.length; slot++) {
    for (let day = 0; day < columns.value.length; day++) {
      if (getDutyStaff(slot, day).length > 0) {
        count++
      }
    }
  }
  return count
})

// 加载办公室列表
const loadOffices = async () => {
  try {
    const res = await getOfficeList({ status: 1 })
    if (res.code === 200 && Array.isArray(res.data)) {
      offices.value = res.data.map((item: any) => ({
        label: item.name,
        value: String(item.id),
      }));
      if (offices.value.length > 0 && !selectedOffice.value) {
        selectedOffice.value = offices.value[0].value
      }
    } else {
      offices.value = []
    }
  } catch (error) {
    console.error('加载办公室列表失败:', error)
    offices.value = []
  }
}

// 加载人员列表(含值班次数)
const loadStaff = async () => {
  try {
    const res = await request.get('/users/staff-with-counts', { params: { type: 'office' } })
    if (res.code === 200 && Array.isArray(res.data)) {
      staffWithCounts.value = res.data
      availableStaff.value = res.data.map((item: any) => ({
        id: item.id,
        name: item.realName,
      }));
    } else { staffWithCounts.value = []; availableStaff.value = [] }
  } catch (error) {
    console.error('加载人员列表失败:', error)
    staffWithCounts.value = []; availableStaff.value = []
  }
}

// 加载时间段列表
const loadTimeSlots = async () => {
  try {
    const res = await getTimeSlotList()
    if (res.code === 200 && Array.isArray(res.data)) {
      timeSlots.value = res.data.map((item: any) => ({
        id: item.id,
        label: item.name || item.label,
        period: item.startTime && item.endTime ? `${item.startTime}-${item.endTime}` : item.period,
      }));
    } else {
      timeSlots.value = []
    }
  } catch (error) {
    console.error('加载时间段列表失败:', error)
    timeSlots.value = []
  }
}

// 添加人员
const addStaff = (slotIndex: number, dayIndex: number) => {
  if (getDutyStaff(slotIndex, dayIndex).length >= slotCapacity.value) {
    ElMessage.warning(`该时段已达每节课 ${slotCapacity.value} 人，不可继续添加`)
    return
  }
  addForm.value.slotIndex = slotIndex
  addForm.value.dayIndex = dayIndex
  addForm.value.staffIds = []
  showAddDialog.value = true
}

// 确认添加人员
const confirmAddStaff = async () => {
  const { slotIndex, dayIndex, staffIds } = addForm.value
  const selectedNames = staffWithCounts.value
    .filter((s: any) => staffIds.includes(s.id))
    .map((s: any) => s.realName)

  if (!dutyData.value[slotIndex]) {
    dutyData.value[slotIndex] = {}
  }
  if (!dutyData.value[slotIndex]![dayIndex]) {
    dutyData.value[slotIndex]![dayIndex] = []
  }

  const col = columns.value[dayIndex]
  if (!col) return
  const currentData = dutyData.value[slotIndex]![dayIndex]
  const officeName = getOfficeName(selectedOffice.value)
  const dutyDate = col.date
  const slotLabel = timeSlots.value[slotIndex]?.label || ''

  for (const name of selectedNames) {
    if (!currentData.includes(name)) {
      currentData.push(name)
    }
  }
  try {
    for (const staffId of staffIds) {
      const s = staffWithCounts.value.find((x: any) => x.id === staffId)
      if (!s) continue
      await addSchedule({
        type: 'office',
        locationId: selectedOffice.value,
        locationName: officeName,
        userId: s.id,
        userName: s.realName,
        dutyDate,
        timeSlot: slotLabel,
      })
    }
    await loadScheduleData()
    ElMessage.success('添加成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '添加失败')
  }
  showAddDialog.value = false
}

// 移除人员
const removeStaff = (slotIndex: number, dayIndex: number, staffName: string) => {
  ElMessageBox.confirm(`确定要移除 ${staffName} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    const currentData = dutyData.value[slotIndex]?.[dayIndex]
    if (currentData) {
      const index = currentData.indexOf(staffName)
      if (index > -1) {
        currentData.splice(index, 1)
      }
    }
    const key = `${slotIndex}-${dayIndex}-${staffName}`
    const scheduleId = dutyIdMap.value[key]
    if (scheduleId) {
      deleteSchedule(scheduleId).then(() => loadScheduleData())
    }
    ElMessage.success('已移除')
  }).catch(() => { })
}

// 确认智能排班
async function handleAutoScheduleThis() {
  if (autoRunning.value) return // B修复：运行中禁止连点
  const range = getColumnsRange()
  autoRunning.value = true
  try {
    const res = await autoScheduleOffice({
      officeIds: [parseInt(selectedOffice.value)],
      startDate: range.start,
      endDate: range.end,
      weekParity: selectedWeek.value % 2 === 1 ? 'odd' : 'even',
    })
    if (res.code === 200) {
      const r = res.data
      let msg = `排班完成，${r.totalSchedules || 0} 条`
      if (r.warnings?.length) msg += `，${r.warnings.length} 条警告`
      ElMessage.success(msg)
      await loadScheduleData()
    }
  } catch (e: any) { ElMessage.error(e.message || '排班失败') }
  finally { autoRunning.value = false }
}

const autoRunning = ref(false)
async function handleFullAutoSchedule() {
  if (autoRunning.value) return // B修复：运行中禁止连点
  const range = getColumnsRange()
  autoRunning.value = true
  try {
    const res = await autoScheduleOffice({
      startDate: range.start,
      endDate: range.end,
      weekParity: selectedWeek.value % 2 === 1 ? 'odd' : 'even',
    })
    if (res.code === 200) {
      const r = res.data
      let msg = `全体办公室排班完成，${r.totalSchedules || 0} 条`
      if (r.warnings?.length) msg += `，${r.warnings.length} 条警告`
      ElMessage.success(msg)
      await loadScheduleData()
    }
  } catch (e: any) { ElMessage.error(e.message || '排班失败') }
  finally { autoRunning.value = false }
}

// 加载排班数据
function startLoading() {
  tableLoading.value = false
  if (loadingTimer.value) clearTimeout(loadingTimer.value)
  loadingTimer.value = setTimeout(() => { tableLoading.value = true }, 250)
}
function stopLoading() {
  if (loadingTimer.value) { clearTimeout(loadingTimer.value); loadingTimer.value = null }
  tableLoading.value = false
}

function getSemesterStart() { return semesterConfig.value?.startDate || '2026-03-09' }
function getOfficeWeekRange() {
  // 办公室排班固定周一~周五，不随开学日调整而变
  return getOfficeWeekRangeUtil(getSemesterStart(), selectedWeek.value)
}
// 当前排班列的实际日期范围（columns[0].date ~ columns[last].date）；
// columns 为空时兜底固定周一~周五，保证导出/自动排班也能覆盖调休补班列
function getColumnsRange() {
  const cols = columns.value
  const first = cols[0]
  const last = cols.length ? cols[cols.length - 1] : undefined
  return first && last ? { start: first.date, end: last.date } : getOfficeWeekRange()
}

// 加载该周要显示的排班列（后端权威给出，含调休补班列，如周六补班）
async function loadWeekColumns() {
  try {
    const res = await request.get('/schedules/office/week-columns', { params: { week: selectedWeek.value } })
    if (res.code === 200 && Array.isArray(res.data)) {
      columns.value = res.data as OfficeColumn[]
    } else {
      columns.value = []
    }
  } catch (e) {
    console.error('加载排班列失败:', e)
    columns.value = []
  }
}

const loadScheduleData = async () => {
  startLoading()
  try {
    // 加载排班数据前先确保 columns 就绪（columns 决定网格列）
    await loadWeekColumns()
    // 查询范围取"该周第一列 ~ 最后一列"，把补班日等特殊列也包含进来
    const range = getColumnsRange()
    const res = await getScheduleList({
      type: 'office',
      locationId: selectedOffice.value,
      startDate: range.start,
      endDate: range.end,
    })
    if (res.code === 200 && res.data) {
      // 根据API返回的数据格式填充dutyData
      // 后端返回: { id, type, locationId, locationName, userId, userName, dutyDate, timeSlot }
      dutyData.value = {}

      const newDutyIdMap: Record<string, number> = {}
      res.data.forEach((item: any) => {
        const slotIndex = timeSlots.value.findIndex((t: any) =>
          t.label === item.timeSlot || t.period === item.timeSlot
        )

        // 用 dutyDate 在 columns 中找对应列索引；找不到（不在本周排班列）则跳过
        const dayIndex = item.dutyDate
          ? columns.value.findIndex((c: OfficeColumn) => c.date === item.dutyDate)
          : -1

        const staffName = item.userName || ''

        if (dayIndex >= 0 && slotIndex !== -1 && staffName) {
          if (!dutyData.value[slotIndex]) {
            dutyData.value[slotIndex] = {}
          }
          if (!dutyData.value[slotIndex]![dayIndex]) {
            dutyData.value[slotIndex]![dayIndex] = []
          }
          const currentDayData = dutyData.value[slotIndex]![dayIndex]
          if (currentDayData && !currentDayData.includes(staffName)) {
            currentDayData.push(staffName)
          }
          newDutyIdMap[`${slotIndex}-${dayIndex}-${staffName}`] = item.id
        }
      })
      dutyIdMap.value = newDutyIdMap
      ElMessage({ message: '加载成功', type: 'success', duration: 1200, showClose: false })
    }
  } catch (error) {
    console.error('加载排班数据失败:', error)
    ElMessage.error('加载排班数据失败')
  } finally { stopLoading() }
}

// 导出 Excel（导出所有办公室当前周）
const handleExport = async () => {
  try {
    const range = getColumnsRange()
    const res = await exportOfficeExcel({ startDate: range.start, endDate: range.end, week: selectedWeek.value })
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `第${selectedWeek.value}周办公室值班安排.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

async function handleClearSchedule() {
  if (!selectedOffice.value) { ElMessage.warning('请先选择办公室'); return }
  try {
    await ElMessageBox.confirm(
      `确定要清空「${getOfficeName(selectedOffice.value)}」的所有排班数据吗？此操作不可恢复。`,
      '清空排班确认', { confirmButtonText: '确定清空', cancelButtonText: '取消', type: 'warning' }
    )
    await batchDeleteSchedule({ type: 'office', locationId: selectedOffice.value })
    ElMessage.success('已清空')
    await loadScheduleData()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('清空失败')
  }
}

// 组件挂载时加载数据
async function loadSemesterConfig() { const res = await request.get('/semester'); if (res.code === 200) semesterConfig.value = res.data }
onMounted(async () => {
  await Promise.all([loadOffices(), loadStaff(), loadTimeSlots(), loadSemesterConfig(), loadHolidaysAdjustments(), loadOfficeCap()])
  const start = new Date(getSemesterStart() + 'T00:00:00')
  const now = new Date()
  const wk = Math.floor((now.getTime() - start.getTime()) / (7 * 24 * 60 * 60 * 1000)) + 1
  const max = semesterConfig.value?.totalWeeks || 18
  selectedWeek.value = wk > 0 ? Math.min(wk, max) : max
  await loadScheduleData()
})

// 组件从缓存中激活时重新加载数据
onActivated(async () => {
  await loadSemesterConfig()
  await loadOfficeCap()
  await loadScheduleData()
})
</script>

<style scoped>
.office-management {
  display: flex;
  flex-direction: column;
  gap: 6px;
  height: 100%;
  overflow: hidden;
}

.control-section {
  flex-shrink: 0;
}

.control-card {
  background: #fff;
  border-radius: 12px;
  padding: 10px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.control-row {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.control-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.control-label {
  font-size: 12px;
  font-weight: 500;
  color: #374151;
}

.control-select {
  width: 160px;
}

.control-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.schedule-card {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.table-container {
  flex: 1;
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: #c0c0c0 transparent;
}

.table-container::-webkit-scrollbar { width: 6px; height: 6px; }
.table-container::-webkit-scrollbar-track { background: transparent; }
.table-container::-webkit-scrollbar-thumb { background: #c0c0c0; border-radius: 3px; }
.table-container::-webkit-scrollbar-thumb:hover { background: #a0a0a0; }

.schedule-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  min-height: 100%;
}

.schedule-table thead {
  position: sticky;
  top: 0;
  z-index: 10;
  background: linear-gradient(135deg, #e0f2fe 0%, #bae6fd 100%);
  border-bottom: 2px solid #7dd3fc;
}

.schedule-table th {
  padding: 12px 10px;
  text-align: center;
  font-weight: 600;
  font-size: 14px;
  color: #0369a1;
  white-space: nowrap;
}

.col-time {
  width: 100px;
}

.col-day {
}

.schedule-table tbody tr {
  border-bottom: 1px solid #e5e7eb;
  transition: background 0.2s;
}

.schedule-table tbody tr:hover {
  background: #f0f9ff;
}

.schedule-table td {
  padding: 8px;
  font-size: 14px;
  vertical-align: top;
  min-height: 70px;
}

.cell-time {
  background: #f8fafc;
}

.time-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
}

.time-label {
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
}

.time-period {
  color: #6b7280;
  font-size: 11px;
}

.cell-duty {
  padding: 6px;
}

.duty-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 50px;
}

.duty-cell.has-duty {
  padding: 4px;
}

.staff-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  background: #f1f5f9;
  border-radius: 6px;
  font-size: 13px;
  color: #374151;
}

.staff-item .el-button {
  flex-shrink: 0;
}

.empty-duty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 50px;
  color: #9ca3af;
}

.duty-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 4px;
}

.autoschedule-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rule-list {
  margin: 8px 0 0 20px;
  padding: 0;
  list-style: disc;
}

.rule-list li {
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.5;
}

.mb-4 {
  margin-bottom: 16px;
}

.empty-msg { text-align: center; padding: 40px 0; color: #9ca3af; font-size: 15px; }

/* ========== 手动互换模式 ========== */
.swap-bar { display: flex; align-items: center; gap: 12px; padding: 8px 16px; margin-top: 10px; background: #fef3c7; border: 1px solid #fcd34d; border-radius: 8px; font-size: 13px; color: #92400e; }
.swap-hint { color: #92400e; }
.swap-mode .staff-item { cursor: pointer; }
.swap-target { outline: 2px solid #3b82f6; outline-offset: 1px; background: #dbeafe !important; box-shadow: 0 0 0 4px rgba(59,130,246,.15); }

/* 放假/调休提示 */
.adj-tips { display: flex; flex-wrap: wrap; gap: 8px; flex-shrink: 0; }
</style>
