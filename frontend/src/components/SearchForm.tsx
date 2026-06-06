import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'

const Schema = z.object({
  origin: z.string().min(2, 'Required'),
  destination: z.string().min(2, 'Required'),
  earliestDepartureDate: z.string().min(1, 'Required'),
  latestDepartureDate: z.string().min(1, 'Required'),
  earliestReturnDate: z.string().optional(),
  latestReturnDate: z.string().optional(),
  // valueAsNumber gives NaN for empty inputs; treat NaN as absent
  maxBudget: z.preprocess(
    val => (typeof val === 'number' && isNaN(val) ? undefined : val),
    z.number().min(0, 'Must be ≥ 0').optional()
  ),
  numTravelers: z.number({ invalid_type_error: 'Required' }).min(1, 'At least 1')
})

type FormValues = z.infer<typeof Schema>

type Props = {
  onSearch: (payload: FormValues & { tripType: 'ONE_WAY' | 'ROUND_TRIP' }) => void
  isLoading?: boolean
}

const PRESETS = [
  {
    tripType: 'ROUND_TRIP' as const,
    origin: 'ORD',
    destination: 'MIA',
    departureOffset: [14, 16] as [number, number],
    returnOffset: [21, 23] as [number, number]
  },
  {
    tripType: 'ROUND_TRIP' as const,
    origin: 'JFK',
    destination: 'LAX',
    departureOffset: [7, 9] as [number, number],
    returnOffset: [12, 14] as [number, number]
  },
  {
    tripType: 'ONE_WAY' as const,
    origin: 'ORD',
    destination: 'DEN',
    departureOffset: [5, 8] as [number, number],
    returnOffset: null,
    maxBudget: 250,
    numTravelers: 1
  }
] satisfies Array<{
  tripType: 'ONE_WAY' | 'ROUND_TRIP'
  origin: string
  destination: string
  departureOffset: [number, number]
  returnOffset: [number, number] | null
  maxBudget?: number
  numTravelers?: number
}>

