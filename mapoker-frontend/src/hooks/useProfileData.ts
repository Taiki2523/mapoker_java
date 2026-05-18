import { useState } from 'react'
import { claimDailyBonus, fetchHistory, fetchTables, fetchWallet, fetchWalletLedger } from '../api'
import type { Table, UserTableHistoryEntry, WalletLedgerEntry, WalletSummary } from '../types'

export function useProfileData(formatError: (e: unknown) => string) {
  const [profileTables, setProfileTables] = useState<Table[]>([])
  const [profileHistory, setProfileHistory] = useState<UserTableHistoryEntry[]>([])
  const [wallet, setWallet] = useState<WalletSummary | null>(null)
  const [walletLedger, setWalletLedger] = useState<WalletLedgerEntry[]>([])
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState('')

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
    const ledger = await fetchWalletLedger()
    setWallet(next)
    setWalletLedger(ledger)
  }

  const refreshProfileTables = async () => {
    setProfileLoading(true)
    setProfileError('')
    try {
      const [tables, history] = await Promise.all([fetchTables(), fetchHistory()])
      setProfileTables(tables)
      setProfileHistory(history)
      await refreshWallet()
    } catch (err) {
      setProfileError(formatError(err))
    } finally {
      setProfileLoading(false)
    }
  }

  const handleClaimDailyBonus = async () => {
    setProfileLoading(true)
    setProfileError('')
    try {
      await claimDailyBonus()
      await refreshWallet()
    } catch (err) {
      setProfileError(formatError(err))
    } finally {
      setProfileLoading(false)
    }
  }

  const clear = () => {
    setProfileTables([])
    setProfileHistory([])
    setWallet(null)
    setWalletLedger([])
  }

  return {
    wallet, walletLedger, profileTables, profileHistory,
    profileLoading, profileError,
    refreshProfileTables, refreshWallet, handleClaimDailyBonus, clear,
  }
}
