<template>
  <div class="dormitory-manage">
    <div class="toolbar">
      <el-button type="primary" @click="openAdd"><el-icon><Plus /></el-icon>添加宿舍楼</el-button>
    </div>

    <el-card>
      <el-table :data="dormitories" stripe border v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="code" label="编码" width="80" />
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="building" label="楼栋" width="80" />
        <el-table-column prop="floor" label="楼层" width="70" />
        <el-table-column prop="gender" label="限制性别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.gender === '男' ? 'primary' : 'danger'" size="small">{{ row.gender || '-' }}</el-tag>
          </template>
        </el-table-column>
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

    <el-dialog v-model="showDialog" :title="isEdit ? '编辑宿舍楼' : '添加宿舍楼'" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="如 A1" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 A1 宿舍楼" />
        </el-form-item>
        <el-form-item label="楼栋">
          <el-input v-model="form.building" placeholder="如 A" />
        </el-form-item>
        <el-form-item label="楼层">
          <el-input-number v-model="form.floor" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="限制性别">
          <el-select v-model="form.gender" clearable style="width:100%">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
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
import { getDormitoryList, addDormitory, updateDormitory, deleteDormitory } from '@/api/dormitory'

interface DormItem { id?: number; code: string; name: string; building: string; floor: number | null; gender: string; description: string; status: number; statusBool?: boolean }

const loading = ref(false)
const dormitories = ref<DormItem[]>([])
const showDialog = ref(false)
const isEdit = ref(false)
const formRef = ref()

const form = reactive<DormItem>({
  code: '', name: '', building: '', floor: null, gender: '', description: '', status: 1, statusBool: true,
})

const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  const res = await getDormitoryList()
  if (res.code === 200) dormitories.value = res.data || []
  loading.value = false
}

function resetForm() {
  Object.assign(form, { id: undefined, code: '', name: '', building: '', floor: null, gender: '', description: '', status: 1, statusBool: true })
  formRef.value?.resetFields()
}

function openAdd() { isEdit.value = false; resetForm(); showDialog.value = true }

function openEdit(row: DormItem) {
  isEdit.value = true
  Object.assign(form, { ...row, statusBool: row.status === 1 })
  showDialog.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const data = { ...form, status: form.statusBool ? 1 : 0 }
  if (isEdit.value) {
    await updateDormitory(data)
    ElMessage.success('修改成功')
  } else {
    await addDormitory(data)
    ElMessage.success('添加成功')
  }
  showDialog.value = false
  loadData()
}

async function handleDelete(row: DormItem) {
  await ElMessageBox.confirm(`确定删除 "${row.name}"？`, '提示', { type: 'warning' })
  await deleteDormitory(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.dormitory-manage { display: flex; flex-direction: column; gap: 16px; height: 100%; }
.toolbar { display: flex; gap: 8px; }
</style>
