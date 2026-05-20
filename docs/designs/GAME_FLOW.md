# ゲームフロー

この文書は、Go 版の `internal/core/game/game.go` と `showdown.go` を基に、`mapoker` の 1 hand の進行を `mermaid` で可視化したものです。

## 見方

- `active` は `fold していない` かつ `all-in ではない` プレイヤーを指す
- `remaining` は `fold していない` プレイヤーを指す
- `contributed` はその street での投入額
- `totalContrib` はその hand 全体での投入額

## 1. ゲーム作成からハンド開始まで

```mermaid
graph TD
    A[CreateGame] --> B[入力検証]
    B --> C{playersは2から9人か}
    C -- No --> Z1[create error]
    C -- Yes --> D{bigBlindは正か}
    D -- No --> Z1
    D -- Yes --> E{buttonIndexは有効か}
    E -- No --> Z1
    E -- Yes --> F[GameState生成]

    F --> G[Deckを52枚生成]
    G --> H[seedありなら決定的シャッフル]
    H --> I[初期値を設定]
    I --> I1[street=preflop]
    I1 --> I2[status=in_progress]
    I2 --> I3[currentBet=0]
    I3 --> I4[lastRaiseSize=bigBlind]
    I4 --> I5[oddChipRule=low_index]
    I5 --> I6[raiseOpen=true]
    I6 --> J[Actedを全員falseで初期化]
    J --> K[storeへ保存]

    K --> L[StartHand]
    L --> M{statusがshowdownか}
    M -- Yes --> Z2[start error]
    M -- No --> N[hand状態をリセット]

    N --> N1[LastShowdown=nil]
    N1 --> N2[Communityを空にする]
    N2 --> N3[DeckPos=0]
    N3 --> N4[Pot=0]
    N4 --> N5[CurrentBet=0]
    N5 --> N6[LastRaiseSize=bigBlind]
    N6 --> N7[BigBlindSize=bigBlind]
    N7 --> N8[RaiseOpen=true]
    N8 --> N9[Street=preflop]

    N9 --> O[各プレイヤーを初期化]
    O --> O1[Folded=false]
    O1 --> O2[AllIn=false]
    O2 --> O3[Contributed=0]
    O3 --> O4[TotalContrib=0]
    O4 --> O5[Holeを空にする]

    O5 --> P[stackが0以下のプレイヤーはfold扱い]
    P --> Q{チップを持つプレイヤーが2人以上か}
    Q -- No --> Z2
    Q -- Yes --> R[SBを決定<br/>2人: button=SB<br/>3人以上: button左のactive]
    R --> S[BBを決定<br/>2人: もう1人<br/>3人以上: SB左のactive]
    S --> T[SBをpostBlind]
    T --> U[BBをpostBlind]
    U --> V[blindでstackが0になったらall-in]
    V --> W[activeプレイヤーへ2周してholeを2枚配る]
    W --> X[CurrentBet = BBのContributed]
    X --> Y[LastRaiseSize = bigBlind]
    Y --> Y1[Actedを全員falseへ]
    Y1 --> Y2[RaiseOpen=true]
    Y2 --> Z[CurrentPlayer = firstToAct]
    Z --> ZA[first betting roundへ]
```

## 2. 手番決定と betting round の大枠

```mermaid
graph TD
    A[firstToActを決定] --> B{プレイヤー数は2人か}
    B -- Yes --> C{streetはpreflopか}
    C -- Yes --> D[smallBlindが先手]
    C -- No --> E[bigBlindが先手]
    B -- No --> F{streetはpreflopか}
    F -- Yes --> G[bigBlindの左のactiveが先手]
    F -- No --> H[buttonの左のactiveが先手]

    D --> I[CurrentPlayerを設定]
    E --> I
    G --> I
    H --> I

    I --> J[手番プレイヤーがaction実行]
    J --> K[ValidateAction]
    K --> L{合法か}
    L -- No --> M[invalid_actionを返す]
    L -- Yes --> N[ApplyActionで状態更新]
    N --> O{remainingが1人以下か}
    O -- Yes --> P[勝者を即確定して配当へ]
    O -- No --> Q{betting round完了か}
    Q -- No --> R[CurrentPlayer = nextActive]
    R --> J
    Q -- Yes --> S{activeCountが0か}
    S -- Yes --> T[残りstreetを自動進行]
    T --> U[status=showdown]
    S -- No --> V{streetはriverか}
    V -- Yes --> U
    V -- No --> W[advanceStreet]
    W --> X[次streetの先手を再計算]
    X --> J
```

