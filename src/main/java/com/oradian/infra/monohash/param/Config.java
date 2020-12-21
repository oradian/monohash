package com.oradian.infra.monohash.param;

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
    private static Properties loadProperties(final String path) {
        try {
            final Properties props = new Properties();
            props.load(Config.class.getResourceAsStream(path));
            return props;
        } catch (final Exception e) {
            throw new RuntimeException("Cannot load resource properties: " + path, e);
        }
    }
    static {
        defaults = loadProperties("monohash.properties");
    }
}
