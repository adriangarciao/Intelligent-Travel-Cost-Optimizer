import { useEffect, useState } from 'react'

const OFFER_IDS_KEY = 'traveloptimizer.compare.offerIds'
const SNAPSHOT_KEY = 'traveloptimizer.compare.snapshots'

type Snapshot = {
  id: string
  tripOptionId?: string
  totalPrice?: number
  currency?: string
  valueScore?: number
  flight?: any
  // additional fields as needed
}

function readIds(): string[] {
  try {
    const raw = localStorage.getItem(OFFER_IDS_KEY)
    if (!raw) return []
    return JSON.parse(raw)
  } catch (e) {
    return []
  }
}

function writeIds(ids: string[]) {
  localStorage.setItem(OFFER_IDS_KEY, JSON.stringify(ids))
}

function readSnapshots(): Record<string, Snapshot> {
  try {
    const raw = localStorage.getItem(SNAPSHOT_KEY)
    if (!raw) return {}
    return JSON.parse(raw)
  } catch (e) {
    return {}
  }
}

function writeSnapshots(s: Record<string, Snapshot>) {
  localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(s))
}

function broadcastChange() {
  try {
    // notify other hook instances in the same window
    window.dispatchEvent(new CustomEvent('traveloptimizer.compare:changed'))
  } catch (e) {
    // ignore
  }
}

export default function useCompare() {
  const [ids, setIds] = useState<string[]>(() => readIds())
  const [snapshots, setSnapshots] = useState<Record<string, Snapshot>>(() => readSnapshots())
  // refs to hold latest values for event handler comparisons
  const idsRef = { current: ids } as { current: string[] }
  const snapshotsRef = { current: snapshots } as { current: Record<string, Snapshot> }

  // keep refs up-to-date when state changes (no broadcasting here)
  useEffect(() => { idsRef.current = ids }, [ids])
  useEffect(() => { snapshotsRef.current = snapshots }, [snapshots])

  // listen for changes from other hook instances & storage events (cross-tab)
  useEffect(() => {
    function handle() {
      try {
        const newIds = readIds()
        const newSnapshots = readSnapshots()
        // only update state if values actually differ to avoid update loops
        const idsChanged = JSON.stringify(newIds) !== JSON.stringify(idsRef.current || [])
        const snapsChanged = JSON.stringify(newSnapshots) !== JSON.stringify(snapshotsRef.current || {})
        if (idsChanged) setIds(newIds)
        if (snapsChanged) setSnapshots(newSnapshots)
      } catch (e) {
        // ignore
      }
    }
    window.addEventListener('traveloptimizer.compare:changed', handle)
    window.addEventListener('storage', handle)
    return () => {
      window.removeEventListener('traveloptimizer.compare:changed', handle)
      window.removeEventListener('storage', handle)
    }
  }, [])

  function has(id: string) {
    return ids.includes(id)
  }

  function remove(id: string) {
    // operate against localStorage so instances stay in sync
    const curIds = readIds().filter(x => x !== id)
    const curSnapshots = readSnapshots()
    delete curSnapshots[id]
    writeIds(curIds)
    writeSnapshots(curSnapshots)
    setIds(curIds)
    setSnapshots(curSnapshots)
    broadcastChange()
  }

  function clear() {
    writeIds([])
    writeSnapshots({})
    setIds([])
    setSnapshots({})
    broadcastChange()
  }

  /**
   * Toggle an id into compare. If snapshot provided, store it.
   * Returns { ok, message }
   */
  function toggle(id: string, snapshot?: Snapshot): { ok: boolean; message?: string } {
    // operate against localStorage atomically to enforce max-3 across hook instances
    const curIds = readIds()
    const curSnapshots = readSnapshots()
    const present = curIds.includes(id)
    if (present) {
      const nextIds = curIds.filter(x => x !== id)
      delete curSnapshots[id]
      writeIds(nextIds)
      writeSnapshots(curSnapshots)
      setIds(nextIds)
      setSnapshots(curSnapshots)
      broadcastChange()
      return { ok: true }
    }
    if (curIds.length >= 3) return { ok: false, message: 'You can compare up to 3 flights.' }
    const nextIds = [...curIds, id]
    if (snapshot) curSnapshots[id] = snapshot
    writeIds(nextIds)
    writeSnapshots(curSnapshots)
    setIds(nextIds)
    setSnapshots(curSnapshots)
    broadcastChange()
    return { ok: true }
  }

  function listSnapshots(): Snapshot[] {
    return ids.map(id => snapshots[id]).filter(Boolean)
  }

  return { ids, snapshots, has, toggle, remove, clear, listSnapshots }
}
