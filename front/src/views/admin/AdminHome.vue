<template>
  <div class="admin-home">
    <!-- 欢迎卡片 -->
    <div class="welcome-card">
      <div class="welcome-content">
        <div class="welcome-text">
          <h2 class="welcome-title">欢迎回来,{{ userStore.userName }}!</h2>
          <p class="welcome-subtitle">今天是 {{ currentDate }},祝您工作愉快!</p>
        </div>
        <div class="quick-actions">
          <el-button type="primary" @click="navigateTo('/admin/dormitory')">
            <el-icon>
              <House />
            </el-icon>
            宿舍排班
          </el-button>
          <el-button type="success" @click="navigateTo('/admin/office')">
            <el-icon>
              <OfficeBuilding />
            </el-icon>
            办公室排班
          </el-button>
          <el-button type="warning" @click="navigateTo('/admin/leave')">
            <el-icon>
              <Bell />
            </el-icon>
            请假审批
            <el-badge :value="statistics.pendingLeaveCount" :hidden="statistics.pendingLeaveCount <= 0" type="danger" class="action-badge" />
          </el-button>
          <el-button type="info" @click="navigateTo('/admin/message')">
            <el-icon>
              <Message />
            </el-icon>
            消息通知
          </el-button>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card dormitory">
        <div class="stat-icon">
          <el-icon :size="28">
            <House />
          </el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ statistics.dormitoryCount }}</span>
          <span class="stat-label">宿舍楼数量</span>
        </div>
      </div>

      <div class="stat-card office">
        <div class="stat-icon">
          <el-icon :size="28">
            <OfficeBuilding />
          </el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ statistics.officeCount }}</span>
          <span class="stat-label">办公室数量</span>
        </div>
      </div>

      <div class="stat-card staff">
        <div class="stat-icon">
          <el-icon :size="28">
            <User />
          </el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ statistics.staffCount }}</span>
          <span class="stat-label">值班人员总数</span>
        </div>
      </div>

      <div class="stat-card pending">
        <div class="stat-icon">
          <el-icon :size="28">
            <Clock />
          </el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ statistics.pendingLeaveCount }}</span>
          <span class="stat-label">待审批请假</span>
        </div>
      </div>

      <div class="stat-card week">
        <div class="stat-icon">
          <el-icon :size="28">
            <Calendar />
          </el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ statistics.weekDutyCount }}</span>
          <span class="stat-label">本周值班人次</span>
        </div>
      </div>

      <div class="stat-card message">
        <div class="stat-icon">
          <el-icon :size="28">
            <Bell />
          </el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ statistics.messageCount }}</span>
          <span class="stat-label">已发通知数</span>
        </div>
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="content-grid">
      <!-- 待审批请假 -->
      <div class="content-card">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon>
              <Document />
            </el-icon>
            待审批请假
          </h3>
          <el-button text type="primary" @click="navigateTo('/admin/leave')">
            查看全部
            <el-icon>
              <ArrowRight />
            </el-icon>
          </el-button>
        </div>
        <div class="card-body">
          <div v-for="(item, index) in pendingLeaves" :key="index" class="leave-item"
            @click="navigateTo('/admin/leave')">
            <div class="leave-avatar">
              {{ item.userName?.charAt(0) || '未知' }}
            </div>
            <div class="leave-content">
              <div class="leave-header">
                <span class="leave-name">{{ item.userName }}</span>
                <el-tag :type="getLeaveTypeTag(item.leaveType)" size="small">
                  {{ getLeaveTypeName(item.leaveType) }}
                </el-tag>
              </div>
              <div class="leave-info">
                <span class="leave-days">{{ item.days }}天</span>
                <span class="leave-reason">{{ item.reason }}</span>
              </div>
            </div>
            <div class="leave-time">{{ formatTime(item.createTime) }}</div>
          </div>
          <div v-if="pendingLeaves.length === 0" class="empty-tip">
            <el-icon :size="40" color="#d1d5db">
              <Checked />
            </el-icon>
            <p>暂无待审批</p>
          </div>
        </div>
      </div>

      <!-- 最近通知 -->
      <div class="content-card">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon>
              <Bell />
            </el-icon>
            最近通知
          </h3>
          <el-button text type="primary" @click="navigateTo('/admin/message')">
            查看全部
            <el-icon>
              <ArrowRight />
            </el-icon>
          </el-button>
        </div>
        <div class="card-body">
          <div v-for="(item, index) in recentMessages" :key="index" class="message-item"
            @click="navigateTo('/admin/message')">
            <div class="message-icon" :style="{ background: getMessageColor(item.type) + '20' }">
              <el-icon :size="18" :color="getMessageColor(item.type)">
                <component :is="getMessageIcon(item.type)" />
              </el-icon>
            </div>
            <div class="message-content">
              <div class="message-title">{{ item.title }}</div>
              <div class="message-summary">{{ item.content?.substring(0, 30) }}...</div>
            </div>
            <div class="message-time">{{ formatTime(item.createTime) }}</div>
          </div>
          <div v-if="recentMessages.length === 0" class="empty-tip">
            <el-icon :size="40" color="#d1d5db">
              <Message />
            </el-icon>
            <p>暂无通知</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated, onDeactivated, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  House,
  OfficeBuilding,
  Bell,
  Message,
  User,
  Clock,
  Calendar,
  Document,
  ArrowRight,
  Checked,
} from '@element-plus/icons-vue'
import { getAdminStatistics, getPendingLeaves, getRecentMessages } from '@/api/statistics'

