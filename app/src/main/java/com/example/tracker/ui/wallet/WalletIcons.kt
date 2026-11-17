package com.example.tracker.ui.wallet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.tracker.data.db.AccountType

/** Vector icon per account type (consistent rounded family, themable — unlike emoji). */
fun AccountType.icon(): ImageVector = when (this) {
    AccountType.BANK -> Icons.Rounded.AccountBalance
    AccountType.CASH -> Icons.Rounded.Payments
    AccountType.BKASH, AccountType.NAGAD, AccountType.ROCKET -> Icons.Rounded.Smartphone
    AccountType.OTHER -> Icons.Rounded.Wallet
}
