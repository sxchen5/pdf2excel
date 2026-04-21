<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { saveDraftState, loadDraftState, clearAllDraft, saveBlob, deleteBlob } from './persist.js'

const COLS = [
  { key: 'seqNo', label: '序号', width: '52px' },
  { key: 'invoiceType', label: '票据类型', width: '120px' },
  { key: 'transferDate', label: '移交日期', width: '100px' },
  { key: 'invoiceDate', label: '开票日期', width: '100px' },
  { key: 'invoiceNumber', label: '票据号码', width: '140px' },
  { key: 'issuer', label: '开票单位', width: '180px' },
  { key: 'invoiceItem', label: '开票项目', width: '160px' },
  { key: 'invoiceAmount', label: '开票金额', width: '88px' },
  { key: 'taxAmount', label: '税额', width: '72px' },
  { key: 'transferor', label: '移交人', width: '80px' },
  { key: 'receiver', label: '接收人', width: '80px' },
  { key: 'supervisor', label: '监督人', width: '80px' },
  { key: 'remarks', label: '备注', width: '140px' }
]

const sheetTitle = ref('锡彭市政建设（江苏）有限公司发票移交表')
const serialNo = ref('')
const sheetName = ref('已收发票')

const defaults = reactive({
  invoiceType: '电子普票',
  transferDate: '',
  transferor: '',
  receiver: '吴现敏',
  supervisor: '',
  remarks: ''
})

const rows = ref([])
const busy = ref(false)
const message = ref('')
const fileInput = ref(null)

function emptyRow(overrides = {}) {
  return {
    seqNo: rows.value.length + 1,
    invoiceType: defaults.invoiceType,
    transferDate: defaults.transferDate,
    invoiceDate: '',
    invoiceNumber: '',
    issuer: '',
    invoiceItem: '',
    invoiceAmount: '',
    taxAmount: '',
    transferor: defaults.transferor,
    receiver: defaults.receiver,
    supervisor: defaults.supervisor,
    remarks: defaults.remarks,
    cachedFileName: '',
    blobId: '',
    ...overrides,
    seqNo: overrides.seqNo != null && overrides.seqNo !== '' ? Number(overrides.seqNo) : rows.value.length + 1
  }
}

function renumber() {
  rows.value.forEach((r, i) => {
    r.seqNo = i + 1
  })
}

function addBlankRow() {
  rows.value.push(emptyRow())
}

function clearRow(index) {
  const r = rows.value[index]
  if (r?.blobId) {
    deleteBlob(r.blobId)
  }
  rows.value.splice(index, 1)
  renumber()
}

async function clearAllRows() {
  for (const r of rows.value) {
    if (r.blobId) await deleteBlob(r.blobId)
  }
  rows.value = []
  message.value = '已清空全部行与缓存文件'
}

async function onFilesSelected(e) {
  const files = Array.from(e.target.files || [])
  if (!files.length) return
  busy.value = true
  message.value = ''
  for (const file of files) {
    try {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch('/api/extract', { method: 'POST', body: fd })
      if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        throw new Error(err.error || res.statusText)
      }
      const data = await res.json()
      const blobId = await saveBlob(file)
      rows.value.push(
        emptyRow({
          invoiceDate: data.invoiceDate || '',
          invoiceNumber: data.invoiceNumber || '',
          issuer: data.issuer || '',
          invoiceItem: data.invoiceItem || '',
          invoiceAmount: data.invoiceAmount || '',
          taxAmount: data.taxAmount || '',
          cachedFileName: file.name,
          blobId
        })
      )
      renumber()
    } catch (err) {
      message.value = `识别失败（${file.name}）：${err.message || err}`
    }
  }
  busy.value = false
  if (fileInput.value) fileInput.value.value = ''
}

async function exportExcel() {
  busy.value = true
  message.value = ''
  try {
    const body = {
      sheetTitle: sheetTitle.value,
      serialNo: serialNo.value,
      sheetName: sheetName.value,
      rows: rows.value.map((r) => ({
        seqNo: r.seqNo,
        invoiceType: r.invoiceType,
        transferDate: r.transferDate,
        invoiceDate: r.invoiceDate,
        invoiceNumber: r.invoiceNumber,
        issuer: r.issuer,
        invoiceItem: r.invoiceItem,
        invoiceAmount: r.invoiceAmount,
        taxAmount: r.taxAmount,
        transferor: r.transferor,
        receiver: r.receiver,
        supervisor: r.supervisor,
        remarks: r.remarks
      }))
    }
    const res = await fetch('/api/export', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    if (!res.ok) throw new Error(await res.text())
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '发票移交表.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    message.value = '已生成并下载 Excel'
  } catch (err) {
    message.value = `导出失败：${err.message || err}`
  }
  busy.value = false
}

const draftPayload = computed(() => ({
  sheetTitle: sheetTitle.value,
  serialNo: serialNo.value,
  sheetName: sheetName.value,
  defaults: { ...defaults },
  rows: rows.value.map((r) => ({
    seqNo: r.seqNo,
    invoiceType: r.invoiceType,
    transferDate: r.transferDate,
    invoiceDate: r.invoiceDate,
    invoiceNumber: r.invoiceNumber,
    issuer: r.issuer,
    invoiceItem: r.invoiceItem,
    invoiceAmount: r.invoiceAmount,
    taxAmount: r.taxAmount,
    transferor: r.transferor,
    receiver: r.receiver,
    supervisor: r.supervisor,
    remarks: r.remarks,
    cachedFileName: r.cachedFileName,
    blobId: r.blobId
  }))
}))

let saveTimer
function scheduleSave() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(async () => {
    try {
      await saveDraftState(draftPayload.value)
    } catch {
      /* ignore quota */
    }
  }, 400)
}

