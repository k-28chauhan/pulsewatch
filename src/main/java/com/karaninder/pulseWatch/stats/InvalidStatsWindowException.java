package com.karaninder.pulseWatch.stats;

/** The client asked for a window we cannot serve. Answered with HTTP 400. */
public class InvalidStatsWindowException extends RuntimeException {

	private InvalidStatsWindowException(String message) {
		super(message);
	}

	/** Anything other than 24h, 7d, 30d. */
	public static InvalidStatsWindowException unknown(String requested) {
		return new InvalidStatsWindowException("Unknown window '" + requested + "'; use one of 24h, 7d, 30d");
	}

	/** The latency series comes from raw rows only, which are kept for 7 days. */
	public static InvalidStatsWindowException noLatencySeries(StatsWindow window) {
		return new InvalidStatsWindowException("Latency series are available for 24h and 7d only, not " + window.label()
				+ " (percentiles cannot be rebuilt from hourly summaries)");
	}
}
