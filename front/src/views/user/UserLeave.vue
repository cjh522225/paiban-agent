<template>
  <div class="leave-page">
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
              <label class="filter-label">请假状态</label>
              <el-select v-model="selectedStatus" placeholder="全部状态" class="filter-select" clearable>
                <el-option label="待审核" value="pending" />
                <el-option label="已通过" value="approved" />
                <el-option label="已拒绝" value="rejected" />
              </el-select>
            </div>

            <div class="filter-actions">
              <el-button type="primary" @click="handleQuery">查询</el-button>
              <el-button type="success" @click="showCreateDialog = true">
                <el-icon>
                  <Plus />
                </el-icon>
                新建申请
              </el-button>
            </div>
          </div>
        </div>

        <!-- 申请列表 -->
        <div class="list-card">
          <div class="list-container">
            <div v-for="(item, index) in filteredLeaveList" :key="index" class="leave-item"
              @click="viewLeaveDetail(item)">
              <div class="item-header">
                <div class="item-type-group">
                  <el-tag :type="item.dutyType === 'dormitory' ? 'warning' : 'primary'" size="small" effect="light"
                    class="duty-tag">
                    <el-icon>
                      <HomeFilled v-if="item.dutyType === 'dormitory'" />
                      <OfficeBuilding v-else />
                    </el-icon>
                    {{ item.dutyTypeName }}
                  </el-tag>
                  <div class="item-type">
                    <el-icon :color="getTypeIconColor(item.type)">
                      <Document />
                    </el-icon>
                    <span>{{ item.typeName }}</span>
                  </div>
                </div>
                <el-tag :type="getStatusType(item.status)" size="small" effect="light">
                  {{ item.statusText }}
                </el-tag>
              </div>

              <div class="item-body">
                <div class="item-row">
                  <span class="row-label">请假时间：</span>
                  <span class="row-value">{{ formatLeaveTime(item) }}</span>
                </div>
                <div class="item-row">
                  <span class="row-label">请假事由：</span>
                  <span class="row-value reason">{{ item.reason }}</span>
                </div>
              </div>

              <div class="item-footer">
                <span class="item-date">申请于 {{ item.applyDate }}</span>
                <div class="item-actions">
                  <el-button v-if="item.status === 'pending'" text type="danger" @click.stop="cancelLeave(item)">
                    撤销
                  </el-button>
                  <el-button text type="primary" @click.stop="viewLeaveDetail(item)">
                    详情
                  </el-button>
                </div>
              </div>
            </div>

            <div v-if="filteredLeaveList.length === 0" class="empty-state">
              <el-icon :size="64" color="#a0a0a0">
                <Document />
              </el-icon>
              <p>暂无请假申请</p>
              <el-button type="primary" @click="showCreateDialog = true">新建申请</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧统计面板 -->
      <div class="stats-section">
        <div class="stats-card">
          <div class="card-header">
            <el-icon class="title-icon">
              <DataAnalysis />
            </el-icon>
            <span>请假统计</span>
          </div>

          <div class="stats-body">
            <div class="stat-item pending">
              <div class="stat-icon">
                <el-icon :size="24">
                  <Clock />
                </el-icon>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ pendingCount }}</span>
                <span class="stat-label">待审核</span>
              </div>
            </div>

            <div class="stat-item approved">
              <div class="stat-icon">
                <el-icon :size="24">
                  <CircleCheck />
                </el-icon>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ approvedCount }}</span>
                <span class="stat-label">已通过</span>
              </div>
            </div>

            <div class="stat-item rejected">
              <div class="stat-icon">
                <el-icon :size="24">
                  <CircleClose />
                </el-icon>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ rejectedCount }}</span>
                <span class="stat-label">已拒绝</span>
              </div>
            </div>

            <div class="stat-item total">
              <div class="stat-icon">
                <el-icon :size="24">
                  <Document />
                </el-icon>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ totalDays }}</span>
                <span class="stat-label">总天数</span>
              </div>
            </div>
          </div>

          <!-- 按值班类型统计 -->
          <div class="duty-type-stats">
            <div class="duty-type-title">
              <el-icon>
                <PieChart />
              </el-icon>
              <span>按值班类型</span>
            </div>
            <div class="duty-type-list">
              <div class="duty-type-item dormitory">
                <div class="duty-type-icon">
                  <el-icon :size="20">
                    <HomeFilled />
                  </el-icon>
                </div>
                <div class="duty-type-info">
                  <span class="duty-type-label">宿舍值班</span>
                  <span class="duty-type-value">{{ dormitoryLeaveCount }} 次</span>
                </div>
              </div>
              <div class="duty-type-item office">
                <div class="duty-type-icon">
                  <el-icon :size="20">
                    <OfficeBuilding />
                  </el-icon>
                </div>
                <div class="duty-type-info">
                  <span class="duty-type-label">办公室值班</span>
                  <span class="duty-type-value">{{ officeLeaveCount }} 次</span>
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>

    <!-- 新建请假申请对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建请假申请" width="520px" :close-on-click-modal="false">
      <el-form ref="leaveFormRef" :model="leaveForm" :rules="leaveFormRules" label-width="90px" label-position="left">
        <el-form-item label="值班类型" prop="dutyType">
          <el-select v-model="leaveForm.dutyType" placeholder="请选择值班类型" style="width: 100%">
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
        </el-form-item>

        <el-form-item label="请假类型" prop="type">
          <el-select v-model="leaveForm.type" placeholder="请选择请假类型" style="width: 100%">
            <el-option v-for="type in leaveTypes" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="leaveForm.startDate" type="date" placeholder="选择请假开始日期" style="width: 100%"
            value-format="YYYY-MM-DD" @change="onDateChange" />
        </el-form-item>

        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="leaveForm.endDate" type="date" placeholder="选择请假结束日期" style="width: 100%"
            value-format="YYYY-MM-DD" :disabled-date="disabledEndDate" @change="onEndDateChange" />
        </el-form-item>

        <el-form-item label="第几周">
          <el-input-number v-model="leaveForm.weekNumber" :min="1" :max="totalWeeks" disabled style="width: 100%" />
        </el-form-item>

        <el-form-item label="星期几" v-if="!isMultiDayLeave">
          <el-select v-model="leaveForm.dayOfWeek" disabled style="width: 100%">
            <el-option v-for="d in weekdays" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="值班地点" prop="locationId">
          <el-select v-model="leaveForm.locationId" placeholder="选择值班地点" style="width: 100%" @change="onLocationChange">
            <el-option v-for="loc in locationOptions" :key="loc.id" :label="loc.name" :value="loc.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="请假事由" prop="reason">
          <el-input v-model="leaveForm.reason" type="textarea" :rows="4" placeholder="请详细描述请假原因" />
        </el-form-item>

        <el-form-item label="附件证明" prop="attachment">
          <el-upload class="attachment-uploader" drag multiple :limit="5"
            :http-request="handleAttachmentUpload" :file-list="leaveFileList" :on-remove="handleAttachmentRemove">
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
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitLeave">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 请假详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="请假申请详情" width="560px">
      <div v-if="currentLeave" class="leave-detail">
        <div class="detail-row">
          <span class="detail-label">申请状态</span>
          <el-tag :type="getStatusType(currentLeave.status)" effect="light">
            {{ currentLeave.statusText }}
          </el-tag>
        </div>

        <div class="detail-row">
          <span class="detail-label">审核意见</span>
          <span class="detail-value" :class="{ 'text-red': currentLeave.status === 'rejected' }">
            {{ currentLeave.auditComment || '无' }}
          </span>
        </div>

        <div class="detail-row">
          <span class="detail-label">值班类型</span>
          <el-tag :type="currentLeave.dutyType === 'dormitory' ? 'warning' : 'primary'" effect="light" size="small">
            <el-icon>
              <HomeFilled v-if="currentLeave.dutyType === 'dormitory'" />
              <OfficeBuilding v-else />
            </el-icon>
            {{ currentLeave.dutyTypeName }}
          </el-tag>
        </div>

        <div class="detail-row">
          <span class="detail-label">请假类型</span>
          <span class="detail-value">{{ currentLeave.typeName }}</span>
        </div>

        <div class="detail-row">
          <span class="detail-label">请假时间</span>
          <span class="detail-value">{{ formatLeaveTime(currentLeave) }}</span>
        </div>

        <div class="detail-row">
          <span class="detail-label">请假事由</span>
          <span class="detail-value reason">{{ currentLeave.reason }}</span>
        </div>

        <div class="detail-row" v-if="currentLeave.attachment && currentLeave.attachment.length">
          <span class="detail-label">附件证明</span>
          <div class="attachment-list">
            <div v-for="(file, idx) in currentLeave.attachment" :key="idx" class="attach-item">
              <a v-if="isImage(file)" :href="file" target="_blank" class="attach-link">
                <img :src="file" alt="附件图片" class="attach-img" />
              </a>
              <a v-else :href="file" target="_blank" class="attach-link attach-pdf">
                <el-icon><Document /></el-icon>
                <span>{{ getFileName(file) }}</span>
              </a>
            </div>
          </div>
        </div>

        <div class="detail-section" v-if="currentLeave.status !== 'pending'">
          <div class="detail-title">审核信息</div>
          <div class="detail-row">
            <span class="detail-label">审核人</span>
            <span class="detail-value">{{ currentLeave.auditor || '无' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">审核时间</span>
            <span class="detail-value">{{ currentLeave.auditTime || '无' }}</span>
          </div>
        </div>

        <div class="detail-footer">
          <span class="detail-date">申请时间：{{ currentLeave.applyDate }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLeaveList, addLeave, deleteLeave } from '@/api/leave'
import { getOfficeList } from '@/api/office'
import { getDormitoryList } from '@/api/dormitory'
import request from '@/utils/request'
import { getTimeSlotList } from '@/api/timeSlot'
import { uploadAttachment } from '@/api/message'
import {
  Bell,
  Plus,
  Document,
  UploadFilled,
  Clock,
  CircleCheck,
  CircleClose,
  DataAnalysis,
  HomeFilled,
  OfficeBuilding,
  PieChart,
} from '@element-plus/icons-vue'

const userStore = useUserStore()

const weekdays = [
  { label: '周一', value: 1 }, { label: '周二', value: 2 }, { label: '周三', value: 3 },
  { label: '周四', value: 4 }, { label: '周五', value: 5 }, { label: '周六', value: 6 }, { label: '周日', value: 7 },
]

const totalWeeks = ref(18)
const semesterStartDate = ref('')
const locationOptions = ref<any[]>([])

async function loadSemesterConfig() {
  const res = await request.get('/semester')
  if (res.code === 200 && res.data) {
    totalWeeks.value = res.data.totalWeeks || 18
    semesterStartDate.value = res.data.startDate || ''
  }
}

async function loadLocationOptions() {
  if (!leaveForm.value.dutyType) { locationOptions.value = []; return }
  try {
    if (leaveForm.value.dutyType === 'office') {
      const res = await getOfficeList({ status: 1 })
      if (res.code === 200) locationOptions.value = (res.data || []).map((o: any) => ({ id: o.id, name: o.name }))
    } else {
      const res = await getDormitoryList({ status: 1 })
      if (res.code === 200) locationOptions.value = (res.data || []).map((d: any) => ({ id: d.id, name: d.name }))
    }
  } catch { locationOptions.value = [] }
}

function onLocationChange() {
  const loc = locationOptions.value.find(l => l.id === leaveForm.value.locationId)
  leaveForm.value.locationName = loc ? loc.name : ''
}

function weekDayLabel(d: number | null) {
  const found = weekdays.find(w => w.value === d)
  return found ? found.label : '-'
}

function onDateChange() {
  if (!leaveForm.value.startDate || !semesterStartDate.value) return
  const date = new Date(leaveForm.value.startDate + 'T00:00:00')
  const start = new Date(semesterStartDate.value + 'T00:00:00')
  const diffDays = Math.floor((date.getTime() - start.getTime()) / 86400000)
  const w = Math.floor(diffDays / 7) + 1
  const d = date.getDay()
  const dayOfWeek = d === 0 ? 7 : d
  leaveForm.value.weekNumber = w >= 1 && w <= totalWeeks.value ? w : null
  leaveForm.value.dayOfWeek = dayOfWeek >= 1 && dayOfWeek <= 7 ? dayOfWeek : null
}

// 请假时间显示：单天 → 2026-08-09，第22周，周日，教学楼办公室；多天 → 2026-08-09 ~ 2026-08-12，教学楼办公室
function formatLeaveTime(item: any) {
  if (item.endDate && item.endDate !== item.startDate) {
    return `${item.startDate} ~ ${item.endDate}，${item.locationName || ''}`
  }
  return `${item.startDate}，第${item.weekNumber ?? '-'}周，${weekDayLabel(item.dayOfWeek)}，${item.locationName || ''}`
}

// 结束日期：禁用早于开始日期的日期
const disabledEndDate = (date: Date) => {
  if (!leaveForm.value.startDate) return false
  return date.getTime() < new Date(leaveForm.value.startDate + 'T00:00:00').getTime()
}

// 结束日期校验规则
const validateEndDate = (rule: any, value: string, callback: any) => {
  if (value && leaveForm.value.startDate && value < leaveForm.value.startDate) {
    callback(new Error('结束日期不能早于开始日期'))
  } else {
    callback()
  }
}

// 选择结束日期：若早于开始日期则清空，强制重新选择
function onEndDateChange() {
  if (leaveForm.value.endDate && leaveForm.value.startDate && leaveForm.value.endDate < leaveForm.value.startDate) {
    leaveForm.value.endDate = ''
    ElMessage.warning('结束日期不能早于开始日期，请重新选择')
  }
}

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

const dutyTypes = [
  { label: '宿舍值班', value: 'dormitory', icon: 'HomeFilled' },
  { label: '办公室值班', value: 'office', icon: 'OfficeBuilding' },
]

const selectedStatus = ref('')
const selectedType = ref('')
const selectedDutyType = ref('')
const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const currentLeave = ref<any>(null)

// 请假表单
const leaveFormRef = ref()
const leaveForm = ref({
  dutyType: '',
  type: '',
  startDate: '',
  endDate: '',
  weekNumber: null as number | null,
  dayOfWeek: null as number | null,
  locationId: null as number | null,
  locationName: '',
  reason: '',
  attachment: [] as string[],
})
const leaveFileList = ref<any[]>([])

// 附件工具
const isImage = (path: string) => /\.(jpg|jpeg|png)$/i.test(path)
const getFileName = (path: string) => path.split('/').pop() || path

// 附件上传（复用消息模块上传接口）
const handleAttachmentUpload = async (options: any) => {
  try {
    const res = await uploadAttachment(options.file)
    if (res.code === 200 && res.data) {
      leaveForm.value.attachment.push(res.data)
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

// 移除附件
const handleAttachmentRemove = (file: any) => {
  const path = file.url || file.response?.data || ''
  if (path) {
    const idx = leaveForm.value.attachment.indexOf(path)
    if (idx > -1) leaveForm.value.attachment.splice(idx, 1)
  }
}

const leaveFormRules = {
  dutyType: [{ required: true, message: '请选择值班类型', trigger: 'change' }],
  type: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择请假开始日期', trigger: 'change' }],
  endDate: [
    { required: true, message: '请选择请假结束日期', trigger: 'change' },
    { validator: validateEndDate, trigger: 'change' },
  ],
  locationId: [{ required: true, message: '请选择值班地点', trigger: 'change' }],
  reason: [{ required: true, message: '请填写请假事由', trigger: 'blur' }],
}

// 请假列表数据
const leaveList = ref<any[]>([])

// 筛选后的列表
const filteredLeaveList = computed(() => {
  let list = leaveList.value
  if (selectedStatus.value) {
    list = list.filter(item => item.status === selectedStatus.value)
  }
  if (selectedType.value) {
    list = list.filter(item => item.type === selectedType.value)
  }
  if (selectedDutyType.value) {
    list = list.filter(item => item.dutyType === selectedDutyType.value)
  }
  return list
})

// 统计数据
const pendingCount = computed(() => leaveList.value.filter(item => item.status === 'pending').length)
const approvedCount = computed(() => leaveList.value.filter(item => item.status === 'approved').length)
const rejectedCount = computed(() => leaveList.value.filter(item => item.status === 'rejected').length)

// 是否多天请假（结束日期 > 开始日期）：多天时不显示"星期几"
const isMultiDayLeave = computed(() =>
  !!leaveForm.value.endDate && leaveForm.value.endDate !== leaveForm.value.startDate
)
const totalDays = computed(() =>
  leaveList.value
    .filter(item => item.status === 'approved')
    .reduce((sum, item) => sum + (item.days || 1), 0)
)

watch(() => leaveForm.value.dutyType, async (newVal) => {
  if (newVal) {
    leaveForm.value.locationId = null
    leaveForm.value.locationName = ''
    await loadLocationOptions()
  }
})

// 按值班类型统计（已通过的）
const dormitoryLeaveCount = computed(() =>
  leaveList.value.filter(item => item.status === 'approved' && item.dutyType === 'dormitory').length
)
const officeLeaveCount = computed(() =>
  leaveList.value.filter(item => item.status === 'approved' && item.dutyType === 'office').length
)

// 获取类型图标颜色
const getTypeIconColor = (type: string) => {
  const colors: Record<string, string> = {
    sick: '#ef4444',
    personal: '#f59e0b',
    compensatory: '#3b82f6',
    annual: '#10b981',
    marriage: '#ec4899',
    maternity: '#8b5cf6',
    paternity: '#06b6d4',
    bereavement: '#6b7280',
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

// 查询
const handleQuery = () => {
  loadLeaveData()
}

// 加载请假数据
const loadLeaveData = async () => {
  try {
    const res = await getLeaveList()
    if (res.code === 200 && Array.isArray(res.data)) {
      // 转换后端数据格式为前端期望的格式
      leaveList.value = res.data.map((item: any) => {
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
          dutyType: item.dutyType || 'office',
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
    }
  } catch (error) {
    ElMessage.error('加载请假数据失败')
  }
}

// 查看请假详情
const viewLeaveDetail = (item: any) => {
  currentLeave.value = item
  showDetailDialog.value = true
}

// 撤销请假申请（确认后真实删除该条记录，用户端和管理员端都看不到）
const cancelLeave = (item: any) => {
  ElMessageBox.confirm('确定要撤销该请假申请吗？撤销后无法恢复。', '撤销申请', {
    confirmButtonText: '确定撤销',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deleteLeave(item.id)
      ElMessage.success('已撤销申请')
      loadLeaveData()
    } catch (error: any) {
      ElMessage.error(error.message || '撤销失败')
    }
  }).catch(() => { })
}

// 提交请假申请
const submitLeave = () => {
  leaveFormRef.value?.validate((valid: boolean) => {
    if (valid) {
      ElMessageBox.confirm('确定提交请假申请吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      }).then(async () => {
        try {
          const submitData = {
            dutyType: leaveForm.value.dutyType,
            leaveType: leaveForm.value.type,
            startDate: leaveForm.value.startDate,
            endDate: leaveForm.value.endDate || leaveForm.value.startDate,
            weekNumber: leaveForm.value.weekNumber,
            dayOfWeek: leaveForm.value.dayOfWeek,
            locationId: leaveForm.value.locationId,
            locationName: leaveForm.value.locationName,
            reason: leaveForm.value.reason,
            attachment: leaveForm.value.attachment.join(','),
          }
          await addLeave(submitData)
          ElMessage.success('申请已提交，等待审核')
          showCreateDialog.value = false
          leaveFormRef.value?.resetFields()
          leaveForm.value.attachment = []
          leaveFileList.value = []
          loadLeaveData()
        } catch (error) {
          ElMessage.error('提交请假申请失败')
        }
      }).catch(() => { })
    }
  })
}

onMounted(() => {
  loadSemesterConfig()
  loadLocationOptions()
  loadLeaveData()
})

// 组件从缓存中激活时重新加载数据
onActivated(() => {
  loadLeaveData()
})
</script>

<style scoped>
.leave-page {
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

/* ========== 左侧请假列表区域 ========== */
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
  padding: 16px;
}

/* 请假项 */
.leave-item {
  background: #f8fafc;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 12px;
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

.leave-item:last-child {
  margin-bottom: 0;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.item-type-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.duty-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.item-type {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.item-body {
  margin-bottom: 12px;
}

.item-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #475569;
}

.item-row:last-child {
  margin-bottom: 0;
}

.row-label {
  font-weight: 500;
  color: #6b7280;
  flex-shrink: 0;
}

.row-value {
  color: #374151;
}

.row-value.reason {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-days {
  margin-left: auto;
  font-weight: 600;
  color: #2563eb;
  background: #dbeafe;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #e2e8f0;
}

.item-date {
  font-size: 12px;
  color: #7b7b7b;
}

.item-actions {
  display: flex;
  gap: 8px;
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
  margin: 16px 0 24px;
  font-size: 14px;
}

/* ========== 右侧统计区域 ========== */
.stats-section {
  width: 300px;
  flex-shrink: 0;
}

.stats-card {
  height: 100%;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  border-bottom: 1px solid #e5e7eb;
}

.title-icon {
  color: #3b82f6;
}

.stats-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  transition: all 0.2s;
}

.stat-item.pending {
  background: #fef3c7;
}

.stat-item.approved {
  background: #d1fae5;
}

.stat-item.rejected {
  background: #fee2e2;
}

.stat-item.total {
  background: #dbeafe;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  flex-shrink: 0;
}

.stat-item.pending .stat-icon {
  background: #fde68a;
  color: #92400e;
}

.stat-item.approved .stat-icon {
  background: #a7f3d0;
  color: #047857;
}

.stat-item.rejected .stat-icon {
  background: #fecaca;
  color: #991b1b;
}

.stat-item.total .stat-icon {
  background: #bfdbfe;
  color: #1e40af;
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
}

/* 值班选项样式 */
.duty-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* ========== 请假详情对话框 ========== */
.leave-detail {
  padding: 8px 0;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-label {
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  min-width: 80px;
  flex-shrink: 0;
}

.detail-value {
  font-size: 14px;
  color: #1f2937;
  flex: 1;
}

.detail-value.reason {
  line-height: 1.6;
}

.text-red {
  color: #dc2626;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.attachment-tag {
  cursor: pointer;
}

.attach-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #f8fafc;
}

.attach-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #0369a1;
  text-decoration: none;
}

.attach-link:hover {
  background: #e0f2fe;
}

.attach-img {
  display: block;
  max-width: 240px;
  max-height: 160px;
}

.attach-pdf {
  padding: 10px 14px;
}

.detail-section {
  margin-top: 16px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
}

.detail-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.detail-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
}

.detail-date {
  font-size: 12px;
  color: #7b7b7b;
}

/* 表单提示 */
.form-tip {
  font-size: 12px;
  color: #7b7b7b;
  margin-left: 8px;
}

/* 附件上传 */
.attachment-uploader {
  width: 100%;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .content-wrapper {
    flex-direction: column;
  }

  .stats-section {
    width: 100%;
  }
}
</style>
