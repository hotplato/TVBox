package com.hotplato.tvbox.util;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Dns;

/**
 * 将 IPv4 排在 IPv6 之前，并在已有 IPv4 时丢弃明显不可路由的 ULA/链路本地 IPv6。
 * <p>
 * 规避 fake-ip / 部分 CDN 返回 {@code fc00::}/{@code fe80::} 导致 OkHttp 优先连 IPv6 失败、
 * 源站封面（如荐片 {@code static.ztcuc.com}）整批占位的问题。
 */
public final class PreferIpv4Dns implements Dns {
    private final Dns delegate;

    public PreferIpv4Dns(@NonNull Dns delegate) {
        this.delegate = delegate;
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        List<InetAddress> addresses = delegate.lookup(hostname);
        if (addresses == null || addresses.size() <= 1) {
            return addresses;
        }

        List<InetAddress> ipv4 = new ArrayList<>(addresses.size());
        List<InetAddress> ipv6 = new ArrayList<>(addresses.size());
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address) {
                ipv4.add(address);
            } else {
                ipv6.add(address);
            }
        }
        if (ipv4.isEmpty()) {
            return addresses;
        }

        List<InetAddress> ordered = new ArrayList<>(addresses.size());
        ordered.addAll(ipv4);
        for (InetAddress address : ipv6) {
            if (!isUnroutableIpv6(address)) {
                ordered.add(address);
            }
        }
        return ordered.isEmpty() ? addresses : ordered;
    }

    private static boolean isUnroutableIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        if (address.isLinkLocalAddress()) {
            return true;
        }
        // Unique Local Address fc00::/7（含 fake-ip 常用的 fc00::xx）
        byte[] raw = address.getAddress();
        return raw != null && raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
    }
}
