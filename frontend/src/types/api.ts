export type FlightSummary = {
  airline?: string
  airlineCode?: string
  airlineName?: string
  flightNumber?: string | null
  stops?: number
  duration?: number
  durationText?: string
  segments?: string[]
}

export type TripOptionDTO = {
  id?: string
  totalPrice?: number
  currency?: string
  valueScore?: number
  flightSummary?: FlightSummary
  valueScoreBreakdown?: Record<string, number>
  mlRecommendation?: {
    action?: 'BUY' | 'WAIT'
    trend?: 'likely_up' | 'likely_down' | 'stable'
    confidence?: number
    reasons?: string[]
  }
}
