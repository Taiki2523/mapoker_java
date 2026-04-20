// 将来 i18next 等に置き換えやすいよう t() でラップしておく
// 言語を追加する場合は locale を切り替えるだけでOK

const ja = {
  // --- ナビ・ヘッダー ---
  user: 'ユーザー',
  status: 'ステータス',
  street: 'ストリート',
  pot: 'ポット',
  currentBet: '現在のベット',

  // --- ウェルカム ---
  welcomeTitle: 'ようこそ',
  welcomeDesc: 'テーブルに参加するには表示名を入力してください',
  yourName: '表示名',
  namePlaceholder: '例：山田太郎',
  continue: '続ける',

  // --- ルーム ---
  roomTitle: 'ルーム',
  roomDesc: '新しいルームを作成するか、招待から参加してください',
  roomIdLabel: 'ルームID / 招待URL',
  roomIdPlaceholder: 'game_xxx または招待URL',
  joinRoom: 'ルームに参加',
  inviteUrl: '招待URL',
  copied: 'コピー済み',
  copy: 'コピー',

  // --- ルーム作成 ---
  createRoomTitle: 'ルームを作成',
  createRoomDesc: 'オーナーのみ全テーブル情報を閲覧できます',
  players: 'プレイヤー数',
  stack: 'スタック',
  bigBlind: 'ビッグブラインド',
  buttonIndex: 'ボタンポジション',
  seed: 'シード（任意）',
  seedPlaceholder: '42',
  autoStartHand: 'ハンド自動開始',
  createGame: 'ゲームを作成',
  startHand: 'ハンド開始',
  refresh: '更新',
  autoRefresh: '自動更新',
  every2s: '2秒ごと',

  // --- 待機室 ---
  waitingRoomTitle: '待機室',
  waitingRoomDesc: '招待を共有して全員の参加を待ちましょう',
  inviteCode: '招待コード',
  seat: 'シート',
  joinLobby: 'ロビーに参加',
  playersJoined: '参加プレイヤー',
  waitingForPlayers: 'プレイヤーを待っています...',

  // --- テーブル ---
  tableTitle: 'テーブル',
  tableDesc: 'コミュニティカード・スタック・行動順',
  community: 'コミュニティ',
  folded: 'フォールド',
  allIn: 'オールイン',
  button: 'ボタン',
  noGame: 'テーブルを表示するにはゲームを作成してください',

  // --- アクション ---
  actionTitle: 'アクション',
  waitingShowdown: 'ショーダウン待ちです',
  yourTurn: 'あなたのターンです',
  currentPlayerLabel: '現在のプレイヤー',
  toCall: 'コール額',
  yourHand: 'あなたのハンド',
  fold: 'フォールド',
  check: 'チェック',
  call: 'コール',
  betLabel: 'ベット',
  raiseLabel: 'レイズ',
  allInBtn: 'オールイン',
  actionType: 'アクション種別',
  callCheck: 'コール / チェック',
  betRaise: 'ベット / レイズ',
  foldAllIn: 'フォールド / オールイン',
  amount: '金額',
  sendAction: 'アクション送信',
  loginToAct: 'アクションを行うにはログインしてください',
  spectatorView: 'チップがありません。スペクテーターとして観戦中です',
  handComplete: 'ハンド終了。ショーダウン待ちです',

  // --- セッション ---
  session: 'セッション',
  takeSeat: '席に着く',
  leave: '退席',

  // --- ショーダウン ---
  showdownTitle: 'ショーダウン',
  showdownDesc: 'ハンドを解決してペイアウトを確認',
  runShowdown: 'ショーダウン実行',
  waitingHostShowdown: 'ホストがショーダウンを実行するまでお待ちください',
  winners: '勝者',
  bestHand: 'ベストハンド',
  payouts: 'ペイアウト',
  noShowdown: 'まだショーダウンはありません',
  nextHand: '次のハンド',

  // --- フッター ---
  tip: 'ヒント',
  tipText: '自動更新をオンにするとリアルタイムに近い感覚で遊べます',

  // --- ハンドランク ---
  HighCard: 'ハイカード',
  OnePair: 'ワンペア',
  TwoPair: 'ツーペア',
  ThreeOfAKind: 'スリーカード',
  Straight: 'ストレート',
  Flush: 'フラッシュ',
  FullHouse: 'フルハウス',
  FourOfAKind: 'フォーカード',
  StraightFlush: 'ストレートフラッシュ',

  // --- スーツ（aria用）---
  Spades: 'スペード',
  Hearts: 'ハート',
  Diamonds: 'ダイヤ',
  Clubs: 'クラブ',

  // --- 認証 ---
  signIn: 'サインイン',
  signUp: 'サインアップ',
  usernameLabel: 'ユーザー名',
  passwordLabel: 'パスワード',
  usernamePlaceholder: '例：yamada_taro',
  passwordPlaceholder: '8文字以上',
  goToSignUp: 'アカウントを作成',
  goToSignIn: 'サインインに戻る',
  logout: 'ログアウト',
  loggedInAs: 'ログイン中',
  myPage: 'マイページ',

  // --- エラーメッセージ ---
  errEnterName: '名前を入力してください',
  errLoadRoom: 'まずルームを読み込んでください',
  errSelectSeat: '有効なシートを選択してください',
  errMissingRoom: 'ルームIDがありません',
  errSelectSeatFirst: 'アクション前にシートを選択してください',

  // --- 動的テキスト（{n} はシート番号）---
  seatN: 'シート{n}',
  playerN: 'プレイヤー{n}',
  playingTurn: '{name} のターン',
  callN: 'コール {n}',
} as const

type Key = keyof typeof ja

export function t(key: Key, vars?: Record<string, string | number>): string {
  let s: string = ja[key]
  if (vars) {
    for (const [k, v] of Object.entries(vars)) {
      s = s.replace(`{${k}}`, String(v))
    }
  }
  return s
}
