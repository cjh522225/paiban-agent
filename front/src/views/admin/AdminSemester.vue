<template>
  <div class="semester-shell">
    <section class="semester-hero">
      <div class="hero-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>
      </div>
      <h1 class="hero-title">学期调整</h1>
      <p class="hero-sub">配置学期时间线与节假日规则</p>
    </section>

    <nav class="tab-bar">
      <button :class="{ active: activeTab === 'semester' }" @click="activeTab = 'semester'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
        学期设置
      </button>
      <button :class="{ active: activeTab === 'holiday' }" @click="activeTab = 'holiday'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M5 3v2M19 3v2M3 9h18M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/><path d="M9 15l2 2 4-4"/></svg>
        节假日
      </button>
      <button :class="{ active: activeTab === 'adjustment' }" @click="activeTab = 'adjustment'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M8 7V3m8 4V3M3 11h18M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/><path d="M12 15h.01M16 15h.01M8 15h.01"/></svg>
        调休
      </button>
    </nav>

    <!-- 学期设置 -->
    <div v-show="activeTab === 'semester'" class="panel">
      <div class="form-card">
        <div class="field">
          <label>学期名称</label>
          <input v-model="form.name" placeholder="如 2025-2026学年第二学期" />
        </div>
        <div class="field">
          <label>开始日期（周一）</label>
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择第一周的周一" class="pure-picker" />
        </div>
        <div class="field">
          <label>总周数</label>
          <el-input-number v-model="form.totalWeeks" :min="1" :max="30" class="pure-number" />
        </div>
        <div class="btn-save-wrap"><button class="btn-save" :disabled="saving" @click="handleSave">
          <span v-if="!saving">保存设置</span>
          <span v-else class="spinner"></span>
        </button>
        </div>
      </div>

      <div v-if="config" class="info-strip">
        <span class="info-dot"></span>
        <span class="info-text">{{ config.name }}</span>
        <span class="info-sep">·</span>
        <span class="info-text">{{ config.startDate }} 起</span>
        <span class="info-sep">·</span>
        <span class="info-text">共 {{ config.totalWeeks }} 周</span>
      </div>
    </div>

    <!-- 节假日 -->
    <div v-show="activeTab === 'holiday'" class="panel">
      <div class="holiday-toolbar">
        <button class="btn-add" @click="openHolidayAdd">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
          添加节假日
        </button>
      </div>

      <div v-if="holidays.length" class="holiday-list" v-loading="hLoading">
        <div v-for="h in holidays" :key="h.id" class="holiday-item">
          <div class="holiday-left">
            <span class="holiday-name">{{ h.name }}</span>
            <span class="holiday-range">{{ h.startDate }} → {{ h.endDate }}</span>
          </div>
          <button class="btn-del" @click="handleHolidayDelete(h)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
        </div>
      </div>
      <div v-else class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1"><path d="M5 3v2M19 3v2M3 9h18M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>
        <p>暂未设置节假日</p>
      </div>
    </div>

    <!-- 调休 -->
    <div v-show="activeTab === 'adjustment'" class="panel">
      <div class="holiday-toolbar">
        <button class="btn-add" @click="openAdjustAdd">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
          添加调休
        </button>
      </div>

      <div v-if="adjustments.length" class="holiday-list" v-loading="adjLoading">
        <div v-for="a in adjustments" :key="a.id" class="holiday-item">
          <div class="holiday-left">
            <span class="holiday-name">{{ a.note || '调休' }}</span>
            <span class="holiday-range">{{ a.startDate }} → {{ a.endDate }}</span>
            <span v-if="a.makeups?.length" class="holiday-range">
              补课：{{ a.makeups.map((m: any) => `${m.makeupDate}（${weekdayOf(m.makeupDate)}）补 ${m.replacedDate}（${weekdayOf(m.replacedDate)}·${parityOf(m.replacedDate)}）`).join('；') }}
            </span>
          </div>
          <button class="btn-del" @click="handleAdjustDelete(a)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
        </div>
      </div>
      <div v-else class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1"><path d="M8 7V3m8 4V3M3 11h18M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>
        <p>暂未设置调休</p>
      </div>
    </div>

    <!-- 对话框 -->
    <Teleport to="body">
      <div v-if="showHolidayDialog" class="overlay" @click.self="showHolidayDialog = false">
        <div class="dialog">
          <h3 class="dialog-title">添加节假日</h3>
          <div class="field">
            <label>名称</label>
            <input v-model="hForm.name" placeholder="如 清明节" />
          </div>
          <div class="field-row">
            <div class="field">
              <label>开始</label>
              <el-date-picker v-model="hForm.startDate" type="date" value-format="YYYY-MM-DD" class="pure-picker" />
            </div>
            <div class="field">
              <label>结束</label>
              <el-date-picker v-model="hForm.endDate" type="date" value-format="YYYY-MM-DD" class="pure-picker" />
            </div>
          </div>
          <div class="dialog-actions">
            <button class="btn-cancel" @click="showHolidayDialog = false">取消</button>
            <button class="btn-confirm" @click="handleHolidayAdd">确定</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 调休设置对话框 -->
    <Teleport to="body">
      <div v-if="showAdjustDialog" class="overlay" @click.self="showAdjustDialog = false">
        <div class="dialog" style="width: 540px">
          <h3 class="dialog-title">设置调休</h3>
          <div class="field">
            <label>调休周期</label>
            <div class="field-row">
              <div class="field"><label>开始日期</label><el-date-picker v-model="aForm.startDate" type="date" value-format="YYYY-MM-DD" class="pure-picker" /></div>
              <div class="field"><label>结束日期</label><el-date-picker v-model="aForm.endDate" type="date" value-format="YYYY-MM-DD" class="pure-picker" /></div>
            </div>
            <p style="font-size:12px;color:#94a3b8;margin:4px 0 0">宿舍排班将覆盖 开始前一天 ~ 结束前一天 的晚上值班</p>
          </div>
          <div class="field">
            <label>说明</label>
            <input v-model="aForm.note" placeholder="如 国庆调休" />
          </div>
          <div class="field">
            <label>补课安排（调休覆盖周六/周日时填写，可多条；空闲时间按"补哪天的课"的星期+单双周计算）</label>
            <div v-for="(mk, i) in aForm.makeups" :key="i" class="makeup-row">
              <el-date-picker v-model="mk.makeupDate" type="date" value-format="YYYY-MM-DD" class="pure-picker" placeholder="补班日(周六/周日)" />
              <el-date-picker v-model="mk.replacedDate" type="date" value-format="YYYY-MM-DD" class="pure-picker" placeholder="补哪天的课" />
              <span v-if="mk.replacedDate" class="makeup-tip">{{ weekdayOf(mk.replacedDate) }} · {{ parityOf(mk.replacedDate) }}</span>
              <button class="btn-del" @click="aForm.makeups.splice(i, 1)"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg></button>
            </div>
            <button class="btn-add" style="margin-top:8px" @click="aForm.makeups.push({ makeupDate: '', replacedDate: '' })">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
              添加补课
            </button>
          </div>
          <div class="dialog-actions">
            <button class="btn-cancel" @click="showAdjustDialog = false">取消</button>
            <button class="btn-confirm" @click="handleAdjustAdd">保存</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const activeTab = ref('semester')
