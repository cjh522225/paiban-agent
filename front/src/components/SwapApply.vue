<template>
  <div class="swap-apply">
    <el-button type="primary" size="small" @click="openSwap">
      <el-icon><RefreshLeft /></el-icon>申请换班
    </el-button>
    <el-button link type="primary" size="small" @click="openMyRequests">我的换班申请</el-button>

    <!-- 申请换班 -->
    <el-dialog v-model="showSwapDialog" :title="'申请换班（' + typeLabel + '）'" width="500px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" style="margin-bottom:12px">
        换班申请只是提交意向，需管理员审批；实际换班由管理员在排班表中调整。
      </el-alert>
      <el-form label-width="90px">
        <el-form-item label="当前班次">
          <el-select v-model="swapForm.scheduleId" placeholder="选择你要换掉的一个班次" filterable style="width:100%">
            <el-option v-for="s in mySchedules" :key="s.id" :label="s.label" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="想换到">
          <el-select v-model="swapForm.targetDay" placeholder="目标星期" style="width:130px">
            <el-option v-for="d in targetDays" :key="d" :label="d" :value="d" />
          </el-select>
          <el-select v-model="swapForm.targetSlot" placeholder="目标时段" style="width:150px;margin-left:8px">
            <el-option v-for="s in targetSlots" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="换班原因">
          <el-input v-model="swapForm.reason" type="textarea" :rows="2" placeholder="如：周三晚有课" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSwapDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 我的换班申请 -->
    <el-dialog v-model="showMyRequests" title="我的换班申请" width="580px">
      <el-table :data="myRequests" border stripe size="small" max-height="320">
        <el-table-column label="当前班次" min-width="150">
          <template #default="{ row }">{{ row.dutyDate }} {{ row.timeSlot }} · {{ row.locationName }}</template>
        </el-table-column>
        <el-table-column label="想换到" width="120">
          <template #default="{ row }">{{ row.targetDay }} {{ row.targetSlot }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" type="danger" link size="small" @click="handleCancel(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showMyRequests = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getMyScheduleList } from '@/api/schedule'
import { submitSwapRequest, getMySwapRequests, deleteSwapRequest } from '@/api/swapRequest'

const props = defineProps<{ type: 'dormitory' | 'office'; typeLabel: string }>()

const showSwapDialog = ref(false)
const showMyRequests = ref(false)
const mySchedules = ref<any[]>([])
const myRequests = ref<any[]>([])
const semesterStart = ref('')

// 目标星期/时段按类型区分
const targetDays = props.type === 'dormitory' ? ['周日', '周一', '周二', '周三', '周四'] : ['周一', '周二', '周三', '周四', '周五']
const targetSlots = props.type === 'dormitory' ? ['巡班', '坐班', '敲灯'] : ['1-2节', '3-4节', '5-6节', '7-8节']

const swapForm = ref({
  scheduleId: null as number | null,
  targetDay: '',
  targetSlot: '',
  reason: '',
})

const statusType = (s: string) => s === 'pending' ? 'warning' : s === 'approved' ? 'success' : 'danger'
const statusLabel = (s: string) => s === 'pending' ? '待处理' : s === 'approved' ? '已同意' : '已拒绝'

async function loadSemester() {
  const r = await request.get('/semester')
  if (r.code === 200 && r.data?.startDate) semesterStart.value = r.data.startDate
}

async function loadMySchedules() {
  // 拉整个学期范围，列出用户自己的所有班次供选择
  const start = semesterStart.value || new Date().toISOString().slice(0, 10)
  const totalWeeks = 18
  const end = new Date(new Date(start + 'T00:00:00').getTime() + totalWeeks * 7 * 24 * 3600 * 1000)
  const endStr = `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}`
  const r = await getMyScheduleList({ startDate: start, endDate: endStr })
  if (r.code === 200) {
    mySchedules.value = (r.data || [])
      .filter((s: any) => s.type === props.type)
      .map((s: any) => ({ id: s.id, label: `${s.dutyDate} ${s.timeSlot} · ${s.locationName}` }))
  }
}

async function openSwap() {
  swapForm.value = { scheduleId: null, targetDay: '', targetSlot: '', reason: '' }
  await loadSemester()
  await loadMySchedules()
  showSwapDialog.value = true
}

function computeWeek(dutyDate: string) {
  if (!semesterStart.value) return 1
  const start = new Date(semesterStart.value + 'T00:00:00')
  const d = new Date(dutyDate + 'T00:00:00')
  return Math.floor((d.getTime() - start.getTime()) / (7 * 24 * 3600 * 1000)) + 1
}

async function handleSubmit() {
  if (!swapForm.value.scheduleId) return ElMessage.warning('请选择要换掉的班次')
  if (!swapForm.value.targetDay || !swapForm.value.targetSlot) return ElMessage.warning('请选择目标星期和时段')
  const selected = mySchedules.value.find(s => s.id === swapForm.value.scheduleId)
  if (!selected) return
  const labelParts = selected.label.split(' ')
  const dutyDate = labelParts[0]
  const timeSlot = labelParts[1]
  const locationName = labelParts.slice(2).join(' ').replace(/^·\s*/, '')
  const r = await submitSwapRequest({
    type: props.type,
    weekNumber: computeWeek(dutyDate),
    dutyDate,
    timeSlot,
    locationName,
    targetDay: swapForm.value.targetDay,
    targetSlot: swapForm.value.targetSlot,
    reason: swapForm.value.reason,
  })
  if (r.code === 200) {
    ElMessage.success('已提交，等待管理员审批')
    showSwapDialog.value = false
    showMyRequests.value = true
    await loadMyRequests()
  }
}

async function loadMyRequests() {
  const r = await getMySwapRequests()
  if (r.code === 200) myRequests.value = r.data || []
}

async function openMyRequests() {
  await loadMyRequests()
  showMyRequests.value = true
}

async function handleCancel(row: any) {
  const r = await deleteSwapRequest(row.id)
  if (r.code === 200) { ElMessage.success('已撤销'); await loadMyRequests() }
}
</script>

<style scoped>
.swap-apply {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
