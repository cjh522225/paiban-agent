<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <!-- Logo 区域 -->
      <div class="sidebar-logo">
        <img src="@/assets/logo.png" alt="logo" class="logo-img" />
        <span class="logo-text">智能排班系统</span>
      </div>

      <!-- 用户信息卡片 -->
      <div class="user-card">
        <div class="user-avatar">
          {{ userStore.userName?.charAt(0) || '用' }}
        </div>
        <div class="user-info">
          <p class="user-name">{{ userStore.userName || '用户' }}</p>
          <p class="user-role">{{ isAdmin ? '管理员' : '普通用户' }}</p>
        </div>
      </div>

      <!-- 导航菜单 -->
      <nav class="sidebar-nav">
        <ul class="nav-list">
          <li v-for="item in menuItems" :key="item.path || item.name">
            <template v-if="item.children">
              <div class="nav-item nav-parent" :class="{ active: isActive(item), expanded: submenuExpanded[item.name] }" @click="handleSubmenuClick(item)">
                <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
                <span class="nav-text">{{ item.name }}</span>
                <el-icon class="nav-arrow"><component :is="submenuExpanded[item.name] ? ArrowDown : ArrowRight" /></el-icon>
              </div>
              <ul v-show="submenuExpanded[item.name]" class="sub-nav">
                <li v-for="child in item.children" :key="child.path">
                  <router-link :to="child.path" class="nav-item sub-nav-item" :class="{ active: route.path === child.path || route.path.startsWith(child.path + '/') }">
                    <span class="nav-text sub-nav-text">{{ child.name }}</span>
                  </router-link>
                </li>
              </ul>
            </template>
            <router-link v-else :to="item.path" class="nav-item" :class="{ active: isActive(item) }">
              <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
              <span class="nav-text">{{ item.name }}</span>
              <span v-if="(item.badge ?? 0) > 0" class="nav-badge">{{ (item.badge ?? 0) > 99 ? '99+' : item.badge }}</span>
            </router-link>
          </li>
        </ul>
      </nav>

      <!-- 底部退出按钮 -->
      <div class="sidebar-footer">
        <button @click="handleLogout" class="logout-btn">
          <el-icon class="logout-icon">
            <SwitchButton />
          </el-icon>
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-wrapper">
      <!-- 顶部导航栏 -->
      <header class="top-header">
        <div class="header-left">
          <h1 class="page-title">{{ currentPageTitle }}</h1>
        </div>
        <div class="header-right">
          <span class="current-date">{{ currentDate }}</span>
        </div>
      </header>

      <!-- 内容区域 -->
      <main class="main-content">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" :key="route.fullPath + ':' + viewRefreshTick" />
          </keep-alive>
        </router-view>
      </main>
    </div>

    <!-- AI 悬浮球助手 -->
    <AgentBall
      base-url="http://localhost:8090"
      title="排班助手"
      hint="可以问我排班、统计、请假规则、换班多排、纪律处理等问题。"
      :prompts="agentPrompts"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, watch, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import AgentBall from '@/components/AgentBall.vue'