const saving = ref(false); const config = ref<any>(null)
const form = reactive({ id: null as number|null, name: '', startDate: '', totalWeeks: 18 })

const holidays = ref<any[]>([]); const hLoading = ref(false)
const showHolidayDialog = ref(false)
const hForm = reactive({ name: '', startDate: '', endDate: '' })

// ========== 调休 ==========
const adjustments = ref<any[]>([]); const adjLoading = ref(false)
const showAdjustDialog = ref(false)
const aForm = reactive({ startDate: '', endDate: '', note: '', makeups: [] as { makeupDate: string; replacedDate: string }[] })
const weekDayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
function weekdayOf(d: string) { return d ? weekDayNames[new Date(d + 'T00:00:00').getDay()] : '' }
function parityOf(d: string) {
  const st = config.value?.startDate
  if (!d || !st) return ''
  const base = new Date(st + 'T00:00:00')
  const firstMon = new Date(base); firstMon.setDate(firstMon.getDate() - ((firstMon.getDay() + 6) % 7))
  const dt = new Date(d + 'T00:00:00')
  const week = Math.floor((dt.getTime() - firstMon.getTime()) / (7 * 24 * 60 * 60 * 1000)) + 1
  return week % 2 === 1 ? '单周' : '双周'
}
async function loadAdjustments() {
  adjLoading.value = true
  const r = await request.get('/duty-adjustments')
  if (r.code === 200) adjustments.value = r.data || []
  adjLoading.value = false
}
function openAdjustAdd() {
  aForm.startDate = ''; aForm.endDate = ''; aForm.note = ''; aForm.makeups = []
  showAdjustDialog.value = true
}
async function handleAdjustAdd() {
  if (!aForm.startDate || !aForm.endDate) { ElMessage.warning('请填写调休周期'); return }
  if (aForm.endDate < aForm.startDate) { ElMessage.warning('结束日期不能早于开始日期'); return }
  const makeups = aForm.makeups.filter(m => m.makeupDate && m.replacedDate)
    .map(m => ({ makeupDate: m.makeupDate, replacedDate: m.replacedDate }))
  await request.post('/duty-adjustments', { startDate: aForm.startDate, endDate: aForm.endDate, note: aForm.note, makeups })
  ElMessage.success('保存成功'); showAdjustDialog.value = false; loadAdjustments()
}
async function handleAdjustDelete(row: any) {
  await ElMessageBox.confirm(`确定删除该调休？`, '提示', { type: 'warning' })
  await request.delete(`/duty-adjustments/${row.id}`); ElMessage.success('删除成功'); loadAdjustments()
}

