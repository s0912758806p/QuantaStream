package com.regy.quantalink.flink.core.connector.oracle.config;

import com.regy.quantalink.common.config.ConfigOption;
import com.regy.quantalink.common.config.ConfigOptions;
import com.regy.quantalink.common.config.Configuration;

import com.ververica.cdc.connectors.base.options.StartupMode;

import java.util.List;

/**
 * @author regy
 */
public interface OracleOptions {

    ConfigOption<Configuration> CDC = ConfigOptions.key("oracle-cdc")
            .configType()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<String> HOSTNAME = ConfigOptions.key("hostname")
            .stringType()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<String> USERNAME = ConfigOptions.key("username")
            .stringType()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<String> PASSWORD = ConfigOptions.key("password")
            .stringType()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<String> DATABASE = ConfigOptions.key("database")
            .stringType()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<List<String>> SCHEMA_LIST = ConfigOptions.key("schema-list")
            .stringType()
            .asList()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<List<String>> TABLE_LIST = ConfigOptions.key("table-list")
            .stringType()
            .asList()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<Integer> PORT = ConfigOptions.key("port")
            .intType()
            .defaultValue(1521)
            .withDescription("");

    ConfigOption<String> URL = ConfigOptions.key("url")
            .stringType()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<Configuration> DEBEZIUM_PROPERTIES = ConfigOptions.key("debezium-properties")
            .configType()
            .defaultValue(new Configuration())
            .withDescription("");

    ConfigOption<StartupMode> STARTUP_MODE = ConfigOptions.key("startup-mode")
            .enumType(StartupMode.class)
            .defaultValue(StartupMode.INITIAL)
            .withDescription("");

    ConfigOption<String> SPECIFIC_OFFSET_FILE = ConfigOptions.key("specific-offset-file")
            .stringType()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<Integer> SPECIFIC_OFFSET_POS = ConfigOptions.key("specific-offset-pos")
            .intType()
            .noDefaultValue()
            .withDescription("");

    ConfigOption<Long> STARTUP_TIMESTAMP_MS = ConfigOptions.key("startup-timestamp-ms")
            .longType()
            .noDefaultValue()
            .withDescription("");
}
