package com.oradian.infra.monohash;

import com.oradian.infra.monohash.impl.NoopLogger;
import com.oradian.infra.monohash.param.Algorithm;
import com.oradian.infra.monohash.param.Concurrency;
import com.oradian.infra.monohash.param.Verification;
import com.oradian.infra.monohash.util.Format;

import java.io.File;
import java.util.Objects;

public class MonoHashBuilder {
    public final Logger logger;
    public final Algorithm algorithm;
    public final Concurrency concurrency;
    public final Verification verification;
    public final File export;

    private MonoHashBuilder(
            final Logger logger,
            final Algorithm algorithm,
            final Concurrency concurrency,
            final Verification verification,
            final File export) {
        this.logger = logger;
        this.algorithm = algorithm;
        this.concurrency = concurrency;
        this.verification = verification;
        this.export = export;
    }

    static final MonoHashBuilder DEFAULT =
            new MonoHashBuilder(NoopLogger.INSTANCE, Algorithm.DEFAULT, Concurrency.DEFAULT, Verification.DEFAULT, null);

    public class Ready extends MonoHashBuilder {
        public final File hashPlan;

        private Ready(
                final Logger logger,
                final Algorithm algorithm,
                final Concurrency concurrency,
                final Verification verification,
                final File hashPlan,
                final File export) {
            super(logger, algorithm, concurrency, verification, export);
            this.hashPlan = hashPlan;
        }

        @Override
        public Ready withLogger(final Logger logger) {
            return logger == this.logger ? this : super.withLogger(logger).withHashPlan(hashPlan);
        }

        @Override
        public Ready withAlgorithm(final Algorithm algorithm) {
            return algorithm == this.algorithm ? this : super.withAlgorithm(algorithm).withHashPlan(hashPlan);
        }

        @Override
        public Ready withConcurrency(final Concurrency concurrency) {
            return concurrency == this.concurrency ? this : super.withConcurrency(concurrency).withHashPlan(hashPlan);
        }

        @Override
        public Ready withVerification(final Verification verification) {
            return verification == this.verification ? this : super.withVerification(verification).withHashPlan(hashPlan);
        }

        @Override
        public Ready withHashPlan(final File hashPlan) {
            return hashPlan == this.hashPlan ? this : super.withHashPlan(hashPlan);
        }

        @Override
        public Ready withExport(final File export) {
            return export == this.export ? this : super.withExport(export).withHashPlan(hashPlan);
        }

        public HashResults run() throws ExitException {
            return MonoHash.run(this);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Ready)) {
                return false;
            }
            final Ready that = (Ready) obj;
            return super.equals(obj) && hashPlan.equals(that.hashPlan);
        }

        @Override
        public int hashCode() {
            return Objects.hash(logger, algorithm, concurrency, verification, hashPlan, export);
        }

        @Override
        public String toString() {
            return "MonoHashBuilder.Ready(logger=" + logger +
                    ", algorithm=" + algorithm +
                    ", concurrency=" + concurrency +
                    ", verification=" + verification +
                    ", hashPlan=" + Format.file(hashPlan) +
                    ", export=" + (export == null ? "null" : Format.file(export)) +
                    ')';
        }
    }

    public MonoHashBuilder withLogger(final Logger logger) {
        return logger == this.logger ? this : new MonoHashBuilder(logger, algorithm, concurrency, verification, export);
    }

    public MonoHashBuilder withAlgorithm(final Algorithm algorithm) {
        return algorithm == this.algorithm ? this : new MonoHashBuilder(logger, algorithm, concurrency, verification, export);
    }

    public MonoHashBuilder withConcurrency(final Concurrency concurrency) {
        return concurrency == this.concurrency ? this : new MonoHashBuilder(logger, algorithm, concurrency, verification, export);
    }

    public MonoHashBuilder withVerification(final Verification verification) {
        return verification == this.verification ? this : new MonoHashBuilder(logger, algorithm, concurrency, verification, export);
    }

    public MonoHashBuilder withExport(final File export) {
        return export == this.export ? this : new MonoHashBuilder(logger, algorithm, concurrency, verification, export);
    }

    public Ready withHashPlan(final File hashPlan) {
        return new Ready(logger, algorithm, concurrency, verification, hashPlan, export);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MonoHashBuilder)) {
            return false;
        }
        final MonoHashBuilder that = (MonoHashBuilder) obj;
        return logger.equals(that.logger) &&
                algorithm.equals(that.algorithm) &&
                concurrency.equals(that.concurrency) &&
                verification == that.verification &&
                Objects.equals(export, that.export);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logger, algorithm, concurrency, verification, export);
    }

    @Override
    public String toString() {
        return "MonoHashBuilder(logger=" + logger +
                ", algorithm=" + algorithm +
                ", concurrency=" + concurrency +
                ", verification=" + verification +
                ", export=" + (export == null ? "null" : Format.file(export)) +
                ')';
    }
}
