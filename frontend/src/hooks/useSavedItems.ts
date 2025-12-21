const KEY = 'traveloptimizer:saved'

export type SavedItem = {
  id: string // composite id (searchId:optionId)
  searchId: string
  optionId: string
  title?: string
  price?: number
  currency?: string
  createdAt: string
}

export function getSavedItems(): SavedItem[] {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : []
  } catch (e) {
    return []
  }
}

export function saveItem(item: SavedItem) {
  const items = getSavedItems()
  const exists = items.find((i) => i.id === item.id)
  if (!exists) {
    items.unshift(item)
    localStorage.setItem(KEY, JSON.stringify(items))
  }
}

export function removeItem(id: string) {
  const items = getSavedItems().filter((i) => i.id !== id)
  localStorage.setItem(KEY, JSON.stringify(items))
}

export default function useSavedItems() {
  return {
    list: getSavedItems(),
    saveItem,
    removeItem
  }
}
