<template>
  <div class="multi-duty-page">
    <div class="control-card">
      <div class="control-row">
        <el-radio-group v-model="filterStatus" @change="loadList">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="pending">待审批</el-radio-button>
          <el-radio-button value="approved">已通过</el-radio-button>
          <el-radio-button value="rejected">已拒绝</el-radio-button>
        </el-radio-group>
        <el-button type="primary" style="margin-left:auto" @click="showAdd = true"><el-icon><Plus /></el-icon>代客申请</el-button>
      </div>
    </div>

    <el-card>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="userName" label="申请人" width="100" />
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 'dormitory' ? 'warning' : 'primary'" size="small">
              {{ row.type === 'dormitory' ? '宿舍' : '办公室' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="多排周次" width="110">
          <template #default="{ row }">第 {{ row.weekStart }} ~ {{ row.weekEnd }} 周</template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'pending' ? 'warning' : row.status === 'approved' ? 'success' : 'danger'" size="small">
              {{ row.status === 'pending' ? '待审批' : row.status === 'approved' ? '已通过' : '已拒绝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'pending'">
              <el-button type="success" link size="small" @click="handleApprove(row, 'approved')">通过</el-button>
              <el-button type="danger" link size="small" @click="handleApprove(row, 'rejected')">拒绝</el-button>
            </template>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAdd" title="代客申请多排" width="440px">
      <el-form label-width="80px">
        <el-form-item label="用户">
          <el-select v-model="addForm.userId" filterable placeholder="选择用户" style="width:100%">
            <el-option v-for="u in users" :key="u.id" :label="`${u.realName}(${u.username})`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="addForm.type">
            <el-radio-button value="office">办公室</el-radio-button>
            <el-radio-button value="dormitory">宿舍</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="周次范围">
          <el-input-number v-model="addForm.weekStart" :min="1" :max="18" /> ~
          <el-input-number v-model="addForm.weekEnd" :min="1" :max="18" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.note" type="textarea" :rows="2" placeholder="如: 本周需要多排" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">提交并默认通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getMultiDutyList, approveMultiDuty, deleteMultiDuty, submitMultiDuty } from '@/api/multiDuty'
import request from '@/utils/request'

const filterStatus = ref('pending')
const list = ref<any[]>([])
const loading = ref(false)
const showAdd = ref(false)
const users = ref<any[]>([])
const addForm = ref({ userId: null as number | null, type: 'office', weekStart: 1, weekEnd: 1, note: '' })

function fmtTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

async function loadList() {
  loading.value = true
  const r = await getMultiDutyList(filterStatus.value || undefined)
  if (r.code === 200) list.value = r.data || []
  loading.value = false
}

async function loadUsers() {
  const r = await request.get('/users')
  if (r.code === 200) users.value = r.data || []
}

async function handleApprove(row: any, action: string) {
  const r = await approveMultiDuty(row.id, action)
  if (r.code === 200) { ElMessage.success(action === 'approved' ? '已通过' : '已拒绝'); await loadList() }
}

async function handleDelete(row: any) {
  const ok = await ElMessageBox.confirm(`删除 ${row.userName} 的多排申请？`, '确认删除', { type: 'warning' }).catch(() => false)
  if (!ok) return
  const r = await deleteMultiDuty(row.id)
  if (r.code === 200) { ElMessage.success('已删除'); await loadList() }
}

async function handleAdd() {
  if (!addForm.value.userId) return ElMessage.warning('请选择用户')
  const r = await submitMultiDuty({ ...addForm.value })
  if (r.code === 200) {
    const newest = (await getMultiDutyList('pending')).data?.[0]
    if (newest) await approveMultiDuty(newest.id, 'approved')
    ElMessage.success('已提交并默认通过')
    showAdd.value = false
    filterStatus.value = ''
    await loadList()
  }
}

onMounted(() => { loadList(); loadUsers() })
</script>

<style scoped>
.multi-duty-page { display: flex; flex-direction: column; gap: 16px; }
.control-card { background: #fff; border-radius: 12px; padding: 14px 18px; box-shadow: 0 2px 8px rgba(0,0,0,.06); }
.control-row { display: flex; gap: 12px; align-items: center; }
</style>