const router = useRouter()
const userStore = useUserStore()

const currentDate = computed(() => {
  const now = new Date()
  const weekDays = ['日', '一', '二', '三', '四', '五', '六']
  return `${now.getMonth() + 1}月${now.getDate()}日 周${weekDays[now.getDay()]}`
})

// 统计数据
const statistics = ref({
  dormitoryCount: 0,
  officeCount: 0,
  staffCount: 0,
  pendingLeaveCount: 0,
  weekDutyCount: 0,
  messageCount: 0,
})

// 待审批请假
const pendingLeaves = ref<any[]>([])

// 最近通知
const recentMessages = ref<any[]>([])

// 获取统计数据
const loadStatistics = async () => {
  try {
    const res = await getAdminStatistics()
    const data = res.data as Partial<typeof statistics.value>
    if (data && typeof data === 'object') {
      statistics.value = { ...statistics.value, ...data }
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

// 获取待审批请假
const loadPendingLeaves = async () => {
  try {
    const res = await getPendingLeaves()
    pendingLeaves.value = res.data
  } catch (error) {
    console.error('获取待审批请假失败:', error)
  }
}

// 获取最近通知
const loadRecentMessages = async () => {
  try {
    const res = await getRecentMessages()
    recentMessages.value = res.data
  } catch (error) {
    console.error('获取最近通知失败:', error)
  }
}

// C14: 一键刷新统计/待审批/最近通知
const refreshAll = () => {
  loadStatistics()
  loadPendingLeaves()
  loadRecentMessages()
}

// C14: 首页 30s 自动轮询（keep-alive 下仅在激活期间轮询，避免叠加定时器）
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null
const startAutoRefresh = () => {
  if (autoRefreshTimer) return
  autoRefreshTimer = setInterval(refreshAll, 30000)
}
const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

onMounted(() => {
  refreshAll()
  startAutoRefresh()
})

// 页面激活时重新加载并保证轮询在跑（keep-alive 场景下确保数据同步）
onActivated(() => {
  refreshAll()
  startAutoRefresh()
})
onDeactivated(() => {
  stopAutoRefresh()
})
onUnmounted(() => {
  stopAutoRefresh()
})

// 获取请假类型标签
const getLeaveTypeTag = (type: string) => {
  const types: Record<string, any> = {
    sick: 'danger',
    personal: 'warning',
    compensatory: 'primary',
    annual: 'success',
  }
  return types[type] || 'info'
}

// 获取请假类型名称
const getLeaveTypeName = (type: string) => {
  const names: Record<string, string> = {
    sick: '病假',
    personal: '事假',
    compensatory: '调休',
    annual: '年假',
    marriage: '婚假',
    maternity: '产假',
    paternity: '陪产假',
    bereavement: '丧假',
  }
  return names[type] || type
}

// 获取消息图标
const getMessageIcon = (type: string) => {
  const icons: Record<string, any> = {
    duty: Bell,
    approval: Checked,
    system: Document,
    urgent: Clock,
  }
  return icons[type] || Message
}

// 获取消息颜色
const getMessageColor = (type: string) => {
  const colors: Record<string, string> = {
    duty: '#f59e0b',
    approval: '#10b981',
    system: '#3b82f6',
    urgent: '#ef4444',
  }
  return colors[type] || '#6b7280'
}

// 格式化时间
const formatTime = (time: string) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 1000 / 60)
  const hours = Math.floor(diff / 1000 / 60 / 60)
  const days = Math.floor(diff / 1000 / 60 / 60 / 24)

  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return `${date.getMonth() + 1}-${date.getDate()}`
}

const navigateTo = (path: string) => {
  router.push(path)
}
</script>

<style scoped>
.admin-home {
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
  overflow: hidden;
}

/* ========== 欢迎卡片 ========== */
.welcome-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 24px 32px;
  color: #fff;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.welcome-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.welcome-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
}

