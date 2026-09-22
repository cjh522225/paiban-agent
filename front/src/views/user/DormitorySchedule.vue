<template>
  <div class="dormitory-schedule">
    <div class="content-wrapper">
      <!-- 左侧值班表格 -->
      <div class="schedule-section">
        <!-- 筛选控制区 -->
        <div class="filter-section">
          <div class="filter-card">
            <div class="filter-item">
              <label class="filter-label">宿舍楼</label>
              <el-select v-model="selectedDormitory" placeholder="选择宿舍楼" class="filter-select" @change="loadScheduleData">
                <el-option v-for="dorm in dormitories" :key="dorm.value" :label="dorm.label" :value="dorm.value" />
              </el-select>
            </div>

            <div class="filter-item">
              <label class="filter-label">周次</label>
              <el-select v-model="selectedWeek" placeholder="选择周次" class="filter-select" @change="loadScheduleData">
                <el-option v-for="week in weekOptions" :key="week.value" :label="week.label" :value="week.value" />
              </el-select>
            </div>

            <div class="filter-actions">
              <MultiDutyApply type="dormitory" type-label="宿舍" />
              <SwapApply type="dormitory" type-label="宿舍" />
            </div>
          </div>
        </div>

        <!-- 值班安排表格 -->
        <div class="schedule-card">
          <div class="table-container" v-loading="tableLoading" element-loading-text="加载中..." element-loading-background="rgba(255,255,255,0.7)">
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
                <template v-if="weeklySchedule.length || tableLoading">
                  <tr v-for="(day, index) in weeklySchedule" :key="index" class="schedule-row">
                  <td class="cell-date">
                    <div class="date-content">
                      <span class="date-week">{{ day.weekDay }}</span>
                      <span class="date-day">{{ day.monthDay }}</span>
                    </div>
                  </td>
                  <td class="cell-patrol">
                    <div v-if="day.patrol" class="staff-cell" :class="{ 'is-my-duty': day.patrol === currentUserName }">
                      <el-icon v-if="day.patrol === currentUserName">
                        <UserFilled />
                      </el-icon>
                      <el-icon v-else>
                        <User />
                      </el-icon>
                      <span>{{ day.patrol }}</span>
                    </div>
                    <span v-else class="no-duty">—</span>
                  </td>
                  <td class="cell-duty">
                    <div v-if="day.duty" class="staff-cell" :class="{ 'is-my-duty': day.duty === currentUserName }">
                      <el-icon v-if="day.duty === currentUserName">
                        <Star />
                      </el-icon>
                      <el-icon v-else>
                        <User />
                      </el-icon>
                      <span>{{ day.duty }}</span>
                    </div>
                    <span v-else class="no-duty">—</span>
                  </td>
                  <td class="cell-light">
                    <div v-if="day.light1 || day.light2" class="light-group">
                      <span class="light-text"
                        :class="{ 'is-my-duty': day.light1 === currentUserName || day.light2 === currentUserName }">
                        <span v-if="day.light1">{{ day.light1 }}</span>
                        <span v-if="day.light1 && day.light2" class="light-separator">、</span>
                        <span v-if="day.light2">{{ day.light2 }}</span>
                      </span>
                    </div>
                    <span v-else class="no-duty">—</span>
                  </td>
                </tr>
                </template>
                <tr v-else><td colspan="4" class="empty-msg">当周暂无值班安排</td></tr>
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
                <span class="item-type">{{ item.typeName }}</span>
                <span class="item-dorm">{{ item.dormitory }}</span>
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
              <span class="stat-label">巡班</span>
              <span class="stat-value patrol">{{ patrolCount }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">坐班</span>
              <span class="stat-value duty">{{ dutyCount }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">敲灯</span>
              <span class="stat-value light">{{ lightCount }}</span>
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
import { getDormitoryList } from '@/api/dormitory'
import { getScheduleList } from '@/api/schedule'
import request from '@/utils/request'
import { getDormitoryWeekRange } from '@/utils/date'
import MultiDutyApply from '@/components/MultiDutyApply.vue'
import SwapApply from '@/components/SwapApply.vue'
import {
  Calendar,
  User,
  Star,
  Bell,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()

const dormitories = ref<Array<{ label: string; value: string }>>([])
const semesterConfig = ref<any>(null)
const tableLoading = ref(false)
const loadingTimer = ref(null as ReturnType<typeof setTimeout> | null)

const weekOptions = computed(() => {
  const total = semesterConfig.value?.totalWeeks || 18
  return Array.from({ length: total }, (_, i) => ({ label: `第${i + 1}周`, value: i + 1 }))
})

const selectedDormitory = ref('1')
const selectedWeek = ref(1)

const weekLabels = ['周日', '周一', '周二', '周三', '周四']
const currentSemester = '2025-2026 学年 第二学期'

// 当前登录用户名
const currentUserName = computed(() => userStore.userName || '张三')

// 周值班安排 - 从API获取
const weeklySchedule = ref<any[]>([])

function fmtDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function getSemesterStart() {
  return semesterConfig.value?.startDate || '2026-03-09'
}

function getWeekDateRange() {
  // 宿舍排班固定周日~周四，不随开学日调整而变
  const r = getDormitoryWeekRange(getSemesterStart(), selectedWeek.value)
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
  startLoading()
  try {
    const { startDate, endDate } = getWeekDateRange()
    const params = { type: 'dormitory', locationId: selectedDormitory.value, startDate, endDate }
    const res = await getScheduleList(params)
    if (res.code === 200 && res.data) {
      const dateMap = new Map<string, any>()
      res.data.forEach((item: any) => {
        if (!item.dutyDate) return
        const dateStr = item.dutyDate
        if (!dateMap.has(dateStr)) {
          const date = new Date(dateStr)
          const weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
          dateMap.set(dateStr, {
            date: dateStr,
            weekDay: weekDays[date.getDay()],
            monthDay: `${date.getMonth() + 1}/${date.getDate()}`,
            patrol: null, duty: null, light1: null, light2: null,
            isMyDuty: false,
          })
        }
        const dayData = dateMap.get(dateStr)
        const staffName = item.userName || ''
        if (item.timeSlot?.includes('巡')) { dayData.patrol = staffName }
        else if (item.timeSlot?.includes('坐')) { dayData.duty = staffName }
        else if (item.timeSlot?.includes('敲')) {
          if (!dayData.light1) { dayData.light1 = staffName }
          else if (!dayData.light2) { dayData.light2 = staffName }
        }
        if (staffName === currentUserName.value) dayData.isMyDuty = true
      })
      weeklySchedule.value = [...dateMap.values()].sort((a, b) => a.date.localeCompare(b.date))
      ElMessage({ message: '加载成功', type: 'success', duration: 1200, showClose: false })
    } else { weeklySchedule.value = [] }
  } catch { weeklySchedule.value = [] } finally { stopLoading() }
}

// 我的值班安排(从API数据生成)
const myScheduleList = computed(() => {
  // 基于weeklySchedule生成当前用户的值班列表
  const list: any[] = []

  weeklySchedule.value.forEach((day: any) => {
    const roles = []
    if (day.patrol === currentUserName.value) roles.push({ type: 'patrol', typeName: '巡班', role: '巡班人员' })
    if (day.duty === currentUserName.value) roles.push({ type: 'duty', typeName: '坐班', role: '负责人' })
    if (day.light1 === currentUserName.value || day.light2 === currentUserName.value) {
      roles.push({ type: 'light', typeName: '敲灯', role: '敲灯人员' })
    }

    roles.forEach(roleInfo => {
      list.push({
        weekDay: day.weekDay,
        monthDay: day.monthDay,
        week: selectedWeek.value,
        dormitory: selectedDormitory.value,
        ...roleInfo,
      })
    })
  })

  return list.sort((a, b) => a.week - b.week)
})

const totalDutyCount = computed(() => myScheduleList.value.length)
const patrolCount = computed(() => myScheduleList.value.filter(s => s.type === 'patrol').length)
const dutyCount = computed(() => myScheduleList.value.filter(s => s.type === 'duty').length)
const lightCount = computed(() => myScheduleList.value.filter(s => s.type === 'light').length)

// 跳转到指定周次
const jumpToWeek = (week: number) => {
  selectedWeek.value = week
  // 滚动到表格顶部
  const tableContainer = document.querySelector('.table-container')
  if (tableContainer) {
    tableContainer.scrollTop = 0
  }
}

// 加载宿舍楼数据
const loadDormitoryData = async () => {
  try {
    const res = await getDormitoryList({ status: 1 })
    if (res.code === 200 && Array.isArray(res.data)) {
      dormitories.value = res.data.map((item: any) => ({
        label: item.name || item.label,
        value: String(item.id),
      }))
    } else {
      dormitories.value = []
    }
  } catch (error) {
    console.error('加载宿舍楼数据失败:', error)
    dormitories.value = []
  }
}

async function loadSemesterConfig() {
  const res = await request.get('/semester')
  if (res.code === 200) semesterConfig.value = res.data
}
onMounted(async () => {
  await Promise.all([loadDormitoryData(), loadSemesterConfig()])
  const start = new Date(getSemesterStart() + 'T00:00:00')
  const now = new Date()
  const wk = Math.floor((now.getTime() - start.getTime()) / (7 * 24 * 60 * 60 * 1000)) + 1
  const max = semesterConfig.value?.totalWeeks || 18
  selectedWeek.value = wk > 0 ? Math.min(wk, max) : max
  await loadScheduleData()
})

onActivated(() => { loadScheduleData() })
</script>

<style scoped>
.dormitory-schedule {
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
  text-align: left;
  font-weight: 600;
  font-size: 14px;
  color: #0369a1;
  white-space: nowrap;
}

.col-date {
  width: 95px;
}

.col-patrol {
  width: 125px;
}

.col-duty {
  width: 125px;
}

.col-light {
  width: 200px;
}

.schedule-table tbody tr {
  border-bottom: 2px solid #e5e7eb;
  transition: background 0.2s;
}

.schedule-table tbody tr:hover {
  background: #f0f9ff;
}

.schedule-table td {
  padding: 14px 12px;
  font-size: 14px;
  vertical-align: middle;
}

/* 日期单元格 */
.cell-date {
  font-weight: 600;
}

.date-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.date-week {
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
}

.date-day {
  color: #6b7280;
  font-size: 13px;
  font-weight: 500;
}

/* 人员单元格 */
.cell-patrol,
.cell-duty {
  padding: 8px 12px;
}

.staff-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  width: 100%;
  background: #f8fafc;
  color: #374151;
}

/* 我的值班高亮样式 - 只高亮名字 */
.staff-cell.is-my-duty {
  background: #fef3c7;
  color: #92400e;
  font-weight: 600;
  border: 2px solid #f59e0b;
}

/* 敲灯人员高亮 */
.light-text.is-my-duty {
  color: #92400e;
  font-weight: 600;
  border: 2px solid #f59e0b;
  padding: 4px 8px;
  border-radius: 6px;
}

.staff-cell .el-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.light-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.light-text {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: #374151;
  font-size: 14px;
  font-weight: 500;
  padding: 4px 8px;
  border-radius: 6px;
}

/* 敲灯人员高亮 */
.light-text.is-my-duty {
  color: #92400e;
  font-weight: 600;
}

.light-separator {
  color: #a0a0a0;
}

.no-duty {
  color: #a0a0a0;
  font-size: 16px;
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

.item-type {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
  width: fit-content;
  background: #f1f5f9;
  color: #475569;
}

.item-dorm {
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
  grid-template-columns: repeat(4, 1fr);
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

.stat-value.patrol {
  color: #2563eb;
}

.stat-value.duty {
  color: #d97706;
}

.stat-value.light {
  color: #db2777;
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
.placeholder-row td { opacity: 0.4; }
.empty-msg { text-align: center; padding: 40px 0; color: #9ca3af; font-size: 15px; }
</style>