## 3. action 検証と状態更新

```mermaid
graph TD
    A[ApplyAction開始] --> B{statusはin_progressか}
    B -- No --> Z1[game not in progress]
    B -- Yes --> C{playerIndexはCurrentPlayerか}
    C -- No --> Z1
    C -- Yes --> D[TableStateとPlayerStateを構築]
    D --> E[ValidateAction]

    E --> F{playerはfold済みか}
    F -- Yes --> Z2[player already folded]
    F -- No --> G{playerはall-inか}
    G -- Yes --> Z2[player is all-in]
    G -- No --> H{stackは正か}
    H -- No --> Z2[player has no chips]
    H -- Yes --> I{amountは0以上か}
    I -- No --> Z2[amount must be non-negative]
    I -- Yes --> J[toCall = currentBet - contributed]
    J --> K{action type}

    K -->|fold| L[常に許可]
    K -->|check| M{toCallは0か}
    M -- No --> Z2[cannot check]
    M -- Yes --> N[checkを適用]
    K -->|call| O{toCallは0か}
    O -- Yes --> Z2[cannot call]
    O -- No --> P{amountは0か}
    P -- Yes --> Q[auto-callを許可]
    P -- No --> R{amountは妥当なcall額か}
    R -- No --> Z2[call amount invalid]
    R -- Yes --> S[callを適用]
    K -->|bet| T{currentBetは0か}
    T -- No --> Z2[cannot bet]
    T -- Yes --> U{amountはbigBlind以上かつstack以内か}
    U -- No --> Z2[bet invalid]
    U -- Yes --> V[betを適用]
    K -->|raise| W{currentBetは0より大か}
    W -- No --> Z2[cannot raise]
    W -- Yes --> X{raiseOpenはtrueか}
    X -- No --> Z2[betting not reopened]
    X -- Yes --> Y{amountはcurrentBet超か}
    Y -- No --> Z2[raise too small]
    Y -- Yes --> AA{raiseSizeはminRaise以上か}
    AA -- No --> Z2[min raise不足]
    AA -- Yes --> AB{amountはstack以内か}
    AB -- No --> Z2[raise exceeds stack]
    AB -- Yes --> AC[raiseを適用]
    K -->|all_in| AD[all-inを適用]

    L --> AE[Folded=true]
    AE --> AF[Acted=true]

    N --> AF

    Q --> AG[target = min currentBet, contributed+stack]
    R --> AH[target = amount]
    S --> AH
    AG --> AI[commitBet]
    AH --> AI
    AI --> AF

    V --> AJ[commitBet amount]
    AJ --> AK[CurrentBet=amount]
    AK --> AL[LastRaiseSize=amount]
    AL --> AM[resetActedExcept self]
    AM --> AN[RaiseOpen=true]

    AC --> AO[raiseSize = amount - oldCurrentBet]
    AO --> AP[commitBet amount]
    AP --> AQ[CurrentBet=amount]
    AQ --> AR[LastRaiseSize=raiseSize]
    AR --> AS[resetActedExcept self]
    AS --> AN

    AD --> AT[target = contributed + stack]
    AT --> AU[commitBet target]
    AU --> AV{target > oldCurrentBetか}
    AV -- No --> AF
    AV -- Yes --> AW[raiseSize = target - oldCurrentBet]
    AW --> AX{raiseSizeはminRaise以上か}
    AX -- Yes --> AY[CurrentBet=target]
    AY --> AZ[LastRaiseSize=raiseSize]
    AZ --> BA[RaiseOpen=true]
    BA --> BB[resetActedExcept self]
    AX -- No --> BC[CurrentBet=target]
    BC --> BD[RaiseOpen=false]
    BD --> AF
```

