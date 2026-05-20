import { useEffect, useRef, useState } from 'react'
import type { BetPreset, GameState, Player, RoomMember, Table } from '../../types'
import { TopBar } from './TopBar'
import { ActionPanel } from './ActionPanel'
import { t } from '../../i18n'

const STACK_MODE_KEY = 'mapoker_stack_mode'

type Props = {
  game: GameState
  mySeat: number | null
  mySeatIndex: number | null
  table: Table
  roster: RoomMember[]
  autoRefresh: boolean
  setAutoRefresh: (v: boolean) => void
  loading: boolean
  error: string
  inviteCopied: boolean
  leavePending: boolean
  toCall: number
  minRaise: number
  maxBet: number
  betPresets: BetPreset[]
  actionAmount: number
  setActionAmount: (n: number) => void
  canAct: boolean
  winnerNames: string
  displayName: (idx: number) => string
  onCopyInvite: () => void
  onOpenMyPage: () => void
  onLeaveRoom: () => void
  onSendAction: (type: string, amount: number) => void
  onOfflineWin: (win: boolean) => void
  doStraddle: boolean
  onToggleStraddle: (v: boolean) => void
}

function roleLabel(game: GameState, idx: number): string {
  if (idx === game.button_index) return 'BTN'
  if (idx === game.small_blind_idx) return 'SB'
  if (idx === game.big_blind_idx) return 'BB'
  return ''
}

/** 常に ¥ 表記（BB なし） */
function fmtChips(chips: number): string {
  return `¥${chips.toLocaleString()}`
}

