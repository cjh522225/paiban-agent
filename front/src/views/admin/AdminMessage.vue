<template>
  <div class="message-management">
    <div class="content-wrapper">
      <!-- 左侧消息列表 -->
      <div class="message-list-section">
        <!-- 操作控制区 -->
        <div class="control-section">
          <el-button type="primary" class="send-btn" @click="openSendDialog">
            <el-icon>
              <Edit />
            </el-icon>
            发送通知
          </el-button>
        </div>

        <!-- 消息列表 -->
        <div class="list-card">
          <div class="list-header">
            <span class="list-title">已发送通知</span>
            <span class="list-count">共 {{ messageList.length }} 条</span>
          </div>

          <div class="list-container">
            <div v-for="(item, index) in messageList" :key="index" class="message-item"
              :class="{ 'is-selected': selectedMessage?.id === item.id }" @click="selectMessage(item)">
              <div class="message-left">
                <div class="message-icon" :style="{ background: getTypeColor(item.type) + '20' }">
                  <el-icon :size="18" :color="getTypeColor(item.type)">
                    <component :is="getMessageIcon(item.type)" />
                  </el-icon>
                </div>
              </div>

              <div class="message-content">
                <div class="message-header">
                  <span class="message-title">{{ item.title }}</span>
                  <el-tag :type="getMessageTagType(item.type)" size="small" effect="light">
                    {{ item.typeName }}
                  </el-tag>
                </div>
                <div class="message-summary">{{ item.summary }}</div>
                <div class="message-footer">
                  <span class="message-time">{{ item.sendTime }}</span>
                  <span class="message-stats">
                    <el-icon>
                      <View />
                    </el-icon>
                    {{ item.viewCount }}
                  </span>
                </div>
              </div>
            </div>

            <div v-if="messageList.length === 0" class="empty-state">
              <el-icon :size="64" color="#d1d5db">
                <Message />
              </el-icon>
              <p>暂无通知消息</p>
              <el-button type="primary" @click="openSendDialog">发送通知</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧消息详情/发送 -->
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
              <el-button size="small" @click="handleEdit(selectedMessage)">
                <el-icon>
                  <Edit />
                </el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" @click="handleDelete(selectedMessage)">
                <el-icon>
                  <Delete />
                </el-icon>
                撤销发送
              </el-button>
            </div>
          </div>

          <div class="detail-meta">
            <span class="meta-item">
              <el-icon>
                <Clock />
              </el-icon>
              {{ selectedMessage.fullSendTime }}
            </span>
            <span class="meta-item">
              <el-icon>
                <Flag />
              </el-icon>
              {{ selectedMessage.typeName }}
            </span>
            <span class="meta-item">
              <el-icon>
                <User />
              </el-icon>
              {{ selectedMessage.sender }}
            </span>
          </div>

          <div class="detail-stats">
            <div class="stat-item">
              <div class="stat-icon">
                <el-icon>
                  <View />
                </el-icon>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ selectedMessage.viewCount }}</span>
                <span class="stat-label">已查看</span>
              </div>
            </div>
            <div class="stat-item">
              <div class="stat-icon">
                <el-icon>
                  <Checked />
                </el-icon>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ selectedMessage.readCount }}</span>
                <span class="stat-label">已读</span>
              </div>
            </div>
            <div class="stat-item">
              <div class="stat-icon">
                <el-icon>
                  <User />
                </el-icon>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ selectedMessage.targetCount }}</span>
                <span class="stat-label">通知人数</span>
              </div>
            </div>
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
        </div>

        <div v-else class="empty-detail">
          <el-icon :size="80" color="#d1d5db">
            <Message />
          </el-icon>
          <p>请选择一条消息查看详情</p>
        </div>
      </div>
    </div>

    <!-- 发送通知对话框（原功能保持不变） -->
    <el-dialog v-model="showSendDialog" title="发送通知" width="640px" :close-on-click-modal="false">
      <el-form ref="sendFormRef" :model="sendForm" :rules="sendFormRules" label-width="90px" label-position="left">
        <el-form-item label="通知类型" prop="type">
          <el-select v-model="sendForm.type" placeholder="请选择通知类型" style="width: 100%">
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
        </el-form-item>

        <el-form-item label="通知标题" prop="title">
          <el-input v-model="sendForm.title" placeholder="请输入通知标题" />
        </el-form-item>

        <el-form-item label="通知对象" prop="targetType">
          <el-radio-group v-model="sendForm.targetType">
            <el-radio label="all">全体人员</el-radio>
            <el-radio label="dormitory">宿舍及办公室值班人员</el-radio>
            <el-radio label="office">办公室及宿舍值班人员</el-radio>
            <el-radio label="custom">自定义</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="sendForm.targetType === 'custom'" label="选择人员">
          <el-select v-model="sendForm.targetUsers" multiple placeholder="请选择通知人员" style="width: 100%">
            <el-option v-for="user in availableUsers" :key="user.id" :label="`${user.realName}(${user.username})`" :value="user.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="通知内容" prop="content">
          <el-input v-model="sendForm.content" type="textarea" :rows="6" placeholder="请输入通知内容" />
        </el-form-item>

        <el-form-item label="附件">
          <el-upload class="attachment-uploader" drag multiple :limit="5"
            :http-request="handleSendAttachmentUpload" :file-list="sendFileList">
            <el-icon class="el-icon--upload">
              <UploadFilled />
            </el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 jpg/png/pdf 格式，单个文件不超过 5MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showSendDialog = false">取消</el-button>
        <el-button type="info" @click="handleSaveDraft">保存草稿</el-button>
        <el-button type="primary" @click="handleSend">
          <el-icon>
            <Promotion />
          </el-icon>
          发送
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑消息对话框（独立新增表单，不复用发送表单） -->
    <el-dialog v-model="showEditDialog" title="编辑消息" width="640px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" :rules="editFormRules" label-width="90px" label-position="left">
        <el-form-item label="通知类型" prop="type">
          <el-select v-model="editForm.type" placeholder="请选择通知类型" style="width: 100%">
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
        </el-form-item>

        <el-form-item label="通知标题" prop="title">
          <el-input v-model="editForm.title" placeholder="请输入通知标题" />
        </el-form-item>

        <el-form-item label="通知对象">
          <!-- 编辑时通知对象不可更改 -->
          <el-input :value="editTargetLabel" disabled style="width: 100%" />
        </el-form-item>

        <el-form-item label="通知内容" prop="content">
          <el-input v-model="editForm.content" type="textarea" :rows="6" placeholder="请输入通知内容" />
        </el-form-item>

        <el-form-item label="附件">
          <el-upload class="attachment-uploader" drag multiple :limit="5"
            :http-request="handleEditAttachmentUpload" :file-list="editFileList" :on-remove="handleEditFileRemove">
            <el-icon class="el-icon--upload">
              <UploadFilled />
            </el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 jpg/png/pdf 格式，单个文件不超过 5MB；编辑后可新增附件
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmEdit">
          <el-icon>
            <Promotion />
          </el-icon>
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Message,
  Edit,
  Delete,
  Clock,
  Document,
  Flag,
  User,
  View,
  Checked,
  UploadFilled,
  Promotion,
  Bell,
  Check,
  InfoFilled,
  Warning,
  ChatDotRound,
} from '@element-plus/icons-vue'
import { getMessageList, addMessage, deleteMessage, getDraft, saveDraft, deleteDraft, updateMessage, uploadAttachment } from '@/api/message'
import { getStaffList } from '@/api/user'