import { getMultiDutyList } from '@/api/multiDuty'
import { getSwapRequests } from '@/api/swapRequest'
import {
  HomeFilled,
  OfficeBuilding,
  House,
  Bell,
  Message,
  DataAnalysis,
  Calendar,
  SwitchButton,
  ArrowDown,
  ArrowRight,
  Plus,
  Warning,
  RefreshLeft,
  User as UserIcon,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const submenuExpanded = ref<Record<string, boolean>>({})
const semesterStart = ref<string>('')
const pendingMultiDuty = ref(0)
const pendingSwapRequests = ref(0)
const viewRefreshTick = ref(0)
const agentPrompts = [
  '现在第几周？我本周有排班吗？',
  '统计一下每个人这学期值班次数前 5 名',
  '请假需要提前几天申请？',
  '为什么 223 号用户在第 5 教学周没有排班？',
  '帮我解析：国庆节 10 月 1 日到 7 日放假，10 月 11 日补周三的课'
]
let pollTimer: ReturnType<typeof setInterval> | null = null

// AI 助手完成写操作后，强制重挂载当前视图以刷新数据
const handleAgentDataChanged = () => {
  viewRefreshTick.value += 1
  if (isAdmin.value) refreshPendingCounts()
}

// 根据当前路由路径判断是否是管理员
const isAdmin = computed(() => route.path.startsWith('/admin'))

// 根据角色生成菜单项
const menuItems = computed(() => {
  const prefix = isAdmin.value ? '/admin' : '/user'

  return [
    { path: prefix, name: '首页', icon: HomeFilled },
    ...(isAdmin.value
      ? [{ name: '宿舍值班', icon: House, children: [
        { path: `${prefix}/dormitory`, name: '排班表' },
        { path: `${prefix}/dormitory-manage`, name: '宿舍楼管理' },
      ]}]
      : [{ path: `${prefix}/dormitory`, name: '宿舍值班', icon: House }]),
    ...(isAdmin.value
      ? [{ name: '办公室值班', icon: OfficeBuilding, children: [
        { path: `${prefix}/office`, name: '排班表' },
        { path: `${prefix}/office-manage`, name: '办公室管理' },
        { path: `${prefix}/office-availability`, name: '空闲时间' },
      ]}]
      : [{ name: '办公室值班', icon: OfficeBuilding, children: [
        { path: `${prefix}/office`, name: '排班表' },
        { path: `${prefix}/availability`, name: '空闲时间' },
      ]}]),
    { path: `${prefix}/leave`, name: isAdmin.value ? '请假管理' : '申请请假', icon: Bell },
    { path: `${prefix}/message`, name: '消息通知', icon: Message },
    ...(isAdmin.value
      ? [
          { path: `${prefix}/users`, name: '用户管理', icon: UserIcon },
          { path: `${prefix}/statistics`, name: '值班统计', icon: DataAnalysis },
          { path: `${prefix}/semester`, name: '学期调整', icon: Calendar },
          { path: `${prefix}/multi-duty`, name: '多排申请', icon: Plus, badge: pendingMultiDuty.value },
          { path: `${prefix}/swap-requests`, name: '换班请求', icon: RefreshLeft, badge: pendingSwapRequests.value },
          { path: `${prefix}/discipline`, name: '纪律管理', icon: Warning },
        ]
      : []),
  ]
})

const currentPageTitle = computed(() => {
  const routeName = route.name as string
  const nameMap: Record<string, string> = {
    AdminHome: '管理员首页',
    AdminDormitory: '宿舍值班管理',
    AdminOffice: '办公室排班表',
    AdminAvailability: '空闲时间查看',
    AdminOfficeManage: '办公室管理',
    AdminLeave: '请假管理',
    AdminMessage: '消息通知管理',
    AdminSemester: '学期调整',
    AdminStatistics: '值班统计',
    AdminUsers: '用户管理',
    UserHome: '首页',
    UserDormitory: '宿舍值班表',
    UserOffice: '办公室值班表',
    UserLeave: '请假申请',
    UserMessage: '消息通知',
    UserAvailability: '空闲时间设置',
  }
  return nameMap[routeName] || '值班管理系统'
})

const currentDate = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  const date = now.getDate()
  const weekDays = ['日', '一', '二', '三', '四', '五', '六']
  const week = weekDays[now.getDay()]
  let wn = ''
  if (semesterStart.value) {
    const start = new Date(semesterStart.value + 'T00:00:00')
    const diff = Math.floor((now.getTime() - start.getTime()) / (7 * 24 * 60 * 60 * 1000)) + 1
    if (diff > 0) wn = `  第${diff}周`
  }
  return `${year}年${month}月${date}日 星期${week}${wn}`
})

const isActive = (item: any) => {
  if (item.children) return item.children.some((c: any) => route.path === c.path || route.path.startsWith(c.path + '/'))
  const p = typeof item === 'string' ? item : item.path
  if (!p) return false
  if (p === '/user' || p === '/admin') return route.path === p
  return route.path === p || route.path.startsWith(p + '/')
}

const handleLogout = () => {
  userStore.clearUser()
  localStorage.removeItem('token')
  router.push('/')
}

onMounted(async () => {
  window.addEventListener('agent:data-changed', handleAgentDataChanged)
  try {
    const res = await request.get('/semester')
    if (res.code === 200 && res.data?.startDate) semesterStart.value = res.data.startDate
  } catch {}
  if (isAdmin.value) {
    await refreshPendingCounts()
    pollTimer = setInterval(refreshPendingCounts, 30000)
  }
})