async function loadConfig() {
  const r = await request.get('/semester')
  if (r.code===200 && r.data) { config.value=r.data; form.id=r.data.id; form.name=r.data.name; form.startDate=r.data.startDate; form.totalWeeks=r.data.totalWeeks }
}
async function handleSave() {
  if(!form.name||!form.startDate){ElMessage.warning('请填写完整');return}
  saving.value=true
  await request.post('/semester',{id:form.id,name:form.name,startDate:form.startDate,totalWeeks:form.totalWeeks})
  ElMessage.success('保存成功'); saving.value=false; loadConfig()
}
async function loadHolidays() { hLoading.value=true; const r=await request.get('/holidays'); if(r.code===200) holidays.value=r.data||[]; hLoading.value=false }
function openHolidayAdd(){hForm.name='';hForm.startDate='';hForm.endDate='';showHolidayDialog.value=true}
async function handleHolidayAdd(){
  if(!hForm.name||!hForm.startDate){ElMessage.warning('请填写完整');return}
  await request.post('/holidays',{name:hForm.name,startDate:hForm.startDate,endDate:hForm.endDate||hForm.startDate})
  ElMessage.success('添加成功，节假日排班已清除'); showHolidayDialog.value=false; loadHolidays()
}
async function handleHolidayDelete(row:any){
  await ElMessageBox.confirm(`确定删除"${row.name}"？`,'提示',{type:'warning'})
  await request.delete(`/holidays/${row.id}`); ElMessage.success('删除成功'); loadHolidays()
}

onMounted(()=>{loadConfig();loadHolidays();loadAdjustments()})
</script>

<style scoped>
.semester-shell { max-width: 640px; margin: 0 auto; padding: 20px 20px 0; height: 100%; display: flex; flex-direction: column; }

