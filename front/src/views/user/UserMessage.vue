<template>
  <div class="message-page">
    <div class="content-wrapper">
      <!-- 左侧消息列表 -->
      <div class="message-list-section">
        <!-- 筛选控制区 -->
        <div class="filter-section">
          <div class="filter-card">
            <div class="filter-item">
              <label class="filter-label">消息类型</label>
              <el-select v-model="selectedType" placeholder="全部类型" class="filter-select" clearable>
                <el-option v-for="type in messageTypes" :key="type.value" :label="type.label" :value="type.value">
                  <template #default>
                    <span class="type-option">
                      <el-icon :color="type.color">
                        <component :is="type.icon" />
                      </el-icon>
                      <span>{{ type.label }}</span>
                    </span>
                  </template>
                </el-option>
              </el-select>
            </div>

            <div class="filter-item">
              <label class="filter-label">消息状态</label>
              <el-select v-model="selectedStatus" placeholder="全部状态" class="filter-select" clearable>
                <el-option label="未读" value="unread" />
                <el-option label="已读" value="read" />
              </el-select>
            </div>

            <div class="filter-actions">
              <el-button @click="handleQuery">查询</el-button>
              <el-button type="primary" @click="markAllAsRead" :disabled="unreadCount === 0">
                全部已读
              </el-button>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div class="list-card">
          <div class="list-container">
            <div v-for="(item, index) in filteredMessageList" :key="index" class="message-item"
              :class="{ 'is-unread': !item.isRead, 'is-selected': selectedMessage?.id === item.id }"
              @click="selectMessage(item)">
              <div class="message-left">
                <div class="message-icon" :style="{ background: getTypeColor(item.type) + '20' }">
                  <el-icon :size="20" :color="getTypeColor(item.type)">
                    <component :is="getMessageIcon(item.type)" />
                  </el-icon>
                </div>
                <div v-if="!item.isRead" class="unread-dot"></div>
              </div>

              <div class="message-content">
                <div class="message-header">
                  <span class="message-title">{{ item.title }}</span>
                  <span class="message-time">{{ item.time }}</span>
                </div>
                <div class="message-summary">{{ item.summary }}</div>
                <div class="message-footer">
                  <el-tag :type="getMessageTagType(item.type)" size="small" effect="light">
                    {{ item.typeName }}
                  </el-tag>
                  <span v-if="!item.isRead" class="unread-text">未读</span>
                </div>
              </div>
            </div>

            <div v-if="filteredMessageList.length === 0" class="empty-state">
              <el-icon :size="64" color="#a0a0a0">
                <Message />
              </el-icon>
              <p>暂无消息</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧消息详情 -->
      <div class="message-detail-section">
        <div v-if="selectedMessage" class="detail-card">
          <div class="detail-header">
            <div class="detail-title-row">
              <el-icon :size="24" :color="getTypeColor(selectedMessage.type)">
                <component :is="getMessageIcon(selectedMessage.type)" />
              </el-icon>
              <h3 class="detail-title">{{ selectedMessage.title }}</h3>
            </div>
            <div class="detail-actions">
              <el-button v-if="selectedMessage.isRead !== 1" type="primary" size="small"
                @click="markAsRead(selectedMessage)">
                标记为已读
              </el-button>
              <el-button size="small" @click="deleteMessage(selectedMessage)">
                <el-icon>
                  <Delete />
                </el-icon>
              </el-button>
            </div>
          </div>

          <div class="detail-meta">
            <span class="meta-item">
              <el-icon>
                <Clock />
              </el-icon>
              {{ selectedMessage.fullTime }}
            </span>
            <span class="meta-item">
              <el-icon>
                <Flag />
              </el-icon>
              {{ selectedMessage.typeName }}
            </span>
            <span v-if="selectedMessage.sender" class="meta-item">
              <el-icon>
                <User />
              </el-icon>
              {{ selectedMessage.sender }}
            </span>
          </div>

          <div class="detail-content">
            <p v-html="selectedMessage.content"></p>
            <!-- 附件展示：图片正常渲染，PDF 链接打开 -->
            <div v-if="selectedMessage.attachment?.length" class="detail-content-attachment">
              <div v-for="(file, idx) in selectedMessage.attachment" :key="idx" class="content-attach-item">
                <a v-if="isImage(file)" :href="file" target="_blank">
                  <img :src="file" alt="附件图片" class="attach-img" />
                </a>
                <a v-else :href="file" target="_blank" class="attach-pdf">
                  <el-icon><Document /></el-icon>
                  <span>{{ getFileName(file) }}</span>
                </a>
              </div>
            </div>
          </div>

          <div v-if="selectedMessage.relatedSchedule" class="detail-related">
            <div class="related-title">
              <el-icon>
                <Calendar />
              </el-icon>
              <span>相关值班安排</span>
            </div>
            <div class="related-card">
              <div class="related-item">
                <span class="related-date">{{ selectedMessage.relatedSchedule.date }}</span>
                <span class="related-type">{{ selectedMessage.relatedSchedule.type }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="empty-detail">
          <el-icon :size="80" color="#a0a0a0">
            <Message />
          </el-icon>
          <p>请选择一条消息查看详情</p>
        </div>
      </div>
    </div>

    <!-- 消息设置对话框 -->
    <el-dialog v-model="showSettings" title="消息设置" width="480px">
      <div class="settings-content">
        <div class="settings-section">
          <h4 class="settings-title">
            <el-icon>
              <Bell />
            </el-icon>
            通知开关
          </h4>
          <div class="setting-item">
            <div class="setting-info">
              <span class="setting-label">值班通知</span>
              <span class="setting-desc">值班安排变动时接收通知</span>
            </div>
            <el-switch v-model="settings.dutyNotify" />
          </div>
          <div class="setting-item">
            <div class="setting-info">
              <span class="setting-label">审批通知</span>
              <span class="setting-desc">请假审批结果通知</span>
            </div>
            <el-switch v-model="settings.approvalNotify" />
          </div>
          <div class="setting-item">
            <div class="setting-info">
              <span class="setting-label">系统通知</span>
              <span class="setting-desc">系统维护、更新等通知</span>
            </div>
            <el-switch v-model="settings.systemNotify" />
          </div>
        </div>

        <div class="settings-section">
          <h4 class="settings-title">
            <el-icon>
              <Platform />
            </el-icon>
            通知方式
          </h4>
          <div class="setting-item">
            <div class="setting-info">
              <span class="setting-label">站内消息</span>
              <span class="setting-desc">在网站上显示消息通知</span>
            </div>
            <el-switch v-model="settings.inApp" disabled />
          </div>
          <div class="setting-item">
            <div class="setting-info">
              <span class="setting-label">邮件通知</span>
              <span class="setting-desc">发送邮件到绑定的邮箱</span>
            </div>
            <el-switch v-model="settings.email" />
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="showSettings = false">取消</el-button>
        <el-button type="primary" @click="saveSettings">保存设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMessageList, markMessageRead, markAllRead, deleteMessage as apiDeleteMessage } from '@/api/message'
