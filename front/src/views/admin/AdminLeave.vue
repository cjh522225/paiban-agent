<template>
  <div class="leave-management">
    <div class="content-wrapper">
      <!-- 左侧请假申请列表 -->
      <div class="leave-list-section">
        <!-- 筛选控制区 -->
        <div class="filter-section">
          <div class="filter-card">
            <div class="filter-item">
              <label class="filter-label">值班类型</label>
              <el-select v-model="selectedDutyType" placeholder="全部类型" class="filter-select" clearable>
                <el-option v-for="duty in dutyTypes" :key="duty.value" :label="duty.label" :value="duty.value">
                  <template #default>
                    <span class="duty-option">
                      <el-icon v-if="duty.value === 'dormitory'">
                        <HomeFilled />
                      </el-icon>
                      <el-icon v-else>
                        <OfficeBuilding />
                      </el-icon>
                      <span>{{ duty.label }}</span>
                    </span>
                  </template>
                </el-option>
              </el-select>
            </div>

            <div class="filter-item">
              <label class="filter-label">请假类型</label>
              <el-select v-model="selectedType" placeholder="全部类型" class="filter-select" clearable>
                <el-option v-for="type in leaveTypes" :key="type.value" :label="type.label" :value="type.value" />
              </el-select>
            </div>

            <div class="filter-item">
              <label class="filter-label">审批状态</label>
              <el-select v-model="selectedStatus" placeholder="全部状态" class="filter-select" clearable>
                <el-option label="待审核" value="pending" />
                <el-option label="已通过" value="approved" />
                <el-option label="已拒绝" value="rejected" />
              </el-select>
            </div>

            <div class="filter-actions">
              <el-button type="primary" @click="handleQuery">查询</el-button>
            </div>
          </div>
        </div>

        <!-- 申请列表 -->
        <div class="list-card">
          <div class="list-header">
            <span class="list-title">请假申请</span>
            <el-badge :value="pendingCount" :hidden="pendingCount === 0" type="danger">
              <el-icon class="list-icon">
                <Document />
              </el-icon>
            </el-badge>
          </div>

          <div class="list-container">
            <div v-for="(item, index) in filteredLeaveList" :key="index" class="leave-item"
              :class="{ 'is-pending': item.status === 'pending', 'is-selected': selectedLeave?.id === item.id }"
              @click="selectLeave(item)">
              <div class="item-left">
                <div class="item-icon" :style="{ background: getDutyTypeColor(item.dutyType) + '20' }">
                  <el-icon :size="18" :color="getDutyTypeColor(item.dutyType)">
                    <HomeFilled v-if="item.dutyType === 'dormitory'" />
                    <OfficeBuilding v-else />
                  </el-icon>
                </div>
                <div v-if="item.status === 'pending'" class="pending-dot"></div>
              </div>

              <div class="item-content">
                <div class="item-header">
                  <span class="item-title">{{ item.userName }}</span>
                  <el-tag :type="getStatusType(item.status)" size="small" effect="light">
                    {{ item.statusText }}
                  </el-tag>
                </div>
                <div class="item-row">
                  <el-tag :type="getLeaveTypeTag(item.type)" size="small" effect="plain" class="type-tag">
                    {{ item.typeName }}
                  </el-tag>
                  <span class="item-days">{{ item.days }}天</span>
                </div>
                <div class="item-summary">{{ item.reason }}</div>
                <div class="item-footer">
                  <span class="item-date">{{ item.applyDate }}</span>
                  <span class="item-dorm">{{ item.dutyTypeName }}</span>
                </div>
              </div>
            </div>

            <div v-if="filteredLeaveList.length === 0" class="empty-state">
              <el-icon :size="64" color="#d1d5db">
                <Document />
              </el-icon>
              <p>暂无请假申请</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧审批详情 -->
      <div class="approval-section">
        <div v-if="selectedLeave" class="approval-card">
          <div class="approval-header">
            <div class="header-left">
              <el-avatar :size="48" :style="{ background: getDutyTypeColor(selectedLeave.dutyType) }">
                {{ selectedLeave.userName.charAt(0) }}
              </el-avatar>
              <div class="header-info">
                <h3 class="header-name">{{ selectedLeave.userName }}</h3>
                <p class="header-dept">{{ selectedLeave.department }}</p>
              </div>
            </div>
            <el-tag :type="getStatusType(selectedLeave.status)" size="large" effect="light">
              {{ selectedLeave.statusText }}
            </el-tag>
          </div>

          <div class="approval-body">
            <div class="info-section">
              <h4 class="section-title">
                <el-icon>
                  <Calendar />
                </el-icon>
                请假信息
              </h4>
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">值班类型</span>
                  <span class="info-value">
                    <el-tag :type="selectedLeave.dutyType === 'dormitory' ? 'warning' : 'primary'" size="small"
                      effect="light">
                      <el-icon>
                        <HomeFilled v-if="selectedLeave.dutyType === 'dormitory'" />
                        <OfficeBuilding v-else />
                      </el-icon>
                      {{ selectedLeave.dutyTypeName }}
                    </el-tag>
                  </span>
                </div>
                <div class="info-item">
                  <span class="info-label">请假类型</span>
                  <span class="info-value">{{ selectedLeave.typeName }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">请假时间</span>
                  <span class="info-value">{{ formatLeaveTime(selectedLeave) }}</span>
                </div>
              </div>
            </div>

            <div class="info-section">
              <h4 class="section-title">
                <el-icon>
                  <Document />
                </el-icon>
                请假事由
              </h4>
              <p class="reason-text">{{ selectedLeave.reason }}</p>
            </div>

            <div v-if="selectedLeave.attachment && selectedLeave.attachment.length > 0" class="info-section">
              <h4 class="section-title">
                <el-icon>
                  <Paperclip />
                </el-icon>
                附件证明
              </h4>
              <div class="attachment-list">
                <div v-for="(file, idx) in selectedLeave.attachment" :key="idx" class="attach-item">
                  <a v-if="isImage(file)" :href="file" target="_blank" class="attachment-file">
                    <img :src="file" alt="附件图片" class="attach-img" />
                  </a>
                  <a v-else :href="file" target="_blank" class="attachment-file">
                    <el-icon>
                      <Document />
                    </el-icon>
                    <span>{{ getFileName(file) }}</span>
                  </a>
                </div>
              </div>
            </div>

            <div v-if="selectedLeave.status !== 'pending'" class="info-section">
              <h4 class="section-title">
                <el-icon>
                  <Stamp />
                </el-icon>
                审批信息
              </h4>
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">审核人</span>
                  <span class="info-value">{{ selectedLeave.auditor }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">审核时间</span>
                  <span class="info-value">{{ selectedLeave.auditTime }}</span>
                </div>
                <div class="info-item full-width">
                  <span class="info-label">审核意见</span>
                  <span class="info-value" :class="{ 'text-red': selectedLeave.status === 'rejected' }">
                    {{ selectedLeave.auditComment }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="selectedLeave.status === 'pending'" class="approval-footer">
            <el-input v-model="auditComment" type="textarea" :rows="3" placeholder="请输入审核意见（选填）" maxlength="500"
              show-word-limit class="audit-input" />
            <div class="approval-actions">
              <el-button type="success" @click="handleApprove">
                <el-icon>
                  <CircleCheck />
                </el-icon>
                通过
              </el-button>
              <el-button type="danger" @click="handleReject">
                <el-icon>
                  <CircleClose />
                </el-icon>
                拒绝
              </el-button>
            </div>
          </div>
        </div>

        <div v-else class="empty-approval">
          <el-icon :size="80" color="#d1d5db">
            <Document />
          </el-icon>
          <p>请选择一条申请进行审批</p>
        </div>
      </div>
    </div>

    <!-- 审批确认对话框 -->
    <el-dialog v-model="showConfirmDialog" :title="confirmType === 'approve' ? '通过申请' : '拒绝申请'" width="480px"
      :close-on-click-modal="false">
      <div class="confirm-content">
        <p class="confirm-text">
          确定要{{ confirmType === 'approve' ? '通过' : '拒绝' }}
          <strong>{{ selectedLeave?.userName }}</strong> 的请假申请吗？
        </p>
        <el-input v-model="auditComment" type="textarea" :rows="3" maxlength="500" show-word-limit
          :placeholder="confirmType === 'approve' ? '请输入通过意见（选填）' : '请输入拒绝理由（必填）'" class="audit-input" />
      </div>
      <template #footer>
        <el-button @click="showConfirmDialog = false">取消</el-button>
        <el-button :type="confirmType === 'approve' ? 'success' : 'danger'" @click="confirmAudit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Document,
  Calendar,
  Paperclip,
  Stamp,
  CircleCheck,
  CircleClose,
  HomeFilled,
  OfficeBuilding,
} from '@element-plus/icons-vue'

const weekdays = [
  { label: '周一', value: 1 }, { label: '周二', value: 2 }, { label: '周三', value: 3 },
  { label: '周四', value: 4 }, { label: '周五', value: 5 }, { label: '周六', value: 6 }, { label: '周日', value: 7 },
]

function weekDayLabel(d: number | null) {
  const found = weekdays.find(w => w.value === d)
  return found ? found.label : '-'
}

// 请假时间显示：单天 → 2026-08-09，第22周，周日，教学楼办公室；多天 → 2026-08-09 ~ 2026-08-12，教学楼办公室
function formatLeaveTime(item: any) {
  if (item.endDate && item.endDate !== item.startDate) {
    return `${item.startDate} ~ ${item.endDate}，${item.locationName || ''}`
  }
  return `${item.startDate}，第${item.weekNumber ?? '-'}周，${weekDayLabel(item.dayOfWeek)}，${item.locationName || ''}`
}

// 附件工具
const isImage = (path: string) => /\.(jpg|jpeg|png)$/i.test(path)
const getFileName = (path: string) => path.split('/').pop() || path
import { getLeaveList, getPendingLeaveList, approveLeave } from '@/api/leave'

const dutyTypes = [
  { label: '宿舍值班', value: 'dormitory' },
  { label: '办公室值班', value: 'office' },
]

const leaveTypes = [
  { label: '病假', value: 'sick' },
  { label: '事假', value: 'personal' },
  { label: '调休', value: 'compensatory' },
  { label: '年假', value: 'annual' },
  { label: '婚假', value: 'marriage' },
  { label: '产假', value: 'maternity' },
  { label: '陪产假', value: 'paternity' },
  { label: '丧假', value: 'bereavement' },
]

const selectedDutyType = ref('')
const selectedType = ref('')
const selectedStatus = ref('')
const selectedLeave = ref<any>(null)
const showConfirmDialog = ref(false)
const confirmType = ref<'approve' | 'reject'>('approve')
const auditComment = ref('')
const loading = ref(false)

// 请假申请列表
const leaveList = ref<any[]>([])

// 筛选后的列表
const filteredLeaveList = computed(() => {
  let list = leaveList.value
  if (selectedDutyType.value) {
    list = list.filter(item => item.dutyType === selectedDutyType.value)
  }
  if (selectedType.value) {
    list = list.filter(item => item.type === selectedType.value)
  }
  if (selectedStatus.value) {
    list = list.filter(item => item.status === selectedStatus.value)
  }
  // 待审核的排在前面
  list.sort((a, b) => {
    if (a.status === 'pending' && b.status !== 'pending') return -1
    if (a.status !== 'pending' && b.status === 'pending') return 1
    return 0
  })
  return list
})

// 待审核数量
const pendingCount = computed(() => leaveList.value.filter(item => item.status === 'pending').length)

// 获取值班类型颜色
const getDutyTypeColor = (type: string) => {
  const colors: Record<string, string> = {
    dormitory: '#f59e0b',
    office: '#3b82f6',
  }
  return colors[type] || '#6b7280'
}

// 获取状态标签类型
const getStatusType = (status: string) => {
  const types: Record<string, any> = {
    pending: 'warning',
    approved: 'success',
    rejected: 'danger',
    cancelled: 'info',
  }
  return types[status] || 'info'
}

// 获取请假类型标签
const getLeaveTypeTag = (type: string) => {
  const types: Record<string, any> = {
    sick: 'danger',
    personal: 'warning',
    compensatory: 'primary',
    annual: 'success',
    marriage: 'pink',
    maternity: 'purple',
    paternity: 'cyan',
    bereavement: 'info',
  }
  return types[type] || 'info'
}

// 选择申请
const selectLeave = (item: any) => {
  selectedLeave.value = item
  auditComment.value = ''
}

// 查询
const handleQuery = () => {
  ElMessage.success('查询成功')
}

// 通过申请（保留详情里已填的审核意见，带入确认对话框）
const handleApprove = () => {
  confirmType.value = 'approve'
  showConfirmDialog.value = true
}

// 拒绝申请（保留详情里已填的审核意见，带入确认对话框）
const handleReject = () => {
  confirmType.value = 'reject'
  showConfirmDialog.value = true
}

// 确认审批
const confirmAudit = async () => {
  if (confirmType.value === 'reject' && !auditComment.value.trim()) {
    ElMessage.warning('请填写拒绝理由')
    return
  }

  try {
    await approveLeave({
      id: selectedLeave.value.id,
      status: confirmType.value === 'approve' ? 'approved' : 'rejected',
      remark: auditComment.value || (confirmType.value === 'approve' ? '同意' : '拒绝'),
    })

    ElMessage.success(confirmType.value === 'approve' ? '已通过申请' : '已拒绝申请')
    showConfirmDialog.value = false
    auditComment.value = ''
    // 重新加载数据
    await loadLeaveList()
    // 审批后右侧同步为最新状态，立即从"审核状态"变为"详情状态"
    const updated = leaveList.value.find(item => item.id === selectedLeave.value.id)
    if (updated) selectedLeave.value = updated
  } catch (error: any) {
    ElMessage.error(error.message || '审批失败')
  }
}

// 加载请假列表
const loadLeaveList = async () => {
  try {
    loading.value = true
    const res = await getLeaveList()
    // 转换后端数据格式为前端期望的格式
    leaveList.value = (res.data || []).map((item: any) => {
      // 请假类型映射
      const typeMap: Record<string, { name: string; text: string }> = {
        sick: { name: '病假', text: '病假' },
        personal: { name: '事假', text: '事假' },
        compensatory: { name: '调休', text: '调休' },
        annual: { name: '年假', text: '年假' },
        marriage: { name: '婚假', text: '婚假' },
        maternity: { name: '产假', text: '产假' },
        paternity: { name: '陪产假', text: '陪产假' },
        bereavement: { name: '丧假', text: '丧假' },
      }

      // 状态映射
      const statusMap: Record<string, string> = {
        pending: '待审核',
        approved: '已通过',
        rejected: '已拒绝',
        cancelled: '已撤销',
      }

      // 值班类型映射
      const dutyTypeMap: Record<string, string> = {
        dormitory: '宿舍值班',
        office: '办公室值班',
      }

      const typeInfo = typeMap[item.leaveType] || { name: item.leaveType, text: item.leaveType }

      return {
        id: item.id,
        userId: item.userId,
        userName: item.userName,
        dutyType: item.dutyType || 'office', // 默认为办公室
        dutyTypeName: dutyTypeMap[item.dutyType] || '办公室值班',
        type: item.leaveType,
        typeName: typeInfo.name,
        typeText: typeInfo.text,
        startDate: item.startDate,
        endDate: item.endDate,
        weekNumber: item.weekNumber,
        dayOfWeek: item.dayOfWeek,
        locationId: item.locationId,
        locationName: item.locationName,
        days: item.days,
        reason: item.reason,
        attachment: item.attachment ? item.attachment.split(',').filter(Boolean) : [],
        status: item.status,
        statusText: statusMap[item.status] || item.status,
        applyDate: item.createTime ? item.createTime.slice(0, 10) : '',
        auditor: item.approverName || '',
        auditComment: item.remark || '',
        auditTime: item.updateTime ? item.updateTime.replace('T', ' ').slice(0, 16) : '',
      }
    })
  } catch (error: any) {
    ElMessage.error(error.message || '加载请假列表失败')
  } finally {
    loading.value = false
  }
}

// 初始化
onMounted(() => {
  loadLeaveList()
})

// 组件从缓存中激活时重新加载数据
onActivated(() => {
  loadLeaveList()
})
</script>

<style scoped>
.leave-management {
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

/* ========== 左侧列表区域 ========== */
.leave-list-section {
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

.list-icon {
  font-size: 20px;
  color: #6b7280;
}

.list-container {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

/* 申请项 */
.leave-item {
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

.leave-item:hover {
  background: #e0f2fe;
  border-color: #7dd3fc;
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.leave-item.is-pending {
  background: #fff;
  border-left: 3px solid #f59e0b;
}

.leave-item.is-selected {
  background: #dbeafe;
  border-color: #3b82f6;
}

.leave-item:last-child {
  margin-bottom: 0;
}

.item-left {
  position: relative;
  flex-shrink: 0;
}

.item-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pending-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
  border: 2px solid #fff;
}

.item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-tag {
  font-size: 11px;
}

.item-days {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: #2563eb;
}

.item-summary {
  font-size: 13px;
  color: #6b7280;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
}

.item-date {
  font-size: 11px;
  color: #9ca3af;
}

.item-dorm {
  font-size: 11px;
  color: #6b7280;
  font-weight: 500;
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
  margin-top: 16px;
  font-size: 14px;
}

/* ========== 右侧审批区域 ========== */
.approval-section {
  flex: 1;
  min-width: 0;
}

.approval-card {
  height: 100%;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.approval-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.header-dept {
  font-size: 13px;
  color: #6b7280;
  margin: 2px 0 0;
}

.approval-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.info-section {
  margin-bottom: 20px;
}

.info-section:last-child {
  margin-bottom: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item.full-width {
  grid-column: 1 / -1;
}

.info-label {
  font-size: 12px;
  color: #9ca3af;
}

.info-value {
  font-size: 14px;
  color: #374151;
}

.text-red {
  color: #dc2626;
}

.reason-text {
  font-size: 14px;
  color: #374151;
  line-height: 1.8;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-file {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 13px;
  color: #374151;
  text-decoration: none;
  transition: all 0.2s;
}

.attachment-file:hover {
  background: #e0f2fe;
  color: #0369a1;
}

.attach-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.attach-img {
  display: block;
  max-width: 240px;
  max-height: 160px;
}

.approval-footer {
  padding: 20px;
  border-top: 1px solid #e5e7eb;
}

.audit-input {
  margin-bottom: 12px;
}

.approval-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

/* 空审批 */
.empty-approval {
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

.empty-approval p {
  margin-top: 16px;
  font-size: 14px;
}

/* ========== 确认对话框 ========== */
.confirm-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.confirm-text {
  font-size: 14px;
  color: #374151;
  line-height: 1.6;
}

.confirm-text strong {
  color: #1f2937;
}

/* 值班选项 */
.duty-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