const messageTypes = [
  { label: '值班通知', value: 'duty', icon: Bell, color: '#f59e0b' },
  { label: '审批通知', value: 'approval', icon: Check, color: '#10b981' },
  { label: '系统通知', value: 'system', icon: InfoFilled, color: '#3b82f6' },
  { label: '紧急通知', value: 'urgent', icon: Warning, color: '#ef4444' },
  { label: '消息回复', value: 'reply', icon: ChatDotRound, color: '#8b5cf6' },
]

const showSendDialog = ref(false)
const showEditDialog = ref(false)
const selectedMessage = ref<any>(null)

// ========== 发送表单 ==========
const sendFormRef = ref()
const sendForm = ref({
  type: 'duty',
  title: '',
  targetType: 'all',
  targetUsers: [] as number[],
  content: '',
  attachment: [] as string[],
})
const sendFileList = ref<any[]>([])

const sendFormRules = {
  type: [{ required: true, message: '请选择通知类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知内容', trigger: 'blur' }],
}

// ========== 编辑表单（独立） ==========
const editFormRef = ref()
const editForm = ref({
  id: null as number | null,
  type: 'duty',
  title: '',
  content: '',
  attachment: [] as string[],   // 附件路径数组
  receivers: '',                // 原通知对象（不可改）
})
const editFileList = ref<any[]>([])  // 供 el-upload 展示的 file-list
const editFilesRemoved: string[] = []  // 被删除的附件路径（编辑时移除的）

