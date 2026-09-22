<template>
  <div class="availability-page">
    <div class="content-wrapper">
      <el-tabs v-model="activeTab" class="parity-tabs">
        <el-tab-pane label="单周" name="odd">
          <div class="schedule-card">
        <div class="card-header">
          <span class="card-title">单周空闲时间</span>
          <span class="card-sub">(1,3,5...15 周)</span>
          <div class="header-actions">
            <el-button type="primary" size="small" @click="openAdd('odd')">
              <el-icon><Plus /></el-icon>新增
            </el-button>
            <el-button type="success" size="small" @click="handleSelectAll('odd')">全选</el-button>
            <el-button type="danger" size="small" @click="handleClearAll('odd')">清空</el-button>
          </div>
        </div>
        <div class="table-container">
          <table class="schedule-table">
            <thead>
              <tr>
                <th class="col-time">时间段</th>
                <th class="col-day">周一</th>
                <th class="col-day">周二</th>
                <th class="col-day">周三</th>
                <th class="col-day">周四</th>
                <th class="col-day">周五</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="slot in timeSlots" :key="'odd-' + slot.id" class="schedule-row">
                <td class="cell-time">{{ slot.label }}</td>
                <td v-for="d in weekdays" :key="d.value" class="cell-duty">
                  <el-icon v-if="isAvail('odd', d.value, slot.id)" class="check-icon" @click="handleRemove('odd', d.value, slot.id)"><Check /></el-icon>
                  <span v-else class="empty-cell">-</span>
                </td>
              </tr>
              <tr v-if="!timeSlots.length">
                <td colspan="6" class="empty-message">暂未填写空闲时间</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
        </el-tab-pane>
        <el-tab-pane label="双周" name="even">
          <div class="schedule-card">
        <div class="card-header">
          <span class="card-title">双周空闲时间</span>
          <span class="card-sub">(2,4,6...16 周)</span>
          <div class="header-actions">
            <el-button type="primary" size="small" @click="openAdd('even')">
              <el-icon><Plus /></el-icon>新增
            </el-button>
            <el-button type="success" size="small" @click="handleSelectAll('even')">全选</el-button>
            <el-button type="danger" size="small" @click="handleClearAll('even')">清空</el-button>
          </div>
        </div>
        <div class="table-container">
          <table class="schedule-table">
            <thead>
              <tr>
                <th class="col-time">时间段</th>
                <th class="col-day">周一</th>
                <th class="col-day">周二</th>
                <th class="col-day">周三</th>
                <th class="col-day">周四</th>
                <th class="col-day">周五</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="slot in timeSlots" :key="'even-' + slot.id" class="schedule-row">
                <td class="cell-time">{{ slot.label }}</td>
                <td v-for="d in weekdays" :key="d.value" class="cell-duty">
                  <el-icon v-if="isAvail('even', d.value, slot.id)" class="check-icon" @click="handleRemove('even', d.value, slot.id)"><Check /></el-icon>
                  <span v-else class="empty-cell">-</span>
                </td>
              </tr>
              <tr v-if="!timeSlots.length">
                <td colspan="6" class="empty-message">暂未填写空闲时间</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
        </el-tab-pane>
        <el-tab-pane label="期末周" name="exam">
          <div class="schedule-card">
        <div class="card-header">
          <span class="card-title">期末周空闲时间</span>
          <span class="card-sub">(17-18 周)</span>
          <div class="header-actions">
            <el-button type="primary" size="small" @click="openAdd('exam')">
              <el-icon><Plus /></el-icon>新增
            </el-button>
            <el-button type="success" size="small" @click="handleSelectAll('exam')">全选</el-button>
            <el-button type="danger" size="small" @click="handleClearAll('exam')">清空</el-button>
          </div>
        </div>
        <div class="table-container">
          <table class="schedule-table">
            <thead>
              <tr>
                <th class="col-time">时间段</th>
                <th class="col-day">周一</th>
                <th class="col-day">周二</th>
                <th class="col-day">周三</th>
                <th class="col-day">周四</th>
                <th class="col-day">周五</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="slot in timeSlots" :key="'exam-' + slot.id" class="schedule-row">
                <td class="cell-time">{{ slot.label }}</td>
                <td v-for="d in weekdays" :key="d.value" class="cell-duty">
                  <el-icon v-if="isAvail('exam', d.value, slot.id)" class="check-icon" @click="handleRemove('exam', d.value, slot.id)"><Check /></el-icon>
                  <span v-else class="empty-cell">-</span>
                </td>
              </tr>
              <tr v-if="!timeSlots.length">
                <td colspan="6" class="empty-message">暂未填写空闲时间</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新增空闲时间对话框 -->
    <el-dialog v-model="showDialog" title="新增空闲时间" width="440px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="周次类型">
          <el-radio-group v-model="form.weekParity" disabled>
            <el-radio-button value="odd">单周</el-radio-button>
            <el-radio-button value="even">双周</el-radio-button>
            <el-radio-button value="exam">期末周</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="星期几" prop="dayOfWeek">
          <el-select v-model="form.dayOfWeek" placeholder="请选择星期几" style="width: 100%">
            <el-option v-for="d in weekdays" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="第几节课" prop="timeSlotId">
          <el-select v-model="form.timeSlotId" placeholder="请选择时间段" style="width: 100%">
            <el-option v-for="s in timeSlots" :key="s.id" :label="s.label" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Check } from '@element-plus/icons-vue'
import { getMyAvailabilities, batchSaveAvailabilities } from '@/api/availability'
import { getTimeSlotList } from '@/api/timeSlot'

const weekdays = [
  { label: '周一', value: 1 }, { label: '周二', value: 2 }, { label: '周三', value: 3 },
  { label: '周四', value: 4 }, { label: '周五', value: 5 },
]

