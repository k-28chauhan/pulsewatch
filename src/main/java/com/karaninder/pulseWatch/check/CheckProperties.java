package com.karaninder.pulseWatch.check;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed view of pulsewatch.check.* (the keys "enabled" and "tick-delay" are read elsewhere).
 *
 * @param allowPrivateTargets false (the default) forbids monitors that point at loopback, private-network or
 *                            link-local addresses. Local development switches it on (application-local.yaml)
 *                            because its test target, httpbin, lives on localhost. Never enable it on a public deployment.
 */
@ConfigurationProperties(prefix = "pulsewatch.check")
public record CheckProperties(@DefaultValue("false") boolean allowPrivateTargets) {
}