import {
  Message,
  Bell,
  Clock,
  Document,
  Delete,
  Flag,
  User,
  Calendar,
  Platform,
  Warning,
  Check,
  InfoFilled,
  ChatDotRound,
  Star,
} from '@element-plus/icons-vue'

const userStore = useUserStore()

const messageTypes = [
  { label: '值班通知', value: 'duty', icon: 'Bell', color: '#f59e0b' },
  { label: '审批通知', value: 'approval', icon: 'Check', color: '#10b981' },
  { label: '系统通知', value: 'system', icon: 'InfoFilled', color: '#3b82f6' },
  { label: '紧急通知', value: 'urgent', icon: 'Warning', color: '#ef4444' },
  { label: '消息回复', value: 'reply', icon: 'ChatDotRound', color: '#8b5cf6' },
]

const selectedType = ref('')
const selectedStatus = ref('')
const selectedMessage = ref<any>(null)
const showSettings = ref(false)

// 消息设置
const settings = ref({
  dutyNotify: true,
  approvalNotify: true,
  systemNotify: true,
  inApp: true,
  email: true,
})

// 消息列表
const messageList = ref<any[]>([])

// 筛选后的消息列表
const filteredMessageList = computed(() => {
  let list = messageList.value
  if (selectedType.value) {
    list = list.filter(item => item.type === selectedType.value)
  }
  if (selectedStatus.value === 'unread') {
    list = list.filter(item => !item.isRead)
  } else if (selectedStatus.value === 'read') {
    list = list.filter(item => item.isRead)
  }
  return list
})

