import { useNavigate } from 'react-router-dom'

type FeatureCardProps = {
  icon: string
  title: string
  children: React.ReactNode
  delay?: number
}

function FeatureCard({ icon, title, children, delay = 0 }: FeatureCardProps) {
  return (
    <div
      className="hiw-card p-5 rounded-xl bg-white border border-gray-100 shadow-sm"
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className="flex items-start gap-4">
        <div className="hiw-icon flex-shrink-0 w-12 h-12 rounded-xl flex items-center justify-center text-2xl">
          {icon}
        </div>
        <div className="flex-1 min-w-0">
          <h4 className="font-semibold text-gray-800 mb-2">{title}</h4>
          <div className="text-sm text-gray-600 leading-relaxed space-y-1.5">
            {children}
          </div>
        </div>
      </div>
    </div>
  )
}

type HowItWorksSectionProps = {
  onScrollToSearch?: () => void
}

export default function HowItWorksSection({ onScrollToSearch }: HowItWorksSectionProps) {
  const navigate = useNavigate()

  return (
    <section className="hiw-section py-10 px-4">
      <div className="max-w-4xl mx-auto">
        {/* Header */}
        <div className="text-center mb-8 hiw-fade-in">
          <h2 className="text-2xl md:text-3xl font-bold text-gray-800">
            How TravelOptimizer Works
          </h2>
          <p className="mt-3 text-gray-600 max-w-2xl mx-auto">
            Search flights, compare options, and use AI + scoring to make smarter decisions.
          </p>
        </div>

        {/* Feature Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-8">
          {/* Card 1: Search */}
          <FeatureCard icon="🔍" title="Search Flights" delay={50}>
            <p>Enter origin, destination, and date range (one-way or round-trip).</p>
            <p className="text-gray-500">We fetch real flight offers from Amadeus and normalize them into comparable options.</p>
          </FeatureCard>

          {/* Card 2: Value Score */}
          <FeatureCard icon="⭐" title="Understand Value Score" delay={100}>
            <p><span className="font-medium text-gray-700">Value Score (0–1)</span> ranks how good an offer is <em>relative to other offers in the same search</em>.</p>
            <p>Higher = better value. It blends signals like <span className="font-medium">price</span>, <span className="font-medium">stops</span>, and <span className="font-medium">duration</span>.</p>
            <p className="text-gray-500 text-xs mt-1">Open &quot;View Details&quot; on any result to see what influenced the score.</p>
            <p className="text-xs text-amber-600 mt-1">Note: It is not an absolute rating across all flights; only within your search.</p>
          </FeatureCard>

          {/* Card 3: Deal Meter + Buy/Wait */}
          <FeatureCard icon="📊" title="Deal Meter + Buy/Wait" delay={150}>
            <p><span className="font-medium text-gray-700">Deal Meter</span> shows where this offer sits from &quot;bad deal&quot; to &quot;good deal&quot; compared to your search.</p>
            <p><span className="font-medium text-gray-700">Buy/Wait</span> gives an AI-style recommendation with confidence level and explanation.</p>
            <p className="text-gray-500">Look for the colored recommendation badge and reasons in the details panel.</p>
          </FeatureCard>

          {/* Card 4: Save + Compare */}
          <FeatureCard icon="💾" title="Save + Compare" delay={200}>
            <p><span className="font-medium text-gray-700">Save</span> offers to revisit later—persists across sessions.</p>
            <p><span className="font-medium text-gray-700">Compare</span> up to 3 offers side-by-side to spot differences.</p>
            <p className="text-gray-500">Export or share links for your saved offers.</p>
          </FeatureCard>
        </div>

        {/* CTA Buttons */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 hiw-fade-in" style={{ animationDelay: '250ms' }}>
          <button
            onClick={onScrollToSearch}
            className="btn px-6 py-2.5 text-sm font-medium shadow-md hover:shadow-lg"
          >
            Run a Search
          </button>
          <button
            onClick={() => navigate('/saved')}
            className="px-6 py-2.5 text-sm font-medium rounded-full border-2 border-gray-300 text-gray-700 hover:border-gray-400 hover:bg-gray-50 transition-all duration-200"
          >
            View Saved Offers
          </button>
        </div>
      </div>
    </section>
  )
}
