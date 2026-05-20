import { useState } from 'react'
import type { Table } from '../types'

type Props = {
  table: Table
  prefilledCode?: string | null
  onConfirm: (seatIndex: number, inviteCode: string) => void
  onCancel: () => void
}

type SeatButtonProps = {
  index: number
  taken: boolean
  selected: boolean
  x: number
  y: number
  onClick: () => void
}

function SeatButton({ index, taken, selected, x, y, onClick }: SeatButtonProps) {
  return (
    <button
      type="button"
      disabled={taken}
      onClick={onClick}
      style={{
        position: 'absolute',
        left: `${x}%`,
        top: `${y}%`,
        transform: 'translate(-50%, -50%)',
        width: '2.2rem',
        height: '2.2rem',
        borderRadius: '50%',
        background: selected ? 'var(--accent, #6c63ff)' : taken ? 'var(--surface-3, #3a3a4a)' : 'var(--surface-2, #2a2a3a)',
        border: selected ? '2px solid #fff' : taken ? '1px solid #555' : '1px solid #666',
        color: taken ? '#666' : '#fff',
        fontSize: '0.75rem',
        fontWeight: selected ? 700 : 400,
        cursor: taken ? 'not-allowed' : 'pointer',
        transition: 'background 0.15s, border-color 0.15s',
        zIndex: 1,
        padding: 0,
        lineHeight: 1,
      }}
      title={taken ? `席 ${index + 1}（使用中）` : `席 ${index + 1}`}
    >
      {index + 1}
    </button>
  )
}

function MiniTable({
  maxPlayers,
  takenSeats,
  selected,
  onSelect,
}: {
  maxPlayers: number
  takenSeats: Set<number>
  selected: number
  onSelect: (i: number) => void
}) {
  // 楕円テーブルを中心にシートを配置（上から時計回り）
  const cx = 50
  const cy = 50
  const rx = 38
  const ry = 30

  const seats = Array.from({ length: maxPlayers }, (_, i) => {
    const angle = ((i * 360) / maxPlayers - 90) * (Math.PI / 180)
    return {
      index: i,
      x: cx + rx * Math.cos(angle),
      y: cy + ry * Math.sin(angle),
    }
  })

  return (
    <div
      style={{
        position: 'relative',
        width: '100%',
        paddingBottom: '65%',
        userSelect: 'none',
      }}
    >
      {/* テーブル本体 */}
      <div
        style={{
          position: 'absolute',
          left: '15%',
          right: '15%',
          top: '18%',
          bottom: '18%',
          background: 'var(--table-felt, #1a4a2a)',
          borderRadius: '50%',
          border: '2px solid var(--table-border, #2a6a3a)',
          boxShadow: 'inset 0 2px 8px rgba(0,0,0,0.4)',
        }}
      />
      {seats.map(({ index, x, y }) => (
        <SeatButton
          key={index}
          index={index}
          taken={takenSeats.has(index)}
          selected={selected === index}
          x={x}
          y={y}
          onClick={() => onSelect(index)}
        />
      ))}
    </div>
  )
}

export function OfflineSeatPopup({ table, prefilledCode, onConfirm, onCancel }: Props) {
  const takenSeats = new Set(table.members.map((m) => m.seat_index))
  const availableSeats = Array.from({ length: table.max_players }, (_, i) => i).filter(
    (i) => !takenSeats.has(i)
  )

  const [inviteCode, setInviteCode] = useState(prefilledCode ?? '')
  const [seatIndex, setSeatIndex] = useState(availableSeats[0] ?? 0)

  const canConfirm = inviteCode.trim().length > 0 && availableSeats.length > 0

  return (
    <div className="modal-overlay">
      <div className="modal-panel">
        <h3>
          <span className="game-type-dot offline" style={{ marginRight: '0.5rem' }} />
          オフライン着席
        </h3>
        <p className="muted" style={{ margin: '0.25rem 0 1rem' }}>{table.name}</p>

        <label style={{ marginBottom: '0.75rem', display: 'block' }}>
          招待コード
          <input
            type="text"
            placeholder="ホストから聞いてください"
            value={inviteCode}
            onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
            maxLength={8}
            style={{ letterSpacing: '0.2em', fontWeight: 700, textTransform: 'uppercase' }}
          />
        </label>

        <div className="label" style={{ marginBottom: '0.4rem' }}>シート選択</div>
        {availableSeats.length === 0 ? (
          <p className="error">空き席がありません</p>
        ) : (
          <MiniTable
            maxPlayers={table.max_players}
            takenSeats={takenSeats}
            selected={seatIndex}
            onSelect={setSeatIndex}
          />
        )}

        <div className="button-row" style={{ marginTop: '1.25rem' }}>
          <button className="ghost" onClick={onCancel}>キャンセル</button>
          <button
            className="primary"
            onClick={() => onConfirm(seatIndex, inviteCode.trim())}
            disabled={!canConfirm}
          >
            着席
          </button>
        </div>
      </div>
    </div>
  )
}