// 未读消息数量
const unreadCount = computed(() => messageList.value.filter(item => !item.isRead).length)

// 获取消息图标
const getMessageIcon = (type: string) => {
  const icons: Record<string, any> = {
    duty: Bell,
    approval: Check,
    system: InfoFilled,
    urgent: Warning,
    reply: ChatDotRound,
  }
  return icons[type] || Message
}

// 获取类型颜色
const getTypeColor = (type: string) => {
  const colors: Record<string, string> = {
    duty: '#f59e0b',
    approval: '#10b981',
    system: '#3b82f6',
    urgent: '#ef4444',
    reply: '#8b5cf6',
  }
  return colors[type] || '#6b7280'
}

// 获取消息标签类型
const getMessageTagType = (type: string) => {
  const types: Record<string, any> = {
    duty: 'warning',
    approval: 'success',
    system: 'primary',
    urgent: 'danger',
    reply: 'info',
  }
  return types[type] || 'info'
}

// 附件工具
const isImage = (path: string) => /\.(jpg|jpeg|png)$/i.test(path)
const getFileName = (path: string) => path.split('/').pop() || path

// 选择消息
const selectMessage = (item: any) => {
  selectedMessage.value = item
}

// 标记为已读
const markAsRead = async (item: any) => {
  try {
    await markMessageRead(item.id)
    item.isRead = 1
    ElMessage.success('已标记为已读')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 全部标记为已读
const markAllAsRead = async () => {
  try {
    await markAllRead()
    messageList.value.forEach(item => {
      item.isRead = 1
    })
    ElMessage.success('全部标记为已读')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 删除消息
const deleteMessage = (item: any) => {
  ElMessageBox.confirm('确定要删除该消息吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      // 先调用后端删除接口，成功后才在本地移除
      await apiDeleteMessage(item.id)
      const index = messageList.value.findIndex(m => m.id === item.id)
      if (index > -1) {
        messageList.value.splice(index, 1)
      }
      if (selectedMessage.value?.id === item.id) {
        selectedMessage.value = null
      }
      ElMessage.success('已删除')
    } catch (error: any) {
      ElMessage.error(error?.message || '删除失败')
    }
  }).catch(() => { })
}

// 查询
const handleQuery = () => {
  loadMessageData()
}

// 加载消息数据
const loadMessageData = async () => {
  try {
    const res = await getMessageList()
    if (res.code === 200 && Array.isArray(res.data)) {
      // 转换后端数据格式为前端期望的格式
      messageList.value = res.data.map((item: any) => {
        // 消息类型映射
        const typeMap: Record<string, string> = {
          duty: '值班通知',
          approval: '审批通知',
          system: '系统通知',
          urgent: '紧急通知',
          reply: '消息回复',
        }

        return {
          id: item.id,
          title: item.title,
          content: item.content,
          type: item.type,
          typeName: typeMap[item.type] || item.type,
          senderName: item.senderName,
          senderId: item.senderId,
          receivers: item.receivers,
          attachment: item.attachment ? item.attachment.split(',').filter(Boolean) : [],
          status: item.status,
          summary: item.content ? item.content.substring(0, 50) + '...' : '',
          sendTime: item.createTime ? item.createTime.replace('T', ' ').substring(0, 16) : '',
          fullTime: item.createTime ? item.createTime.replace('T', ' ') : '',
          sender: item.senderName || '未知',
          isRead: item.isRead ?? 0, // 后端返回该用户是否已读
          createTime: item.createTime,
        }
      })
    }
  } catch (error) {
    ElMessage.error('加载消息数据失败')
  }
}

// 保存设置
const saveSettings = () => {
  ElMessage.success('设置已保存')
  showSettings.value = false
}

onMounted(() => {
  loadMessageData()
})

// 组件从缓存中激活时重新加载数据
onActivated(() => {
  loadMessageData()
})
</script>

<style scoped>
.message-page {
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

/* ========== 左侧消息列表区域 ========== */
.message-list-section {
  flex: 6;
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
  display: flex;
  gap: 8px;
}

/* 列表卡片 */
.list-card {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.list-container {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

/* 消息项 */
.message-item {
  display: flex;
  gap: 12px;
  padding: 14px;
  background: #f8fafc;
  border-radius: 10px;
  margin-bottom: 10px;
  transition: all 0.2s;
  cursor: pointer;
  border: 1px solid #e2e8f0;
  position: relative;
}

.message-item:hover {
  background: #e0f2fe;
  border-color: #7dd3fc;
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.message-item.is-unread {
  background: #fff;
  border-left: 3px solid #3b82f6;
}

.message-item.is-selected {
  background: #dbeafe;
  border-color: #3b82f6;
}

.message-item:last-child {
  margin-bottom: 0;
}

.message-left {
  position: relative;
  flex-shrink: 0;
}

.message-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.unread-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  background: #ef4444;
  border-radius: 50%;
  border: 2px solid #fff;
}

.message-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.message-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.message-time {
  font-size: 12px;
  color: #7b7b7b;
  flex-shrink: 0;
  margin-left: 8px;
}

.message-summary {
  font-size: 13px;
  color: #6b7280;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.unread-text {
  font-size: 11px;
  color: #3b82f6;
  font-weight: 500;
  margin-left: auto;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #7b7b7b;
}

.empty-state p {
  margin-top: 16px;
  font-size: 14px;
}

/* ========== 右侧消息详情区域 ========== */
.message-detail-section {
  flex: 4;
  min-width: 0;
}

.detail-card {
  height: 100%;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-header {
  padding: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.detail-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  flex: 1;
}

.detail-actions {
  display: flex;
  gap: 8px;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px 20px;
  background: #f8fafc;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #6b7280;
}

.detail-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  font-size: 14px;
  line-height: 1.8;
  color: #374151;
}

.detail-content p {
  margin-bottom: 12px;
}

.detail-content ul {
  margin: 12px 0;
  padding-left: 20px;
}

.detail-content li {
  margin-bottom: 6px;
}

/* 详情内容中的附件 */
.detail-content-attachment {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.content-attach-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #f8fafc;
}

.attach-img {
  width: 100%;
  max-width: 480px;
  display: block;
  margin: 0 auto;
}

.attach-pdf {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: #0369a1;
  text-decoration: none;
}

.attach-pdf:hover {
  background: #e0f2fe;
}

.detail-related {
  padding: 16px 20px;
  border-top: 1px solid #e5e7eb;
}

.related-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 12px;
}

.related-card {
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  padding: 12px;
}

.related-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.related-date {
  font-size: 13px;
  font-weight: 500;
  color: #0369a1;
}

.related-type {
  font-size: 12px;
  color: #6b7280;
  padding: 3px 8px;
  background: #e0f2fe;
  border-radius: 4px;
}

/* 空详情 */
.empty-detail {
  height: 100%;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #7b7b7b;
}

.empty-detail p {
  margin-top: 16px;
  font-size: 14px;
}

/* ========== 消息设置对话框 ========== */
.settings-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settings-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}

.setting-desc {
  font-size: 12px;
  color: #7b7b7b;
}

/* 类型选项 */
.type-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .content-wrapper {
    flex-direction: column;
  }

  .message-detail-section {
    min-height: 400px;
  }
}
</style>
