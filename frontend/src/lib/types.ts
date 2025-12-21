export type FlightDTO = {
  airline?: string
  flightNumber?: string | null
  stops?: number
  duration?: string
  segments?: string[]
}

export type LodgingDTO = {
  hotelName?: string
  lodgingType?: string
  rating?: number
  pricePerNight?: number
  nights?: number
}

export type TripOptionDTO = {
  tripOptionId: string
  totalPrice: number
  currency?: string
  flight?: FlightDTO
  lodging?: LodgingDTO
  valueScore?: number
  mlRecommendation?: { note?: string } | null
}

export type TripSearchResponseDTO = {
  searchId: string
  origin?: string
  destination?: string
  currency?: string
  options: TripOptionDTO[]
  mlBestDateWindow?: any | null
}

export type TripOptionsPageDTO = {
  searchId: string
  page: number
  size: number
  totalOptions: number
  options: TripOptionDTO[]
}
