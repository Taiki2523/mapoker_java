import { useEffect, useMemo, useRef, useState } from 'react'
import './App.css'
import { fetchJSON } from './api'
import { t } from './i18n'
import { bestHandName } from './handEval'
import { mapMembers } from './utils'
import type {
  AuthUser, BetPreset, CreateGameConfig, GameState, HandHistoryEntry, PayoutLine, Table,
  RoomMember, RoomMemberApi, Showdown, StoredSession, UserTableHistoryEntry,
} from './types'
import { AuthScreen } from './components/AuthScreen'
import { LobbyScreen } from './components/LobbyScreen'
import { MyPagePanel } from './components/MyPagePanel'
import { RoomScreen } from './components/RoomScreen'
import { WaitingScreen } from './components/WaitingScreen'
import { GameScreen } from './components/GameScreen'

function App() {
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null)
  const [gameId, setGameId] = useState('')
  const [game, setGame] = useState<GameState | null>(null)
  const [showdown, setShowdown] = useState<Showdown | null>(null)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [actionAmount, setActionAmount] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [inviteCopied, setInviteCopied] = useState(false)
  const showdownInFlight = useRef(false)
  const prevIsMyTurn = useRef(false)
  const [myName, setMyName] = useState('')
  const [mySeatIndex, setMySeatIndex] = useState<number | null>(null)
  const [loginSeatIndex, setLoginSeatIndex] = useState(0)
  const [loginError, setLoginError] = useState('')
  const [isOwner, setIsOwner] = useState(false)
  const [roster, setRoster] = useState<RoomMember[]>([])
  const [showMyPage, setShowMyPage] = useState(false)
  const [profileTables, setProfileTables] = useState<Table[]>([])
  const [profileHistory, setProfileHistory] = useState<UserTableHistoryEntry[]>([])
  const [profileHandHistory, setProfileHandHistory] = useState<HandHistoryEntry[]>([])
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState('')
  const [roomScreenMode, setRoomScreenMode] = useState<'room' | 'lobby'>('room')
  const autoJoinRef = useRef<string | null>(null)

  const currentPlayer = useMemo(() => {
    if (!game) return null
    return game.players[game.current_player]
  }, [game])

  const inferredSeatIndex = useMemo(() => {
    if (!myName.trim()) return null
    const member = roster.find((m) => m.name === myName.trim())
    return member ? member.seatIndex : null
  }, [myName, roster])

  const mySeat = useMemo(() => mySeatIndex ?? inferredSeatIndex, [mySeatIndex, inferredSeatIndex])

  const displayName = useMemo(() => {
    return (seatIndex: number) => {
      const member = roster.find((m) => m.seatIndex === seatIndex)
      return member?.name || game?.players?.[seatIndex]?.id || t('seatN', { n: seatIndex + 1 })
    }
  }, [game?.players, roster])

  const winnerNames = useMemo(() => {
    if (!showdown) return ''
    return (showdown.winners ?? []).map((idx) => displayName(idx)).join(', ')
  }, [displayName, showdown])

  const payoutLines = useMemo((): PayoutLine[] => {
    if (!showdown || !game) return []
    return (showdown.payouts ?? [])
      .map((amount, idx) => ({ name: displayName(idx), amount }))
      .filter((l) => l.amount > 0)
  }, [displayName, game, showdown])

  const isShowdown = game?.status === 'showdown'
    || (game?.status === 'finished' && showdown !== null)

  const inviteUrl = useMemo(() => {
    if (!gameId) return ''
    return `${window.location.origin}?tableId=${gameId}`
  }, [gameId])

  const toCall = useMemo(() => {
    if (!game || !currentPlayer) return 0
    return Math.max(0, game.current_bet - currentPlayer.contributed)
  }, [game, currentPlayer])

  const minRaise = useMemo(() => {
    if (!game) return 0
    if (toCall === 0) return game.big_blind
    return game.current_bet + Math.max(game.big_blind, game.last_raise_size || game.big_blind)
  }, [game, toCall])

  const maxBet = useMemo(() => {
    if (!game || mySeat === null) return 0
    return game.players[mySeat]?.stack ?? 0
  }, [game, mySeat])

  const betPresets = useMemo((): BetPreset[] => {
    if (!game) return []
    const cap = Math.max(maxBet, minRaise)
    const clamp = (v: number) => Math.min(cap, Math.max(minRaise, v))
    if (toCall === 0) {
      const pot = game.pot_total
      return [
        { label: '30%', amount: clamp(Math.round(pot * 0.3)) },
        { label: '50%', amount: clamp(Math.round(pot * 0.5)) },
        { label: '100%', amount: clamp(pot) },
      ]
    }
    const cb = game.current_bet
    return [
      { label: '2x', amount: clamp(cb * 2) },
      { label: '3x', amount: clamp(cb * 3) },
      { label: '4x', amount: clamp(cb * 4) },
    ]
  }, [game, toCall, minRaise, maxBet])

  const myHandName = useMemo(() => {
    if (mySeat === null || !game) return null
    const hole = (game.players[mySeat]?.hole ?? []).filter((c) => c && c !== '--')
    if (hole.length < 2) return null
    const community = (game.community ?? []).filter((c) => c && c !== '--')
    return bestHandName(hole, community)
  }, [game, mySeat])

  const isMyTurn = useMemo(() => {
    if (!game || mySeat === null) return false
    return game.current_player === mySeat
  }, [game, mySeat])

  const isSpectator = useMemo(() => {
    if (!game || mySeat === null) return false
    return game.players[mySeat]?.stack === 0
  }, [game, mySeat])

  const canAct = useMemo(() => {
    return isMyTurn && game?.status === 'in_progress' && !isSpectator
  }, [game?.status, isMyTurn, isSpectator])

  const targetPlayerCount = useMemo(() => game?.players?.length ?? 0, [game])

  const canStartHand = game?.can_start_hand ?? true

  const lobbyReady = useMemo(() => {
    return roster.length >= targetPlayerCount && targetPlayerCount > 0
  }, [roster, targetPlayerCount])

  const viewMode = useMemo(() => {
    if (!currentUser) return 'auth'
    if (!gameId) return 'room'
    if (!lobbyReady) return 'waiting'
    return 'game'
  }, [currentUser, gameId, lobbyReady])

  useEffect(() => {
    fetchJSON<AuthUser>('/v1/auth/me')
      .then((user) => { setCurrentUser(user); setMyName(user.username) })
      .catch(() => {})
  }, [])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const initial = params.get('tableId') ?? params.get('gameId')
    if (initial) {
      setGameId(initial)
      void refreshGame(initial)
    }
  }, [])

  useEffect(() => {
    if (!gameId) return
    const raw = window.localStorage.getItem(`mapoker.session.${gameId}`)
    if (!raw) return
    try {
      const data = JSON.parse(raw) as { name: string; seatIndex: number; owner: boolean }
      setMyName(data.name)
      setMySeatIndex(data.seatIndex)
      setLoginSeatIndex(data.seatIndex)
      setIsOwner(data.owner)
    } catch {
      // ignore
    }
  }, [gameId])

  useEffect(() => {
    if (!gameId || !myName.trim() || mySeatIndex !== null) return
    const member = roster.find((m) => m.name === myName.trim())
    if (member) {
      setMySeatIndex(member.seatIndex)
      setLoginSeatIndex(member.seatIndex)
    }
  }, [gameId, myName, mySeatIndex, roster])

  useEffect(() => {
    if (!autoRefresh || !gameId) return
    const timer = window.setInterval(() => {
      void refreshGame(gameId)
      void refreshMembers(gameId)
    }, 2000)
    return () => window.clearInterval(timer)
  }, [autoRefresh, gameId])

  useEffect(() => {
    if (!game) return
    if (game.status === 'showdown' && !showdown && isOwner) {
      void runShowdown()
    }
  }, [game, isOwner, showdown])

  useEffect(() => {
    if (isMyTurn && !prevIsMyTurn.current) {
      setActionAmount(minRaise)
    }
    prevIsMyTurn.current = isMyTurn
  }, [isMyTurn, minRaise])

  useEffect(() => {
    if (viewMode !== 'waiting') return
    if (!gameId || !game) return
    if (mySeatIndex !== null) return
    if (!myName.trim()) return
    if (!roster.length) return
    if (autoJoinRef.current === gameId) return
    autoJoinRef.current = gameId
    const seat = firstAvailableSeat()
    setLoginSeatIndex(seat)
    void loginAsPlayer(seat)
  }, [game, gameId, myName, mySeatIndex, roster, viewMode])

  const refreshGame = async (id = gameId) => {
    if (!id) return
    try {
      let seat = mySeat
      if (seat === null) {
        const raw = window.localStorage.getItem(`mapoker.session.${id}`)
        if (raw) {
          try {
            const data = JSON.parse(raw) as { seatIndex?: number }
            if (typeof data.seatIndex === 'number') {
              seat = data.seatIndex
              setMySeatIndex(data.seatIndex)
            }
          } catch {
            // ignore
          }
        }
      }
      const params = new URLSearchParams()
      if (seat !== null) params.set('viewer_index', String(seat))
      if (isSpectator) params.set('spectator', '1')
      const query = params.toString()
      const path = query ? `/v1/games/${id}?${query}` : `/v1/games/${id}`
      const data = await fetchJSON<GameState>(path)
      setGame(data)
      if (data.last_showdown) {
        setShowdown(data.last_showdown)
      } else {
        setShowdown(null)
      }
      setLoginSeatIndex((prev) => (prev < data.players.length ? prev : 0))
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const refreshMembers = async (id = gameId) => {
    if (!id) return
    try {
      const data = await fetchJSON<{ members: RoomMemberApi[] }>(`/v1/tables/${id}/members`)
      setRoster(mapMembers(data.members))
    } catch {
      // ignore
    }
  }

  const firstAvailableSeat = () => {
    if (!game) return 0
    const taken = new Set(roster.map((m) => m.seatIndex))
    for (let i = 0; i < game.players.length; i += 1) {
      if (!taken.has(i)) return i
    }
    return 0
  }

  const handleAuthSuccess = (user: AuthUser) => {
    setCurrentUser(user)
    setMyName(user.username)
  }

  const handleLogout = async () => {
    try {
      await fetchJSON('/v1/auth/logout', { method: 'POST' })
    } catch {
      // ignore
    }
    setCurrentUser(null)
    setMyName('')
    setGameId('')
    setGame(null)
    setMySeatIndex(null)
    setIsOwner(false)
    setProfileHistory([])
    setProfileHandHistory([])
    setShowMyPage(false)
    setRoomScreenMode('room')
  }

  const persistSession = (tableId: string, session: Omit<StoredSession, 'updatedAt'>) => {
    window.localStorage.setItem(
      `mapoker.session.${tableId}`,
      JSON.stringify({
        ...session,
        updatedAt: new Date().toISOString(),
      })
    )
  }

  const refreshProfileTables = async () => {
    setProfileLoading(true)
    setProfileError('')
    try {
      const [tables, history, handHistory] = await Promise.all([
        fetchJSON<Table[]>('/v1/tables'),
        fetchJSON<UserTableHistoryEntry[]>('/v1/auth/history'),
        fetchJSON<HandHistoryEntry[]>('/v1/auth/hand-history'),
      ])
      setProfileTables(tables)
      setProfileHistory(history)
      setProfileHandHistory(handHistory)
    } catch (err) {
      setProfileError((err as Error).message)
    } finally {
      setProfileLoading(false)
    }
  }

  const openMyPage = async () => {
    setShowMyPage(true)
    await refreshProfileTables()
  }

  const createGame = async (config: CreateGameConfig) => {
    setLoading(true)
    setError('')
    setShowdown(null)
    setRoomScreenMode('room')
    try {
      const payload: Record<string, unknown> = {
        table_name: config.tableName.trim() || 'Cash Orbit',
        player_count: config.playerCount,
        stack_size: config.stackSize,
        button_index: config.buttonIndex,
        big_blind: config.bigBlind,
        odd_chip_rule: 'low_index',
        visibility: config.visibility,
        flags: config.flags,
      }
      if (config.seed.trim()) payload.seed = Number(config.seed)
      const data = await fetchJSON<Table>('/v1/tables', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      if (!data.game) throw new Error('table game payload is missing')
      setGameId(data.id)
      setGame(data.game)
      setMySeatIndex(0)
      setLoginSeatIndex(0)
      setIsOwner(true)
      await fetchJSON(`/v1/tables/${data.id}/join`, {
        method: 'POST',
        body: JSON.stringify({ name: myName.trim() || 'Host', seat_index: 0 }),
      })
      await refreshMembers(data.id)
      persistSession(data.id, { name: myName.trim() || 'Host', seatIndex: 0, owner: true })
      window.history.replaceState(null, '', `?tableId=${data.id}`)
      if (config.autoStart) await startHand(data.id, config.bigBlind)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const joinRoom = async (raw: string) => {
    if (!raw) return
    setLoginError('')
    setRoomScreenMode('room')
    let id = raw
    try {
      if (raw.includes('://')) {
        const url = new URL(raw)
        id = url.searchParams.get('tableId') ?? url.searchParams.get('gameId') ?? raw
      } else if (raw.includes('tableId=') || raw.includes('gameId=')) {
        const url = new URL(`http://local.test/?${raw}`)
        id = url.searchParams.get('tableId') ?? url.searchParams.get('gameId') ?? raw
      }
    } catch {
      id = raw
    }
    setGameId(id)
    setShowdown(null)
    window.history.replaceState(null, '', `?tableId=${id}`)
    await refreshGame(id)
    await refreshMembers(id)
  }

  const loginAsPlayer = async (seatOverride?: number) => {
    if (!game) { setLoginError(t('errLoadRoom')); return }
    if (!myName.trim()) { setLoginError(t('errEnterName')); return }
    const seatIndex = seatOverride ?? loginSeatIndex
    if (seatIndex < 0 || seatIndex >= game.players.length) { setLoginError(t('errSelectSeat')); return }
    if (!gameId) { setLoginError(t('errMissingRoom')); return }
    setMySeatIndex(seatIndex)
    setIsOwner(false)
    setLoginError('')
    try {
      const result = await fetchJSON<{ members: RoomMemberApi[] }>(`/v1/tables/${gameId}/join`, {
        method: 'POST',
        body: JSON.stringify({ name: myName.trim(), seat_index: seatIndex }),
      })
      setRoster(mapMembers(result.members))
    } catch (err) {
      setLoginError((err as Error).message)
    }
    persistSession(game.id, { name: myName.trim(), seatIndex, owner: false })
  }

  const leaveRoom = () => {
    if (!gameId) return
    if (mySeatIndex !== null) {
      void fetchJSON<{ members: RoomMemberApi[] }>(`/v1/tables/${gameId}/leave`, {
        method: 'POST',
        body: JSON.stringify({ seat_index: mySeatIndex }),
      }).then((result) => setRoster(mapMembers(result.members)))
    }
    window.localStorage.removeItem(`mapoker.session.${gameId}`)
    setMySeatIndex(null)
    setIsOwner(false)
  }

  const copyInvite = async () => {
    if (!inviteUrl) return
    try {
      await navigator.clipboard.writeText(inviteUrl)
      setInviteCopied(true)
      window.setTimeout(() => setInviteCopied(false), 1500)
    } catch {
      setInviteCopied(false)
    }
  }

  const startHand = async (id = gameId, bigBlindOverride?: number) => {
    if (!id) return
    setLoading(true)
    setError('')
    setShowdown(null)
    try {
      const bb = bigBlindOverride ?? game?.big_blind ?? 10
      await fetchJSON(`/v1/games/${id}/start`, {
        method: 'POST',
        body: JSON.stringify({ big_blind: bb }),
      })
      await refreshGame(id)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const sendAction = async (type: string, amount: number) => {
    const seat = mySeat
    if (!gameId || seat === null) { setError(t('errSelectSeatFirst')); return }
    setLoading(true)
    setError('')
    try {
      const data = await fetchJSON<GameState>(`/v1/games/${gameId}/actions`, {
        method: 'POST',
        body: JSON.stringify({ player_index: seat, action: { type, amount } }),
      })
      setGame(data)
      if (data.last_showdown) {
        setShowdown(data.last_showdown)
      } else {
        setShowdown(null)
      }
      if (data.status === 'showdown' && !showdown && isOwner) {
        void runShowdown()
      } else {
        await refreshGame(gameId)
      }
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const runShowdown = async () => {
    if (!gameId || showdownInFlight.current) return
    showdownInFlight.current = true
    setLoading(true)
    setError('')
    try {
      const data = await fetchJSON<Showdown>(`/v1/games/${gameId}/showdown`, { method: 'POST' })
      setShowdown(data)
      await refreshGame(gameId)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      showdownInFlight.current = false
      setLoading(false)
    }
  }

  return (
    <div className="app">
      {viewMode !== 'game' && (
        <div className="lobby-screen">
          <div className="lobby-panel">
            {viewMode === 'auth' && (
              <AuthScreen onAuthSuccess={handleAuthSuccess} />
            )}
            {viewMode === 'room' && (
              roomScreenMode === 'room' ? (
                <RoomScreen
                  loading={loading}
                  error={error}
                  onCreateGame={createGame}
                  onJoinRoom={joinRoom}
                  currentUser={currentUser}
                  onOpenMyPage={() => void openMyPage()}
                  onLogout={() => void handleLogout()}
                  onOpenLobby={() => setRoomScreenMode('lobby')}
                />
              ) : (
                <LobbyScreen
                  currentUser={currentUser}
                  onOpenMyPage={() => void openMyPage()}
                  onLogout={() => void handleLogout()}
                  onJoinRoom={joinRoom}
                  onBack={() => setRoomScreenMode('room')}
                />
              )
            )}
            {viewMode === 'waiting' && (
              <WaitingScreen
                gameId={gameId}
                inviteUrl={inviteUrl}
                inviteCopied={inviteCopied}
                onCopyInvite={() => void copyInvite()}
                game={game}
                roster={roster}
                mySeatIndex={mySeatIndex}
                loginSeatIndex={loginSeatIndex}
                setLoginSeatIndex={setLoginSeatIndex}
                onJoinLobby={(seat) => void loginAsPlayer(seat)}
                onStartHand={() => void startHand()}
                isOwner={isOwner}
                canStartHand={canStartHand}
                lobbyReady={lobbyReady}
                targetPlayerCount={targetPlayerCount}
                loading={loading}
                loginError={loginError}
                currentUser={currentUser}
                onOpenMyPage={() => void openMyPage()}
                onLogout={() => void handleLogout()}
              />
            )}
          </div>
        </div>
      )}
      {viewMode === 'game' && game && (
        <GameScreen
          game={game}
          showdown={showdown}
          isShowdown={isShowdown ?? false}
          currentUser={currentUser}
          myName={myName}
          mySeat={mySeat}
          mySeatIndex={mySeatIndex}
          loginSeatIndex={loginSeatIndex}
          setLoginSeatIndex={setLoginSeatIndex}
          isOwner={isOwner}
          isSpectator={isSpectator}
          autoRefresh={autoRefresh}
          setAutoRefresh={setAutoRefresh}
          actionAmount={actionAmount}
          setActionAmount={setActionAmount}
          loading={loading}
          error={error}
          inviteCopied={inviteCopied}
          loginError={loginError}
          toCall={toCall}
          minRaise={minRaise}
          maxBet={maxBet}
          betPresets={betPresets}
          myHandName={myHandName}
          currentPlayer={currentPlayer}
          canAct={canAct}
          winnerNames={winnerNames}
          payoutLines={payoutLines}
          displayName={displayName}
          onCopyInvite={() => void copyInvite()}
          onOpenMyPage={() => void openMyPage()}
          onLoginAsPlayer={() => void loginAsPlayer()}
          onLeaveRoom={leaveRoom}
          onLogout={() => void handleLogout()}
          onStartHand={() => void startHand()}
          onRunShowdown={() => void runShowdown()}
          onSendAction={(type, amount) => void sendAction(type, amount)}
        />
      )}
      {showMyPage && currentUser && (
        <MyPagePanel
          currentUser={currentUser}
          tables={profileTables}
          history={profileHistory}
          handHistory={profileHandHistory}
          currentTableId={gameId}
          loading={profileLoading}
          error={profileError}
          onClose={() => setShowMyPage(false)}
          onRefresh={() => void refreshProfileTables()}
          onOpenTable={(tableId) => {
            setShowMyPage(false)
            void joinRoom(tableId)
          }}
        />
      )}
    </div>
  )
}

export default App
