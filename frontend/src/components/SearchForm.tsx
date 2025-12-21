import React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'

const Schema = z.object({
  origin: z.string().min(2),
  destination: z.string().min(2),
  earliestDepartureDate: z.string().optional(),
  latestDepartureDate: z.string().optional(),
  earliestReturnDate: z.string().optional(),
  latestReturnDate: z.string().optional(),
  maxBudget: z.number().optional(),
  numTravelers: z.number().min(1)
})

type FormValues = z.infer<typeof Schema>

type Props = {
  onSearch: (payload: FormValues) => void
}

export default function SearchForm({ onSearch }: Props) {
  const { register, handleSubmit } = useForm<FormValues>({
    resolver: zodResolver(Schema),
    defaultValues: { numTravelers: 1 }
  })

  return (
    <form onSubmit={handleSubmit(onSearch)} className="space-y-4 bg-white p-6 rounded shadow">
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700">Origin</label>
          <input {...register('origin')} className="mt-1 block w-full border rounded px-2 py-1" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">Destination</label>
          <input {...register('destination')} className="mt-1 block w-full border rounded px-2 py-1" />
        </div>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <div>
          <label className="block text-sm">Earliest Depart</label>
          <input type="date" {...register('earliestDepartureDate')} className="mt-1 block w-full border rounded px-2 py-1" />
        </div>
        <div>
          <label className="block text-sm">Latest Depart</label>
          <input type="date" {...register('latestDepartureDate')} className="mt-1 block w-full border rounded px-2 py-1" />
        </div>
        <div>
          <label className="block text-sm">Earliest Return</label>
          <input type="date" {...register('earliestReturnDate')} className="mt-1 block w-full border rounded px-2 py-1" />
        </div>
        <div>
          <label className="block text-sm">Latest Return</label>
          <input type="date" {...register('latestReturnDate')} className="mt-1 block w-full border rounded px-2 py-1" />
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div>
          <label className="block text-sm">Max Budget</label>
          <input type="number" {...register('maxBudget', { valueAsNumber: true })} className="mt-1 block w-full border rounded px-2 py-1" />
        </div>
        <div>
          <label className="block text-sm">Travelers</label>
          <input type="number" {...register('numTravelers', { valueAsNumber: true })} className="mt-1 block w-full border rounded px-2 py-1" />
        </div>
        <div className="flex items-end">
          <button type="submit" className="bg-blue-600 text-white px-4 py-2 rounded">Search</button>
        </div>
      </div>
    </form>
  )
}
