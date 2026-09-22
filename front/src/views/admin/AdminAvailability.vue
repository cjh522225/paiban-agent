<template>
  <div class="availability-page">
    <div class="control-section">
      <div class="control-card">
        <div class="control-row">
          <div class="control-item">
            <label class="control-label">周次类型</label>
            <el-radio-group v-model="weekParity" size="small" @change="buildGrid">
              <el-radio-button value="odd">单周</el-radio-button>
              <el-radio-button value="even">双周</el-radio-button>
              <el-radio-button value="exam">考试周</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </div>
    </div>

    <div class="schedule-card">
      <div class="table-container" v-loading="loading" element-loading-text="加载中..." element-loading-background="rgba(255,255,255,0.7)">
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
            <tr v-for="slot in timeSlots" :key="slot.id" class="schedule-row">
              <td class="cell-time">
                <div class="time-content">
                  <span class="time-label">{{ slot.label }}</span>
                </div>
              </td>
              <td v-for="d in weekdays" :key="d.value" class="cell-duty"
                :class="{ 'has-data': getNames(d.value, slot.id) }"
                @click="openDetail(d.value, slot.id)">
                <span v-if="getNames(d.value, slot.id)" class="staff-names">{{ getDisplayNames(d.value, slot.id) }}</span>
                <span v-else class="empty-cell">-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <el-dialog v-model="showDetail" :title="detailTitle" width="480px" class="avail-dialog">
      <div class="detail-search">
        <el-input v-model="searchName" placeholder="输入姓名，快速查找该时段是否有此人空闲" clearable>
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <span class="detail-count">共 {{ detailEntries.length }} 人</span>
      </div>
      <div class="detail-scroll">
        <div v-for="item in filteredEntries" :key="item.id" class="detail-item">
          <span class="detail-name">{{ item.realName }}</span>
          <el-button type="danger" link size="small" @click="handleDelete(item)">删除</el-button>
        </div>
        <div v-if="!filteredEntries.length" class="empty-msg">
          {{ searchName ? `未找到「${searchName}」，该时段没有此人空闲` : '暂无数据' }}
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getAllAvailabilities, deleteAvailability } from '@/api/availability'
import { getTimeSlotList } from '@/api/timeSlot'

const weekdays = [{ label: '周一', value: 1 }, { label: '周二', value: 2 }, { label: '周三', value: 3 },
  { label: '周四', value: 4 }, { label: '周五', value: 5 }]
const weekParity = ref('odd')
const timeSlots = ref<any[]>([])
const allData = ref<any[]>([])
const loading = ref(false)
const showDetail = ref(false)
const detailTitle = ref('')
const detailEntries = ref<any[]>([])
const searchName = ref('')

// 弹窗内按姓名过滤后的空闲人员列表
const filteredEntries = computed(() => {
  const kw = searchName.value.trim()
  if (!kw) return detailEntries.value
  return detailEntries.value.filter(e => e.realName.includes(kw))
})

function getEntries(dayOfWeek: number, slotId: number) {
  const result: any[] = []
  for (const user of allData.value) {
    for (const a of (user.availabilities || [])) {
      if (a.weekParity === weekParity.value && a.dayOfWeek === dayOfWeek && a.timeSlotId === slotId) {
        result.push({ ...a, realName: user.realName })
      }
    }
  }
  return result
}

function getNames(dayOfWeek: number, slotId: number) {
  return getEntries(dayOfWeek, slotId).map(e => e.realName).join('、')
}

// 格子显示：超过4人只显示前4个，其余点击弹出查看框
function getDisplayNames(dayOfWeek: number, slotId: number) {
  const names = getEntries(dayOfWeek, slotId).map(e => e.realName)
  if (names.length <= 4) return names.join('、')
  return names.slice(0, 4).join('、') + ` 等${names.length}人`
}

function openDetail(dayOfWeek: number, slotId: number) {
  const entries = getEntries(dayOfWeek, slotId)
  if (!entries.length) return
  const dayLabel = weekdays.find(d => d.value === dayOfWeek)?.label || ''
  const slotLabel = timeSlots.value.find(s => s.id === slotId)?.label || ''
  detailTitle.value = `${dayLabel} ${slotLabel} - 空闲人员`
  detailEntries.value = entries
  searchName.value = ''
  showDetail.value = true
}

