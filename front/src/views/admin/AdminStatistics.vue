<template>
  <div class="stats-page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="stats-tabs">
      <el-tab-pane label="宿舍值班统计" name="dormitory">
        <div class="toolbar">
          <el-input v-model="dormSearch" placeholder="搜索姓名" clearable size="small" class="search-inp" />
          <span class="total-hint">共 {{ filteredDorm.length }} 人</span>
          <el-button type="primary" size="small" style="margin-left:auto" @click="exportDormitoryCounts">导出 Excel</el-button>
        </div>
        <el-table :data="filteredDorm" size="small" class="stats-table" row-class-name="stats-row" :header-cell-style="{ background: '#f8fafc', color: '#666', fontSize: '13px' }">
          <el-table-column prop="userName" label="姓名" width="120" />
          <el-table-column label="巡班" width="90" align="center">
            <template #default="{ row }"><span class="badge badge-warning">{{ row['巡班'] || 0 }}</span></template>
          </el-table-column>
          <el-table-column label="坐班" width="90" align="center">
            <template #default="{ row }"><span class="badge badge-success">{{ row['坐班'] || 0 }}</span></template>
          </el-table-column>
          <el-table-column label="敲灯" width="90" align="center">
            <template #default="{ row }"><span class="badge badge-primary">{{ row['敲灯'] || 0 }}</span></template>
          </el-table-column>
          <el-table-column label="总次数" align="center">
            <template #default="{ row }"><span class="badge total">{{ row.totalCount || 0 }}</span></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="办公室值班统计" name="office">
        <div class="toolbar">
          <el-input v-model="officeSearch" placeholder="搜索姓名" clearable size="small" class="search-inp" />
          <span class="total-hint">共 {{ filteredOffice.length }} 人</span>
          <el-button type="primary" size="small" style="margin-left:auto" @click="exportOfficeCounts">导出 Excel</el-button>
        </div>
        <el-table :data="filteredOffice" size="small" class="stats-table" row-class-name="stats-row" :header-cell-style="{ background: '#f8fafc', color: '#666', fontSize: '13px' }">
          <el-table-column prop="userName" label="姓名" width="140" />
          <el-table-column label="值班次数" align="center">
            <template #default="{ row }"><span class="badge total">{{ row.totalCount || 0 }}</span></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="历史记录" name="history">
        <div class="filter-bar">
          <el-select v-model="semesterId" placeholder="选择学期" size="small" @change="onSemesterChange" style="width:200px">
            <el-option v-for="s in semesters" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <el-select v-model="selectedWeekNum" placeholder="选择周次" size="small" @change="loadHistory" style="width:130px">
            <el-option v-for="w in pastWeeks" :key="w.weekNum" :label="`第${w.weekNum}周`" :value="w.weekNum" />
          </el-select>
          <el-select v-model="hBuilding" :placeholder="hType==='dormitory'?'选择宿舍楼':'选择办公室'" size="small" @change="loadHistory" style="width:160px">
            <el-option label="全部" value="" />
            <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="String(b.id)" />
          </el-select>
          <el-radio-group v-model="hType" size="small" @change="onTypeChange">
            <el-radio-button value="dormitory">宿舍</el-radio-button>
            <el-radio-button value="office">办公室</el-radio-button>
          </el-radio-group>
          <el-button type="primary" size="small" style="margin-left:auto" @click="exportHistory">导出 Excel</el-button>
        </div>
        <div v-loading="hLoading" style="min-height:200px;margin-top:8px">
          <template v-if="hType === 'dormitory'">
            <div v-for="loc in hData" :key="loc.locationName" class="history-block">
              <div class="history-title">{{ loc.locationName }}</div>
              <table class="history-table">
                <thead><tr><th>日期</th><th>巡班</th><th>坐班</th><th>敲灯</th></tr></thead>
                <tbody>
                  <tr v-for="d in loc.days" :key="d.date">
                    <td>{{ d.date }}</td>
                    <td>{{ d.patrol?.join('、') || '-' }}</td>
                    <td>{{ d.sitting?.join('、') || '-' }}</td>
                    <td>{{ d.knockLights?.join('、') || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
          <template v-else>
            <div v-for="loc in hData" :key="loc.locationName" class="history-block">
              <div class="history-title">{{ loc.locationName }}</div>
              <table class="history-table">
                <thead><tr><th>日期</th><th v-for="s in officeSlots" :key="s.key">{{ s.label }}</th></tr></thead>
                <tbody>
                  <tr v-for="d in loc.days" :key="d.date">
                    <td>{{ d.date }}</td>
                    <td v-for="s in officeSlots" :key="s.key">{{ d.slots?.[s.key]?.join('、') || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
          <el-empty v-if="!hData.length && !hLoading" description="暂无历史记录" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDutyCounts, exportDutyCountsExcel } from '@/api/statistics'
import { exportHistoryExcel } from '@/api/history'
import { getOfficeList } from '@/api/office'
import request from '@/utils/request'

const activeTab = ref('dormitory')
const dormitoryStats = ref<any[]>([]); const officeStats = ref<any[]>([])
const dormSearch = ref(''); const officeSearch = ref('')
const filteredDorm = computed(() => dormitoryStats.value.filter((x: any) => !dormSearch.value || (x.userName||'').includes(dormSearch.value)))
const filteredOffice = computed(() => officeStats.value.filter((x: any) => !officeSearch.value || (x.userName||'').includes(officeSearch.value)))

const semesters = ref<any[]>([]); const pastWeeks = ref<any[]>([]); const buildings = ref<any[]>([])
const semesterId = ref<number|null>(null); const selectedWeekNum = ref<number|null>(null)
const hBuilding = ref(''); const hType = ref('dormitory'); const hData = ref<any[]>([]); const hLoading = ref(false)
const officeSlots = [
  { key: '1-2节', label: '1-2 节' },
  { key: '3-4节', label: '3-4 节' },
  { key: '5-6节', label: '5-6 节' },
  { key: '7-8节', label: '7-8 节' },
]
const semesterStartDate = ref(''); const semesterEndDate = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null
let semesterFetched = false

async function fetchSemester() {
  if (semesterFetched) return
  const res = await request.get('/semester')
  if (res.code === 200 && res.data?.startDate) {
    semesterStartDate.value = res.data.startDate
    const start = new Date(res.data.startDate + 'T00:00:00')
    const totalWeeks = res.data.totalWeeks || 18
    const end = new Date(start.getTime() + totalWeeks * 7 * 86400000)
    const endIso = end.toISOString().split('T')[0]
    if (endIso) semesterEndDate.value = endIso
    semesterFetched = true
  }
}

async function loadStats() {
  await fetchSemester()
  if (activeTab.value === 'dormitory') {
    const r = await getDutyCounts('dormitory', semesterStartDate.value, semesterEndDate.value)
    if (r.code===200) dormitoryStats.value = r.data||[]
  }
  else if (activeTab.value === 'office') {
    const r = await getDutyCounts('office', semesterStartDate.value, semesterEndDate.value)
    if (r.code===200) officeStats.value = r.data||[]
  }
  else if (activeTab.value === 'history' && !semesters.value.length) { await loadSemesters(); await loadBuildings() }
}

async function loadSemesters() { const r = await request.get('/history/semesters'); if (r.code===200) { semesters.value=r.data||[]; if(semesters.value.length){semesterId.value=semesters.value[0].id; onSemesterChange()} } }
async function loadBuildings() {
  if (hType.value === 'dormitory') {
    const r = await request.get('/dormitories')
    if (r.code===200) buildings.value = r.data || []
  } else {
    const r = await getOfficeList({ status: 1 })
    if (r.code===200) buildings.value = r.data || []
  }
}
function onTypeChange() {
  hBuilding.value = ''
  loadBuildings()
  loadHistory()
}
async function onSemesterChange() {
  const s = semesters.value.find(x => x.id === semesterId.value)
  if (!s?.startDate) return
  const r = await request.get('/history/weeks', { params: { startDateStr: s.startDate } })
  if (r.code === 200) {
    pastWeeks.value = r.data || []
    // 默认选中最近一周，避免下拉框空白或固定显示旧值
    selectedWeekNum.value = pastWeeks.value.length ? pastWeeks.value[pastWeeks.value.length - 1].weekNum : null
    await loadHistory()
  }
}
function fmtDate(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

async function loadHistory() {
  if (selectedWeekNum.value == null) return
  const wk = pastWeeks.value.find(w => w.weekNum === selectedWeekNum.value)
  if (!wk) return
  hLoading.value = true
  const api = hType.value === 'dormitory' ? '/history/dormitory' : '/history/office'
  const range = getHistoryRange(wk)
  const p: any = { startDate: range.start, endDate: range.end }
  if (hBuilding.value) p.locationId = hBuilding.value
  const r = await request.get(api, { params: p })
  if (r.code === 200) hData.value = r.data || []
  hLoading.value = false
}

// 计算当前筛选对应的历史起止日期（与页面展示一致：宿舍排班为周日~周四，办公室为周一~周五）
function getHistoryRange(wk: any) {
  let start = wk.startDate
  let end = wk.endDate
  if (hType.value === 'dormitory') {
    const sd = new Date(start + 'T00:00:00')
    const ed = new Date(start + 'T00:00:00')
    sd.setDate(sd.getDate() - 1) // 周日
    ed.setDate(ed.getDate() + 3)  // 周四
    start = fmtDate(sd)
    end = fmtDate(ed)
  }
  return { start, end }
}

// 触发浏览器下载 blob 文件
function downloadBlob(data: any, filename: string) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

// 导出宿舍值班统计 Excel（全学期，与页面统计同一范围）
async function exportDormitoryCounts() {
  await fetchSemester()
  try {
    const res = await exportDutyCountsExcel({ type: 'dormitory', startDate: semesterStartDate.value, endDate: semesterEndDate.value })
    downloadBlob(res.data, '宿舍值班统计.xlsx')
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

// 导出办公室值班统计 Excel（全学期，与页面统计同一范围）
async function exportOfficeCounts() {
  await fetchSemester()
  try {
    const res = await exportDutyCountsExcel({ type: 'office', startDate: semesterStartDate.value, endDate: semesterEndDate.value })
    downloadBlob(res.data, '办公室值班统计.xlsx')
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

// 导出历史记录 Excel（按当前筛选条件：类型+周次+楼）
async function exportHistory() {
  if (selectedWeekNum.value == null) { ElMessage.warning('请先选择周次'); return }
  const wk = pastWeeks.value.find(w => w.weekNum === selectedWeekNum.value)
  if (!wk) return
  const range = getHistoryRange(wk)
  const p: any = { type: hType.value, startDate: range.start, endDate: range.end, week: selectedWeekNum.value }
  if (hBuilding.value) p.locationId = hBuilding.value
  try {
    const res = await exportHistoryExcel(p)
    const name = `历史记录（${hType.value === 'dormitory' ? '宿舍' : '办公室'}）_第${selectedWeekNum.value}周.xlsx`
    downloadBlob(res.data, name)
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

function onTabChange(tab: string) { activeTab.value = tab; loadStats() }

function onVisibilityChange() {
  if (document.visibilityState === 'visible') loadStats()
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    if (document.visibilityState === 'visible') loadStats()
  }, 30000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

onMounted(() => {
  loadStats()
  startPolling()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.stats-page { padding: 16px; max-width: 1000px; margin: 0 auto; }
.stats-tabs :deep(.el-tabs__header) { margin-bottom: 4px; }
.stats-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; }
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.search-inp { width: 180px; }
.total-hint { font-size: 13px; color: #999; }
.stats-table { border-radius: 8px; overflow: hidden; }
.stats-table :deep(.cell) { font-size: 14px; }
.stats-table :deep(td) { padding: 6px 0; }
.stats-table :deep(th) .cell { font-size: 13px; }
.stats-row:hover { background: #f5f8ff; }
.badge { display: inline-block; min-width: 32px; padding: 3px 12px; border-radius: 10px; font-size: 14px; font-weight: 500; }
.badge-warning { background: #fff3e0; color: #e65100; }
.badge-success { background: #e8f5e9; color: #2e7d32; }
.badge-primary { background: #e3f2fd; color: #1565c0; }
.total { background: #f3e5f5; color: #7b1fa2; font-weight: 600; min-width: 36px; }
.filter-bar { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin-bottom: 4px; }
.history-block { margin-bottom: 12px; }
.history-title { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 4px; }
.history-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.history-table th, .history-table td { border: 1px solid #e8e8e8; padding: 5px 8px; }
.history-table th { background: #f8fafc; font-weight: 600; color: #555; font-size: 13px; }
.history-table td { color: #333; }
.history-table tbody tr:hover { background: #f5f8ff; }
</style>
