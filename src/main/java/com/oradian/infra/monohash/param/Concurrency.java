package com.oradian.infra.monohash.param;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Concurrency {
    public abstract int getConcurrency();

    // -----------------------------------------------------------------------------------------------------------------

    public static Fixed fixed(final int concurrency) {
        return new Fixed(concurrency);
    }

    public static CpuRelative cpuRelative(final double factor) {
        return new CpuRelative(factor);
    }

    // -----------------------------------------------------------------------------------------------------------------

    public static final class Fixed extends Concurrency {
        public static final int MIN = Config.getInt("Concurrency.Fixed.MIN");
        public static final int MAX = Config.getInt("Concurrency.Fixed.MAX");

        public final int concurrency;

        private Fixed(final int concurrency) {
            if (concurrency < MIN) {
                throw new IllegalArgumentException("Fixed concurrency cannot be lower than " + MIN + ", got: " + concurrency);
            }
            if (concurrency > MAX) {
                throw new IllegalArgumentException("Fixed concurrency cannot be higher than " + MAX + ", got: " + concurrency);
            }
            this.concurrency = concurrency;
        }

        @Override
        public int getConcurrency() {
            return concurrency;
        }

        public Fixed withConcurrency(final int concurrency) {
            return concurrency == this.concurrency ? this : new Fixed(concurrency);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Fixed)) {
                return false;
            }
            final Fixed that = (Fixed) obj;
            return concurrency == that.concurrency;
        }

        @Override
        public int hashCode() {
            return concurrency;
        }

        @Override
        public String toString() {
            return "Concurrency.Fixed(" + concurrency + ')';
        }

        static Fixed parseString(final String value) throws ParamParseException {
            final int concurrency;
            try {
                concurrency = Integer.parseInt(value);
            } catch (final NumberFormatException e) {
                throw new ParamParseException("Could not parse fixed concurrency: " + value, e);
            }
            try {
                return new Fixed(concurrency);
            } catch (final IllegalArgumentException e) {
                throw new ParamParseException(e.getMessage());
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    public static final class CpuRelative extends Concurrency {
        public static final double MIN = Config.getDouble("Concurrency.CpuRelative.MIN");
        public static final double MAX = Config.getDouble("Concurrency.CpuRelative.MAX");

        public final double factor;

        private CpuRelative(final double factor) {
            if (!Double.isFinite(factor)) {
                throw new IllegalArgumentException("CPU relative concurrency factor needs to be a finite number, got: " + factor);
            }
            if (factor < MIN) {
                throw new IllegalArgumentException("CPU relative concurrency factor cannot be lower than " + MIN + ", got: " + factor);
            }
            if (factor > MAX) {
                throw new IllegalArgumentException("CPU relative concurrency factor cannot be higher than " + MAX + ", got: " + factor);
            }
            this.factor = factor;
        }

        public CpuRelative withFactor(final double factor) {
            return factor == this.factor ? this : new CpuRelative(factor);
        }

        @Override
        public int getConcurrency() {
            final int processors = Runtime.getRuntime().availableProcessors();
            final int calculated = (int) Math.round(factor * processors);
            return Math.max(Fixed.MIN, Math.min(calculated, Fixed.MAX));
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CpuRelative)) {
                return false;
            }
            final CpuRelative that = (CpuRelative) obj;
            return that.factor == factor; // NaN-safe as only finite values are allowed in constructor
        }

        @Override
        public int hashCode() {
            return Double.hashCode(factor);
        }

        @Override
        public String toString() {
            return "Concurrency.CpuRelative(" + factor + ')';
        }

        static CpuRelative parseString(final String value) throws ParamParseException {
            final Pattern pattern = Pattern.compile("(?i)^cpu(?: *\\* *(\\d+(?:\\.\\d+)?))?$");
            final Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                final double factor;
                final String multiplier = matcher.group(1);
                if (multiplier == null) {
                    factor = 1.0;
                } else {
                    try {
                       factor = Double.parseDouble(multiplier);
                    } catch (final NumberFormatException e) {
                        throw new ParamParseException("Could not parse CPU-relative concurrency: " + value, e);
                    }
                }
                try {
                    return new CpuRelative(factor);
                } catch (final IllegalArgumentException e) {
                    throw new ParamParseException(e.getMessage());
                }
            } else {
                throw new ParamParseException("Could not parse CPU-relative concurrency: " + value);
            }
        }
    }

    // #################################################################################################################

    public static final Concurrency DEFAULT;
    static {
        try {
            DEFAULT = parseString(Config.getString("Concurrency.DEFAULT"));
        } catch (final ParamParseException e) {
            throw new RuntimeException(e);
        }
    }

    static Concurrency parseString(final String value) throws ParamParseException {
        if (value.toLowerCase(Locale.ROOT).contains("cpu")) {
            return CpuRelative.parseString(value);
        }
        return Fixed.parseString(value);
    }
}
