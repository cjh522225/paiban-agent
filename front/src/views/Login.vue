<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-left">
        <div class="left-content">
          <div class="icon-wrapper">
            <el-icon :size="64" color="#64b5f6"><Document /></el-icon>
          </div>
          <h1 class="title">党群值班智能排班系统</h1>
          <p class="subtitle">智能化排班管理,让值班安排更高效、更公平、更便捷</p>
        </div>
      </div>
      <div class="login-right">
        <div class="right-content">
          <div class="form-header">
            <h2 class="form-title">欢迎登录</h2>
            <p class="form-subtitle">管理员请用admin，学生请用学号</p>
          </div>
          <form @submit.prevent="handleLogin" class="login-form">
            <div class="form-item">
              <label class="form-label">学号</label>
              <el-input v-model="loginForm.account" placeholder="请输入学号" size="large" clearable>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </div>
            <div class="form-item">
              <label class="form-label">密码</label>
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" size="large" show-password @keyup.enter="handleLogin">
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </div>
            <div class="form-options">
              <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
              <a href="#" class="forgot-link">忘记密码?</a>
            </div>
            <button type="submit" :disabled="loading || !loginForm.account || !loginForm.password" class="login-btn">
              <span v-if="loading" class="flex items-center justify-center gap-2">
                <el-icon class="is-loading"><Loading /></el-icon>登录中...
              </span>
              <span v-else>登 录</span>
            </button>
          </form>
          <p class="copyright">© 2026 党群值班智能排班系统。All rights reserved.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { User, Lock, Loading, Document } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { markViewed } from '@/api/message'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const loginForm = reactive({ account: '', password: '', remember: false })

const handleLogin = async () => {
  if (!loginForm.account || !loginForm.password) return
  loading.value = true
  try {
    const res = await login({ username: loginForm.account, password: loginForm.password })
    const { token, username, realName, role } = res.data
    localStorage.setItem('token', token)
    userStore.setUser(realName || username, role)
    // 登录成功后：把所有未查看的新消息标记为已查看（仅普通用户，管理员无此操作）
    if (role !== 'admin') {
      try { await markViewed() } catch { /* 标记失败不影响登录 */ }
    }
    router.push(role === 'admin' ? '/admin' : '/user')
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}
</script>

<style scoped>
* { margin: 0; padding: 0; box-sizing: border-box; }
.login-container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; }
.login-card { display: flex; width: 100%; max-width: 900px; height: 500px; background: #fff; border-radius: 16px; overflow: hidden; box-shadow: 0 25px 50px rgba(0,0,0,.15); }
.login-left { flex: 1; background: linear-gradient(135deg, #1e88e5 0%, #1565c0 100%); display: flex; align-items: center; justify-content: center; padding: 40px; position: relative; overflow: hidden; }
.login-left::before { content: ''; position: absolute; top: -50%; left: -50%; width: 200%; height: 200%; background: radial-gradient(circle, rgba(255,255,255,.1) 0%, transparent 70%); animation: pulse 15s ease-in-out infinite; }
@keyframes pulse { 0%,100% { transform: scale(1) rotate(0deg); } 50% { transform: scale(1.1) rotate(180deg); } }
.left-content { position: relative; z-index: 1; text-align: center; color: #fff; }
.icon-wrapper { width: 100px; height: 100px; margin: 0 auto 24px; background: rgba(255,255,255,.15); border-radius: 20px; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(10px); }
.title { font-size: 24px; font-weight: 600; margin-bottom: 12px; line-height: 1.4; }
.subtitle { font-size: 13px; opacity: .9; line-height: 1.6; max-width: 280px; margin: 0 auto; }
.login-right { flex: 1; background: #fff; display: flex; align-items: center; justify-content: center; padding: 40px; }
.right-content { width: 100%; max-width: 320px; }
.form-header { text-align: center; margin-bottom: 32px; }
.form-title { font-size: 22px; font-weight: 600; color: #1f2937; margin-bottom: 8px; }
.form-subtitle { font-size: 13px; color: #9ca3af; }
.login-form { display: flex; flex-direction: column; gap: 20px; }
.form-item { display: flex; flex-direction: column; gap: 8px; }
.form-label { font-size: 14px; font-weight: 500; color: #374151; }
.form-options { display: flex; justify-content: space-between; align-items: center; }
.forgot-link { font-size: 13px; color: #6366f1; text-decoration: none; }
.forgot-link:hover { color: #4f46e5; }
.login-btn { width: 100%; height: 44px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border: none; border-radius: 8px; color: #fff; font-size: 15px; font-weight: 500; cursor: pointer; transition: all .3s; box-shadow: 0 4px 12px rgba(102,126,234,.35); }
.login-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(102,126,234,.45); }
.login-btn:active:not(:disabled) { transform: translateY(0); }
.login-btn:disabled { background: #d1d5db; cursor: not-allowed; box-shadow: none; }
.copyright { text-align: center; font-size: 12px; color: #9ca3af; margin-top: 24px; }
@media (max-width: 768px) { .login-card { flex-direction: column; height: auto; max-width: 400px; } .login-left { padding: 30px; } .login-right { padding: 30px; } .title { font-size: 20px; } .subtitle { font-size: 12px; } }
</style>
