const DB_NAME = 'invoice-transfer-db'
const DB_VERSION = 1
const STORE_STATE = 'state'
const STORE_BLOBS = 'blobs'

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(STORE_STATE)) {
        db.createObjectStore(STORE_STATE)
      }
      if (!db.objectStoreNames.contains(STORE_BLOBS)) {
        db.createObjectStore(STORE_BLOBS)
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

export async function saveBlob(blob) {
  const id = crypto.randomUUID()
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_BLOBS, 'readwrite')
    tx.objectStore(STORE_BLOBS).put(blob, id)
    tx.oncomplete = () => resolve(id)
    tx.onerror = () => reject(tx.error)
  })
}

export async function loadBlob(id) {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_BLOBS, 'readonly')
    const req = tx.objectStore(STORE_BLOBS).get(id)
    req.onsuccess = () => resolve(req.result ?? null)
    req.onerror = () => reject(req.error)
  })
}

export async function deleteBlob(id) {
  if (!id) return
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_BLOBS, 'readwrite')
    tx.objectStore(STORE_BLOBS).delete(id)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

const STATE_KEY = 'draft-v1'

export async function saveDraftState(payload) {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_STATE, 'readwrite')
    tx.objectStore(STORE_STATE).put(payload, STATE_KEY)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function loadDraftState() {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_STATE, 'readonly')
    const req = tx.objectStore(STORE_STATE).get(STATE_KEY)
    req.onsuccess = () => resolve(req.result ?? null)
    req.onerror = () => reject(req.error)
  })
}

export async function clearAllDraft() {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_STATE, STORE_BLOBS], 'readwrite')
    tx.objectStore(STORE_STATE).clear()
    tx.objectStore(STORE_BLOBS).clear()
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}
