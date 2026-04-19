import { formatRank, SUIT_META } from '../utils'
import { t } from '../i18n'

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
