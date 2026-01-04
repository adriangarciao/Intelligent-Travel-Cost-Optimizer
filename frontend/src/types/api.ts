export type FlightSummary = {
  airline?: string
  airlineCode?: string
  airlineName?: string
  flightNumber?: string | null
  stops?: number
  duration?: number
  durationText?: string
  segments?: string[]
  origin?: string
  destination?: string
}

/** Severity levels for trip option flags */
export type FlagSeverity = 'BAD' | 'WARN' | 'GOOD' | 'INFO'

/** A single flag/chip explaining something about a trip option */
export type TripFlagDTO = {
  code: string
  severity: FlagSeverity
  title: string
  details?: string
  metrics?: Record<string, unknown>
}

export type TripOptionDTO = {
  id?: string
  tripOptionId?: string
  totalPrice?: number
  currency?: string
  valueScore?: number
  tripType?: 'ONE_WAY' | 'ROUND_TRIP'
  flight?: FlightSummary
  flights?: {
    outbound?: FlightSummary
    inbound?: FlightSummary
  }
  flightSummary?: FlightSummary
  lodging?: {
    hotelName?: string
  }
  valueScoreBreakdown?: Record<string, number>
  mlRecommendation?: {
    action?: 'BUY' | 'WAIT'
    trend?: 'likely_up' | 'likely_down' | 'stable'
    confidence?: number
    reasons?: string[]
    note?: string
  }
  buyWait?: {
    decision?: 'BUY' | 'WAIT'
    action?: 'BUY' | 'WAIT'
    trend?: string
    confidence?: number
    reasons?: string[]
  }
  /** Explainability flags computed by backend */
  flags?: TripFlagDTO[]
  mlNote?: string
  __raw?: TripOptionDTO
}
