import React, { useEffect, useState, useCallback } from 'react'
import { getSmartFilters, sendFeedback } from '../lib/api'
import type { SmartFiltersResponse, FilterSuggestion } from '../lib/api'

interface SuggestedFiltersProps {
  /** Callback when user applies a filter suggestion */
  onApplyFilter?: (key: string, value: any) => void
  /** Optional class name for styling */
  className?: string
  /** Refresh trigger - increment to refetch suggestions */
  refreshTrigger?: number
}

/**
 * Displays smart filter suggestions based on user's interaction history.
 * Shows personalized filters with confidence scores and explanations.
 */
export default function SuggestedFilters({ 
  onApplyFilter, 
  className = '',
  refreshTrigger = 0
}: SuggestedFiltersProps) {
  const [data, setData] = useState<SmartFiltersResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [dismissed, setDismissed] = useState<Set<string>>(new Set())
  const [error, setError] = useState<string | null>(null)

  const fetchSuggestions = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await getSmartFilters()
      setData(response)
    } catch (e) {
      setError('Failed to load suggestions')
      console.error('Failed to fetch smart filters:', e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchSuggestions()
  }, [fetchSuggestions, refreshTrigger])

  const handleApply = (suggestion: FilterSuggestion) => {
    // Send feedback that user applied this filter
    sendFeedback({
      eventType: 'APPLY_FILTER',
      filterKey: suggestion.key,
      filterValue: String(suggestion.value)
    })
    
    // Call the parent callback to actually apply the filter
    if (onApplyFilter) {
      onApplyFilter(suggestion.key, suggestion.value)
    }
  }

  const handleDismiss = (suggestion: FilterSuggestion) => {
    // Send feedback that user dismissed this filter
    sendFeedback({
      eventType: 'DISMISS_FILTER',
      filterKey: suggestion.key,
      filterValue: String(suggestion.value)
    })
    
    // Hide from UI
    setDismissed(prev => new Set([...prev, suggestion.key]))
  }

  // Filter out dismissed suggestions
  const visibleSuggestions = data?.suggestions?.filter(s => !dismissed.has(s.key)) ?? []

  // Don't render anything if no suggestions or loading
  if (loading) {
    return (
      <div className={`p-3 bg-gray-50 rounded-lg ${className}`}>
        <div className="text-sm text-gray-500 animate-pulse">Loading suggestions...</div>
      </div>
    )
  }

  if (error || !data || visibleSuggestions.length === 0) {
    return null // Don't show the panel if no suggestions
  }

  return (
    <div className={`p-4 bg-gradient-to-r from-blue-50 to-indigo-50 rounded-lg border border-blue-100 ${className}`}>
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-800 flex items-center gap-2">
          <span className="text-lg">✨</span>
          Suggested Filters
        </h3>
        <span className="text-xs text-gray-500">
          Based on {data.totalEventsAnalyzed} interactions
        </span>
      </div>
      
      <div className="space-y-2">
        {visibleSuggestions.map((suggestion) => (
          <SuggestionCard 
            key={suggestion.key}
            suggestion={suggestion}
            onApply={() => handleApply(suggestion)}
            onDismiss={() => handleDismiss(suggestion)}
          />
        ))}
      </div>
    </div>
  )
}

interface SuggestionCardProps {
  suggestion: FilterSuggestion
  onApply: () => void
  onDismiss: () => void
}

function SuggestionCard({ suggestion, onApply, onDismiss }: SuggestionCardProps) {
  const confidencePercent = Math.round(suggestion.confidence * 100)
  
  // Format the filter display based on key
  const getFilterDisplay = () => {
    switch (suggestion.key) {
      case 'nonStopOnly':
        return suggestion.value ? 'Nonstop flights only' : 'Allow layovers'
      case 'maxLayovers':
        return `Max ${suggestion.value} layover${suggestion.value === 1 ? '' : 's'}`
      case 'avoidAirlines':
        return `Avoid ${suggestion.value}`
      case 'preferAirlines':
        return `Prefer ${suggestion.value}`
      default:
        return `${suggestion.key}: ${suggestion.value}`
    }
  }

  // Get icon based on filter type
  const getIcon = () => {
    switch (suggestion.key) {
      case 'nonStopOnly':
        return '✈️'
      case 'maxLayovers':
        return '🔄'
      case 'avoidAirlines':
        return '🚫'
      case 'preferAirlines':
        return '⭐'
      default:
        return '🎯'
    }
  }

  return (
    <div className="flex items-center justify-between bg-white rounded-lg p-3 shadow-sm border border-gray-100 hover:border-blue-200 transition-colors">
      <div className="flex-1">
        <div className="flex items-center gap-2">
          <span className="text-base">{getIcon()}</span>
          <span className="font-medium text-gray-800 text-sm">{getFilterDisplay()}</span>
          <span 
            className={`text-xs px-1.5 py-0.5 rounded ${
              confidencePercent >= 70 
                ? 'bg-green-100 text-green-700' 
                : confidencePercent >= 50 
                  ? 'bg-yellow-100 text-yellow-700'
                  : 'bg-gray-100 text-gray-600'
            }`}
          >
            {confidencePercent}%
          </span>
        </div>
        <div className="text-xs text-gray-500 mt-1 ml-6">
          {suggestion.why}
        </div>
      </div>
      
      <div className="flex items-center gap-2 ml-3">
        <button
          onClick={onApply}
          className="px-3 py-1 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 transition-colors"
        >
          Apply
        </button>
        <button
          onClick={onDismiss}
          className="px-2 py-1 text-gray-400 hover:text-gray-600 text-sm"
          title="Dismiss suggestion"
        >
          ✕
        </button>
      </div>
    </div>
  )
}
