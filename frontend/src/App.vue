<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { saveDraftState, loadDraftState, clearAllDraft, saveBlob, deleteBlob } from './persist.js'

const COL_WIDTHS_STORAGE_KEY = 'invoice-table-col-widths-v1'

function parsePx(w) {
  const m = String(w || '').match(/(\d+)/)
  return m ? Number(m[1]) : 96
}

/** 与常见移交表、数电票版式一致，可下拉选择 */
const INVOICE_TYPE_OPTIONS = [
  '电子普票',
  '电子专票',
  '电子发票（普通发票）',
  '电子发票（增值税专用发票）',
  '增值税电子普通发票',
  '增值税电子专用发票',
  '纸质增值税普通发票',
  '纸质增值税专用发票',
  '广西通用机打发票',
  '代开发票',
  '其他'
]

const COLS = [
  { key: 'seqNo', label: '序号', width: '52px' },
  { key: 'invoiceType', label: '票据类型', width: '140px' },
  { key: 'transferDate', label: '移交日期', width: '118px' },
  { key: 'invoiceDate', label: '开票日期', width: '118px' },
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
  invoiceType: '电子发票（普通发票）',
  transferDate: '',
  transferor: '',
  receiver: '',
  supervisor: '',
  remarks: ''
})

/** 表格与导出使用 YYYY/M/D；日期选择器使用 YYYY-MM-DD */
function dateToIso(slash) {
  const s = (slash && String(slash).trim()) || ''
  if (!s) return ''
  const m = s.match(/^(\d{4})[\/年\-](\d{1,2})[\/月\-](\d{1,2})/)
  if (!m) return ''
  const y = m[1]
  const mo = String(Number(m[2])).padStart(2, '0')
  const d = String(Number(m[3])).padStart(2, '0')
  return `${y}-${mo}-${d}`
}

function isoToSlash(iso) {
  const s = (iso && String(iso).trim()) || ''
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return ''
  const [y, mo, d] = s.split('-')
  return `${Number(y)}/${Number(mo)}/${Number(d)}`
}

const rows = ref([])
const busy = ref(false)
const message = ref('')
const fileInput = ref(null)
const dropActive = ref(false)
let dragDepth = 0

/** 列宽（px），表头可左右拖拽调整 */
const colWidths = ref({})
let resizeState = null

function defaultColWidths() {
  const o = { actions: 118 }
  for (const c of COLS) {
    o[c.key] = parsePx(c.width)
  }
  return o
}

function loadColWidths() {
  try {
    const raw = localStorage.getItem(COL_WIDTHS_STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed === 'object') {
        return { ...defaultColWidths(), ...parsed }
      }
    }
  } catch {
    /* ignore */
  }
  return defaultColWidths()
}

const tableWidthPx = computed(() => {
  let sum = colWidths.value.actions ?? 118
  for (const c of COLS) {
    sum += colWidths.value[c.key] ?? parsePx(c.width)
  }
  return Math.max(sum, 800)
})

function onResizeDown(key, e) {
  e.preventDefault()
  e.stopPropagation()
  resizeState = {
    key,
    originX: e.clientX,
    originW: colWidths.value[key] ?? parsePx(COLS.find((c) => c.key === key)?.width) ?? 96
  }
  window.addEventListener('mousemove', onResizeMove)
  window.addEventListener('mouseup', onResizeUp, { once: true })
}

function onResizeMove(e) {
  if (!resizeState) return
  const dx = e.clientX - resizeState.originX
  const min = resizeState.key === 'actions' ? 88 : 40
  const max = 640
  const w = Math.min(max, Math.max(min, resizeState.originW + dx))
  colWidths.value = { ...colWidths.value, [resizeState.key]: Math.round(w) }
}

function onResizeUp() {
  resizeState = null
  window.removeEventListener('mousemove', onResizeMove)
  try {
    localStorage.setItem(COL_WIDTHS_STORAGE_KEY, JSON.stringify(colWidths.value))
  } catch {
    /* ignore */
  }
}

