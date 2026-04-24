import { useState } from 'react'
import { t } from '../i18n'

type Props = {
  tableName: string
  minBuyIn: number
  maxBuyIn: number
  bigBlind: number
  onConfirm: (amount: number) => void
  onCancel: () => void
}

export function BuyInPopup({ tableName, minBuyIn, maxBuyIn, bigBlind, onConfirm, onCancel }: Props) {
  const [amount, setAmount] = useState(minBuyIn)

  const handleSlider = (e: React.ChangeEvent<HTMLInputElement>) => {
    setAmount(Number(e.target.value))
  }

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = Number(e.target.value)
    if (!Number.isNaN(v)) setAmount(v)
  }

  const clamped = Math.min(maxBuyIn, Math.max(minBuyIn, amount))
  const bbCount = bigBlind > 0 ? Math.round(clamped / bigBlind) : 0

  return (
    <div className="modal-overlay">
      <div className="modal-panel">
        <h3>{t('buyInTitle')}</h3>
        <p className="muted" style={{ margin: '0.25rem 0 1.25rem' }}>{tableName}</p>

        <div className="buyin-slider-row">
          <input
            type="range"
            min={minBuyIn}
            max={maxBuyIn}
            step={bigBlind}
            value={clamped}
            onChange={handleSlider}
          />
          <input
            type="number"
            min={minBuyIn}
            max={maxBuyIn}
            step={bigBlind}
            value={amount}
            onChange={handleInput}
            style={{ width: '6rem', textAlign: 'right' }}
          />
        </div>
        <div className="buyin-range-labels">
          <span>{t('buyInMin')}: {minBuyIn}</span>
          <span style={{ fontWeight: 700, color: 'var(--accent)' }}>{clamped} ({bbCount} BB)</span>
          <span>{t('buyInMax')}: {maxBuyIn}</span>
        </div>

        <div className="button-row" style={{ marginTop: '1.5rem' }}>
          <button className="ghost" onClick={onCancel}>{t('cancel')}</button>
          <button
            className="primary"
            onClick={() => onConfirm(clamped)}
            disabled={clamped < minBuyIn || clamped > maxBuyIn}
          >
            {t('confirmBuyIn')}
          </button>
        </div>
      </div>
    </div>
  )
}
