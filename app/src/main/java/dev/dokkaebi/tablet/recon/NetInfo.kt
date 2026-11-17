package dev.dokkaebi.tablet.recon

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

/** Snapshot of the device's active network, read via ConnectivityManager (no location permission). */
data class NetInfo(
    val transport: String,
    val iface: String?,
    val ipv4: String?,
    val prefix: Int?,
    val gateway: String?,
    val dns: List<String>,
    val ipv6: List<String>,
    val validated: Boolean,
    val metered: Boolean,
    val vpn: Boolean,
    val downKbps: Int?,
    val upKbps: Int?,
    val mac: String?,
) {
    val cidr: String? get() = if (ipv4 != null && prefix != null) NetTools.cidr(ipv4, prefix) else null
}

fun readNetInfo(context: Context): NetInfo? {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val net = cm.activeNetwork ?: return null
    val lp = cm.getLinkProperties(net) ?: return null
    val caps = cm.getNetworkCapabilities(net)
    val transport = when {
        caps == null -> "UNKNOWN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
        else -> "OTHER"
    }
    val v4 = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
    val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
    val mac = runCatching {
        NetworkInterface.getByName(lp.interfaceName)?.hardwareAddress?.joinToString(":") { "%02X".format(it) }
    }.getOrNull()
    return NetInfo(
        transport = transport,
        iface = lp.interfaceName,
        ipv4 = v4?.address?.hostAddress,
        prefix = v4?.prefixLength,
        gateway = gw,
        dns = lp.dnsServers.mapNotNull { it.hostAddress },
        ipv6 = lp.linkAddresses.filter { it.address !is Inet4Address }.mapNotNull { it.address.hostAddress?.substringBefore('%') },
        validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
        vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
        downKbps = caps?.linkDownstreamBandwidthKbps,
        upKbps = caps?.linkUpstreamBandwidthKbps,
        mac = mac,
    )
}
