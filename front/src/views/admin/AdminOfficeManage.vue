<template>
  <div class="office-manage">
    <div class="toolbar">
      <el-button type="primary" @click="openAdd"><el-icon><Plus /></el-icon>添加办公室</el-button>
      <span style="margin-left:14px;display:inline-flex;align-items:center;gap:6px">
        <span>每节课值班人数</span>
        <el-input-number v-model="slotCap" :min="1" :max="20" size="small" style="width:110px" />
        <el-button size="small" type="primary" plain :loading="capSaving" @click="saveCap">保存</el-button>
      </span>
    </div>

    <el-card>
      <el-table :data="offices" stripe border v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="code" label="编码" width="80" />
        <el-table-column prop="name" label="名称" width="160" />
        <el-table-column prop="building" label="楼栋" width="80" />
        <el-table-column prop="floor" label="楼层" width="70" />
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showDialog" :title="isEdit ? '编辑办公室' : '添加办公室'" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="如 B101" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 教务处办公室" />
        </el-form-item>
        <el-form-item label="楼栋">
          <el-input v-model="form.building" placeholder="如 B" />
        </el-form-item>
        <el-form-item label="楼层">
          <el-input v-model="form.floor" placeholder="如 2F" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="禁用" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getOfficeList, addOffice, updateOffice, deleteOffice } from '@/api/office'
import request from '@/utils/request'

interface OfficeItem { id?: number; code: string; name: string; building: string; floor: string; description: string; status: number; statusBool?: boolean }

const loading = ref(false)
const offices = ref<OfficeItem[]>([])
const showDialog = ref(false)
const isEdit = ref(false)
const formRef = ref()

const form = reactive<OfficeItem>({
  code: '', name: '', building: '', floor: '', description: '', status: 1, statusBool: true,
})

const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  const res = await getOfficeList()
  if (res.code === 200) offices.value = res.data || []
  loading.value = false
}

function resetForm() {
  Object.assign(form, { id: undefined, code: '', name: '', building: '', floor: '', description: '', status: 1, statusBool: true })
  formRef.value?.resetFields()
}

function openAdd() { isEdit.value = false; resetForm(); showDialog.value = true }

function openEdit(row: OfficeItem) {
  isEdit.value = true
  Object.assign(form, { ...row, statusBool: row.status === 1 })
  showDialog.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const data = { ...form, status: form.statusBool ? 1 : 0 }
  if (isEdit.value) {
    await updateOffice(data)
    ElMessage.success('修改成功')
  } else {
    await addOffice(data)
    ElMessage.success('添加成功')
  }
  showDialog.value = false
  loadData()
}

async function handleDelete(row: OfficeItem) {
  await ElMessageBox.confirm(`确定删除 "${row.name}"？`, '提示', { type: 'warning' })
  await deleteOffice(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

// ===== 每节课值班人数（全局）=====
const slotCap = ref(4)
const capSaving = ref(false)
async function loadCap() {
  try { const res = await request.get('/office-schedule-config'); if (res.code === 200) slotCap.value = res.data?.slotCapacity ?? 4 } catch {}
}
async function saveCap() {
  capSaving.value = true
  try {
    const v = Number(slotCap.value)
    if (!Number.isInteger(v) || v < 1 || v > 20) { ElMessage.warning('每节课人数须为 1~20 的整数'); return }
    const res = await request.put('/office-schedule-config', { slotCapacity: v })
    if (res.code === 200) ElMessage.success(`已保存：每节课 ${res.data?.slotCapacity} 人`)
    else ElMessage.error(res.message || '保存失败')
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { capSaving.value = false }
}

onMounted(() => { loadData(); loadCap() })
</script>

<style scoped>
.office-manage { display: flex; flex-direction: column; gap: 16px; height: 100%; }
.toolbar { display: flex; gap: 8px; }
</style>
