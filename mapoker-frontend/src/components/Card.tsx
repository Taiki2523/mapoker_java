import { formatRank, SUIT_META } from '../utils'
import { t } from '../i18n'

type CardVariant = 'front' | 'back' | 'slot'

interface CardProps {
  card?: string
  variant: CardVariant
  size?: 'sm' | 'md'
}

export function Card({ card, variant, size = 'sm' }: CardProps) {
  const cls = `card card--${variant} card--${size}`

  if (variant === 'back') {
    return <span className={cls} aria-hidden="true" />
  }
  if (variant === 'slot') {
    return <span className={cls} aria-hidden="true" />
  }
  if (!card || card === '--') {
    return <span className={`${cls} card--empty`} />
  }

  const suit = card.slice(-1)
  const rank = formatRank(card.slice(0, -1))
  const meta = SUIT_META[suit]
  if (!meta) {
    return <span className={cls}>{card}</span>
  }
  return (
    <span className={cls} aria-label={`${rank} ${t(meta.label as Parameters<typeof t>[0])}`}>
      <span className="card-rank">{rank}</span>
      <span className={`card-suit ${meta.color}`} aria-hidden="true">
        {meta.symbol}
      </span>
    </span>
  )
}

export function CardFace({ card }: { card: string }) {
  if (!card || card === '--') {
    return <span className="card-face placeholder">??</span>
  }

  const suit = card.slice(-1)
  const rank = formatRank(card.slice(0, -1))
  const meta = SUIT_META[suit]
  if (!meta) {
    return <span className="card-face">{card}</span>
  }
  return (
    <span className="card-face" aria-label={`${rank} ${t(meta.label as Parameters<typeof t>[0])}`}>
      <span className="card-rank">{rank}</span>
      <span className={`card-suit ${meta.color}`} aria-hidden="true">
        {meta.symbol}
      </span>
    </span>
  )
}
