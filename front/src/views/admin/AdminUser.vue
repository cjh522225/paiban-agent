<template>
  <div class="user-management">
    <div class="toolbar">
      <el-input v-model="searchKeyword" placeholder="搜索账号/姓名/班级/部门" clearable style="width: 280px" @input="onSearch" />
      <el-button type="primary" @click="openAddDialog"><el-icon><Plus /></el-icon>添加用户</el-button>
      <el-button type="success" @click="showImportDialog = true"><el-icon><Upload /></el-icon>导入Excel</el-button>
      <el-button type="danger" @click="handleBatchDelete"><el-icon><Delete /></el-icon>批量删除</el-button>
    </div>

    <el-card>
      <el-table ref="tableRef" :data="userList" stripe border v-loading="loading" @selection-change="handleSelectionChange" @cell-click="handleCellClick">
        <el-table-column type="selection" width="50" />
        <el-table-column label="序号" width="60">
          <template #default="{ $index }">{{ (currentPage - 1) * pageSize + $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="username" label="账号" width="110" />
        <el-table-column prop="realName" label="姓名" width="80" />
        <el-table-column prop="className" label="班级" width="110" />
        <el-table-column prop="role" label="角色" width="65">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">{{ row.role === 'admin' ? '管理员' : '用户' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dutyRole" label="身份" width="70">
          <template #default="{ row }"><el-tag v-if="row.dutyRole" size="small" type="warning">{{ row.dutyRole }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="gender" label="性别" width="50" />
        <el-table-column prop="department" label="部门" width="85" />
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column prop="status" label="状态" width="60">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <el-dialog v-model="showImportDialog" title="导入用户Excel" width="480px">
      <el-alert title="Excel列顺序" type="info" :closable="false" style="margin-bottom:12px">
        <p>账号(学号) | 姓名 | 性别 | 部门 | 值班身份 | 手机号 | 宿舍楼编码 | 班级</p>
        <p style="font-size:12px;color:#909399">默认密码123456，已有账号跳过</p>
      </el-alert>
      <el-upload ref="uploadRef" drag :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="handleFileChange" :disabled="importing" style="margin-bottom:12px">
        <el-icon :size="40"><UploadFilled /></el-icon>
        <div>拖拽或点击上传Excel文件</div>
      </el-upload>

      <!-- 导入进度：实时显示已处理条数、成功/跳过/失败、耗时 -->
      <div v-if="importProgress" style="margin-top:12px">
        <el-progress :percentage="importPercent" :status="importProgressStatus" :stroke-width="14" />
        <div style="margin-top:8px;font-size:13px;color:#606266;line-height:1.6">
          已处理 {{ importProgress.processed }} / {{ importProgress.total }} 条
          <span v-if="importProgress.status === 'running'" style="margin-left:8px">已用时 {{ importElapsed }} 秒</span>
        </div>
        <div style="margin-top:6px;font-size:13px;color:#606266">
          成功 <span style="color:#67c23a;font-weight:600">{{ importProgress.success }}</span>
          · 跳过 <span style="color:#e6a23c;font-weight:600">{{ importProgress.skip }}</span>
          · 失败 <span style="color:#f56c6c;font-weight:600">{{ importProgress.fail }}</span>
        </div>
        <div v-if="importProgress.errors && importProgress.errors.length" style="margin-top:8px;max-height:120px;overflow:auto;font-size:12px;color:#f56c6c;background:#fef0f0;border-radius:6px;padding:8px 12px">
          <div v-for="(err, idx) in importProgress.errors" :key="idx">{{ err }}</div>
        </div>
      </div>

      <template #footer>
        <el-button @click="closeImportDialog">关闭</el-button>
        <el-button type="primary" @click="handleImport" :loading="importLoading" :disabled="importing">开始导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDialog" :title="isEdit ? '编辑用户' : '添加用户'" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入学号作为账号" />
        </el-form-item>
        <el-form-item label="密码" :prop="isEdit ? '' : 'password'">
          <el-input v-model="form.password" :placeholder="isEdit ? '留空则不修改' : '请输入密码'" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="班级">
          <el-input v-model="form.className" placeholder="如 软件2101" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width:100%">
            <el-option label="管理员" value="admin" />
            <el-option label="普通用户" value="user" />
          </el-select>
        </el-form-item>
        <el-form-item label="值班身份">
          <el-select v-model="form.dutyRole" style="width:100%" clearable placeholder="可不选">
            <el-option label="巡班" value="巡班" />
            <el-option label="坐班" value="坐班" />
            <el-option label="敲灯" value="敲灯" />
          </el-select>
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="form.gender" style="width:100%" clearable><el-option label="男" value="男" /><el-option label="女" value="女" /></el-select>
        </el-form-item>
        <el-form-item label="部门"><el-input v-model="form.department" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="居住宿舍">
          <el-select v-model="form.dormitoryId" style="width:100%" clearable>
            <el-option v-for="d in dormitories" :key="d.value" :label="d.label" :value="d.value" />
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
import { ref, reactive, computed, watch, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, UploadFilled, Delete } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getDormitoryList } from '@/api/dormitory'

