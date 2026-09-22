import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
  },
  {
    path: '/admin',
    name: 'AdminLayout',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { role: 'admin' },
    children: [
      {
        path: '',
        name: 'AdminHome',
        component: () => import('@/views/admin/AdminHome.vue'),
      },
      {
        path: 'dormitory',
        name: 'AdminDormitory',
        component: () => import('@/views/admin/AdminDormitory.vue'),
      },
      {
        path: 'office',
        name: 'AdminOffice',
        component: () => import('@/views/admin/AdminOffice.vue'),
      },
      {
        path: 'office-availability',
        name: 'AdminAvailability',
        component: () => import('@/views/admin/AdminAvailability.vue'),
      },
      {
        path: 'leave',
        name: 'AdminLeave',
        component: () => import('@/views/admin/AdminLeave.vue'),
      },
      {
        path: 'message',
        name: 'AdminMessage',
        component: () => import('@/views/admin/AdminMessage.vue'),
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/AdminUser.vue'),
      },
      {
        path: 'dormitory-manage',
        name: 'AdminDormitoryManage',
        component: () => import('@/views/admin/AdminDormitoryManage.vue'),
      },
      {
        path: 'office-manage',
        name: 'AdminOfficeManage',
        component: () => import('@/views/admin/AdminOfficeManage.vue'),
      },
      {

        path: 'semester',
        name: 'AdminSemester',
        component: () => import('@/views/admin/AdminSemester.vue'),
      },
      {
        path: 'multi-duty',
        name: 'AdminMultiDuty',
        component: () => import('@/views/admin/AdminMultiDuty.vue'),
      },
      {
        path: 'swap-requests',
        name: 'AdminSwapRequests',
        component: () => import('@/views/admin/AdminSwapRequests.vue'),
      },
      {
        path: 'discipline',
        name: 'AdminDiscipline',
        component: () => import('@/views/admin/AdminDiscipline.vue'),
      },
      {
        path: 'statistics',
        name: 'AdminStatistics',
        component: () => import('@/views/admin/AdminStatistics.vue'),
      },
    ],
  },
  {
    path: '/user',
    name: 'UserLayout',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { role: 'user' },
    children: [
      {
        path: '',
        name: 'UserHome',
        component: () => import('@/views/user/UserHome.vue'),
      },
      {
        path: 'dormitory',
        name: 'UserDormitory',
        component: () => import('@/views/user/DormitorySchedule.vue'),
      },
      {
        path: 'office',
        name: 'UserOffice',
        component: () => import('@/views/user/OfficeSchedule.vue'),
      },
      {
        path: 'leave',
        name: 'UserLeave',
        component: () => import('@/views/user/UserLeave.vue'),
      },
      {
        path: 'message',
        name: 'UserMessage',
        component: () => import('@/views/user/UserMessage.vue'),
      },
      {
        path: 'availability',
        name: 'UserAvailability',
        component: () => import('@/views/user/UserAvailability.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：根据角色限制页面访问
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  const userStr = localStorage.getItem('user')
  const user = userStr ? JSON.parse(userStr) : null
  const requiredRole = to.meta?.role as string | undefined

  // 未登录 → 只能去登录页
  if (!token && to.path !== '/') {
    return next('/')
  }

  // 已登录但访问 / → 根据角色跳转主页
  if (token && to.path === '/') {
    return next(user?.role === 'admin' ? '/admin' : '/user')
  }

  // 有角色要求但用户角色不匹配 → 拒绝访问
  if (requiredRole && user?.role !== requiredRole) {
    return next(user?.role === 'admin' ? '/admin' : '/user')
  }

  next()
})

export default router