const editFormRules = {
  type: [{ required: true, message: '请选择通知类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知内容', trigger: 'blur' }],
}

// 编辑弹窗中通知对象的只读显示文本
const editTargetLabel = computed(() => {
  const r = editForm.value.receivers
  if (!r || r === '') return '全体人员'
  const count = r.split(',').filter(Boolean).length
  return `自定义（${count}人）`
})

// 用户列表
const availableUsers = ref<any[]>([])

// 消息列表
const messageList = ref<any[]>([])

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

// 选择消息
const selectMessage = (item: any) => {
  selectedMessage.value = item
}

// ========== 附件工具 ==========
const isImage = (path: string) => /\.(jpg|jpeg|png)$/i.test(path)
const getFileName = (path: string) => path.split('/').pop() || path

// 发送表单：上传附件
const handleSendAttachmentUpload = async (options: any) => {
  try {
    const res = await uploadAttachment(options.file)
    if (res.code === 200 && res.data) {
      sendForm.value.attachment.push(res.data)
      ElMessage.success('附件上传成功')
      options.onSuccess?.(res)
    } else {
      ElMessage.error(res.message || '上传失败')
      options.onError?.(new Error(res.message))
    }
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
    options.onError?.(e)
  }
}

// 编辑表单：上传附件
const handleEditAttachmentUpload = async (options: any) => {
  try {
    const res = await uploadAttachment(options.file)
    if (res.code === 200 && res.data) {
      editForm.value.attachment.push(res.data)
      ElMessage.success('附件上传成功')
      options.onSuccess?.(res)
    } else {
      ElMessage.error(res.message || '上传失败')
      options.onError?.(new Error(res.message))
    }
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
    options.onError?.(e)
  }
}

// 编辑表单：移除附件
const handleEditFileRemove = (file: any) => {
  const path = file.url || file.response?.data || ''
  if (path) {
    const idx = editForm.value.attachment.indexOf(path)
    if (idx > -1) editForm.value.attachment.splice(idx, 1)
  }
}

// ========== 编辑消息 ==========
const handleEdit = (item: any) => {
  // 初始化编辑表单（独立表单，不复用发送表单）
  editForm.value = {
    id: item.id,
    type: item.type || 'duty',
    title: item.title || '',
    content: item.content || '',
    attachment: Array.isArray(item.attachment) ? [...item.attachment] : [],
    receivers: item.receivers || '',
  }
  // 构造 file-list 展示已有附件
  editFileList.value = editForm.value.attachment.map((p: string) => ({
    name: getFileName(p),
    url: p,
    response: { data: p },
  }))
  editFilesRemoved.length = 0
  showEditDialog.value = true
}

// 确认修改
const confirmEdit = async () => {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!editForm.value.id) return

  try {
    const payload = {
      type: editForm.value.type,
      title: editForm.value.title,
      content: editForm.value.content,
      attachment: editForm.value.attachment.join(','),
      status: 1,
    }
    await updateMessage(editForm.value.id, payload)
    ElMessage.success('修改成功')
    showEditDialog.value = false
    await loadMessageList()
    // 刷新右侧详情
    const updated = messageList.value.find(m => m.id === editForm.value.id)
    if (updated) selectedMessage.value = updated
  } catch (e: any) {
    ElMessage.error(e.message || '修改失败')
  }
}

// ========== 撤销发送（原删除按钮改名） ==========
const handleDelete = async (item: any) => {
  try {
    await ElMessageBox.confirm('撤销后该通知将无法查看，确定撤销发送吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    await deleteMessage(item.id)

    const index = messageList.value.findIndex(m => m.id === item.id)
    if (index > -1) {
      messageList.value.splice(index, 1)
    }
    if (selectedMessage.value?.id === item.id) {
      selectedMessage.value = null
    }
    ElMessage.success('已撤销发送')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '撤销失败')
    }
  }
}

// ========== 发送通知 ==========
const openSendDialog = () => {
  // 重置发送表单并加载草稿
  sendForm.value = {
    type: 'duty',
    title: '',
    targetType: 'all',
    targetUsers: [],
    content: '',
    attachment: [],
  }
  sendFileList.value = []
  showSendDialog.value = true
  loadDraft()
}