/* Hero */
.semester-hero { text-align: center; margin-bottom: 16px; }
.hero-icon { width: 44px; height: 44px; margin: 0 auto 10px; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #6366f1, #818cf8); border-radius: 16px; color: #fff; }
.hero-icon svg { width: 22px; height: 22px; }
.hero-title { font-size: 20px; font-weight: 700; color: #1e1b4b; margin: 0 0 4px; letter-spacing: -.3px; }
.hero-sub { font-size: 14px; color: #94a3b8; margin: 0; }

/* Tab bar */
.tab-bar { display: flex; gap: 4px; background: #f1f5f9; padding: 3px; border-radius: 10px; margin-bottom: 16px; }
.tab-bar button { flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px; padding: 8px 14px; border: none; border-radius: 10px; font-size: 14px; font-weight: 500; cursor: pointer; color: #64748b; background: transparent; transition: all .25s; }
.tab-bar button svg { width: 16px; height: 16px; }
.tab-bar button.active { background: #fff; color: #4338ca; box-shadow: 0 1px 3px rgba(0,0,0,.08), 0 1px 2px rgba(0,0,0,.04); }

/* Panel */
.panel { flex: 1; overflow: auto; animation: fadeSlide .3s ease; }

/* Form card */
.form-card { background: #fff; border-radius: 14px; padding: 20px 24px; box-shadow: 0 1px 2px rgba(0,0,0,.04), 0 4px 16px rgba(0,0,0,.04); }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12px; font-weight: 600; color: #475569; margin-bottom: 4px; letter-spacing: .2px; }
.field input { width: 100%; padding: 8px 12px; border: 1.5px solid #e2e8f0; border-radius: 10px; font-size: 14px; color: #1e293b; background: #f8fafc; outline: none; transition: all .2s; box-sizing: border-box; }
.field input:focus { border-color: #818cf8; background: #fff; box-shadow: 0 0 0 3px rgba(99,102,241,.12); }
.field input::placeholder { color: #cbd5e1; }

.field-row { display: flex; gap: 16px; }
.field-row .field { flex: 1; }

.btn-save-wrap { display: flex; justify-content: flex-end; }
.btn-save { display: inline-flex; align-items: center; justify-content: center; padding: 9px 28px; border: none; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; background: linear-gradient(135deg, #6366f1, #4f46e5); color: #fff; transition: all .25s; min-width: 120px; }
.btn-save:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(99,102,241,.35); }
.btn-save:disabled { opacity: .6; cursor: not-allowed; }
.spinner { width: 18px; height: 18px; border: 2px solid rgba(255,255,255,.3); border-top-color: #fff; border-radius: 50%; animation: spin .6s linear infinite; }

/* Info strip */
.info-strip { display: flex; align-items: center; gap: 8px; margin-top: 12px; padding: 10px 16px; background: #eef2ff; border-radius: 12px; font-size: 13px; color: #4338ca; }
.info-dot { width: 8px; height: 8px; border-radius: 50%; background: #818cf8; flex-shrink: 0; }
.info-text { font-weight: 500; }
.info-sep { color: #a5b4fc; }

/* Holiday toolbar */
.holiday-toolbar { margin-bottom: 12px; }
.btn-add { display: inline-flex; align-items: center; gap: 5px; padding: 7px 16px; font-size: 13px; border: 1.5px dashed #cbd5e1; border-radius: 10px; font-size: 13px; font-weight: 500; cursor: pointer; color: #6366f1; background: #f8fafc; transition: all .2s; }
.btn-add:hover { border-color: #818cf8; background: #eef2ff; }
.btn-add svg { width: 16px; height: 16px; }

/* 调休补课行 */
.makeup-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.makeup-row :deep(.pure-picker) { flex: 1; }
.makeup-tip { font-size: 12px; color: #4338ca; white-space: nowrap; font-weight: 600; }

/* Holiday list */
.holiday-list { background: #fff; border-radius: 16px; overflow: hidden; box-shadow: 0 1px 2px rgba(0,0,0,.04), 0 4px 16px rgba(0,0,0,.04); }
.holiday-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #f1f5f9; transition: background .15s; }
.holiday-item:last-child { border-bottom: none; }
.holiday-item:hover { background: #f8fafc; }
.holiday-left { display: flex; flex-direction: column; gap: 3px; }
.holiday-name { font-size: 14px; font-weight: 600; color: #1e293b; }
.holiday-range { font-size: 12px; color: #94a3b8; font-variant-numeric: tabular-nums; }
.btn-del { width: 34px; height: 34px; display: flex; align-items: center; justify-content: center; border: none; border-radius: 8px; background: transparent; color: #cbd5e1; cursor: pointer; transition: all .2s; }
.btn-del:hover { background: #fef2f2; color: #ef4444; }
.btn-del svg { width: 16px; height: 16px; }

/* Empty */
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 32px 20px; color: #cbd5e1; }
.empty-state svg { width: 36px; height: 36px; }
.empty-state p { font-size: 13px; margin: 0; }

/* Dialog */
.overlay { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; background: rgba(15,23,42,.4); backdrop-filter: blur(4px); animation: fadeIn .2s ease; }
.dialog { background: #fff; border-radius: 20px; padding: 28px; width: 420px; max-width: 90vw; box-shadow: 0 20px 60px rgba(0,0,0,.15); animation: scaleIn .25s ease; }
.dialog-title { font-size: 17px; font-weight: 700; color: #1e1b4b; margin: 0 0 24px; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 24px; }
.btn-cancel { padding: 9px 20px; border: 1.5px solid #e2e8f0; border-radius: 10px; font-size: 13px; font-weight: 500; cursor: pointer; color: #64748b; background: #fff; transition: all .2s; }
.btn-cancel:hover { background: #f8fafc; border-color: #cbd5e1; }
.btn-confirm { padding: 9px 24px; border: none; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer; color: #fff; background: linear-gradient(135deg, #6366f1, #4f46e5); transition: all .25s; }
.btn-confirm:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(99,102,241,.35); }

/* Override Element Plus styles */
:deep(.pure-picker) { width: 100%; }
:deep(.pure-picker .el-input__wrapper) { padding: 9px 14px; border: 1.5px solid #e2e8f0; border-radius: 10px; background: #f8fafc; box-shadow: none; }
:deep(.pure-picker .el-input__wrapper:hover) { border-color: #cbd5e1; }
:deep(.pure-picker.is-focus .el-input__wrapper) { border-color: #818cf8; background: #fff; box-shadow: 0 0 0 3px rgba(99,102,241,.12); }
:deep(.pure-number) { width: 140px; }
:deep(.pure-number .el-input__wrapper) { padding: 9px 14px; border: 1.5px solid #e2e8f0; border-radius: 10px; background: #f8fafc; box-shadow: none; }
:deep(.pure-number .el-input__wrapper:hover) { border-color: #cbd5e1; }
:deep(.pure-number.is-focus .el-input__wrapper) { border-color: #818cf8; box-shadow: 0 0 0 3px rgba(99,102,241,.12); }

@keyframes fadeSlide { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
@keyframes fadeIn { from { opacity: 0; } }
@keyframes scaleIn { from { opacity: 0; transform: scale(.96); } }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
