<template>
  <div class="office-schedule">
    <div class="content-wrapper">
      <!-- 左侧值班表格 -->
      <div class="schedule-section">
        <!-- 筛选控制区 -->
        <div class="filter-section">
          <div class="filter-card">
            <div class="filter-item">
              <label class="filter-label">办公室</label>
              <el-select v-model="selectedOffice" placeholder="选择办公室" class="filter-select" @change="loadScheduleData">
                <el-option v-for="office in offices" :key="office.value" :label="office.label" :value="office.value" />
              </el-select>
            </div>

            <div class="filter-item">
              <label class="filter-label">周次</label>
              <el-select v-model="selectedWeek" placeholder="选择周次" class="filter-select" @change="loadScheduleData">
                <el-option v-for="week in weekOptions" :key="week.value" :label="week.label" :value="week.value" />
              </el-select>
            </div>

            <div class="filter-actions">
              <MultiDutyApply type="office" type-label="办公室" />
              <SwapApply type="office" type-label="办公室" />
            </div>
          </div>
        </div>

        <!-- 值班安排表格 -->
        <div class="schedule-card">
          <div class="table-container" v-loading="tableLoading" element-loading-text="加载中..." element-loading-background="rgba(255,255,255,0.7)">
            <table class="schedule-table">
              <thead>
                <tr>
                  <th class="col-time">时间段</th>
                  <th class="col-day">周一</th>
                  <th class="col-day">周二</th>
                  <th class="col-day">周三</th>
                  <th class="col-day">周四</th>
                  <th class="col-day">周五</th>
                </tr>
              </thead>
              <tbody>
                <template v-if="hasDutyData || tableLoading">
                  <tr v-for="(slot, slotIndex) in timeSlots" :key="slotIndex" class="schedule-row">
                  <td class="cell-time">
                    <div class="time-content">
                      <span class="time-label">{{ slot.label }}</span>
                      <span class="time-period">{{ slot.period }}</span>
                    </div>
                  </td>
                  <td v-for="(day, dayIndex) in weekDays" :key="dayIndex" class="cell-duty">
                    <div class="duty-cell" :class="{ 'has-duty': getDutyStaff(slotIndex, dayIndex).length > 0 }">
                      <div v-for="(staff, staffIdx) in getDutyStaff(slotIndex, dayIndex)" :key="staffIdx"
                        class="staff-item" :class="{ 'is-my-duty': staff === currentUserName }">
                        <el-icon v-if="staff === currentUserName">
                          <Star />
                        </el-icon>
                        <el-icon v-else>
                          <User />
                        </el-icon>
                        <span>{{ staff }}</span>
                      </div>
                    </div>
                    <span v-if="getDutyStaff(slotIndex, dayIndex).length === 0" class="no-duty">—</span>
                  </td>
                </tr>
                </template>
                <tr v-else><td colspan="6" class="empty-msg">当周暂无值班安排</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 右侧我的值班安排 -->
      <div class="my-schedule-section">
        <div class="my-schedule-card">
          <div class="card-header">
            <div class="header-title">
              <el-icon class="title-icon">
                <UserFilled />
              </el-icon>
              <span>我的值班安排</span>
            </div>
            <span class="semester-tag">{{ currentSemester }}</span>
          </div>

          <div class="schedule-list">
            <div v-for="(item, index) in myScheduleList" :key="index" class="schedule-item"
              @click="jumpToWeek(item.week)">
              <div class="item-date">
                <span class="item-week">{{ item.weekDay }}</span>
                <span class="item-day">{{ item.monthDay }}</span>
              </div>
              <div class="item-info">
                <span class="item-time">{{ item.timeSlot }}</span>
                <span class="item-office">{{ item.office }}</span>
              </div>
              <div class="item-role">
                <el-icon v-if="item.role === '负责人'">
                  <Star />
                </el-icon>
                <span>{{ item.role }}</span>
              </div>
            </div>

            <div v-if="myScheduleList.length === 0" class="empty-state">
              <el-icon :size="48" color="#a0a0a0">
                <Calendar />
              </el-icon>
              <p>暂无值班安排</p>
            </div>
          </div>

          <!-- 值班统计 -->
          <div class="schedule-stats">
            <div class="stat-item">
              <span class="stat-label">总次数</span>
              <span class="stat-value">{{ totalDutyCount }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">负责人</span>
              <span class="stat-value leader">{{ leaderCount }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">值班</span>
              <span class="stat-value duty">{{ dutyCount }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useUserStore } from '@/stores/user'
import { getOfficeList } from '@/api/office'
import { getScheduleList } from '@/api/schedule'
import { getTimeSlotList } from '@/api/timeSlot'
import request from '@/utils/request'
import { getOfficeWeekRange as getOfficeWeekRangeUtil, getWeekMonday } from '@/utils/date'
import MultiDutyApply from '@/components/MultiDutyApply.vue'
import SwapApply from '@/components/SwapApply.vue'
import {
  Calendar,
  User,
  Star,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()

const offices = ref<Array<{ label: string; value: string }>>([])
const semesterConfig = ref<any>(null)
const tableLoading = ref(false)
const loadingTimer = ref(null as ReturnType<typeof setTimeout> | null)

const weekOptions = computed(() => {
  const total = semesterConfig.value?.totalWeeks || 18
  return Array.from({ length: total }, (_, i) => ({ label: `第${i + 1}周`, value: i + 1 }))
})

const timeSlots = ref<Array<{ label: string; period: string }>>([])

const weekDays = ['周一', '周二', '周三', '周四', '周五']

const selectedOffice = ref('1')
const selectedWeek = ref(1)

const currentSemester = '2025-2026 学年 第二学期'

// 当前登录用户名
const currentUserName = computed(() => userStore.userName || '张三')

// 值班数据 - 从API获取
const dutyData = ref<Record<number, Record<number, string[]>>>({})

// 初始化dutyData结构
const initDutyData = () => {
  dutyData.value = {}
  for (let slot = 0; slot < timeSlots.value.length; slot++) {
    dutyData.value[slot] = {}
    for (let day = 0; day < 5; day++) {
      dutyData.value[slot]![day] = []
    }
  }
}

function fmtDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function getSemesterStart() {
  return semesterConfig.value?.startDate || '2026-03-09'
}

function getOfficeWeekRange() {
  // 办公室排班固定周一~周五，不随开学日调整而变
  const r = getOfficeWeekRangeUtil(getSemesterStart(), selectedWeek.value)
  return { startDate: r.start, endDate: r.end }
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

const loadScheduleData = async () => {
  if (timeSlots.value.length === 0) return
  startLoading()
  try {
    const range = getOfficeWeekRange()
    const res = await getScheduleList({
      type: 'office',
      locationId: selectedOffice.value,
      startDate: range.startDate,
      endDate: range.endDate,
    })
    if (res.code === 200 && res.data) {
      initDutyData()
      res.data.forEach((item: any) => {
        const slotIndex = timeSlots.value.findIndex((t: any) =>
          t.label === item.timeSlot || t.period === item.timeSlot
        )
        if (slotIndex === -1) return
        if (item.dutyDate) {
          const date = new Date(item.dutyDate)
          let dayIndex = date.getDay() - 1
          if (dayIndex < 0) dayIndex = 4
          if (dayIndex > 4) dayIndex = 4
          const staffName = item.userName || ''
          if (dayIndex >= 0 && dayIndex < 5 && staffName) {
            if (!dutyData.value[slotIndex]) dutyData.value[slotIndex] = {}
            if (!dutyData.value[slotIndex]![dayIndex]) dutyData.value[slotIndex]![dayIndex] = []
            const currentDayData = dutyData.value[slotIndex]![dayIndex]
            if (currentDayData && !currentDayData.includes(staffName)) currentDayData.push(staffName)
          }
        }
      })
      ElMessage({ message: '加载成功', type: 'success', duration: 1200, showClose: false })
    }
  } catch (error) {
    console.error('加载排班数据失败:', error)
  } finally { stopLoading() }
}

// 我的值班安排(从API数据生成)
const myScheduleList = computed(() => {
  // 基于dutyData生成当前用户的值班列表
  const list: any[] = []

  for (let slot = 0; slot < timeSlots.value.length; slot++) {
    for (let day = 0; day < 5; day++) {
      const staffList = dutyData.value[slot]?.[day] || []
      if (staffList.includes(currentUserName.value)) {
        // 计算日期（该日历周周一 + 星期偏移，保证周一~周五）
        const currentDate = getWeekMonday(getSemesterStart(), selectedWeek.value)
        currentDate.setDate(currentDate.getDate() + day)

        const weekDays = ['周一', '周二', '周三', '周四', '周五']
        const month = currentDate.getMonth() + 1
        const date = currentDate.getDate()

        list.push({
          weekDay: weekDays[day],
          monthDay: `${month}.${date}`,
          week: selectedWeek.value,
          timeSlot: timeSlots.value[slot]?.label || '',
          office: selectedOffice.value,
          type: 'duty',
          typeName: '值班',
          role: '值班',
        })
      }
    }
  }

  return list.sort((a, b) => a.week - b.week)
})

const totalDutyCount = computed(() => myScheduleList.value.length)
const leaderCount = computed(() => myScheduleList.value.filter(s => s.type === 'leader').length)
const dutyCount = computed(() => myScheduleList.value.filter(s => s.type === 'duty').length)

const hasDutyData = computed(() => {
  for (const slot of Object.values(dutyData.value)) {
    for (const day of Object.values(slot || {})) {
      if ((day as string[]).length > 0) return true
    }
  }
  return false
})

// 获取指定时间段和星期的值班人员
const getDutyStaff = (slotIndex: number, dayIndex: number): string[] => {
  return dutyData.value[slotIndex]?.[dayIndex] || []
}

// 跳转到指定周次
const jumpToWeek = (week: number) => {
  selectedWeek.value = week
  // 滚动到表格顶部
  const tableContainer = document.querySelector('.table-container')
  if (tableContainer) {
    tableContainer.scrollTop = 0
  }
}

// 加载办公室数据
const loadOfficeData = async () => {
  try {
    const res = await getOfficeList({ status: 1 })
    if (res.code === 200 && Array.isArray(res.data)) {
      offices.value = res.data.map((item: any) => ({
        label: item.name || item.label,
        value: String(item.id),
      }))
    } else {
      offices.value = []
    }
  } catch (error) {
    console.error('加载办公室数据失败:', error)
    offices.value = []
  }
}

// 加载时间段数据
const loadTimeSlotData = async () => {
  try {
    const res = await getTimeSlotList()
    if (res.code === 200 && Array.isArray(res.data)) {
      timeSlots.value = res.data.map((item: any) => ({
        label: item.name || item.label,
        period: item.timeRange || item.period || `${item.startTime || ''}-${item.endTime || ''}`,
      }))
    } else {
      timeSlots.value = []
    }
  } catch (error) {
    console.error('加载时间段数据失败:', error)
    timeSlots.value = []
  }
}

async function loadSemesterConfig() {
  const res = await request.get('/semester')
  if (res.code === 200) semesterConfig.value = res.data
}
onMounted(async () => {
  await Promise.all([loadOfficeData(), loadTimeSlotData(), loadSemesterConfig()])
  const start = new Date(getSemesterStart() + 'T00:00:00')
  const now = new Date()
  const wk = Math.floor((now.getTime() - start.getTime()) / (7 * 24 * 60 * 60 * 1000)) + 1
  const max = semesterConfig.value?.totalWeeks || 18
  selectedWeek.value = wk > 0 ? Math.min(wk, max) : max
  await loadScheduleData()
})

// 组件从缓存中激活时重新加载数据
onActivated(() => {
  loadScheduleData()
})
</script>

<style scoped>
.office-schedule {
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
  overflow: hidden;
}

.content-wrapper {
  display: flex;
  gap: 20px;
  height: calc(100vh - 140px);
  overflow: hidden;
}

/* ========== 左侧值班表格区域 ========== */
.schedule-section {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}

/* 筛选区域 */
.filter-section {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.filter-card {
  display: flex;
  gap: 16px;
  align-items: flex-end;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-label {
  font-size: 12px;
  font-weight: 500;
  color: #374151;
}

.filter-select {
  width: 140px;
}

.filter-actions {
  margin-left: auto;
}

/* 表格卡片 */
.schedule-card {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 表格容器 */
.table-container {
  flex: 1;
  overflow: auto;
  padding: 0;
}

.schedule-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
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
  width: 1fr;
}

.schedule-table tbody tr {
  border-bottom: 2px solid #e5e7eb;
  transition: background 0.2s;
}

.schedule-table tbody tr:hover {
  background: #f0f9ff;
}

.schedule-table td {
  padding: 10px;
  font-size: 14px;
  vertical-align: top;
  min-height: 80px;
}

/* 时间单元格 */
.cell-time {
  font-weight: 600;
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
  font-size: 15px;
  font-weight: 600;
}

.time-period {
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
}

/* 值班人员单元格 */
.cell-duty {
  padding: 8px;
}

.duty-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 60px;
}

.duty-cell.has-duty {
  padding: 6px;
}

.staff-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: #f1f5f9;
  color: #475569;
  transition: all 0.2s;
}

/* 我的值班高亮样式 */
.staff-item.is-my-duty {
  background: #fef3c7;
  color: #92400e;
  font-weight: 600;
  border: 2px solid #f59e0b;
  box-shadow: 0 2px 4px rgba(245, 158, 11, 0.2);
}

.staff-item .el-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.no-duty {
  color: #a0a0a0;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
}

/* ========== 右侧我的值班区域 ========== */
.my-schedule-section {
  width: 320px;
  flex-shrink: 0;
}

.my-schedule-card {
  height: 100%;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.my-schedule-card .card-header {
  padding: 16px 20px;
}

.my-schedule-card .title-icon {
  color: #10b981;
}

.semester-tag {
  font-size: 11px;
  color: #6b7280;
  background: #f3f4f6;
  padding: 3px 8px;
  border-radius: 4px;
}

/* 值班列表 */
.schedule-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.schedule-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
  margin-bottom: 10px;
  transition: all 0.2s;
  cursor: pointer;
}

.schedule-item:hover {
  transform: translateX(4px);
  background: #e0f2fe;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.item-date {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 45px;
}

.item-week {
  font-size: 12px;
  font-weight: 500;
  color: #1f2937;
}

.item-day {
  font-size: 11px;
  color: #6b7280;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.item-time {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
  width: fit-content;
  background: #dbeafe;
  color: #1e40af;
}

.item-office {
  font-size: 12px;
  color: #6b7280;
}

.item-role {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 500;
  background: #f1f5f9;
  color: #475569;
}

.item-role .el-icon {
  font-size: 12px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #7b7b7b;
}

.empty-state p {
  margin-top: 12px;
  font-size: 14px;
}

/* 值班统计 */
.schedule-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 16px 20px;
  background: #f8fafc;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-label {
  font-size: 11px;
  color: #6b7280;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.stat-value.leader {
  color: #d97706;
}

.stat-value.duty {
  color: #2563eb;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .content-wrapper {
    flex-direction: column;
  }

  .my-schedule-section {
    width: 100%;
  }
}
.empty-msg { text-align: center; padding: 40px 0; color: #9ca3af; font-size: 15px; }
</style>
