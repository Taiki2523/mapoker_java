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
  roomTitle: 'テーブル',
  roomDesc: '新しいリングテーブルを作成するか、招待から参加してください',
  roomIdLabel: 'テーブルID / 招待URL',
  roomIdPlaceholder: 'table_xxx または招待URL',
  joinRoom: 'テーブルに参加',
  openLobbyBrowser: 'ロビーを見る',
  inviteUrl: '招待URL',
  copied: 'コピー済み',
  copy: 'コピー',

  // --- ルーム作成 ---
  createRoomTitle: 'テーブルを作成',
  createRoomDesc: 'Cash Orbit のリングテーブルを作成します',
  tableName: 'テーブル名',
  tableNamePlaceholder: '例：Cash Orbit Tokyo',
  visibility: '公開設定',
  publicTable: '公開',
  privateTable: '非公開',
  tableFlags: 'テーブルフラグ',
  flagCasual: 'カジュアル',
  flagSerious: 'ガチ勢',
  flagNewbie: '初心者歓迎',
  flagShortHanded: 'ショートハンド',
  players: 'プレイヤー数',
  stack: 'スタック',
  bigBlind: 'ビッグブラインド',
  buttonIndex: 'ボタンポジション',
  seed: 'シード（任意）',
  seedPlaceholder: '42',
  autoStartHand: 'ハンド自動開始',
  createGame: 'テーブルを作成',
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
  leavePending: '退席予約中...',

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
  winners: '勝者',
  bestHand: 'ベストハンド',
  payouts: 'ペイアウト',
  noShowdown: 'まだショーダウンはありません',

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
  close: '閉じる',
  currentTable: '現在のテーブル',
  none: 'なし',
  guest: 'ゲスト',
  notJoined: 'まだ着席していません',
  playHistory: 'プレイ履歴',
  playHistoryDesc: '最近参加したテーブルの履歴',
  openTables: '公開テーブル',
  openTablesDesc: 'いま参加可能なテーブル数',
  lobbyBrowserTitle: 'ロビーブラウザ',
  lobbyBrowserDesc: '公開・非公開テーブルを絞り込んで参加先を探せます',
  backToRoomForm: '作成画面に戻る',
  backToLobby: 'ロビーに戻る',
  back: '戻る',
  joinTable: '参加',
  noLobbyTables: '条件に合うテーブルはありません',
  stakeLabel: 'ブラインド',
  membersLabel: '参加人数',
  tableStatusWaiting: '待機中',
  tableStatusInProgress: '進行中',
  tableStatusShowdown: 'ショーダウン',
  tableStatusFinished: '終了',
  myTables: 'マイテーブル',
  myTablesDesc: '参加中または最近プレイしたテーブル',
  tableList: 'テーブル一覧',
  createTable: 'テーブル作成',
  publicTablesTitle: '公開テーブル一覧',
  publicTablesDesc: '参加先をここから選べます',
  noMyTables: 'まだ自分のテーブル履歴はありません',
  noPlayHistory: 'まだプレイ履歴はありません',
  noOpenTables: '公開テーブルはまだありません',
  open: '開く',
  reopen: '再表示',
  currentlySeated: '着席中',
  lastPlayed: '最終参加',
  joinedAt: '参加',
  leftAt: '退出',
  handHistoryTitle: 'ハンド履歴',
  handHistoryDesc: '最近終了したハンドの結果',
  noHandHistory: 'まだハンド履歴はありません',
  finishedAt: '終了',
  streetPreflop: 'プリフロップ',
  streetFlop: 'フロップ',
  streetTurn: 'ターン',
  streetRiver: 'リバー',
  chipBalance: '所持チップ',
  claimDailyBonus: 'デイリーボーナス',
  claimRecovery: '回復チップ',
  walletLedger: '増減履歴',
  noWalletLedger: 'まだ増減履歴はありません',
  REGISTER_BONUS: '新規登録ボーナス',
  DAILY_BONUS: 'デイリーボーナス',
  RECOVERY: '回復チップ',
  RECOVERY_BONUS: '回復チップ',
  ADMIN_GRANT: '管理者付与',
  TABLE_BUY_IN: 'バイイン',
  TABLE_CASH_OUT: 'キャッシュアウト',
  cooldownUntil: '次回: {time}',
  insufficientFunds: 'チップが不足しています',
  buyIn: 'バイイン',
  confirmBuyIn: '参加する',
  ring: 'リングゲーム',
  tournament: 'トーナメント',
  comingSoon: '近日公開',
  gameTypeTitle: 'ゲームタイプを選択',
  gameTypeDesc: 'どのゲームで遊びますか？',
  buyInTitle: 'バイイン',
  buyInAmount: 'バイイン額',
  buyInMin: '最小',
  buyInMax: '最大',
  cancel: 'キャンセル',
  youHaveBeenRemovedFromTable: 'テーブルから退席しました',
  rebuy: 'リバイ',
  presetMin: '最小',
  presetHalf: '½P',
  presetPot: 'Pot',
  presetAll: 'ALL',
  showdown: 'ショーダウン',
  finished: '終了',

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

export function hasTranslation(key: string): key is Key {
  return Object.prototype.hasOwnProperty.call(ja, key)
}
