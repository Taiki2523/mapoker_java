import { useEffect, useMemo, useRef, useState } from 'react'
import './App.css'
import { fetchJSON } from './api'
import { t } from './i18n'
import { bestHandName } from './handEval'
import { mapMembers } from './utils'
import { createStompClient, subscribeGame, subscribeHoleCards, subscribeMembers } from './ws'
import type {
  AuthUser, BetPreset, CreateGameConfig, GameState, JoinResponse, PayoutLine, Table,
  RoomMember, RoomMemberApi, Showdown, StoredSession, UserTableHistoryEntry,
  WalletLedgerEntry, WalletSummary,
} from './types'
import { AuthScreen } from './components/AuthScreen'
import { BuyInPopup } from './components/BuyInPopup'
import { GameTypeScreen } from './components/GameTypeScreen'
import { LobbyScreen } from './components/LobbyScreen'
import { MyPagePanel } from './components/MyPagePanel'
import { RoomScreen } from './components/RoomScreen'
import { GameScreen } from './components/GameScreen'

const NEXT_HAND_DELAY_MS = 7000
// TableArea.tsx の STREET_REVEAL_INTERVAL_MS と同じ値で管理すること
const CARD_REVEAL_MS_PER_STREET = 1500

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
  const startHandInFlight = useRef(false)
  const prevIsMyTurn = useRef(false)
  // コミュニティカード公開アニメーションの終了予定時刻（ネタバレ防止用）
  const cardRevealEndsAtRef = useRef(0)
  const prevCommLenRef = useRef(0)
  const stompClientRef = useRef<ReturnType<typeof createStompClient> | null>(null)
  // リバイポップアップを同じハンドで2度出さないためのフラグ
  const rebuyShownForHandRef = useRef(false)
  const [myName, setMyName] = useState('')
  const [mySeatIndex, setMySeatIndex] = useState<number | null>(null)
  const [leavePending, setLeavePending] = useState(false)
  const [roster, setRoster] = useState<RoomMember[]>([])
  const [showMyPage, setShowMyPage] = useState(false)
  const [table, setTable] = useState<Table | null>(null)
  const [profileTables, setProfileTables] = useState<Table[]>([])
  const [profileHistory, setProfileHistory] = useState<UserTableHistoryEntry[]>([])
  const [wallet, setWallet] = useState<WalletSummary | null>(null)
  const [walletLedger, setWalletLedger] = useState<WalletLedgerEntry[]>([])
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState('')
  const [roomScreenMode, setRoomScreenMode] = useState<'gameType' | 'room' | 'lobby'>('gameType')
  const [buyInContext, setBuyInContext] = useState<{
    tableId: string
    tableName: string
    minBuyIn: number
    maxBuyIn: number
    bigBlind: number
    onConfirm: (amount: number) => void
    onCancel: () => void
  } | null>(null)

  const formatErrorMessage = (err: unknown) => {
    const message = err instanceof Error ? err.message : String(err)
    if (message === 'insufficient funds') return t('insufficientFunds')
    return message
  }

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
    if (game.current_bet === 0) return game.big_blind
    return game.current_bet + Math.max(game.big_blind, game.last_raise_size || game.big_blind)
  }, [game])

  const maxBet = useMemo(() => {
    if (!game || mySeat === null) return 0
    return game.players[mySeat]?.stack ?? 0
  }, [game, mySeat])

  const betPresets = useMemo((): BetPreset[] => {
    if (!game) return []
    const cap = Math.max(maxBet, minRaise)
    const clamp = (v: number) => Math.min(cap, Math.max(minRaise, v))
    if (game.street === 'preflop') {
      return [
        { label: 'x1', amount: clamp(game.current_bet + (game.big_blind * 1)) },
        { label: 'x2', amount: clamp(game.current_bet + (game.big_blind * 2)) },
        { label: 'x3', amount: clamp(game.current_bet + (game.big_blind * 3)) },
        { label: 'x4', amount: clamp(game.current_bet + (game.big_blind * 4)) },
        { label: 'ALL', amount: maxBet },
      ]
        .filter((p) => p.amount > 0 && p.amount <= maxBet)
    }
    const pot = game.pot_total
    return [
      { label: t('presetMin'), amount: minRaise },
      { label: '30%',          amount: clamp(Math.round(pot * 0.3)) },
      { label: '50%',          amount: clamp(Math.round(pot * 0.5)) },
      { label: '100%',         amount: clamp(pot) },
      { label: '200%',         amount: clamp(pot * 2) },
      { label: t('presetAll'), amount: maxBet },
    ]
      .filter((p) => p.amount > 0 && p.amount <= maxBet)
  }, [game, minRaise, maxBet])

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

  const viewMode = useMemo(() => {
    if (!currentUser) return 'auth'
    if (!gameId || !game) return 'room'
    return 'game'
  }, [currentUser, game, gameId])

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
      void refreshMembers(initial)
      void refreshTable(initial)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!gameId) return
    const raw = window.localStorage.getItem(`mapoker.session.${gameId}`)
    if (!raw) return
    try {
      const data = JSON.parse(raw) as StoredSession
      setMyName(data.name)
      setMySeatIndex(data.seatIndex)
    } catch {
      // ignore
    }
  }, [gameId])

  useEffect(() => {
    if (!gameId || !myName.trim() || mySeatIndex !== null) return
    const member = roster.find((m) => m.name === myName.trim())
    if (member) {
      setMySeatIndex(member.seatIndex)
    }
  }, [gameId, myName, mySeatIndex, roster])

  useEffect(() => {
    if (!autoRefresh || !gameId) return
    const client = createStompClient()
    stompClientRef.current = client

    client.onConnect = () => {
      subscribeGame(client, gameId, (payload) => {
        const nextGame = payload.game as GameState
        setShowdown(nextGame.last_showdown ?? null)
        setGame((prev) => {
          if (!prev) return nextGame
          // broadcast は全プレイヤーのホールカードをマスク済みで送信する。
          // 既知のカード（subscribeHoleCards 受信済み or 初回 REST で取得済み）は上書きしない。
          const players = nextGame.players.map((p, i) => {
            const prevHole = prev.players[i]?.hole
            const hasKnownCards = Array.isArray(prevHole) &&
              prevHole.some((c) => c && c !== '??' && c !== '--')
            return hasKnownCards ? { ...p, hole: prevHole } : p
          })
          return { ...nextGame, players }
        })
      })
      subscribeMembers(client, gameId, (payload) => {
        setRoster(mapMembers(payload.members as RoomMemberApi[]))
      })
      subscribeHoleCards(client, (payload) => {
        if (payload.tableId !== gameId) return
        setGame((prev) => {
          if (!prev) return prev
          const players = prev.players.map((p, i) =>
            i === payload.seatIndex ? { ...p, hole: payload.hole } : p
          )
          return { ...prev, players }
        })
      })
      void refreshGame(gameId)
      void refreshMembers(gameId)
    }

    client.activate()
    return () => {
      void client.deactivate()
      stompClientRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoRefresh, gameId])

  useEffect(() => {
    if (!game) return
    if (game.status === 'showdown' && !showdown) {
      void runShowdown({ suppressError: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [game, showdown])

  useEffect(() => {
    if (!game || game.status !== 'finished' || !game.can_start_hand) return
    const timer = window.setTimeout(() => {
      void startHand(undefined, undefined, { suppressError: true })
    }, NEXT_HAND_DELAY_MS)
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [game?.status, game?.can_start_hand, gameId])

  useEffect(() => {
    if (isMyTurn && !prevIsMyTurn.current) {
      setActionAmount(minRaise)
    }
    prevIsMyTurn.current = isMyTurn
  }, [isMyTurn, minRaise])

  // コミュニティカード公開アニメーションの終了時刻を追跡（強制退席・勝者表示ネタバレ防止）
  useEffect(() => {
    const current = (game?.community ?? []).filter(c => c && c !== '--').length
    const prev = prevCommLenRef.current
    prevCommLenRef.current = current

    if (current <= prev) {
      if (current === 0) cardRevealEndsAtRef.current = 0
      return
    }

    let ms = 0
    if (prev < 3 && current >= 3) ms += CARD_REVEAL_MS_PER_STREET
    if (prev < 4 && current >= 4) ms += CARD_REVEAL_MS_PER_STREET
    if (prev < 5 && current >= 5) ms += CARD_REVEAL_MS_PER_STREET
    // +1600 = TableArea の sdStep(3) 発火タイミングと揃える
    if (ms > 0) cardRevealEndsAtRef.current = Date.now() + ms + 1600
  }, [game?.community])

  // 新ハンド開始でリバイフラグをリセット
  useEffect(() => {
    if (game?.status === 'in_progress') {
      rebuyShownForHandRef.current = false
    }
  }, [game?.status])

  // 手が終了してチップが 0 になったら自動でリバイ画面を表示
  // game.can_rebuy は viewer_index 依存のため、ローカルの players スタックを直接参照する
  const myCurrentStack = mySeat !== null ? (game?.players?.[mySeat]?.stack ?? null) : null
  useEffect(() => {
    if (game?.status !== 'finished') return
    if (myCurrentStack !== 0) return
    if (mySeat === null || !myName.trim()) return
    if (rebuyShownForHandRef.current) return

    rebuyShownForHandRef.current = true

    const minBuyIn = table?.min_buy_in ?? game.big_blind * 10
    const maxBuyIn = table?.max_buy_in ?? game.big_blind * 100
    const walletCap = wallet?.chip_balance ?? Infinity

    // runShowdown は即座に実行されるので game.status='finished' は ~300ms で来る。
    // カード公開アニメーション完了後にポップアップを表示するため cardRevealEndsAtRef で遅延する。
    const waitMs = Math.max(500, cardRevealEndsAtRef.current - Date.now())
    const timer = window.setTimeout(() => {
      setBuyInContext({
        tableId: gameId,
        tableName: table?.name ?? 'Table',
        minBuyIn,
        maxBuyIn: Math.min(maxBuyIn, walletCap),
        bigBlind: game.big_blind,
        onConfirm: (amount) => {
          setBuyInContext(null)
          void doTableJoin(gameId, myName, amount).then(() => refreshGame(gameId)).catch(err => setError(formatErrorMessage(err)))
        },
        onCancel: () => {
          setBuyInContext(null)
          void leaveRoom()
        },
      })
    }, waitMs)

    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [game?.status, myCurrentStack, mySeat, myName])

  // Bug3: 退席予約が解消されたらロビーに戻る
  useEffect(() => {
    if (!leavePending || !myName.trim()) return
    const stillInRoster = roster.some((m) => m.name === myName.trim())
    if (!stillInRoster) {
      navigateToLobby()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roster, leavePending, myName])

  // Bug4: ローカルのシート番号が他のプレイヤーと衝突していたらリセット
  useEffect(() => {
    if (mySeatIndex === null || !myName.trim() || !roster.length) return
    const memberAtSeat = roster.find((m) => m.seatIndex === mySeatIndex)
    if (memberAtSeat && memberAtSeat.name !== myName.trim()) {
      setMySeatIndex(null)
      if (gameId) window.localStorage.removeItem(`mapoker.session.${gameId}`)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roster])

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
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const refreshMembers = async (id = gameId) => {
    if (!id) return
    // カード公開アニメーション中はロスター更新をスキップ（強制退席のネタバレ防止）
    if (Date.now() < cardRevealEndsAtRef.current) return
    try {
      const data = await fetchJSON<{ members: RoomMemberApi[] }>(`/v1/tables/${id}/members`)
      setRoster(mapMembers(data.members))
    } catch {
      // ignore
    }
  }

  const refreshTable = async (id = gameId) => {
    if (!id) return null
    try {
      const data = await fetchJSON<Table>(`/v1/tables/${id}`)
      setTable(data)
      return data
    } catch {
      setTable(null)
      return null
    }
  }

  const refreshWallet = async () => {
    let nextWallet: WalletSummary | null = null
    try {
      nextWallet = await fetchJSON<WalletSummary>('/v1/wallet')
      setWallet(nextWallet)
    } catch {
      setWallet(null)
      setWalletLedger([])
      return
    }
    const ledger = await fetchJSON<WalletLedgerEntry[]>('/v1/wallet/ledger?limit=20')
    setWallet(nextWallet)
    setWalletLedger(ledger)
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
    setTable(null)
    setMySeatIndex(null)
    setProfileHistory([])
    setWallet(null)
    setWalletLedger([])
    setShowMyPage(false)
    setBuyInContext(null)
    setRoomScreenMode('gameType')
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
      const [tables, history] = await Promise.all([
        fetchJSON<Table[]>('/v1/tables'),
        fetchJSON<UserTableHistoryEntry[]>('/v1/auth/history'),
      ])
      setProfileTables(tables)
      setProfileHistory(history)
      await refreshWallet()
    } catch (err) {
      setProfileError(formatErrorMessage(err))
    } finally {
      setProfileLoading(false)
    }
  }

  const openMyPage = async () => {
    setShowMyPage(true)
    await refreshProfileTables()
  }

  const doTableJoin = async (tableId: string, name: string, buyIn: number) => {
    const result = await fetchJSON<JoinResponse>(`/v1/tables/${tableId}/join`, {
      method: 'POST',
      body: JSON.stringify({ name, buy_in: buyIn }),
    })
    const assignedSeatIndex = result.assigned_seat_index
    setMySeatIndex(assignedSeatIndex)
    setRoster(mapMembers(result.members))
    persistSession(tableId, { name, seatIndex: assignedSeatIndex })
    return assignedSeatIndex
  }

  const createGame = async (config: CreateGameConfig) => {
    setLoading(true)
    setError('')
    setShowdown(null)
    setRoomScreenMode('room')
    try {
      const payload = {
        table_name: config.tableName.trim() || 'Cash Orbit',
        player_count: config.playerCount,
        small_blind: config.smallBlind,
        big_blind: config.bigBlind,
        visibility: config.visibility,
        flags: config.flags,
      }
      const data = await fetchJSON<Table>('/v1/tables', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      if (!data.game) throw new Error('table game payload is missing')

      const name = myName.trim() || 'Host'

      const doJoinAndNavigate = async (buyIn: number) => {
        setLoading(true)
        try {
          setGameId(data.id)
          setGame(data.game ?? null)
          setTable(data)
          window.history.replaceState(null, '', `?tableId=${data.id}`)
          await doTableJoin(data.id, name, buyIn)
          await refreshGame(data.id)
        } catch (err) {
          setError(formatErrorMessage(err))
        } finally {
          setLoading(false)
        }
      }

      if (data.min_buy_in > 0) {
        setBuyInContext({
          tableId: data.id,
          tableName: data.name,
          minBuyIn: data.min_buy_in,
          maxBuyIn: wallet ? Math.min(data.max_buy_in, wallet.chip_balance) : data.max_buy_in,
          bigBlind: data.stake.big_blind,
          onConfirm: (amount) => { setBuyInContext(null); void doJoinAndNavigate(amount) },
          onCancel: () => { setBuyInContext(null) },
        })
      } else {
        await doJoinAndNavigate(0)
      }
    } catch (err) {
      setError(formatErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }



  const lobbyJoinWithBuyIn = async (tableId: string) => {
    setRoomScreenMode('room')
    const id = tableId
    setGameId(id)
    setTable(null)
    setShowdown(null)
    window.history.replaceState(null, '', `?tableId=${id}`)
    await refreshGame(id)
    await refreshMembers(id)
    const fetchedTable = await refreshTable(id)

    if (!myName.trim()) return

    const doJoin = async (buyIn: number) => {
      try {
        await doTableJoin(id, myName.trim(), buyIn)
      } catch (err) {
        setError(formatErrorMessage(err))
      }
    }

    if (fetchedTable && fetchedTable.min_buy_in > 0) {
      setBuyInContext({
        tableId: id,
        tableName: fetchedTable.name,
        minBuyIn: fetchedTable.min_buy_in,
        maxBuyIn: wallet ? Math.min(fetchedTable.max_buy_in, wallet.chip_balance) : fetchedTable.max_buy_in,
        bigBlind: fetchedTable.stake.big_blind,
        onConfirm: (amount) => { setBuyInContext(null); void doJoin(amount) },
        onCancel: () => { setBuyInContext(null) },
      })
    } else {
      await doJoin(0)
    }
  }

  const handleClaimDailyBonus = async () => {
    setProfileLoading(true)
    setProfileError('')
    try {
      await fetchJSON('/v1/wallet/daily-bonus', { method: 'POST' })
      await refreshWallet()
    } catch (err) {
      setProfileError(formatErrorMessage(err))
    } finally {
      setProfileLoading(false)
    }
  }

  const handleClaimRecovery = async () => {
    setProfileLoading(true)
    setProfileError('')
    try {
      await fetchJSON('/v1/wallet/recovery', { method: 'POST' })
      await refreshWallet()
    } catch (err) {
      setProfileError(formatErrorMessage(err))
    } finally {
      setProfileLoading(false)
    }
  }

  const navigateToLobby = () => {
    if (gameId) {
      window.localStorage.removeItem(`mapoker.session.${gameId}`)
      window.history.replaceState(null, '', window.location.pathname)
    }
    setGameId('')
    setGame(null)
    setMySeatIndex(null)
    setRoster([])
    setLeavePending(false)
    setShowdown(null)
    setRoomScreenMode('lobby')
  }

  const leaveRoom = async () => {
    if (!gameId) return
    try {
      const result = await fetchJSON<{ members: RoomMemberApi[] }>(`/v1/tables/${gameId}/leave`, {
        method: 'POST',
        body: JSON.stringify({ name: myName, seat_index: mySeatIndex }),
      })
      const updatedRoster = mapMembers(result.members)
      setRoster(updatedRoster)
      const stillInRoster = updatedRoster.some(
        (m) => m.name === myName.trim()
      )
      if (stillInRoster) {
        setLeavePending(true)
      } else {
        navigateToLobby()
      }
    } catch {
      navigateToLobby()
    }
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

  const startHand = async (
    id = gameId,
    bigBlindOverride?: number,
    options?: { suppressError?: boolean }
  ) => {
    if (!id || startHandInFlight.current) return
    startHandInFlight.current = true
    setLoading(true)
    setError('')
    setShowdown(null)
    setActionAmount(0)
    prevIsMyTurn.current = false
    try {
      const bb = bigBlindOverride ?? game?.big_blind ?? 10
      await fetchJSON(`/v1/games/${id}/start`, {
        method: 'POST',
        body: JSON.stringify({ big_blind: bb }),
      })
      await refreshGame(id)
    } catch (err) {
      if (!options?.suppressError) {
        setError((err as Error).message)
      }
    } finally {
      startHandInFlight.current = false
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
      if (data.status === 'showdown' && !showdown) {
        void runShowdown({ suppressError: true })
      } else {
        await refreshGame(gameId)
      }
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const runShowdown = async (options?: { suppressError?: boolean }) => {
    if (!gameId || showdownInFlight.current) return
    showdownInFlight.current = true
    setLoading(true)
    setError('')
    try {
      const data = await fetchJSON<Showdown>(`/v1/games/${gameId}/showdown`, { method: 'POST' })
      setShowdown(data)
      await refreshGame(gameId)
    } catch (err) {
      if (!options?.suppressError) {
        setError((err as Error).message)
      }
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
            {viewMode === 'room' && roomScreenMode === 'gameType' && (
              <GameTypeScreen
                currentUser={currentUser}
                onOpenMyPage={() => void openMyPage()}
                onSelectRing={() => setRoomScreenMode('lobby')}
              />
            )}
            {viewMode === 'room' && roomScreenMode === 'room' && (
              <RoomScreen
                loading={loading}
                error={error}
                onCreateGame={createGame}
                currentUser={currentUser}
                onOpenMyPage={() => void openMyPage()}
                onBack={() => setRoomScreenMode('lobby')}
              />
            )}
            {viewMode === 'room' && roomScreenMode === 'lobby' && (
              <LobbyScreen
                currentUser={currentUser}
                onOpenMyPage={() => void openMyPage()}
                onJoinRoom={lobbyJoinWithBuyIn}
                onCreateTable={() => setRoomScreenMode('room')}
                onBack={() => setRoomScreenMode('gameType')}
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
          mySeat={mySeat}
          mySeatIndex={mySeatIndex}
          isSpectator={isSpectator}
          roster={roster}
          autoRefresh={autoRefresh}
          setAutoRefresh={setAutoRefresh}
          actionAmount={actionAmount}
          setActionAmount={setActionAmount}
          loading={loading}
          error={error}
          inviteCopied={inviteCopied}
          leavePending={leavePending}
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
          onLeaveRoom={leaveRoom}
          onSendAction={(type, amount) => void sendAction(type, amount)}
        />
      )}
      {buyInContext && (
        <BuyInPopup
          tableName={buyInContext.tableName}
          minBuyIn={buyInContext.minBuyIn}
          maxBuyIn={buyInContext.maxBuyIn}
          bigBlind={buyInContext.bigBlind}
          onConfirm={buyInContext.onConfirm}
          onCancel={buyInContext.onCancel}
        />
      )}
      {showMyPage && currentUser && (
        <MyPagePanel
          currentUser={currentUser}
          tables={profileTables}
          history={profileHistory}
          wallet={wallet}
          walletLedger={walletLedger}
          currentTableId={gameId}
          loading={profileLoading}
          error={profileError}
          onClose={() => setShowMyPage(false)}
          onRefresh={() => void refreshProfileTables()}
          onLogout={() => void handleLogout()}
          onUpdateUser={(user) => { setCurrentUser(user); setMyName(user.username) }}
          onClaimDailyBonus={() => void handleClaimDailyBonus()}
          onClaimRecovery={() => void handleClaimRecovery()}
        />
      )}
    </div>
  )
}

export default App