export function OfflineGameScreen({
  game, mySeat, mySeatIndex, table, roster: _roster,
  autoRefresh, setAutoRefresh,
  loading, error, inviteCopied, leavePending,
  toCall, minRaise, maxBet, betPresets, actionAmount, setActionAmount,
  canAct, winnerNames, displayName,
  onCopyInvite, onOpenMyPage, onLeaveRoom, onSendAction, onOfflineWin,
  doStraddle, onToggleStraddle,
}: Props) {
  const [stackMode, setStackMode] = useState<'chips' | 'bb'>(() => {
    return (localStorage.getItem(STACK_MODE_KEY) as 'chips' | 'bb') ?? 'chips'
  })

  // 直前のfinished時スタックを記録し、次のfinished時に差分を出す
  const lastFinishedStackRef = useRef<number | null>(null)
  const [handDelta, setHandDelta] = useState<number | null>(null)

  useEffect(() => {
    if (game.status !== 'finished' || mySeat === null) return
    const stack = game.players[mySeat]?.stack ?? 0
    if (lastFinishedStackRef.current !== null) {
      setHandDelta(stack - lastFinishedStackRef.current)
    }
    lastFinishedStackRef.current = stack
  }, [game.status]) // eslint-disable-line react-hooks/exhaustive-deps

  const bb = game.big_blind
  /** 下部（自分向け）— stackMode に従う */
  const fmt = (chips: number) => {
    if (stackMode === 'bb' && bb > 0) return `${Math.round((chips / bb) * 10) / 10}BB`
    return fmtChips(chips)
  }

  const myPlayer: Player | null = mySeat !== null ? (game.players[mySeat] ?? null) : null
  const myBet    = myPlayer?.contributed ?? 0
  const myStack  = myPlayer?.stack ?? 0
  const myRole   = mySeat !== null ? roleLabel(game, mySeat) : ''

  const sweepedPot = game.pot_total - game.players.reduce((s, p) => s + p.contributed, 0)
  const displayPot = sweepedPot > 0 ? sweepedPot : game.pot_total

  const isShowdown = game.status === 'showdown'
  const isFinished = game.status === 'finished'
  const currentPlayer: Player | null = game.players[game.current_player] ?? null

  // 宣言済みかどうか（自分のseat）
  const declarations = game.showdown_declarations ?? {}
  const myDeclared: boolean | undefined = mySeat !== null ? declarations[String(mySeat)] : undefined
  // 非フォールドプレイヤーの宣言状況
  const activePlayers = game.players
    .map((p, i) => ({ player: p, idx: i }))
    .filter(({ player }) => !player.folded)

  return (
    <div className="offline-game-layout">
      <TopBar
        game={game}
        mySeatIndex={mySeatIndex}
        canAct={canAct}
        displayName={displayName}
        error={error}
        autoRefresh={autoRefresh}
        setAutoRefresh={setAutoRefresh}
        inviteCopied={inviteCopied}
        stackMode={stackMode}
        onToggleStackMode={() => setStackMode((m) => m === 'chips' ? 'bb' : 'chips')}
        onCopyInvite={onCopyInvite}
        onOpenMyPage={onOpenMyPage}
        onOpenActionLog={() => {}}
        inviteCode={table.invite_code ?? null}
      />

      {/* ================================================================
          上部ゾーン — 相手に見せる（¥固定 / BB禁止）
          自分のターン時: 背景色を変えてターン中を明示
          ================================================================ */}
      <div className={`offline-opponent-zone${canAct ? ' offline-opponent-zone--my-turn' : ''}`}>
        <div className="offline-opponent-content">
          {mySeat !== null ? (
            <>
              {/* BET — 最重要・最大表示 */}
              <div className="offline-opp-stat">
                <span className="offline-opp-label">BET</span>
                <span className="offline-opp-value offline-opp-value--bet">{fmtChips(myBet)}</span>
              </div>
              {/* BTN のみ表示（SB・BB は下部で確認できるため省略） */}
              {myRole === 'BTN' && (
                <>
                  <div className="offline-opp-divider-v" />
                  <div className="offline-opp-stat">
                    <span className="offline-opp-label">ロール</span>
                    <span className="offline-opp-value offline-opp-value--role">BTN</span>
                  </div>
                </>
              )}
            </>
          ) : (
            <span className="offline-opp-label">観戦中</span>
          )}
        </div>
      </div>

      {/* 区切り */}
      <div className="offline-divider" />

      {/* ================================================================
          下部ゾーン — 自分向け（stackMode 適用）
          ================================================================ */}
      <div className="offline-my-zone">

        {/* ---- 最重要情報: POT & スタック ---- */}
        <div className="offline-primary-row">
          <div className="offline-primary-block">
            <span className="offline-label-sm">POT</span>
            <span className="offline-primary-value">{fmt(displayPot)}</span>
          </div>
          <div className="offline-primary-sep" />
          {mySeat !== null && (
            <div className="offline-primary-block">
              <span className="offline-label-sm">スタック</span>
              <span className="offline-primary-value offline-primary-value--stack">{fmt(myStack)}</span>
            </div>
          )}
          {game.current_bet > 0 && (
            <>
              <div className="offline-primary-sep" />
              <div className="offline-primary-block">
                <span className="offline-label-sm">場のBET</span>
                <span className="offline-primary-value offline-primary-value--cbet">{fmt(game.current_bet)}</span>
              </div>
            </>
          )}
        </div>

        {/* ---- BTN / SB / BB ---- */}
        <div className="offline-pos-row">
          <span className="offline-pos-chip offline-pos-btn">BTN {displayName(game.button_index)}</span>
          <span className="offline-pos-chip offline-pos-sb">SB {displayName(game.small_blind_idx)}</span>
          <span className="offline-pos-chip offline-pos-bb">BB {displayName(game.big_blind_idx)}</span>
        </div>

        {/* ---- プレイヤー一覧 ---- */}
        <div className="offline-player-list">
          {game.players.map((player, idx) => {
            const isCurrent = idx === game.current_player && game.status === 'in_progress'
            const role = roleLabel(game, idx)
            const isMe = idx === mySeat
            return (
              <div
                key={idx}
                className={[
                  'offline-player-row',
                  isCurrent ? 'offline-player-row--active' : '',
                  player.folded ? 'offline-player-row--folded' : '',
                  isMe ? 'offline-player-row--me' : '',
                ].filter(Boolean).join(' ')}
              >
                <span className="offline-player-role">{role || `#${idx + 1}`}</span>
                <span className="offline-player-name">
                  {displayName(idx)}
                  {isMe && <span style={{ fontSize: '0.62rem', color: 'var(--text-muted)', marginLeft: '0.25rem' }}>（自分）</span>}
                </span>
                <span className="offline-player-bet">{fmt(player.contributed)}</span>
                <span className="offline-player-stack">{fmt(player.stack)}</span>
                <span className="offline-player-status">
                  {player.folded && <span className="chip">FOLD</span>}
                  {player.all_in && <span className="chip">ALL IN</span>}
                  {isCurrent && <span className="chip" style={{ background: 'var(--accent)', color: '#fff' }}>◀</span>}
                </span>
              </div>
            )
          })}
        </div>

        {/* ---- 自分の BET & ロール（補助情報） ---- */}
        {mySeat !== null && (
          <div className="offline-my-summary">
            <div className="offline-my-stat">
              <span className="offline-label-sm">自分のBET</span>
              <span className="offline-my-bet">{fmt(myBet)}</span>
            </div>
            {myRole && (
              <div className="offline-my-stat">
                <span className="offline-label-sm">ロール</span>
                <span className="offline-my-role">{myRole}</span>
              </div>
            )}
          </div>
        )}

        {/* ---- 勝者表示 & ペイアウト ---- */}
        {(isShowdown || isFinished) && winnerNames && (
          <div className="offline-winner-banner">🏆 {winnerNames}</div>
        )}
        {isFinished && mySeat !== null && handDelta !== null && (
          <div className="offline-payout-list">
            <div className="offline-payout-row offline-payout-row--self">
              <span className="offline-payout-label">今ハンドの増減</span>
              <span className={`offline-payout-delta ${handDelta > 0 ? 'offline-payout-delta--win' : handDelta < 0 ? 'offline-payout-delta--lose' : 'offline-payout-delta--even'}`}>
                {handDelta > 0 ? '+' : ''}{fmt(handDelta)}
              </span>
            </div>
          </div>
        )}
      </div>

      {/* ================================================================
          アクション / ショーダウン宣言
          ================================================================ */}
      {isShowdown ? (
        <div className="offline-showdown-zone">
          <p className="offline-showdown-label">ショーダウン — 全員が宣言するまで待機</p>

          {/* 宣言状況リスト */}
          <div className="offline-declare-list">
            {activePlayers.map(({ player: _p, idx }) => {
              const declared = declarations[String(idx)]
              return (
                <div key={idx} className="offline-declare-row">
                  <span className="offline-declare-name">{displayName(idx)}</span>
                  <span className={`offline-declare-badge ${declared === true ? 'win' : declared === false ? 'lose' : 'pending'}`}>
                    {declared === true ? 'WIN 🏆' : declared === false ? 'LOSE' : '未宣言...'}
                  </span>
                </div>
              )
            })}
          </div>

          {/* 自分の宣言ボタン（フォールド済みは不要、宣言済みはグレーアウト） */}
          {mySeat !== null && !myPlayer?.folded && (
            <div className="button-row" style={{ gap: '1rem', padding: '0 1rem' }}>
              <button
                className={myDeclared === true ? 'primary' : 'ghost'}
                style={{ fontSize: '1.1rem', padding: '0.75rem 0', flex: 1,
                  ...(myDeclared === true ? { background: '#388e3c', borderColor: '#388e3c' } : {}) }}
                onClick={() => onOfflineWin(true)}
                disabled={loading || myDeclared !== undefined}
              >
                WIN 🏆
              </button>
              <button
                className={myDeclared === false ? 'primary' : 'ghost'}
                style={{ fontSize: '1.1rem', padding: '0.75rem 0', flex: 1,
                  ...(myDeclared === false ? { background: '#c62828', borderColor: '#c62828' } : {}) }}
                onClick={() => onOfflineWin(false)}
                disabled={loading || myDeclared !== undefined}
              >
                LOSE
              </button>
            </div>
          )}

          {mySeat !== null && myPlayer?.folded && (
            <p className="offline-showdown-label" style={{ opacity: 0.5 }}>
              フォールド済み — 他のプレイヤーの宣言を待っています
            </p>
          )}
        </div>
      ) : !isFinished ? (
        <ActionPanel
          game={game}
          mySeatIndex={mySeat}
          canAct={canAct}
          loading={loading}
          toCall={toCall}
          minRaise={minRaise}
          maxBet={maxBet}
          betPresets={betPresets}
          actionAmount={actionAmount}
          setActionAmount={setActionAmount}
          myHandName={null}
          currentPlayer={currentPlayer}
          stackMode={stackMode}
          displayName={displayName}
          onSendAction={onSendAction}
          doStraddle={doStraddle}
          onToggleStraddle={onToggleStraddle}
        />
      ) : null}

      {/* 退席エリア */}
      <div className="leave-area">
        {leavePending && <span className="leave-pending-msg">{t('leavePending')}</span>}
        <button className="leave-btn" onClick={onLeaveRoom} disabled={loading}>
          {t('leave')}
        </button>
      </div>
    </div>
  )
}
