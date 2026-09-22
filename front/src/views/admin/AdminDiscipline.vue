<template>
  <div class="discipline-page">
    <el-alert v-if="pendingActions.length" type="error" show-icon closable class="action-alert" title="以下人员需作出纪律处理（处理后点击'已处理'消除提醒）">
      <div v-for="a in pendingActions" :key="a.id" class="action-line">
        <el-tag :type="a.action === '退出发展' ? 'danger' : 'warning'" size="small">{{ a.action }}</el-tag>
        <span>{{ a.userName }}（旷班 {{ a.absentCount }} 次）</span>
      </div>
    </el-alert>

    <div class="control-card">
      <div class="control-row">
        <el-button type="primary" @click="showAdd = true"><el-icon><Plus /></el-icon>登记缺勤/迟到</el-button>
        <el-radio-group v-model="filterType" @change="loadRecords" style="margin-left:16px">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="late">迟到</el-radio-button>
          <el-radio-button value="absent">缺勤</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <el-card>
      <div class="section-title">纪律累计（迟到3次=旷班1次；旷班≥3通报批评；≥5退出发展）</div>
      <el-table :data="summary" border stripe size="small" v-loading="loading" :row-class-name="rowClassName">
        <el-table-column prop="userName" label="姓名" width="100" />
        <el-table-column prop="lateCount" label="迟到次数" width="90" />
        <el-table-column prop="absentCount" label="缺勤次数" width="90" />
        <el-table-column prop="absentFromLate" label="迟到折算旷班" width="110" />
        <el-table-column prop="totalAbsent" label="总旷班" width="80" sortable />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '退出发展' ? 'danger' : row.status === '通报批评' ? 'warning' : 'success'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.status !== '正常'" type="success" link size="small" @click="handleAction(row)">已处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <div class="section-title">记录明细</div>
      <el-table :data="records" border stripe size="small" v-loading="loading">
        <el-table-column prop="userName" label="姓名" width="100" />
        <el-table-column label="日期" width="120">
          <template #default="{ row }">{{ row.dutyDate }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.recordType === 'late' ? 'warning' : 'danger'" size="small">
              {{ row.recordType === 'late' ? '迟到' : '缺勤' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="120" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAdd" title="登记缺勤/迟到" width="440px">
      <el-form label-width="80px">
        <el-form-item label="用户">
          <el-select v-model="form.userId" filterable placeholder="选择用户" style="width:100%">
            <el-option v-for="u in users" :key="u.id" :label="`${u.realName}(${u.username})`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.recordType">
            <el-radio-button value="late">迟到</el-radio-button>
            <el-radio-button value="absent">缺勤</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.dutyDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" placeholder="如: 未按时到岗" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getDisciplineSummary, getDisciplineList, addDiscipline, deleteDiscipline, getDisciplineActions, handleDisciplineAction } from '@/api/discipline'
import request from '@/utils/request'

const summary = ref<any[]>([])
const records = ref<any[]>([])
const loading = ref(false)
const showAdd = ref(false)
const filterType = ref('')
const users = ref<any[]>([])
const form = ref({ userId: null as number | null, recordType: 'late', dutyDate: '', note: '' })
const pendingActions = ref<any[]>([])

async function loadActions() {
  const r = await getDisciplineActions('0')
  if (r.code === 200) pendingActions.value = r.data || []
}

function rowClassName({ row }: any) {
  if (row.status === '退出发展') return 'row-exit'
  if (row.status === '通报批评') return 'row-warn'
  return ''
}

async function handleAction(row: any) {
  const r = await getDisciplineActions('0')
  const actions = (r.code === 200 ? r.data : []) as any[]
  const target = actions.filter((a: any) => a.userId === row.userId && a.action === row.status)[0]
  if (target) {
    const ok2 = await handleDisciplineAction(target.id)
    if (ok2.code === 200) ElMessage.success('已标记处理')
  } else {
    ElMessage.success('已记录（无待处理提醒）')
  }
  await loadAll()
}

async function loadAll() {
  loading.value = true
  const [s, r, a] = await Promise.all([getDisciplineSummary(), getDisciplineList(), getDisciplineActions('0')])
  if (s.code === 200) summary.value = s.data || []
  if (r.code === 200) records.value = r.data || []
  if (a.code === 200) pendingActions.value = a.data || []
  loading.value = false
}

async function loadRecords() {
  loading.value = true
  const r = await getDisciplineList(filterType.value ? { recordType: filterType.value } : undefined)
  if (r.code === 200) records.value = r.data || []
  loading.value = false
}

async function loadUsers() {
  const r = await request.get('/users')
  if (r.code === 200) users.value = r.data || []
}

async function handleAdd() {
  if (!form.value.userId || !form.value.dutyDate) return ElMessage.warning('请选择用户和日期')
  const r = await addDiscipline({ ...form.value })
  if (r.code === 200) { ElMessage.success('已登记'); showAdd.value = false; form.value = { userId: null, recordType: 'late', dutyDate: '', note: '' }; await loadAll() }
}

async function handleDelete(row: any) {
  const ok = await ElMessageBox.confirm(`删除 ${row.userName} 的记录？`, '确认删除', { type: 'warning' }).catch(() => false)
  if (!ok) return
  const r = await deleteDiscipline(row.id)
  if (r.code === 200) { ElMessage.success('已删除'); await loadAll() }
}

onMounted(() => { loadAll(); loadUsers() })
</script>

<style scoped>
.discipline-page { display: flex; flex-direction: column; gap: 16px; }
.control-card { background: #fff; border-radius: 12px; padding: 14px 18px; box-shadow: 0 2px 8px rgba(0,0,0,.06); }
.control-row { display: flex; align-items: center; }
.section-title { font-size: 13px; font-weight: 600; color: #374151; margin-bottom: 10px; }
.action-alert { margin-bottom: 12px; }
.action-line { display: flex; align-items: center; gap: 8px; margin: 3px 0; font-size: 13px; }
:deep(.row-exit) { background: #fef2f2 !important; }
:deep(.row-warn) { background: #fffbeb !important; }
</style>
