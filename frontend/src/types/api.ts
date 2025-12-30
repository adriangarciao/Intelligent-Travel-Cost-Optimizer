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
}
