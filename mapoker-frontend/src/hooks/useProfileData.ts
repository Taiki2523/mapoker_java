import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  claimDailyBonus, fetchHistory, fetchTables, fetchWallet, fetchWalletLedger,
} from '../api'

/**
 * プロフィール関連データ（wallet / tables / history）を TanStack Query で管理する hook。
 * MyPage が表示されたタイミングで全データをフェッチする。
 *
 * @param showMyPage  MyPage が表示中かどうか（全クエリの fetch トリガー）
 * @param formatError エラーメッセージ整形関数
 */
export function useProfileData(
  showMyPage: boolean,
  formatError: (e: unknown) => string
) {
  const queryClient = useQueryClient()

  // wallet は MyPage 表示時のみフェッチ（ウォレット機能が未設定の環境でも 500 を出さないため）
  const walletQuery = useQuery({
    queryKey: ['wallet'],
    queryFn: fetchWallet,
    enabled: loggedIn && showMyPage,
    staleTime: 2 * 60_000,
  })

  const ledgerQuery = useQuery({
    queryKey: ['wallet-ledger'],
    queryFn: fetchWalletLedger,
    enabled: loggedIn && showMyPage,
    staleTime: 60_000,
  })

  const tablesQuery = useQuery({
    queryKey: ['profile-tables'],
    queryFn: fetchTables,
    enabled: showMyPage,
    staleTime: 30_000,
  })

  const historyQuery = useQuery({
    queryKey: ['profile-history'],
    queryFn: fetchHistory,
    enabled: showMyPage,
    staleTime: 30_000,
  })

  const claimMutation = useMutation({
    mutationFn: claimDailyBonus,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['wallet'] })
      void queryClient.invalidateQueries({ queryKey: ['wallet-ledger'] })
    },
  })

  const refreshWallet = () => {
    void queryClient.invalidateQueries({ queryKey: ['wallet'] })
    void queryClient.invalidateQueries({ queryKey: ['wallet-ledger'] })
  }

  const clear = () => {
    queryClient.removeQueries({ queryKey: ['wallet'] })
    queryClient.removeQueries({ queryKey: ['wallet-ledger'] })
    queryClient.removeQueries({ queryKey: ['profile-tables'] })
    queryClient.removeQueries({ queryKey: ['profile-history'] })
  }

  const anyError = tablesQuery.error ?? historyQuery.error ?? claimMutation.error
  const profileError = anyError ? formatError(anyError) : ''

  return {
    wallet: walletQuery.data ?? null,
    walletLedger: ledgerQuery.data ?? [],
    profileTables: tablesQuery.data ?? [],
    profileHistory: historyQuery.data ?? [],
    profileLoading:
      tablesQuery.isFetching || historyQuery.isFetching || walletQuery.isFetching,
    profileError,
    refreshWallet,
    // showMyPage=true なら自動フェッチ済みだが、手動トリガーも可
    refreshProfileTables: () => {
      void tablesQuery.refetch()
      void historyQuery.refetch()
      void walletQuery.refetch()
    },
    handleClaimDailyBonus: () => claimMutation.mutate(),
    clear,
  }
}