const timeSlots = ref<any[]>([])
const availSet = ref<Set<string>>(new Set())
const activeTab = ref('odd')
const showDialog = ref(false)
const formRef = ref()

const form = reactive({
  weekParity: 'odd',
  dayOfWeek: null as number | null,
  timeSlotId: null as number | null,
})

const rules = {
  dayOfWeek: [{ required: true, message: '请选择星期几', trigger: 'change' }],
  timeSlotId: [{ required: true, message: '请选择时间段', trigger: 'change' }],
}

function cellKey(parity: string, day: number, slotId: number) {
  return `${parity}-${day}-${slotId}`
}
function isAvail(parity: string, day: number, slotId: number) {
  return availSet.value.has(cellKey(parity, day, slotId))
}

async function loadData() {
  const [availRes, slotRes] = await Promise.all([getMyAvailabilities(), getTimeSlotList()])
  timeSlots.value = (slotRes.code === 200 && slotRes.data) ? slotRes.data : []
  const set = new Set<string>()
  if (availRes.code === 200 && availRes.data) {
    for (const a of availRes.data) {
      set.add(cellKey(a.weekParity, a.dayOfWeek, a.timeSlotId))
    }
  }
  availSet.value = set
}

function openAdd(parity: string) {
  activeTab.value = parity
  form.weekParity = parity
  form.dayOfWeek = null
  form.timeSlotId = null
  showDialog.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const key = cellKey(form.weekParity, form.dayOfWeek!, form.timeSlotId!)
  const newSet = new Set(availSet.value)
  newSet.add(key)
  const items: any[] = []
  for (const k of newSet) {
    const [p, d, s] = k.split('-')
    items.push({ weekParity: p, dayOfWeek: Number(d), timeSlotId: Number(s) })
  }
  try {
    await batchSaveAvailabilities(items)
    ElMessage.success('添加成功')
    showDialog.value = false
    availSet.value = newSet
  } catch { ElMessage.error('添加失败') }
}

async function handleRemove(parity: string, day: number, slotId: number) {
  const newSet = new Set(availSet.value)
  newSet.delete(cellKey(parity, day, slotId))
  const items: any[] = []
  for (const k of newSet) {
    const [p, d, s] = k.split('-')
    items.push({ weekParity: p, dayOfWeek: Number(d), timeSlotId: Number(s) })
  }
  try {
    await batchSaveAvailabilities(items)
    ElMessage.success('已移除')
    availSet.value = newSet
  } catch { ElMessage.error('移除失败') }
}

// 全选当前周次的所有时段
async function handleSelectAll(parity: string) {
  const newSet = new Set(availSet.value)
  for (const slot of timeSlots.value) {
    for (const d of weekdays) {
      newSet.add(cellKey(parity, d.value, slot.id))
    }
  }
  const items: any[] = []
  for (const k of newSet) {
    const [p, d, s] = k.split('-')
    items.push({ weekParity: p, dayOfWeek: Number(d), timeSlotId: Number(s) })
  }
  try {
    await batchSaveAvailabilities(items)
    ElMessage.success('已全选')
    availSet.value = newSet
  } catch { ElMessage.error('操作失败') }
}

// 清空当前周次的所有空闲时间
async function handleClearAll(parity: string) {
  const newSet = new Set<string>()
  for (const k of availSet.value) {
    if (!k.startsWith(parity + '-')) {
      newSet.add(k)
    }
  }
  const items: any[] = []
  for (const k of newSet) {
    const [p, d, s] = k.split('-')
    items.push({ weekParity: p, dayOfWeek: Number(d), timeSlotId: Number(s) })
  }
  try {
    await batchSaveAvailabilities(items)
    ElMessage.success('已清空')
    availSet.value = newSet
  } catch { ElMessage.error('操作失败') }
}

onMounted(loadData)
</script>

<style scoped>
.availability-page {
  height: 100%;
  overflow-y: auto;
  padding: 6px;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.schedule-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #e0f2fe 0%, #bae6fd 100%);
  border-bottom: 2px solid #7dd3fc;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #0369a1;
}

.card-sub {
  font-size: 12px;
  color: #0891b2;
  margin-left: 4px;
}

.add-btn {
  margin-left: auto;
}

.table-container {
  overflow-x: auto;
}

.schedule-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.schedule-table thead {
  background: #f0f9ff;
}

.schedule-table th {
  padding: 10px 8px;
  text-align: center;
  font-weight: 600;
  font-size: 13px;
  color: #0369a1;
}

.col-time { width: 90px; }

.schedule-table tbody tr {
  border-bottom: 1px solid #e5e7eb;
  transition: background 0.2s;
}

.schedule-table tbody tr:hover { background: #f8fafc; }

.schedule-table td {
  padding: 8px;
  font-size: 13px;
  text-align: center;
  vertical-align: middle;
}

.cell-time {
  font-weight: 600;
  color: #1f2937;
  background: #f8fafc;
}

.cell-duty {
  color: #374151;
}

.check-icon {
  color: #10b981;
  font-size: 18px;
  cursor: pointer;
}

.empty-cell {
  color: #a0a0a0;
  font-size: 13px;
}

.empty-message {
  text-align: center;
  padding: 24px;
  color: #909399;
  font-size: 13px;
}
.parity-tabs :deep(.el-tabs__nav-wrap) { padding: 0 16px; background: #fff; border-radius: 12px 12px 0 0; box-shadow: 0 2px 8px rgba(0,0,0,0.06); margin-bottom: 0; }
.parity-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
.parity-tabs :deep(.el-tabs__content) { padding: 12px 0 0; }
</style>