watch([sheetTitle, serialNo, sheetName, rows, defaults], scheduleSave, { deep: true })

onMounted(async () => {
  const saved = await loadDraftState()
  if (saved?.rows?.length) {
    sheetTitle.value = saved.sheetTitle ?? sheetTitle.value
    serialNo.value = saved.serialNo ?? ''
    sheetName.value = saved.sheetName ?? sheetName.value
    Object.assign(defaults, saved.defaults || {})
    rows.value = saved.rows.map((r, i) =>
      emptyRow({
        ...r,
        seqNo: i + 1
      })
    )
    message.value = '已恢复上次编辑内容（含已缓存文件引用）'
  } else {
    addBlankRow()
  }
})

async function hardReset() {
  await clearAllDraft()
  rows.value = []
  addBlankRow()
  message.value = '已清除本地草稿与缓存文件'
}
</script>

<template>
  <div class="page">
    <header class="hero">
      <h1>发票移交表生成</h1>
      <p class="sub">
        上传 PDF 或图片自动提取「开票日期、票据号码、开票单位、开票项目、开票金额、税额」；其余列可在表格中编辑。数据会暂存在本机浏览器（IndexedDB）。
      </p>
    </header>

    <section class="panel meta">
      <label>表标题 <input v-model="sheetTitle" type="text" class="grow" /></label>
      <label>编号（右上角） <input v-model="serialNo" type="text" class="narrow" /></label>
      <label>工作表名称 <input v-model="sheetName" type="text" class="mid" /></label>
    </section>

    <section class="panel defaults">
      <span class="section-title">新建行时的默认填写（不含识别字段）</span>
      <div class="defaults-grid">
        <label>票据类型 <input v-model="defaults.invoiceType" /></label>
        <label>移交日期 <input v-model="defaults.transferDate" placeholder="YYYY/M/D" /></label>
        <label>移交人 <input v-model="defaults.transferor" /></label>
        <label>接收人 <input v-model="defaults.receiver" /></label>
        <label>监督人 <input v-model="defaults.supervisor" /></label>
        <label>备注 <input v-model="defaults.remarks" class="grow" /></label>
      </div>
    </section>

    <section class="toolbar">
      <input
        ref="fileInput"
        type="file"
        accept=".pdf,.png,.jpg,.jpeg,.webp"
        multiple
        class="file"
        @change="onFilesSelected"
      />
      <button type="button" class="btn secondary" :disabled="busy" @click="addBlankRow">添加空行</button>
      <button type="button" class="btn primary" :disabled="busy || !rows.length" @click="exportExcel">
        导出 Excel
      </button>
      <button type="button" class="btn danger" :disabled="busy" @click="hardReset">一键清空（含本地缓存）</button>
      <span v-if="busy" class="hint">处理中…</span>
      <span v-if="message" class="msg">{{ message }}</span>
    </section>

    <div class="table-wrap">
      <table class="grid">
        <thead>
          <tr>
            <th v-for="c in COLS" :key="c.key" :style="{ minWidth: c.width }">{{ c.label }}</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, idx) in rows" :key="idx">
            <td v-for="c in COLS" :key="c.key">
              <input v-model="row[c.key]" type="text" />
            </td>
            <td class="actions">
              <button type="button" class="btn sm" @click="clearRow(idx)">清除本行</button>
              <span v-if="row.cachedFileName" class="fname" :title="row.cachedFileName">📎</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style>
:root {
  font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
  color: #1a1a1a;
  background: #f4f6f9;
}
body {
  margin: 0;
}
.page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 16px 48px;
}
.hero h1 {
  margin: 0 0 8px;
  font-size: 1.5rem;
}
.sub {
  margin: 0;
  color: #555;
  line-height: 1.5;
  max-width: 900px;
}
.panel {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  margin-top: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.meta label {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 200px;
}
.defaults-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
  margin-top: 10px;
}
.defaults-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.85rem;
  color: #444;
}
.section-title {
  font-weight: 600;
  font-size: 0.9rem;
}
input[type='text'] {
  border: 1px solid #cfd6e4;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 0.9rem;
}
input.grow {
  flex: 1;
}
input.narrow {
  width: 120px;
}
input.mid {
  width: 180px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}
.file {
  max-width: 220px;
}
.btn {
  border: none;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 0.9rem;
  cursor: pointer;
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn.primary {
  background: #2563eb;
  color: #fff;
}
.btn.secondary {
  background: #e2e8f0;
  color: #1e293b;
}
.btn.danger {
  background: #fee2e2;
  color: #b91c1c;
}
.btn.sm {
  padding: 4px 8px;
  font-size: 0.8rem;
}
.hint {
  color: #64748b;
  font-size: 0.85rem;
}
.msg {
  color: #0f766e;
  font-size: 0.85rem;
}
.table-wrap {
  margin-top: 12px;
  overflow: auto;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
table.grid {
  border-collapse: collapse;
  width: 100%;
  font-size: 0.85rem;
}
.grid th {
  background: #dbeafe;
  font-weight: 600;
  padding: 8px 6px;
  border: 1px solid #bfdbfe;
  text-align: left;
  position: sticky;
  top: 0;
  z-index: 1;
}
.grid td {
  border: 1px solid #e5e7eb;
  padding: 0;
  vertical-align: middle;
}
.grid td input {
  width: 100%;
  min-width: 0;
  border: none;
  border-radius: 0;
  padding: 6px;
  box-sizing: border-box;
}
.actions {
  width: 110px;
  text-align: center;
  white-space: nowrap;
}
.fname {
  margin-left: 4px;
  opacity: 0.7;
}
</style>
