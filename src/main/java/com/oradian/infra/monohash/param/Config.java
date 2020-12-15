package com.oradian.infra.monohash.param;

import java.io.IOException;
import java.util.Properties;

abstract class Config {
    private Config() {}

    public static String getString(final String key) {
        return defaults.getProperty(key);
    }

    public static int getInt(final String key) {
        return Integer.parseInt(getString(key));
    }

    public static double getDouble(final String key) {
        return Double.parseDouble(getString(key));
    }

    private static final Properties defaults;
    static {
        try {
            defaults = new Properties();
            defaults.load(Config.class.getResourceAsStream("monohash.properties"));
        } catch (final IOException e) {
            throw new RuntimeException("Cannot load defaults from 'monohash.properties'", e);
        }
    }
}
