import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  claimDailyBonus, fetchHistory, fetchTables, fetchWallet, fetchWalletLedger,
} from '../api'
import type { Table, UserTableHistoryEntry, WalletLedgerEntry, WalletSummary } from '../types'

/**
 * プロフィール関連データを管理する hook。
 *
 * - wallet / wallet-ledger: 手動フェッチ（ウォレット未設定環境での意図しない 500 を防ぐ）
 * - tables / history: TanStack Query（showMyPage=true で自動フェッチ）
 *
 * @param showMyPage  MyPage が表示中かどうか（tables/history の fetch トリガー）
 * @param formatError エラーメッセージ整形関数
 */
export function useProfileData(
  showMyPage: boolean,
  formatError: (e: unknown) => string
) {
  const queryClient = useQueryClient()

  // wallet は手動フェッチ（ウォレット機能が未設定の環境でも 500 を出さないため）
  const [wallet, setWallet] = useState<WalletSummary | null>(null)
  const [walletLedger, setWalletLedger] = useState<WalletLedgerEntry[]>([])

  const refreshWallet = async () => {
    let next: WalletSummary | null = null
    try {
      next = await fetchWallet()
      setWallet(next)
    } catch {
      setWallet(null)
      setWalletLedger([])
      return
    }
    try {
      setWalletLedger(await fetchWalletLedger())
    } catch {
      setWalletLedger([])
    }
  }

  // tables / history は TanStack Query（MyPage 表示時に自動フェッチ）
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
    onSuccess: () => void refreshWallet(),
  })

  const profileLoading =
    tablesQuery.isFetching || historyQuery.isFetching || claimMutation.isPending

  const anyError = tablesQuery.error ?? historyQuery.error ?? claimMutation.error
  const profileError = anyError ? formatError(anyError) : ''

  const refreshProfileTables = async () => {
    await Promise.all([
      tablesQuery.refetch(),
      historyQuery.refetch(),
      refreshWallet(),
    ])
  }

  const clear = () => {
    setWallet(null)
    setWalletLedger([])
    queryClient.removeQueries({ queryKey: ['profile-tables'] })
    queryClient.removeQueries({ queryKey: ['profile-history'] })
  }

  return {
    wallet,
    walletLedger,
    profileTables: tablesQuery.data ?? [] as Table[],
    profileHistory: historyQuery.data ?? [] as UserTableHistoryEntry[],
    profileLoading,
    profileError,
    refreshWallet,
    refreshProfileTables,
    handleClaimDailyBonus: () => claimMutation.mutate(),
    clear,
  }
}