## 4. betting round 完了判定と street 遷移

```mermaid
graph TD
    A[action適用後] --> B{remainingは1人以下か}
    B -- Yes --> C[remaining全員をwinnersにする]
    C --> D[splitPotsでpayout計算]
    D --> E[ApplyPayouts]
    E --> F[status=finished]

    B -- No --> G[isBettingRoundCompleteを判定]
    G --> H{foldしていないプレイヤーを見る}
    H --> I{そのプレイヤーはall-inか}
    I -- Yes --> H
    I -- No --> J{contributed == currentBetか}
    J -- No --> K[未完了]
    J -- Yes --> L{acted == trueか}
    L -- No --> K
    L -- Yes --> H
    H --> M{全員確認済みか}
    M -- No --> I
    M -- Yes --> N[round完了]

    K --> O[CurrentPlayer = nextActive]

    N --> P{activeCount == 0か}
    P -- Yes --> Q[streetがriverに届くまでadvanceStreetを繰り返す]
    Q --> R[status=showdown]

    P -- No --> S{street == riverか}
    S -- Yes --> R
    S -- No --> T[advanceStreet]
    T --> U[Streetを1進める]
    U --> V[各プレイヤーのContributed=0]
    V --> W[Actedを全員false]
    W --> X[CurrentBet=0]
    X --> Y[LastRaiseSize=bigBlind]
    Y --> Z[RaiseOpen=true]
    Z --> ZA[burnを1枚]
    ZA --> ZB{新streetはflopか}
    ZB -- Yes --> ZC[communityを3枚追加]
    ZB -- No --> ZD[communityを1枚追加]
    ZC --> ZE[CurrentPlayer = firstToAct]
    ZD --> ZE
```

## 5. showdown と side pot 配当

```mermaid
graph TD
    A[status=showdown] --> B{communityは5枚あるか}
    B -- No --> Z1[showdown error]
    B -- Yes --> C[bestを最弱handで初期化]
    C --> D[winnersを空で初期化]
    D --> E[全プレイヤーを走査]
    E --> F{fold済みか}
    F -- Yes --> G[skip]
    F -- No --> H[hole2枚 + community5枚で7枚生成]
    H --> I[Eval7でHandValueを算出]
    I --> J{bestより強いか}
    J -- Yes --> K[bestを更新]
    K --> L[winnersをこのplayerだけに置換]
    J -- Tie --> M[winnersに追加]
    J -- No --> G
    L --> G
    M --> G
    G --> N{全プレイヤー評価済みか}
    N -- No --> E
    N -- Yes --> O{winnersは空か}
    O -- Yes --> Z1
    O -- No --> P[buildSidePots]

    P --> Q[totalContribが正のplayerを集める]
    Q --> R[contribution昇順に並べる]
    R --> S[最小contributionレベルごとにpotを作る]
    S --> T[foldしていないplayerだけをeligibleに入れる]
    T --> U[各potについてeligibleとwinnersの積集合を取る]
    U --> V{eligible winnerはいるか}
    V -- No --> W[そのpotはskip]
    V -- Yes --> X[base = amount / winners数]
    X --> Y[rem = amount mod winners数]
    Y --> Z[baseを均等配分]
    Z --> ZA{rem > 0か}
    ZA -- No --> ZB[次のpotへ]
    ZA -- Yes --> ZC{oddChipRuleはbutton_leftか}
    ZC -- No --> ZD[low_index順で端数配分]
    ZC -- Yes --> ZE[button左から順で端数配分]
    ZD --> ZB
    ZE --> ZB
    W --> ZB
    ZB --> ZF{全pot処理済みか}
    ZF -- No --> U
    ZF -- Yes --> ZG[ApplyPayouts]
    ZG --> ZH[各playerのstackへ加算]
    ZH --> ZI[Pot=0]
    ZI --> ZJ[CurrentBet=0]
    ZJ --> ZK[LastRaiseSize=BigBlindSize]
    ZK --> ZL[status=finished]
```
