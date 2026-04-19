type Card = { rank: number; suit: string }

const RANK_VAL: Record<string, number> = {
  '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7, '8': 8,
  '9': 9, 'T': 10, 'J': 11, 'Q': 12, 'K': 13, 'A': 14,
}

const RANK_LABEL: Record<number, string> = {
  14: 'A', 13: 'K', 12: 'Q', 11: 'J', 10: 'T',
  9: '9', 8: '8', 7: '7', 6: '6', 5: '5', 4: '4', 3: '3', 2: '2',
}

function parseCard(s: string): Card | null {
  if (!s || s === '--') return null
  const suit = s.slice(-1).toUpperCase()
  const rankStr = s.slice(0, -1).toUpperCase()
  const rank = RANK_VAL[rankStr]
  if (!rank || !['S', 'H', 'D', 'C'].includes(suit)) return null
  return { rank, suit }
}

function combinations<T>(arr: T[], k: number): T[][] {
  if (k === 0) return [[]]
  if (arr.length < k) return []
  const [head, ...tail] = arr
  return [
    ...combinations(tail, k - 1).map((c) => [head, ...c]),
    ...combinations(tail, k),
  ]
}

function score5(cards: Card[]): number[] {
  const sorted = [...cards].sort((a, b) => b.rank - a.rank)
  const ranks = sorted.map((c) => c.rank)
  const suits = sorted.map((c) => c.suit)
  const isFlush = suits.every((s) => s === suits[0])

  let isStraight = new Set(ranks).size === 5 && ranks[0] - ranks[4] === 4
  let sHigh = ranks[0]
  if (!isStraight && ranks[0] === 14 && ranks[1] === 5 && ranks[2] === 4 && ranks[3] === 3 && ranks[4] === 2) {
    isStraight = true
    sHigh = 5
  }

  const freq: Record<number, number> = {}
  for (const r of ranks) freq[r] = (freq[r] ?? 0) + 1
  const groups = Object.entries(freq)
    .map(([r, c]) => [Number(r), c] as [number, number])
    .sort((a, b) => b[1] - a[1] || b[0] - a[0])

  const [g0r, g0c] = groups[0]
  const [g1r, g1c] = groups[1] ?? [0, 0]
  const [g2r] = groups[2] ?? [0, 0]
  const [g3r] = groups[3] ?? [0, 0]

  if (isFlush && isStraight) return [8, sHigh]
  if (g0c === 4) return [7, g0r, g1r]
  if (g0c === 3 && g1c === 2) return [6, g0r, g1r]
  if (isFlush) return [5, ...ranks]
  if (isStraight) return [4, sHigh]
  if (g0c === 3) return [3, g0r, g1r, g2r]
  if (g0c === 2 && g1c === 2) return [2, g0r, g1r, g2r]
  if (g0c === 2) return [1, g0r, g1r, g2r, g3r]
  return [0, ...ranks]
}

function compareScore(a: number[], b: number[]): number {
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const d = (a[i] ?? 0) - (b[i] ?? 0)
    if (d !== 0) return d
  }
  return 0
}

const HAND_NAMES: Record<number, string> = {
  8: 'ストレートフラッシュ',
  7: 'フォーカード',
  6: 'フルハウス',
  5: 'フラッシュ',
  4: 'ストレート',
  3: 'スリーカード',
  2: 'ツーペア',
  1: 'ワンペア',
  0: 'ハイカード',
}

function preflopName(hole: Card[]): string {
  const [a, b] = [...hole].sort((x, y) => y.rank - x.rank)
  const ra = RANK_LABEL[a.rank] ?? '?'
  const rb = RANK_LABEL[b.rank] ?? '?'
  if (a.rank === b.rank) return `ポケット${ra}${rb}`
  return `${ra}${rb}${a.suit === b.suit ? 'スーテッド' : 'オフスート'}`
}

export function bestHandName(holeCards: string[], communityCards: string[]): string | null {
  const hole = holeCards.map(parseCard).filter((c): c is Card => c !== null)
  if (hole.length < 2) return null

  const community = communityCards.map(parseCard).filter((c): c is Card => c !== null)
  if (community.length === 0) return preflopName(hole)

  const all = [...hole, ...community]
  if (all.length < 5) return preflopName(hole)

  let best: number[] | null = null
  for (const combo of combinations(all, 5)) {
    const s = score5(combo)
    if (!best || compareScore(s, best) > 0) best = s
  }

  if (!best) return null
  if (best[0] === 8 && best[1] === 14) return 'ロイヤルフラッシュ'
  return HAND_NAMES[best[0]] ?? null
}