function offsetDate(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

export default function SearchForm({ onSearch, isLoading = false }: Props) {
  const [tripType, setTripType] = useState<'ONE_WAY' | 'ROUND_TRIP'>('ROUND_TRIP')
  const [demoIndex, setDemoIndex] = useState(0)

  const { register, handleSubmit, setValue, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(Schema),
    defaultValues: { numTravelers: 1 }
  })

  const onSubmit = (data: FormValues) => {
    const payload = {
      ...data,
      tripType,
      maxBudget: data.maxBudget ?? 100000,
      earliestReturnDate: tripType === 'ONE_WAY' ? undefined : data.earliestReturnDate,
      latestReturnDate: tripType === 'ONE_WAY' ? undefined : data.latestReturnDate
    }
    onSearch(payload)
  }

  function handleDemo() {
    const preset = PRESETS[demoIndex]
    setTripType(preset.tripType)
    setValue('origin', preset.origin)
    setValue('destination', preset.destination)
    setValue('earliestDepartureDate', offsetDate(preset.departureOffset[0]))
    setValue('latestDepartureDate', offsetDate(preset.departureOffset[1]))
    if (preset.tripType === 'ROUND_TRIP' && preset.returnOffset) {
      setValue('earliestReturnDate', offsetDate(preset.returnOffset[0]))
      setValue('latestReturnDate', offsetDate(preset.returnOffset[1]))
    } else {
      setValue('earliestReturnDate', '')
      setValue('latestReturnDate', '')
    }
    if (preset.maxBudget !== undefined) setValue('maxBudget', preset.maxBudget)
    if (preset.numTravelers !== undefined) setValue('numTravelers', preset.numTravelers)
    setDemoIndex((demoIndex + 1) % PRESETS.length)
  }

  const nextDemoNum = demoIndex + 1

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
      {/* Trip Type Toggle */}
      <div className="flex gap-1 p-1 bg-gray-100 rounded-lg w-fit">
        <button
          type="button"
          onClick={() => setTripType('ROUND_TRIP')}
          className={`px-4 py-2 text-sm font-medium rounded-md transition-all ${
            tripType === 'ROUND_TRIP'
              ? 'bg-white shadow-sm text-[var(--dusk-blue)]'
              : 'text-gray-600 hover:text-gray-800'
          }`}
        >
          Round Trip
        </button>
        <button
          type="button"
          onClick={() => setTripType('ONE_WAY')}
          className={`px-4 py-2 text-sm font-medium rounded-md transition-all ${
            tripType === 'ONE_WAY'
              ? 'bg-white shadow-sm text-[var(--dusk-blue)]'
              : 'text-gray-600 hover:text-gray-800'
          }`}
        >
          One Way
        </button>
      </div>

      {/* Origin & Destination */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">From</label>
          <input
            {...register('origin')}
            placeholder="e.g. JFK, LAX"
            className={`w-full ${errors.origin ? 'border-red-400' : ''}`}
          />
          {errors.origin && <span className="text-xs text-red-500 mt-1">{errors.origin.message}</span>}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">To</label>
          <input
            {...register('destination')}
            placeholder="e.g. CDG, LHR"
            className={`w-full ${errors.destination ? 'border-red-400' : ''}`}
          />
          {errors.destination && <span className="text-xs text-red-500 mt-1">{errors.destination.message}</span>}
        </div>
      </div>

      {/* Departure Dates */}
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">Departure Window</label>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <span className="block text-xs text-gray-500 mb-1">Earliest</span>
            <input
              type="date"
              {...register('earliestDepartureDate')}
              className={`w-full ${errors.earliestDepartureDate ? 'border-red-400' : ''}`}
            />
            {errors.earliestDepartureDate && <span className="text-xs text-red-500 mt-1">{errors.earliestDepartureDate.message}</span>}
          </div>
          <div>
            <span className="block text-xs text-gray-500 mb-1">Latest</span>
            <input
              type="date"
              {...register('latestDepartureDate')}
              className={`w-full ${errors.latestDepartureDate ? 'border-red-400' : ''}`}
            />
            {errors.latestDepartureDate && <span className="text-xs text-red-500 mt-1">{errors.latestDepartureDate.message}</span>}
          </div>
        </div>
      </div>

      {/* Return Dates - Only show for Round Trip */}
      {tripType === 'ROUND_TRIP' && (
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Return Window</label>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <span className="block text-xs text-gray-500 mb-1">Earliest</span>
              <input
                type="date"
                {...register('earliestReturnDate')}
                className="w-full"
              />
            </div>
            <div>
              <span className="block text-xs text-gray-500 mb-1">Latest</span>
              <input
                type="date"
                {...register('latestReturnDate')}
                className="w-full"
              />
            </div>
          </div>
        </div>
      )}

      {/* Budget & Travelers */}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Max Budget</label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">$</span>
            <input
              type="number"
              {...register('maxBudget', { valueAsNumber: true })}
              placeholder="Optional"
              className={`w-full pl-7 ${errors.maxBudget ? 'border-red-400' : ''}`}
            />
            {errors.maxBudget && <span className="text-xs text-red-500 mt-1">{errors.maxBudget.message}</span>}
          </div>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Travelers</label>
          <input
            type="number"
            min={1}
            {...register('numTravelers', { valueAsNumber: true })}
            className="w-full"
          />
        </div>
      </div>

      {/* Submit & Demo */}
      <button
        type="submit"
        disabled={isLoading}
        className="w-full btn py-3 text-base font-semibold disabled:opacity-60"
        style={{ background: 'var(--dusk-blue)' }}
      >
        {isLoading ? 'Searching…' : 'Search Flights'}
      </button>
      <button
        type="button"
        onClick={handleDemo}
        className="w-full border border-gray-200 text-gray-500 py-2 rounded-lg text-sm hover:border-[var(--dusk-blue)] hover:text-[var(--dusk-blue)] transition-colors"
      >
        ✦ Try Demo ({nextDemoNum}/3)
      </button>
    </form>
  )
}
