package com.lunasdev.lunasdpi.ui.format

import androidx.annotation.StringRes
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.model.DnsMode
import com.lunasdev.lunasdpi.data.model.DpiMode
import com.lunasdev.lunasdpi.data.model.Ipv6Mode
import com.lunasdev.lunasdpi.data.model.PerAppMode
import com.lunasdev.lunasdpi.data.model.VpnPhase
import java.util.Locale

@StringRes
fun DpiMode.labelRes(): Int = when (this) {
    DpiMode.AUTOMATIC -> R.string.mode_automatic
    DpiMode.BASIC -> R.string.mode_basic
    DpiMode.BALANCED -> R.string.mode_balanced
    DpiMode.AGGRESSIVE -> R.string.mode_aggressive
    DpiMode.CUSTOM -> R.string.mode_custom
}

@StringRes
fun DnsMode.labelRes(): Int = when (this) {
    DnsMode.AUTOMATIC -> R.string.mode_automatic
    DnsMode.SYSTEM -> R.string.dns_system
    DnsMode.CUSTOM -> R.string.mode_custom
}

@StringRes
fun Ipv6Mode.labelRes(): Int = when (this) {
    Ipv6Mode.OFF -> R.string.ipv6_off
    Ipv6Mode.BLOCK -> R.string.ipv6_block
}

@StringRes
fun PerAppMode.labelRes(): Int = when (this) {
    PerAppMode.ALL -> R.string.per_app_all
    PerAppMode.SELECTED -> R.string.per_app_selected
    PerAppMode.EXCLUDED -> R.string.per_app_excluded
}

@StringRes
fun VpnPhase.statusRes(): Int = when (this) {
    VpnPhase.CONNECTED -> R.string.status_protected
    VpnPhase.CONNECTING -> R.string.status_connecting
    VpnPhase.STOPPING -> R.string.status_stopping
    VpnPhase.REQUESTING_PERMISSION -> R.string.status_permission
    VpnPhase.ERROR -> R.string.status_error
    VpnPhase.DISCONNECTED -> R.string.status_not_protected
}

fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}

fun formatExact(value: Long): String = String.format(Locale.US, "%,d", value)

fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
