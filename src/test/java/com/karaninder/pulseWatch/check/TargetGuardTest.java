package com.karaninder.pulseWatch.check;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class TargetGuardTest {

	private final TargetGuard strict = new TargetGuard(new CheckProperties(false));
	private final TargetGuard permissive = new TargetGuard(new CheckProperties(true));

	private static boolean internal(String ip) throws UnknownHostException {
		return TargetGuard.isInternal(InetAddress.getByName(ip));
	}

	@Test
	void everyKindOfInternalIpv4AddressIsRecognised() throws Exception {
		for (String ip : new String[]{"127.0.0.1", "127.8.8.8", "0.0.0.0", "0.1.2.3", "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255",
				"192.168.0.1", "169.254.169.254", "100.64.0.1", "100.127.255.255", "198.18.0.1", "224.0.0.1"}) {
			assertThat(internal(ip)).as(ip).isTrue();
		}
	}

	@Test
	void everyKindOfInternalIpv6AddressIsRecognised() throws Exception {
		for (String ip : new String[]{"::1", "::", "fe80::1", "fc00::1", "fd12:3456:789a::1", "::ffff:10.0.0.1", "::ffff:127.0.0.1"}) {
			assertThat(internal(ip)).as(ip).isTrue();
		}
	}

	@Test
	void publicAddressesAndTheEdgesOfPrivateRangesAreNotBlocked() throws Exception {
		for (String ip : new String[]{"8.8.8.8", "93.184.216.34", "1.1.1.1", "172.15.255.255", "172.32.0.1", "100.63.255.255", "100.128.0.1",
				"198.17.0.1", "198.20.0.1", "2606:4700:4700::1111", "::ffff:8.8.8.8"}) {
			assertThat(internal(ip)).as(ip).isFalse();
		}
	}

	@Test
	void theViolationNamesTheOffendingAddress() {
		assertThat(strict.violation("http://169.254.169.254/latest/meta-data/"))
				.hasValueSatisfying(reason -> assertThat(reason).contains("169.254.169.254").contains("private or internal"));
	}

	@Test
	void aHostnameThatResolvesToLoopbackIsRefused() {
		assertThat(strict.violation("http://localhost:8080/actuator")).isPresent();
	}

	@Test
	void ipv6LiteralsInUrlsAreChecked() {
		assertThat(strict.violation("http://[::1]:8080/")).isPresent();
		assertThat(strict.violation("http://[fd00::1]/")).isPresent();
	}

	@Test
	void publicTargetsAreAllowed() {
		assertThat(strict.violation("https://93.184.216.34/")).isEmpty();
		assertThat(strict.violation("https://[2606:4700:4700::1111]/")).isEmpty();
	}

	@Test
	void whatCannotBeJudgedIsLeftToTheOtherChecks() {
		assertThat(strict.violation("not a url at all")).isEmpty();                       // the validator and the checker report this
		assertThat(strict.violation("http://this-host-does-not-exist.invalid/")).isEmpty(); // DNS failure shows up as a failed check
	}

	@Test
	void theLocalSwitchAllowsEverything() {
		assertThat(permissive.violation("http://localhost:8081/status/200")).isEmpty();
		assertThat(permissive.violation("http://169.254.169.254/")).isEmpty();
	}
}