.welcome-subtitle {
  font-size: 14px;
  opacity: 0.9;
  margin: 0;
}

.quick-actions {
  display: flex;
  gap: 12px;
}

.quick-actions .el-button {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.3);
  color: #fff;
}

.quick-actions .el-button:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
}

.quick-actions .el-button .el-icon {
  margin-right: 6px;
}

.action-badge {
  margin-left: 8px;
}

/* ========== 统计卡片网格 ========== */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card.dormitory .stat-icon {
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  color: #2563eb;
}

.stat-card.office .stat-icon {
  background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%);
  color: #047857;
}

.stat-card.staff .stat-icon {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  color: #92400e;
}

.stat-card.pending .stat-icon {
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
  color: #991b1b;
}

.stat-card.week .stat-icon {
  background: linear-gradient(135deg, #e0e7ff 0%, #c7d2fe 100%);
  color: #3730a3;
}

.stat-card.message .stat-icon {
  background: linear-gradient(135deg, #fce7f3 0%, #fbcfe8 100%);
  color: #9d174d;
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
}

/* ========== 内容区域网格 ========== */
.content-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  flex: 1;
  overflow: auto;
}

.content-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content-card.full-width {
  grid-column: 1 / -1;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.card-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 20px;
}

/* 请假项 */
.leave-item {
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

.leave-item:hover {
  background: #e0f2fe;
  transform: translateX(4px);
}

.leave-item:last-child {
  margin-bottom: 0;
}

.leave-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.leave-content {
  flex: 1;
  min-width: 0;
}

.leave-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.leave-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.leave-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.leave-days {
  font-weight: 500;
  color: #2563eb;
}

.leave-reason {
  color: #6b7280;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.leave-time {
  font-size: 11px;
  color: #9ca3af;
  flex-shrink: 0;
}

/* 消息项 */
.message-item {
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

.message-item:hover {
  background: #e0f2fe;
  transform: translateX(4px);
}

.message-item:last-child {
  margin-bottom: 0;
}

.message-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.message-summary {
  font-size: 12px;
  color: #6b7280;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.message-time {
  font-size: 11px;
  color: #9ca3af;
  flex-shrink: 0;
}

/* 空提示 */
.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #9ca3af;
}

.empty-tip p {
  margin-top: 12px;
  font-size: 14px;
}

/* 响应式设计 */
@media (max-width: 1400px) {
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1024px) {
  .content-grid {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .welcome-content {
    flex-direction: column;
    gap: 16px;
  }

  .quick-actions {
    flex-wrap: wrap;
  }
}
</style>
