package com.karaninder.pulseWatch.check;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * Protects against SSRF (server-side request forgery): a user could register http://169.254.169.254/ (the cloud
 * metadata service), http://localhost:5432 or http://10.0.0.5/admin and make OUR server call it from inside the
 * network, then read the answer back in the check history. So a monitor may only point at public addresses.
 * <p>
 * The hostname is resolved and EVERY address it resolves to must be public. The check runs twice: when a monitor
 * is saved (fast, friendly feedback) and again before every check (the DNS answer can change after saving).
 * Redirects are never followed, so a public URL cannot bounce us to an internal one.
 * Known gap: between this lookup and the connection the JDK resolves the name again; a hostile DNS server could
 * answer differently the second time ("DNS rebinding"). Closing it fully needs egress firewall rules (see README).
 */
@Component
public class TargetGuard {

	private final boolean allowPrivateTargets;

	public TargetGuard(CheckProperties properties) {
		this.allowPrivateTargets = properties.allowPrivateTargets();
	}

	/** @return the reason the URL is not allowed, or empty if it is fine (or cannot be judged here) */
	public Optional<String> violation(String url) {
		if (allowPrivateTargets) {
			return Optional.empty();
		}
		String host;
		try {
			host = URI.create(url.trim()).getHost();
		} catch (IllegalArgumentException e) {
			return Optional.empty(); // malformed: validation and the checker report that separately
		}
		if (host == null || host.isBlank()) {
			return Optional.empty();
		}
		try {
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (isInternal(address)) {
					return Optional.of("The address " + address.getHostAddress() + " (" + host + ") is a private or internal address; only public addresses can be monitored");
				}
			}
		} catch (UnknownHostException e) {
			return Optional.empty(); // not resolvable (yet): allowed to be saved, the check will report the DNS failure
		}
		return Optional.empty();
	}

	/** Loopback, wildcard, private (RFC 1918), link-local (incl. 169.254.169.254), multicast, carrier-grade NAT, IPv6 unique-local. */
	static boolean isInternal(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return true;
		}
		byte[] bytes = address.getAddress();
		if (bytes.length == 4) { // IPv4 (an IPv4-mapped IPv6 address such as ::ffff:10.0.0.1 is already an Inet4Address)
			int first = bytes[0] & 0xff;
			int second = bytes[1] & 0xff;
			return first == 0                                   // 0.0.0.0/8 "this network"
					|| (first == 100 && second >= 64 && second <= 127) // 100.64.0.0/10 carrier-grade NAT
					|| (first == 198 && (second == 18 || second == 19)); // 198.18.0.0/15 benchmarking
		}
		return (bytes[0] & 0xfe) == 0xfc; // IPv6 fc00::/7 unique local addresses
	}
}
