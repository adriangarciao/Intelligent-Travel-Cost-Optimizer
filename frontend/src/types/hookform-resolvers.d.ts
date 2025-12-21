declare module '@hookform/resolvers' {
  import type { Resolver } from 'react-hook-form'
  export function zodResolver(schema: any): Resolver<any>
  export {};
}

declare module '@hookform/resolvers/zod' {
  import type { Resolver } from 'react-hook-form'
  export function zodResolver(schema: any): Resolver<any>
  export default zodResolver
}
