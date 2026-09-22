<template>
  <div class="multi-duty-apply">
    <el-button type="warning" size="small" @click="openMultiDuty">
      <el-icon><Plus /></el-icon>申请多排
    </el-button>
    <el-button link type="primary" size="small" @click="openMyRequests">我的多排申请</el-button>

    <el-dialog v-model="showMultiDialog" :title="'申请多排（' + typeLabel + '）'" width="460px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="值班类型">
          <el-tag>{{ typeLabel }}</el-tag>
        </el-form-item>
        <el-form-item label="周次范围">
          <el-input-number v-model="multiForm.weekStart" :min="1" :max="maxWeek" /> ~
          <el-input-number v-model="multiForm.weekEnd" :min="1" :max="maxWeek" />
        </el-form-item>
        <el-form-item label="申请说明">
          <el-input v-model="multiForm.note" type="textarea" :rows="2" placeholder="如: 本周需要多排值班" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMultiDialog = false">取消</el-button>
        <el-button type="primary" @click="handleMultiSubmit">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showMyRequests" title="我的多排申请" width="520px">
      <el-table :data="myRequests" border stripe size="small" max-height="320">
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ row.type === 'dormitory' ? '宿舍' : '办公室' }}</template>
        </el-table-column>
        <el-table-column label="周次" width="90">
          <template #default="{ row }">第 {{ row.weekStart }}~{{ row.weekEnd }} 周</template>
        </el-table-column>
        <el-table-column prop="note" label="说明" min-width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'pending' ? 'warning' : row.status === 'approved' ? 'success' : 'danger'" size="small">
              {{ row.status === 'pending' ? '待审批' : row.status === 'approved' ? '已通过' : '已拒绝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" type="danger" link size="small" @click="handleCancelMulti(row)">撤销</el-button>
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
import { Plus } from '@element-plus/icons-vue'
import { getMyMultiDuty, submitMultiDuty, deleteMultiDuty } from '@/api/multiDuty'

const props = defineProps<{ type: 'dormitory' | 'office'; typeLabel: string }>()

const maxWeek = 18
const showMultiDialog = ref(false)
const showMyRequests = ref(false)
const myRequests = ref<any[]>([])
const multiForm = ref({ type: props.type, weekStart: 1, weekEnd: 1, note: '' })

function openMultiDuty() {
  multiForm.value = { type: props.type, weekStart: 1, weekEnd: 1, note: '' }
  showMultiDialog.value = true
}

async function loadMyRequests() {
  const r = await getMyMultiDuty()
  if (r.code === 200) myRequests.value = r.data || []
}

async function openMyRequests() {
  await loadMyRequests()
  showMyRequests.value = true
}

async function handleMultiSubmit() {
  if (multiForm.value.weekEnd < multiForm.value.weekStart) return ElMessage.warning('结束周次不能早于开始周次')
  const r = await submitMultiDuty({ ...multiForm.value })
  if (r.code === 200) {
    ElMessage.success('已提交，等待管理员审批')
    showMultiDialog.value = false
    showMyRequests.value = true
    await loadMyRequests()
  }
}

async function handleCancelMulti(row: any) {
  const r = await deleteMultiDuty(row.id)
  if (r.code === 200) { ElMessage.success('已撤销'); await loadMyRequests() }
}
</script>

<style scoped>
.multi-duty-apply {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
