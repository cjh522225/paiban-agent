<template>
  <div class="user-home">
    <div class="home-title-section">
      <h1 class="home-title">智能排班系统</h1>
      <p class="home-subtitle">近 7 日个人值班安排</p>
    </div>

    <div class="content-wrapper">
      <div class="duty-cards" v-loading="loading" element-loading-text="加载中...">
        <div class="duty-card dormitory-card">
          <div class="card-head">
            <el-icon :size="20"><House /></el-icon>
            <span>宿舍值班</span>
          </div>
          <div class="card-body">
            <template v-if="dormitorySchedules.length">
              <div v-for="(item, idx) in dormitorySchedules" :key="'d'+idx" class="duty-item">
                <div class="duty-left">
                  <span class="duty-weekday">{{ item.weekDayLabel }}</span>
                  <span class="duty-date">{{ item.dutyDate }}</span>
                </div>
                <div class="duty-right">
                  <span class="duty-role">{{ item.timeSlot }}</span>
                  <span class="duty-location">{{ item.locationName }}</span>
                </div>
              </div>
            </template>
            <div v-else class="card-empty">
              <el-icon :size="36"><CircleCheck /></el-icon>
              <p>近 7 日无宿舍值班</p>
            </div>
          </div>
        </div>

        <div class="duty-card office-card">
          <div class="card-head">
            <el-icon :size="20"><OfficeBuilding /></el-icon>
            <span>办公室值班</span>
          </div>
          <div class="card-body">
            <template v-if="officeSchedules.length">
              <div v-for="(item, idx) in officeSchedules" :key="'o'+idx" class="duty-item">
                <div class="duty-left">
                  <span class="duty-weekday">{{ item.weekDayLabel }}</span>
                  <span class="duty-date">{{ item.dutyDate }}</span>
                </div>
                <div class="duty-right">
                  <span class="duty-role">{{ item.timeSlot }}</span>
                  <span class="duty-location">{{ item.locationName }}</span>
                </div>
              </div>
            </template>
            <div v-else class="card-empty">
              <el-icon :size="36"><CircleCheck /></el-icon>
              <p>近 7 日无办公室值班</p>
            </div>
          </div>
        </div>
      </div>

      <div class="message-card">
        <div class="card-header">
          <el-icon :size="18"><Bell /></el-icon>
          <span>消息通知</span>
        </div>
        <div class="message-list">
          <template v-if="messages.length">
            <div v-for="(msg, idx) in messages" :key="idx" class="message-item">
              <div class="msg-left">
                <el-icon :size="16" :color="msg.type === 'urgent' ? '#ef4444' : '#3b82f6'">
                  <Bell v-if="msg.type !== 'urgent'" />
                  <Warning v-else />
                </el-icon>
                <span class="msg-title">{{ msg.title }}</span>
              </div>
              <span class="msg-time">{{ msg.createTime?.split(' ')[0] || '' }}</span>
            </div>
          </template>
          <div v-else class="msg-empty">
            <el-icon :size="30"><Bell /></el-icon>
            <p>暂无消息通知</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onActivated, onUnmounted } from 'vue'
import { House, OfficeBuilding, CircleCheck, Bell, Warning } from '@element-plus/icons-vue'
import { getMyScheduleList } from '@/api/schedule'
import { getMessageList } from '@/api/message'

const loading = ref(false)
const dormitorySchedules = ref<any[]>([])
const officeSchedules = ref<any[]>([])
const messages = ref<any[]>([])

const weekDayNames = ['日', '一', '二', '三', '四', '五', '六']

function fmtDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function getWeekDateRange() {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const end = new Date(today)
  end.setDate(end.getDate() + 6)
  return { startDate: fmtDate(today), endDate: fmtDate(end) }
}

async function loadSchedule() {
  const range = getWeekDateRange()
  const res = await getMyScheduleList({ startDate: range.startDate, endDate: range.endDate })
  const all: any[] = []
  if (res.code === 200 && res.data) {
    all.push(...(res.data || []).map((item: any) => {
      const d = new Date(item.dutyDate)
      return { ...item, weekDayLabel: '周' + weekDayNames[d.getDay()] }
    }))
  }
  dormitorySchedules.value = all.filter((s: any) => s.type === 'dormitory')
  officeSchedules.value = all.filter((s: any) => s.type === 'office')
}

async function loadMessages() {
  try {
    const res = await getMessageList()
    if (res.code === 200 && res.data) {
      messages.value = (res.data || []).slice(0, 6)
    }
  } catch { messages.value = [] }
}

async function init() {
  loading.value = true
  await Promise.all([loadSchedule(), loadMessages()])
  loading.value = false
}

onMounted(() => {
  init()
  startPolling()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onActivated(init)

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

let pollTimer: ReturnType<typeof setInterval> | null = null

function onVisibilityChange() {
  if (document.visibilityState === 'visible') init()
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    if (document.visibilityState === 'visible') init()
  }, 30000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}
</script>

<style scoped>
.user-home {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  overflow: hidden;
}

.home-title-section {
  text-align: center;
  padding: 24px 20px 0;
  flex-shrink: 0;
}

.home-title {
  font-size: 32px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 6px 0;
  background: linear-gradient(135deg, #1e40af 0%, #3b82f6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.home-subtitle {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

.content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  padding: 0 12px 12px;
}

.duty-cards {
  flex: 1;
  display: flex;
  gap: 14px;
  min-height: 0;
}

.duty-card {
  flex: 1;
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dormitory-card { border: 1px solid #fcd34d; }
.office-card { border: 1px solid #7dd3fc; }

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
  font-size: 15px;
  font-weight: 600;
}

.dormitory-card .card-head { background: #fffbeb; color: #92400e; }
.office-card .card-head { background: #f0f9ff; color: #0369a1; }

.card-body {
  flex: 1;
  padding: 14px 16px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

.duty-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
}

.duty-left {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.duty-weekday {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.duty-date {
  font-size: 13px;
  color: #6b7280;
}

.duty-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}

.duty-role {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}

.duty-location {
  font-size: 13px;
  color: #6b7280;
}

.card-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  gap: 8px;
  padding: 8px 0;
}

.card-empty p {
  font-size: 14px;
  margin: 0;
}

.dormitory-card .card-empty .el-icon { color: #f59e0b; }
.office-card .card-empty .el-icon { color: #3b82f6; }

.message-card {
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.06);
  border: 1px solid #d1d5db;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  font-size: 15px;
  font-weight: 600;
  background: #f3f4f6;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
}

.message-list {
  padding: 10px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  transition: background 0.15s;
}

.message-item:hover { background: #f8fafc; }

.msg-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.msg-title {
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.msg-time {
  font-size: 12px;
  color: #9ca3af;
  flex-shrink: 0;
  margin-left: 8px;
}

.msg-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
  color: #9ca3af;
  gap: 6px;
}

.msg-empty p { font-size: 14px; margin: 0; }

@media (max-width: 768px) {
  .duty-cards { flex-direction: column; }
}
</style>
