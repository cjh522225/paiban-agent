import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  // 从 localStorage 恢复用户信息
  const storedUser = localStorage.getItem('user')
  const initialUser = storedUser ? JSON.parse(storedUser) : { name: '', role: 'user' }

  const userName = ref<string>(initialUser.name || '')
  const role = ref<'admin' | 'user'>(initialUser.role || 'user')

  function setUser(name: string, userRole: 'admin' | 'user') {
    userName.value = name
    role.value = userRole
    // 保存到 localStorage
    localStorage.setItem('user', JSON.stringify({ name, role: userRole }))
  }

  function clearUser() {
    userName.value = ''
    role.value = 'user'
    // 清除所有登录相关 localStorage
    localStorage.removeItem('user')
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return {
    userName,
    role,
    setUser,
    clearUser,
  }
})
