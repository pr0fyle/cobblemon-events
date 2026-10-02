package com.alex.shinyevents.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the command's human-readable multiplier and duration arguments. */
public final class EventArguments {
    public static final double MAX_MULTIPLIER = 1_000_000;
    public static final long MAX_DURATION_MILLIS = 7L * 24 * 60 * 60 * 1_000;

    private static final Pattern MULTIPLIER = Pattern.compile("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)x?");
    private static final Pattern DURATION_PART = Pattern.compile("([0-9]+)([smhd])");

    private EventArguments() {
    }

    public static double parseMultiplier(String input) {
        String value = normalized(input, "Multiplier");
        if (!MULTIPLIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Use a multiplier such as 8x or 2.5x.");
        }
        if (value.endsWith("x")) {
            value = value.substring(0, value.length() - 1);
        }
        double multiplier;
        try {
            multiplier = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Use a multiplier such as 8x or 2.5x.", e);
        }
        validateMultiplier(multiplier);
        return multiplier;
    }

    public static double parseMultiplier(EventType type, String input) {
        double multiplier = parseMultiplier(input);
        validateMultiplier(type, multiplier);
        return multiplier;
    }

    public static long parseDurationMillis(String input) {
        String value = normalized(input, "Duration");
        Matcher matcher = DURATION_PART.matcher(value);
        int position = 0;
        long durationMillis = 0;
        while (matcher.find()) {
            if (matcher.start() != position) {
                throw invalidDuration();
            }
            long unitMillis = switch (matcher.group(2)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> throw invalidDuration();
            };
            try {
                long amount = Long.parseLong(matcher.group(1));
                durationMillis = Math.addExact(durationMillis, Math.multiplyExact(amount, unitMillis));
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalArgumentException("Duration must be between 1 second and 7 days.", e);
            }
            if (durationMillis > MAX_DURATION_MILLIS) {
                throw new IllegalArgumentException("Duration cannot exceed 7 days.");
            }
            position = matcher.end();
        }
        if (position != value.length() || position == 0) {
            throw invalidDuration();
        }
        if (durationMillis == 0) {
            throw new IllegalArgumentException("Duration must be between 1 second and 7 days.");
        }
        return durationMillis;
    }

    static void validateMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 1 || multiplier > MAX_MULTIPLIER) {
            throw new IllegalArgumentException("Multiplier must be greater than 1 and at most 1000000.");
        }
    }

    public static void validateMultiplier(EventType type, double multiplier) {
        if (type == null) {
            throw new IllegalArgumentException("Event type is required.");
        }
        if (!Double.isFinite(multiplier) || multiplier <= 1 || multiplier > type.maximumMultiplier()) {
            throw new IllegalArgumentException("Multiplier for " + type.id()
                    + " must be greater than 1 and at most " + (long) type.maximumMultiplier() + ".");
        }
    }

    private static String normalized(String input, String label) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return input.trim().toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException invalidDuration() {
        return new IllegalArgumentException("Use a duration such as 30s, 15m, 1h30m, or 1d (up to 7 days).");
    }
}
