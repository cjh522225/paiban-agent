<template>
  <div class="swap-requests-page">
    <div class="control-card">
      <div class="control-row">
        <el-radio-group v-model="filterStatus" @change="loadList">
          <el-radio-button value="pending">待处理</el-radio-button>
          <el-radio-button value="approved">已同意</el-radio-button>
          <el-radio-button value="rejected">已拒绝</el-radio-button>
          <el-radio-button value="">全部</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="filterType" @change="loadList" style="margin-left:16px">
          <el-radio-button value="">全部类型</el-radio-button>
          <el-radio-button value="office">办公室</el-radio-button>
          <el-radio-button value="dormitory">宿舍</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <el-card>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="userName" label="申请人" width="90" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === 'dormitory' ? 'warning' : 'primary'" size="small">
              {{ row.type === 'dormitory' ? '宿舍' : '办公室' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前班次" min-width="150">
          <template #default="{ row }">第{{ row.weekNumber }}周 {{ row.dutyDate }} {{ row.timeSlot }} · {{ row.locationName }}</template>
        </el-table-column>
        <el-table-column label="想换到" width="110">
          <template #default="{ row }">{{ row.targetDay }} {{ row.targetSlot }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'pending' ? 'warning' : row.status === 'approved' ? 'success' : 'danger'" size="small">
              {{ row.status === 'pending' ? '待处理' : row.status === 'approved' ? '已同意' : '已拒绝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'pending'">
              <el-button type="success" link size="small" @click="handleApprove(row, 'approved')">同意</el-button>
              <el-button type="danger" link size="small" @click="handleApprove(row, 'rejected')">拒绝</el-button>
            </template>
            <el-button type="info" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!list.length && !loading" class="empty">暂无换班请求</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSwapRequests, approveSwapRequest, deleteSwapRequest } from '@/api/swapRequest'

const filterStatus = ref('pending')
const filterType = ref('')
const list = ref<any[]>([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  const params: any = {}
  if (filterStatus.value) params.status = filterStatus.value
  if (filterType.value) params.type = filterType.value
  const r = await getSwapRequests(params)
  if (r.code === 200) list.value = r.data || []
  loading.value = false
}

async function handleApprove(row: any, action: string) {
  const label = action === 'approved' ? '同意' : '拒绝'
  const ok = await ElMessageBox.confirm(`确认${label} ${row.userName} 的换班申请？`, `换班申请${label}`, { type: 'warning' }).catch(() => false)
  if (!ok) return
  const r = await approveSwapRequest(row.id, action)
  if (r.code === 200) { ElMessage.success(`已${label}`); await loadList() }
}

async function handleDelete(row: any) {
  const ok = await ElMessageBox.confirm(`删除 ${row.userName} 的换班请求？`, '确认删除', { type: 'warning' }).catch(() => false)
  if (!ok) return
  const r = await deleteSwapRequest(row.id)
  if (r.code === 200) { ElMessage.success('已删除'); await loadList() }
}

onMounted(loadList)
</script>

<style scoped>
.swap-requests-page { display: flex; flex-direction: column; gap: 16px; }
.control-card { background: #fff; border-radius: 12px; padding: 14px 18px; box-shadow: 0 2px 8px rgba(0,0,0,.06); }
.control-row { display: flex; align-items: center; }
.empty { text-align: center; padding: 30px 0; color: #9ca3af; }
</style>
