import { useEffect, useState, useRef } from 'react'

const STORAGE_IDS = 'traveloptimizer.compare.offerIds'
const STORAGE_SNAPS = 'traveloptimizer.compare.snapshots'
const BROADCAST_EVENT = 'traveloptimizer.compare:changed'

function readStorage() {
	try {
		const idsRaw = localStorage.getItem(STORAGE_IDS)
		const snapsRaw = localStorage.getItem(STORAGE_SNAPS)
		const ids = idsRaw ? JSON.parse(idsRaw) : []
		const snapshots = snapsRaw ? JSON.parse(snapsRaw) : {}
		return { ids, snapshots }
	} catch (err) {
		return { ids: [], snapshots: {} }
	}
}

function writeStorage(ids: string[], snapshots: Record<string, any>) {
	localStorage.setItem(STORAGE_IDS, JSON.stringify(ids))
	localStorage.setItem(STORAGE_SNAPS, JSON.stringify(snapshots))
}

function broadcast(ids: string[], snapshots: Record<string, any>) {
	try {
		const ev = new CustomEvent(BROADCAST_EVENT, { detail: { ids, snapshots } })
		window.dispatchEvent(ev)
	} catch (err) {
		// ignore
	}
}

export default function useCompare() {
	const initial = readStorage()
	const [ids, setIds] = useState<string[]>(initial.ids || [])
	const [snapshots, setSnapshots] = useState<Record<string, any>>(initial.snapshots || {})
	const jsonRef = useRef({ ids: JSON.stringify(initial.ids || []), snaps: JSON.stringify(initial.snapshots || {}) })

	useEffect(() => {
		function handleBroadcast(e: Event) {
			const detail = (e as CustomEvent)?.detail
			if (!detail) return
			const newIds = detail.ids || []
			const newSnaps = detail.snapshots || {}
			const s1 = JSON.stringify(newIds)
			const s2 = JSON.stringify(newSnaps)
			if (s1 === jsonRef.current.ids && s2 === jsonRef.current.snaps) return
			jsonRef.current.ids = s1
			jsonRef.current.snaps = s2
			setIds(newIds)
			setSnapshots(newSnaps)
		}

		function handleStorage(e: StorageEvent) {
			if (!e.key) return
			if (e.key !== STORAGE_IDS && e.key !== STORAGE_SNAPS) return
			const cur = readStorage()
			const s1 = JSON.stringify(cur.ids)
			const s2 = JSON.stringify(cur.snapshots)
			if (s1 === jsonRef.current.ids && s2 === jsonRef.current.snaps) return
			jsonRef.current.ids = s1
			jsonRef.current.snaps = s2
			setIds(cur.ids)
			setSnapshots(cur.snapshots)
		}

		window.addEventListener(BROADCAST_EVENT, handleBroadcast)
		window.addEventListener('storage', handleStorage)
		return () => {
			window.removeEventListener(BROADCAST_EVENT, handleBroadcast)
			window.removeEventListener('storage', handleStorage)
		}
	}, [])

	function syncAndBroadcast(nextIds: string[], nextSnaps: Record<string, any>) {
		jsonRef.current.ids = JSON.stringify(nextIds)
		jsonRef.current.snaps = JSON.stringify(nextSnaps)
		setIds(nextIds)
		setSnapshots(nextSnaps)
		writeStorage(nextIds, nextSnaps)
		broadcast(nextIds, nextSnaps)
	}

	function toggle(id: string, snap?: any) {
		const curIds = JSON.parse(jsonRef.current.ids) as string[]
		const curSnaps = JSON.parse(jsonRef.current.snaps) as Record<string, any>
		if (curIds.includes(id)) {
			const nextIds = curIds.filter(x => x !== id)
			const nextSnaps = { ...curSnaps }
			delete nextSnaps[id]
			syncAndBroadcast(nextIds, nextSnaps)
			return { ok: true }
		}
		if (curIds.length >= 3) return { ok: false, message: 'Maximum reached' }
		const nextIds = curIds.concat([id])
		const nextSnaps = { ...curSnaps }
		if (snap) nextSnaps[id] = snap
		syncAndBroadcast(nextIds, nextSnaps)
		return { ok: true }
	}

	function remove(id: string) {
		const curIds = JSON.parse(jsonRef.current.ids) as string[]
		const curSnaps = JSON.parse(jsonRef.current.snaps) as Record<string, any>
		const next = curIds.filter(x => x !== id)
		const nextSnaps = { ...curSnaps }
		delete nextSnaps[id]
		syncAndBroadcast(next, nextSnaps)
	}

	function clear() {
		syncAndBroadcast([], {})
	}

	function has(id: string) {
		return ids.includes(id)
	}

	return { ids, snapshots, toggle, remove, clear, has }
}