async function handleDelete(item: any) {
  try {
    await ElMessageBox.confirm(`确定删除 ${item.realName} 的此条空闲记录？`, '确认删除', { type: 'warning' })
    await deleteAvailability(item.id)
    ElMessage.success('已删除')
    detailEntries.value = detailEntries.value.filter((e: any) => e.id !== item.id)
    const user = allData.value.find((u: any) => u.availabilities?.some((a: any) => a.id === item.id))
    if (user) {
      user.availabilities = user.availabilities.filter((a: any) => a.id !== item.id)
    }
  } catch { /* 取消 */ }
}

function buildGrid() { /* 由 computed 自动刷新 */ }

onMounted(async () => {
  loading.value = true
  const [availRes, slotRes] = await Promise.all([getAllAvailabilities(), getTimeSlotList()])
  if (slotRes.code === 200 && slotRes.data) timeSlots.value = slotRes.data
  if (availRes.code === 200 && availRes.data) allData.value = availRes.data
  loading.value = false
})

onActivated(async () => {
  loading.value = true
  const [availRes, slotRes] = await Promise.all([getAllAvailabilities(), getTimeSlotList()])
  if (slotRes.code === 200 && slotRes.data) timeSlots.value = slotRes.data
  if (availRes.code === 200 && availRes.data) allData.value = availRes.data
  loading.value = false
})
</script>

<style scoped>
.availability-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  overflow: hidden;
}

.control-section { flex-shrink: 0; }

.control-card {
  background: #fff; border-radius: 12px; padding: 12px 18px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.control-row { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; }

.control-item { display: flex; flex-direction: column; gap: 6px; }

.control-label { font-size: 12px; font-weight: 500; color: #374151; }

.schedule-card {
  flex: 1; background: #fff; border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06); overflow: hidden;
  display: flex; flex-direction: column;
}

.table-container { flex: 1; overflow: auto; }

.schedule-table { width: 100%; height: 100%; border-collapse: collapse; }

.schedule-table thead {
  position: sticky; top: 0; z-index: 10;
  background: linear-gradient(135deg, #e0f2fe 0%, #bae6fd 100%);
  border-bottom: 2px solid #7dd3fc;
}

.schedule-table th {
  padding: 14px 10px; text-align: center;
  font-weight: 600; font-size: 14px; color: #0369a1;
}

.col-time { width: 100px; }

.schedule-table tbody tr { border-bottom: 1px solid #e5e7eb; transition: background 0.2s; }

.schedule-table tbody tr:hover { background: #f0f9ff; }

.schedule-table td { padding: 12px 10px; font-size: 14px; vertical-align: middle; }

.cell-time { background: #f8fafc; text-align: center; }

.time-content { display: flex; flex-direction: column; gap: 4px; align-items: center; }

.time-label { color: #1f2937; font-size: 14px; font-weight: 600; }

.cell-duty { text-align: center; cursor: default; }

.cell-duty.has-data { cursor: pointer; background: #f0fdf4; transition: background 0.15s; }

.cell-duty.has-data:hover { background: #dcfce7; }

.staff-names {
  color: #1677ff; font-size: 13px; line-height: 1.6;
  display: block; word-break: break-all;
}

.empty-cell { color: #d1d5db; }

.detail-search {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 12px;
}

.detail-count {
  flex-shrink: 0; font-size: 13px; color: #6b7280;
}

.detail-scroll {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}

.detail-scroll::-webkit-scrollbar { width: 6px; }
.detail-scroll::-webkit-scrollbar-track { background: transparent; }
.detail-scroll::-webkit-scrollbar-thumb { background: #c0c0c0; border-radius: 3px; }
.detail-scroll::-webkit-scrollbar-thumb:hover { background: #a0a0a0; }

.detail-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; background: #f8fafc; border-radius: 8px;
}

.detail-name { font-size: 14px; font-weight: 500; color: #1f2937; }

.empty-msg { text-align: center; padding: 24px 0; color: #9ca3af; font-size: 14px; }
</style>