// 加载草稿
const loadDraft = async () => {
  try {
    const res = await getDraft()
    if (res.code === 200 && res.data) {
      const draft = res.data
      sendForm.value.type = draft.type || 'duty'
      sendForm.value.title = draft.title || ''
      sendForm.value.content = draft.content || ''
    }
  } catch {
    // 无草稿或加载失败，忽略
  }
}

// 保存草稿
const handleSaveDraft = async () => {
  try {
    await saveDraft({
      type: sendForm.value.type,
      title: sendForm.value.title || '未命名草稿',
      content: sendForm.value.content,
    })
    ElMessage.info('草稿已保存')
    showSendDialog.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '保存草稿失败')
  }
}

// 发送通知
const handleSend = async () => {
  try {
    const valid = await sendFormRef.value?.validate().catch(() => false)
    if (!valid) return

    await ElMessageBox.confirm('确定发送该通知吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })

    let receivers: string[] = []
    if (sendForm.value.targetType === 'custom') {
      receivers = [...sendForm.value.targetUsers.map(String)]
    }

    const payload: any = {
      type: sendForm.value.type,
      title: sendForm.value.title,
      targetType: sendForm.value.targetType,
      targetUsers: sendForm.value.targetUsers,
      receivers: receivers.join(','),
      content: sendForm.value.content,
      attachment: sendForm.value.attachment.join(','),
      status: 1,
    }

    await addMessage(payload)

    // 发送成功后删除草稿
    try {
      await deleteDraft()
    } catch { /* 删草稿失败不阻塞发送流程 */ }

    ElMessage.success('通知已发送')
    showSendDialog.value = false
    sendFormRef.value?.resetFields()
    sendForm.value.targetUsers = []
    sendFileList.value = []
    await loadMessageList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '发送失败')
    }
  }
}

// ========== 列表加载 ==========
const loadMessageList = async () => {
  try {
    const res = await getMessageList()
    // 转换后端数据格式为前端期望的格式
    messageList.value = (res.data || []).map((item: any) => {
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
        fullSendTime: item.createTime ? item.createTime.replace('T', ' ') : '',
        sender: item.senderName || '未知',
        viewCount: item.viewCount ?? 0,
        readCount: item.readCount ?? 0,
        targetCount: item.targetCount ?? 0,
        createTime: item.createTime,
      }
    })
  } catch (error: any) {
    ElMessage.error(error.message || '加载消息列表失败')
  }
}

// 加载人员列表
const loadStaffList = async () => {
  try {
    const res = await getStaffList()
    availableUsers.value = Array.isArray(res.data) ? res.data : []
  } catch (error: any) {
    console.error('加载人员列表失败:', error)
    availableUsers.value = []
  }
}

// 初始化
onMounted(() => {
  loadMessageList()
  loadStaffList()
})

// 组件从缓存中激活时重新加载数据
onActivated(() => {
  loadMessageList()
})
</script>

<style scoped>
.message-management {
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
  width: 420px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}

/* 操作控制区 */
.control-section {
  flex-shrink: 0;
}

.send-btn {
  width: 100%;
  height: 44px;
  font-size: 14px;
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

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
}

.list-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.list-count {
  font-size: 12px;
  color: #9ca3af;
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
}

.message-item:hover {
  background: #e0f2fe;
  border-color: #7dd3fc;
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.message-item.is-selected {
  background: #dbeafe;
  border-color: #3b82f6;
}

.message-item:last-child {
  margin-bottom: 0;
}

.message-left {
  flex-shrink: 0;
}

.message-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
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
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
}

.message-time {
  font-size: 11px;
  color: #9ca3af;
}

.message-stats {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #9ca3af;
  margin-left: auto;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #9ca3af;
}

.empty-state p {
  margin: 16px 0 24px;
  font-size: 14px;
}

/* ========== 右侧详情区域 ========== */
.message-detail-section {
  flex: 1;
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
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.detail-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.detail-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
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

.detail-stats {
  display: flex;
  gap: 16px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-bottom: 1px solid #e5e7eb;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 10px;
  flex: 1;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #dbeafe;
  color: #2563eb;
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
}

.stat-label {
  font-size: 11px;
  color: #9ca3af;
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
  color: #9ca3af;
}

.empty-detail p {
  margin-top: 16px;
  font-size: 14px;
}

/* 类型选项 */
.type-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* 附件上传 */
.attachment-uploader {
  width: 100%;
}
</style>