onUnmounted(() => {
  window.removeEventListener('agent:data-changed', handleAgentDataChanged)
  if (pollTimer) clearInterval(pollTimer)
})

// 待处理申请数：多排申请 + 换班请求（仅管理员菜单角标）
async function refreshPendingCounts() {
  try {
    const [multi, swap] = await Promise.all([
      getMultiDutyList('pending'),
      getSwapRequests({ status: 'pending' }),
    ])
    pendingMultiDuty.value = multi.code === 200 ? (multi.data || []).length : 0
    pendingSwapRequests.value = swap.code === 200 ? (swap.data || []).length : 0
  } catch {}
}

watch(isAdmin, async (val) => {
  if (val) {
    await refreshPendingCounts()
    if (!pollTimer) pollTimer = setInterval(refreshPendingCounts, 30000)
  } else {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  }
})

function handleSubmenuClick(item: any) {
  const was = submenuExpanded.value[item.name]
  submenuExpanded.value = {}
  if (!was && item.children?.length) {
    router.push(item.children[0].path)
  }
  submenuExpanded.value[item.name] = !was
}

watch(() => route.path, () => {
  submenuExpanded.value = {}
  for (const item of menuItems.value) {
    if (item.children && item.children.some((c: any) => route.path.startsWith(c.path))) {
      submenuExpanded.value[item.name] = true
    }
  }
}, { immediate: true })


</script>

<style scoped>
/* 布局容器 - 占满整个视口 */
.layout-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: #f0f2f5;
}

/* ========== 侧边栏样式 ========== */
.sidebar {
  width: 260px;
  min-width: 260px;
  height: 100%;
  background: linear-gradient(180deg, #1e40af 0%, #3b82f6 100%);
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
}

/* Logo 区域 */
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-img {
  width: 40px;
  height: 40px;
  object-fit: contain;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
}

/* 用户信息卡片 */
.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 24px;
  margin: 16px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 12px;
  backdrop-filter: blur(10px);
}

.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
}

/* 导航菜单 */
.sidebar-nav {
  flex: 1;
  padding: 0 12px;
  overflow-y: auto;
}

.nav-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  margin-bottom: 2px;
  border-radius: 10px;
  color: rgba(255, 255, 255, 0.85);
  text-decoration: none;
  transition: all 0.5s ease;
  cursor: pointer;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.nav-item.active {
  background: #fff;
  color: #1e40af;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.nav-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.nav-text {
  font-size: 15px;
  white-space: nowrap;
}

/* 菜单角标：醒目红色数字提示 */
.nav-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #ef4444;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.35);
  animation: badge-pulse 2s ease-in-out infinite;
}

@keyframes badge-pulse {
  0%, 100% { box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.35); }
  50% { box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.15); }
}

/* 底部退出按钮 */
.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.nav-parent { cursor: pointer; display: flex; align-items: center; justify-content: space-between; }
.nav-arrow { font-size: 12px; margin-left: auto; transition: transform 0.3s; }
.nav-arrow.expanded { transform: rotate(180deg); }
.sub-nav { padding-left: 12px; }
.sub-nav-item { padding: 8px 16px 8px 36px; }
.sub-nav-text { font-size: 13px; }
.nav-parent.expanded { background: rgba(255,255,255,0.1); }

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 12px;
  background: rgba(255, 255, 255, 0.15);
  border: none;
  border-radius: 10px;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}

.logout-icon {
  font-size: 18px;
}

/* ========== 主内容区样式 ========== */
.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
}

/* 顶部导航栏 */
.top-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.current-date {
  font-size: 14px;
  color: #6b7280;
}

/* 内容区域 */
.main-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #f0f2f5;
}

/* 滚动条样式 */
.sidebar-nav {
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,0.25) transparent;
}
.sidebar-nav::-webkit-scrollbar { width: 6px; }
.sidebar-nav::-webkit-scrollbar-track { background: transparent; }
.sidebar-nav::-webkit-scrollbar-thumb {
  background: rgba(255,255,255,0.25);
  border-radius: 10px;
  transition: background 0.3s;
}
.sidebar-nav::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.45); }

.page-fade-enter-active,
.page-fade-leave-active { transition: opacity 0.2s ease; }
.page-fade-enter-from,
.page-fade-leave-to { opacity: 0; }
</style>
