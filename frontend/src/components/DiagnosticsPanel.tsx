import React, { useState, useEffect, useCallback } from 'react'

// Vite environment variable access
const getEnvVar = (key: string): string | undefined => {
  try {
    // @ts-ignore - Vite import.meta.env
    return import.meta.env?.[key]
  } catch {
    return undefined
  }
}

const isDevelopment = (): boolean => {
  try {
    // @ts-ignore - Vite import.meta.env
    return import.meta.env?.DEV || import.meta.env?.MODE === 'development'
  } catch {
    return process.env.NODE_ENV === 'development'
  }
}

interface DiagnosticsData {
  requestId?: string
  latencyMs?: number
  providerStatus?: string
  providerMessage?: string
  offersCount?: number
  timestamp?: string
  tripType?: string
}

interface DiagnosticsPanelProps {
  /** The data to display in the diagnostics panel */
  data?: DiagnosticsData
  /** Force show even in production (for testing) */
  forceShow?: boolean
  /** Optional class name */
  className?: string
}

/**
 * Dev-only diagnostics panel showing request metadata.
 * Only renders in development mode by default.
 * 
 * Shows: requestId, latency, provider status, offers count
 */
export default function DiagnosticsPanel({ 
  data, 
  forceShow = false,
  className = ''
}: DiagnosticsPanelProps) {
  const [collapsed, setCollapsed] = useState(true)
  const [metricsData, setMetricsData] = useState<any>(null)
  
  // Check if we're in development mode
  const isDev = forceShow || isDevelopment()
  
  // Fetch metrics from actuator endpoint (only in dev)
  const fetchMetrics = useCallback(async () => {
    if (!isDev) return
    try {
      const baseUrl = getEnvVar('VITE_API_BASE_URL') || 'http://localhost:8080'
      const res = await fetch(`${baseUrl}/actuator/metrics`, {
        headers: { 'Accept': 'application/json' }
      })
      if (res.ok) {
        const json = await res.json()
        setMetricsData(json)
      }
    } catch (e) {
      // Silently fail - metrics endpoint may not be exposed
      console.debug('Metrics fetch failed:', e)
    }
  }, [isDev])

  useEffect(() => {
    if (isDev && !collapsed) {
      fetchMetrics()
      // Refresh every 10 seconds when panel is open
      const interval = setInterval(fetchMetrics, 10000)
      return () => clearInterval(interval)
    }
  }, [isDev, collapsed, fetchMetrics])

  // Don't render in production
  if (!isDev) {
    return null
  }

  if (!data && !metricsData) {
    return null
  }

  return (
    <div className={`fixed bottom-4 right-4 z-50 ${className}`}>
      {/* Toggle button */}
      <button
        onClick={() => setCollapsed(c => !c)}
        className="absolute -top-3 -left-3 w-8 h-8 bg-gray-800 text-white rounded-full flex items-center justify-center text-sm font-mono shadow-lg hover:bg-gray-700 transition-colors"
        title={collapsed ? 'Show diagnostics' : 'Hide diagnostics'}
      >
        {collapsed ? '🔧' : '✕'}
      </button>
      
      {!collapsed && (
        <div className="bg-gray-900 text-gray-100 rounded-lg shadow-xl p-4 min-w-[300px] max-w-[400px] font-mono text-xs">
          <div className="flex items-center justify-between mb-3 pb-2 border-b border-gray-700">
            <h3 className="text-sm font-semibold text-yellow-400">🔧 Diagnostics</h3>
            <span className="text-gray-500">DEV ONLY</span>
          </div>

          {/* Request data */}
          {data && (
            <div className="space-y-2 mb-4">
              <h4 className="text-gray-400 text-xs uppercase tracking-wider">Request Info</h4>
              
              {data.requestId && (
                <div className="flex justify-between">
                  <span className="text-gray-400">Request ID:</span>
                  <span className="text-green-400 truncate ml-2 max-w-[180px]" title={data.requestId}>
                    {data.requestId}
                  </span>
                </div>
              )}
              
              {data.latencyMs !== undefined && (
                <div className="flex justify-between">
                  <span className="text-gray-400">Latency:</span>
                  <span className={`${data.latencyMs > 2000 ? 'text-red-400' : data.latencyMs > 1000 ? 'text-yellow-400' : 'text-green-400'}`}>
                    {data.latencyMs}ms
                  </span>
                </div>
              )}
              
              {data.providerStatus && (
                <div className="flex justify-between">
                  <span className="text-gray-400">Provider Status:</span>
                  <span className={`${data.providerStatus === 'OK' ? 'text-green-400' : 'text-red-400'}`}>
                    {data.providerStatus}
                  </span>
                </div>
              )}
              
              {data.providerMessage && (
                <div className="flex justify-between">
                  <span className="text-gray-400">Provider Msg:</span>
                  <span className="text-gray-300 truncate ml-2 max-w-[180px]" title={data.providerMessage}>
                    {data.providerMessage}
                  </span>
                </div>
              )}
              
              {data.offersCount !== undefined && (
                <div className="flex justify-between">
                  <span className="text-gray-400">Offers:</span>
                  <span className="text-blue-400">{data.offersCount}</span>
                </div>
              )}
              
              {data.tripType && (
                <div className="flex justify-between">
                  <span className="text-gray-400">Trip Type:</span>
                  <span className="text-purple-400">{data.tripType}</span>
                </div>
              )}
              
              {data.timestamp && (
                <div className="flex justify-between">
                  <span className="text-gray-400">Timestamp:</span>
                  <span className="text-gray-300">{data.timestamp}</span>
                </div>
              )}
            </div>
          )}

          {/* Metrics data */}
          {metricsData && (
            <div className="space-y-2 border-t border-gray-700 pt-3">
              <h4 className="text-gray-400 text-xs uppercase tracking-wider flex items-center justify-between">
                Actuator Metrics
                <button 
                  onClick={fetchMetrics}
                  className="text-blue-400 hover:text-blue-300"
                  title="Refresh metrics"
                >
                  ↻
                </button>
              </h4>
              
              {metricsData.names && (
                <div className="text-xs text-gray-500">
                  {metricsData.names.filter((n: string) => n.startsWith('travel.')).length} travel metrics available
                </div>
              )}
              
              <div className="text-xs text-gray-500 mt-1">
                View at: <a 
                  href={`${getEnvVar('VITE_API_BASE_URL') || 'http://localhost:8080'}/actuator/metrics`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-blue-400 hover:underline"
                >
                  /actuator/metrics
                </a>
              </div>
            </div>
          )}

          {/* Quick actions */}
          <div className="mt-4 pt-3 border-t border-gray-700 flex gap-2">
            <button
              onClick={() => {
                const logData = { ...data, metricsEndpoint: `${getEnvVar('VITE_API_BASE_URL') || 'http://localhost:8080'}/actuator/metrics` }
                console.log('Diagnostics Data:', logData)
                alert('Logged to console')
              }}
              className="flex-1 px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs"
            >
              Log to Console
            </button>
            <button
              onClick={() => {
                const text = JSON.stringify(data, null, 2)
                navigator.clipboard.writeText(text)
                alert('Copied to clipboard')
              }}
              className="flex-1 px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs"
            >
              Copy JSON
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * Hook to extract diagnostics data from a search response
 */
export function useDiagnosticsFromResponse(response: any): DiagnosticsData | undefined {
  if (!response) return undefined
  
  return {
    requestId: response.requestId || response.searchId,
    latencyMs: response.latencyMs,
    providerStatus: response.flightProviderStatus,
    providerMessage: response.flightProviderMessage,
    offersCount: response.options?.length ?? response.totalOptions,
    tripType: response.tripType,
    timestamp: new Date().toISOString()
  }
}