function collectAcceptedFiles(fileList) {
  const out = []
  for (const f of Array.from(fileList || [])) {
    const name = f.name || ''
    if (/\.(pdf|png|jpg|jpeg|webp)$/i.test(name)) {
      out.push(f)
      continue
    }
    if (f.type === 'application/pdf' || f.type.startsWith('image/')) {
      out.push(f)
    }
  }
  return out
}

async function processFiles(files) {
  const accepted = collectAcceptedFiles(files)
  if (!accepted.length) {
    message.value = '未识别到支持的文件（PDF、PNG、JPG、JPEG、WEBP）'
    return
  }
  busy.value = true
  message.value = ''
  for (const file of accepted) {
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

function onFilesSelected(e) {
  processFiles(collectAcceptedFiles(e.target.files))
}

function onDragEnter() {
  dragDepth++
  dropActive.value = true
}

function onDragLeave() {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dropActive.value = false
}

function onDrop(e) {
  dragDepth = 0
  dropActive.value = false
  const fromFiles = e.dataTransfer?.files
  if (fromFiles?.length) {
    processFiles(collectAcceptedFiles(fromFiles))
    return
  }
  const items = e.dataTransfer?.items
  if (!items?.length) return
  const files = []
  for (const it of Array.from(items)) {
    if (it.kind === 'file') {
      const f = it.getAsFile()
      if (f) files.push(f)
    }
  }
  processFiles(collectAcceptedFiles(files))
}

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
  colWidths.value = loadColWidths()
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
        <label
          >票据类型
          <select v-model="defaults.invoiceType" class="cell-control">
            <option v-for="opt in INVOICE_TYPE_OPTIONS" :key="opt" :value="opt">{{ opt }}</option>
          </select>
        </label>
        <label
          >移交日期
          <input
            type="date"
            class="cell-control"
            :value="dateToIso(defaults.transferDate)"
            @input="defaults.transferDate = isoToSlash($event.target.value)"
          />
        </label>
        <label>移交人 <input v-model="defaults.transferor" /></label>
        <label>接收人 <input v-model="defaults.receiver" /></label>
        <label>监督人 <input v-model="defaults.supervisor" /></label>
        <label>备注 <input v-model="defaults.remarks" class="grow" /></label>
      </div>
    </section>

    <section class="upload-row panel">
      <div class="upload-column-title">文件上传</div>
      <div
        class="dropzone dropzone-full"
        :class="{ active: dropActive, disabled: busy }"
        @dragenter.prevent="onDragEnter"
        @dragleave.prevent="onDragLeave"
        @dragover.prevent
        @drop.prevent="onDrop"
        @click="!busy && fileInput?.click()"
      >
        <input
          ref="fileInput"
          type="file"
          accept=".pdf,.png,.jpg,.jpeg,.webp,application/pdf,image/*"
          multiple
          class="file-hidden"
          @change="onFilesSelected"
        />
        <p class="dropzone-title">点击或拖入文件到此处</p>
        <p class="dropzone-hint">支持 PDF、PNG、JPG、JPEG、WEBP，可多选或一次拖入多个文件</p>
      </div>
    </section>

    <section class="toolbar-actions-row">
      <div class="toolbar-actions">
        <button type="button" class="btn secondary" :disabled="busy" @click="addBlankRow">添加空行</button>
        <button type="button" class="btn primary" :disabled="busy || !rows.length" @click="exportExcel">
          导出 Excel
        </button>
        <button type="button" class="btn danger" :disabled="busy" @click="hardReset">一键清空（含本地缓存）</button>
        <span v-if="busy" class="hint">处理中…</span>
        <span v-if="message" class="msg">{{ message }}</span>
      </div>
      <p class="table-hint">表头右侧竖线可左右拖拽，调整列宽（设置会保存在本机浏览器）。</p>
    </section>

    <div class="table-wrap">
      <table class="grid" :style="{ width: tableWidthPx + 'px' }">
        <colgroup>
          <col v-for="c in COLS" :key="'col-' + c.key" :style="{ width: (colWidths[c.key] ?? parsePx(c.width)) + 'px' }" />
          <col :style="{ width: (colWidths.actions ?? 118) + 'px' }" />
        </colgroup>
        <thead>
          <tr>
            <th v-for="c in COLS" :key="c.key" class="th-resizable">
              <span class="th-label">{{ c.label }}</span>
              <span
                class="col-resize-handle"
                title="拖拽调整列宽"
                @mousedown="onResizeDown(c.key, $event)"
              />
            </th>
            <th class="actions th-resizable">
              <span class="th-label">操作</span>
              <span
                class="col-resize-handle"
                title="拖拽调整列宽"
                @mousedown="onResizeDown('actions', $event)"
              />
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, idx) in rows" :key="idx">
            <td v-for="c in COLS" :key="c.key">
              <select v-if="c.key === 'invoiceType'" v-model="row.invoiceType" class="cell-control">
                <option v-for="opt in INVOICE_TYPE_OPTIONS" :key="opt" :value="opt">{{ opt }}</option>
              </select>
              <input
                v-else-if="c.key === 'transferDate' || c.key === 'invoiceDate'"
                type="date"
                class="cell-control"
                :value="dateToIso(row[c.key])"
                @input="row[c.key] = isoToSlash($event.target.value)"
              />
              <input v-else v-model="row[c.key]" type="text" />
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
  max-width: 1600px;
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
input[type='text'],
input[type='date'],
select.cell-control {
  border: 1px solid #cfd6e4;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 0.9rem;
}
select.cell-control {
  width: 100%;
  max-width: 100%;
  background: #fff;
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
.upload-row {
  margin-top: 16px;
}
.upload-row.panel {
  padding-top: 14px;
}
.upload-column-title {
  font-weight: 600;
  font-size: 0.95rem;
  margin: 0 0 12px;
  color: #1e293b;
}
.toolbar-actions-row {
  margin-top: 12px;
}
.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.table-hint {
  margin: 8px 0 0;
  font-size: 0.78rem;
  color: #64748b;
}
.dropzone {
  width: 100%;
  border: 2px dashed #94a3b8;
  border-radius: 10px;
  padding: 22px 12px;
  text-align: center;
  cursor: pointer;
  background: #f8fafc;
  transition: border-color 0.15s, background 0.15s;
}
.dropzone-full {
  min-height: 88px;
}
.dropzone:hover:not(.disabled),
.dropzone.active:not(.disabled) {
  border-color: #2563eb;
  background: #eff6ff;
}
.dropzone.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.dropzone-title {
  margin: 0 0 6px;
  font-weight: 600;
  font-size: 0.95rem;
}
.dropzone-hint {
  margin: 0;
  font-size: 0.8rem;
  color: #64748b;
}
.file-hidden {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
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
  border: 1px solid #e2e8f0;
}
table.grid {
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
  font-size: 0.85rem;
}
.grid th {
  background: linear-gradient(180deg, #e8f2fe 0%, #dbeafe 100%);
  font-weight: 600;
  padding: 0;
  border: 1px solid #bfdbfe;
  text-align: left;
  position: sticky;
  top: 0;
  z-index: 2;
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.06);
  vertical-align: middle;
}
.th-resizable {
  position: relative;
}
.th-label {
  display: block;
  padding: 10px 10px 10px 8px;
  line-height: 1.25;
  user-select: none;
}
.col-resize-handle {
  position: absolute;
  top: 0;
  right: 0;
  width: 8px;
  height: 100%;
  cursor: col-resize;
  z-index: 3;
  border-right: 2px solid transparent;
}
.col-resize-handle:hover {
  border-right-color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
}
.grid td {
  border: 1px solid #e5e7eb;
  padding: 0;
  vertical-align: middle;
}
.grid td input[type='text'],
.grid td input[type='date'],
.grid td select.cell-control {
  width: 100%;
  min-width: 0;
  border: none;
  border-radius: 0;
  padding: 6px;
  box-sizing: border-box;
}
.grid td input[type='date'],
.grid td select.cell-control {
  border: none;
  font-size: 0.82rem;
}
.actions {
  text-align: center;
  white-space: nowrap;
}
.actions .th-label {
  text-align: center;
  padding-left: 4px;
  padding-right: 4px;
}
.fname {
  margin-left: 4px;
  opacity: 0.7;
}
</style>
