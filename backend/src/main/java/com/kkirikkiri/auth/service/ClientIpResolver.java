package com.kkirikkiri.auth.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private final List<IpAddressMatcher> trusted;
    public ClientIpResolver(@Value("${auth.protection.trusted-proxies:}") String proxies) {
        trusted=Arrays.stream(proxies.split(",")).map(String::trim).filter(s -> !s.isEmpty())
            .map(IpAddressMatcher::new).toList();
    }
    public String resolve(HttpServletRequest request) {
        String remote=numeric(request.getRemoteAddr());
        if (remote == null) return "unknown-peer";
        if (!isTrusted(remote)) return remote;
        String header=request.getHeader("X-Forwarded-For");
        if (header == null || header.length() > 2048) return remote;
        String[] hops=header.split(",", -1);
        if (hops.length > 20) return remote;
        String current=remote;
        for (int i=hops.length-1; i>=0 && isTrusted(current); i--) {
            String next=numeric(hops[i].trim());
            if (next == null) return remote;
            current=next;
        }
        return current;
    }
    private boolean isTrusted(String ip) { return trusted.stream().anyMatch(m -> m.matches(ip)); }
    private String numeric(String ip) {
        if (ip == null || !ip.matches("[0-9A-Fa-f:.]+")) return null; // Never resolve client supplied hostnames.
        try { return InetAddress.getByName(ip).getHostAddress(); }
        catch (UnknownHostException e) { return null; }
    }
}