interface UserItem {
  id?: number; username: string; password?: string; realName: string;
  className: string; role: string; dutyRole: string; gender: string;
  department: string; phone: string; status: number; dormitoryId: number | null; statusBool?: boolean;
}

const loading = ref(false); const searchKeyword = ref(''); const userList = ref<UserItem[]>([])
const currentPage = ref(1); const pageSize = ref(50); const total = ref(0)
const selectedUsers = ref<UserItem[]>([])
const tableRef = ref()
const showDialog = ref(false); const isEdit = ref(false); const formRef = ref(); const dormitories = ref<any[]>([])
const showImportDialog = ref(false); const importLoading = ref(false)
const importFile = ref<File | null>(null)
const importTaskId = ref('')
const importing = ref(false)
const importProgress = ref<{ total: number; processed: number; success: number; skip: number; fail: number; status: string; errors: string[] } | null>(null)
const importElapsed = ref(0)
let importPollTimer: ReturnType<typeof setInterval> | null = null
let importElapsedTimer: ReturnType<typeof setInterval> | null = null

// 导入进度百分比 / 进度条状态
const importPercent = computed(() => {
  const p = importProgress.value
  if (!p) return 0
  if (p.status === 'done' || p.status === 'error') return 100
  if (!p.total) return 0
  return Math.min(100, Math.round((p.processed / p.total) * 100))
})
const importProgressStatus = computed<'' | 'success' | 'exception'>(() => {
  const p = importProgress.value
  if (!p) return ''
  if (p.status === 'done') return 'success'
  if (p.status === 'error') return 'exception'
  return ''
})

const form = reactive<UserItem>({
  username: '', password: '', realName: '', className: '',
  role: 'user', dutyRole: '', gender: '', department: '', phone: '', status: 1, dormitoryId: null, statusBool: true,
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

function onSearch() { currentPage.value = 1; loadUsers() }

async function loadUsers() {
  loading.value = true
  const params: any = { current: currentPage.value, size: pageSize.value }
  if (searchKeyword.value) params.keyword = searchKeyword.value
  const res = await request.get('/users/page', { params })
  if (res.code === 200) {
    userList.value = res.data?.records || []
    total.value = res.data?.total || 0
  }
  loading.value = false
}

function handleSelectionChange(rows: UserItem[]) { selectedUsers.value = rows }

// 勾选列整格可点击切换（仅勾选列生效）；点到原生勾选框本身交给 Element 原生处理，避免二次切换
function handleCellClick(row: UserItem, column: any, _cell: any, event: MouseEvent) {
  if (column.type !== 'selection') return
  const target = event.target as HTMLElement
  if (target.closest('.el-checkbox')) return
  tableRef.value?.toggleRowSelection(row)
}

async function handleBatchDelete() {
  if (selectedUsers.value.length === 0) { ElMessage.warning('请先勾选要删除的用户'); return }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedUsers.value.length} 个用户？`, '提示', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
  } catch {
    // 用户点了取消：清空勾选状态
    tableRef.value?.clearSelection()
    return
  }
  const ids = selectedUsers.value.map(u => u.id).filter((id): id is number => id != null)
  await request.post('/users/batch-delete', ids)
  ElMessage.success('删除成功')
  selectedUsers.value = []
  // 当前页删空时回退一页
  if (userList.value.length === ids.length && currentPage.value > 1) currentPage.value--
  loadUsers()
}

async function loadDormitories() {
  const res = await getDormitoryList()
  if (res.code === 200 && Array.isArray(res.data)) {
    dormitories.value = res.data.map((d: any) => ({ label: `${d.name}(${d.gender||'-'})`, value: d.id }))
  }
}

function resetForm() {
  Object.assign(form, { id: undefined, username: '', password: '', realName: '', className: '', role: 'user', dutyRole: '', gender: '', department: '', phone: '', status: 1, dormitoryId: null, statusBool: true })
  formRef.value?.resetFields()
}

function openAddDialog() { isEdit.value = false; resetForm(); showDialog.value = true }

function openEditDialog(row: UserItem) {
  isEdit.value = true
  Object.assign(form, { ...row, password: '', statusBool: row.status === 1, className: row.className || '' })
  showDialog.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const data: any = {
    id: form.id, username: form.username, realName: form.realName,
    className: form.className || null, role: form.role, dutyRole: form.dutyRole || null,
    gender: form.gender || null, department: form.department || null,
    phone: form.phone || null, status: form.statusBool ? 1 : 0,
    dormitoryId: form.dormitoryId || null,
  }
  if (!isEdit.value || form.password) data.password = form.password
  try {
    if (isEdit.value) { await request.put('/users', data); ElMessage.success('修改成功') }
    else { await request.post('/users', data); ElMessage.success('添加成功') }
    showDialog.value = false; loadUsers()
  } catch {}
}

async function handleDelete(row: UserItem) {
  await ElMessageBox.confirm(`确定删除 "${row.realName}"？`, '提示', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
  await request.delete(`/users/${row.id}`); ElMessage.success('删除成功'); loadUsers()
}

function handleFileChange(file: any) { importFile.value = file.raw }

// 导入改为"提交后台任务"，立即返回任务ID，再轮询进度接口
async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请选择文件'); return }
  if (importing.value) return
  importing.value = true
  importLoading.value = true
  importProgress.value = null
  importElapsed.value = 0
  const fd = new FormData(); fd.append('file', importFile.value)
  try {
    const res = await request.post('/users/import/excel', fd, { timeout: 60000 })
    if (res.code === 200 && res.data?.taskId) {
      importTaskId.value = res.data.taskId
      startImportPolling()
    } else {
      importing.value = false
      ElMessage.error(res.message || '导入任务提交失败')
    }
  } catch (e: any) {
    importing.value = false
    ElMessage.error(e.message || '导入任务提交失败')
  }
  finally { importLoading.value = false }
}

function startImportPolling() {
  // 耗时计时器（秒）
  if (importElapsedTimer) clearInterval(importElapsedTimer)
  importElapsedTimer = setInterval(() => { importElapsed.value += 1 }, 1000)
  // 进度轮询
  if (importPollTimer) clearInterval(importPollTimer)
  importPollTimer = setInterval(async () => {
    try {
      const res = await request.get(`/users/import/progress/${importTaskId.value}`)
      if (res.code !== 200 || res.data == null) {
        // 任务不存在（如服务器重启）：停止轮询，提示刷新
        stopImportPolling()
        importing.value = false
        ElMessage.warning('导入任务已失效，请关闭窗口后刷新列表查看结果')
        return
      }
      importProgress.value = res.data
      if (res.data.status === 'done') {
        stopImportPolling()
        importing.value = false
        ElMessage.success(`导入完成: 成功${res.data.success}人, 跳过${res.data.skip}人, 失败${res.data.fail}人`)
        loadUsers()
      } else if (res.data.status === 'error') {
        stopImportPolling()
        importing.value = false
        ElMessage.error(res.data.message || '导入失败')
      }
    } catch { /* 单次轮询失败忽略，下次继续 */ }
  }, 600)
}

function stopImportPolling() {
  if (importPollTimer) { clearInterval(importPollTimer); importPollTimer = null }
  if (importElapsedTimer) { clearInterval(importElapsedTimer); importElapsedTimer = null }
}

// 关闭导入弹窗：导入进行中给出提示（后台任务仍会继续执行）
async function closeImportDialog() {
  if (importing.value) {
    try {
      await ElMessageBox.confirm('导入正在进行，关闭后将看不到进度（导入仍会继续），确定关闭？', '提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
    } catch { return }
  }
  stopImportPolling()
  importing.value = false
  importProgress.value = null
  importFile.value = null
  showImportDialog.value = false
}

const weekDayLabels = ['', '周一', '周二', '周三', '周四', '周五']
const weekParityLabels: Record<string, string> = { odd: '单周', even: '双周', both: '不限' }

onMounted(() => { loadUsers(); loadDormitories() })

onActivated(() => { loadUsers(); loadDormitories() })

// 弹窗被直接关闭（如右上角 X）时停止轮询
watch(showImportDialog, (val) => {
  if (!val) { stopImportPolling(); importing.value = false }
})
</script>

<style scoped>
.user-management { display: flex; flex-direction: column; gap: 16px; height: 100%; }
.toolbar { display: flex; gap: 12px; align-items: center; }
</style>
