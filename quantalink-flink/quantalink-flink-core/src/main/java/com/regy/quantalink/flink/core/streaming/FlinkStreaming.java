package com.regy.quantalink.flink.core.streaming;

import com.regy.quantalink.common.config.Configuration;
import com.regy.quantalink.common.config.ConfigurationUtils;
import com.regy.quantalink.common.exception.ErrCode;
import com.regy.quantalink.common.exception.FlinkException;
import com.regy.quantalink.flink.core.config.FlinkOptions;
import com.regy.quantalink.flink.core.connector.ConnectorUtils;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * @author regy
 */
public abstract class FlinkStreaming {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkStreaming.class);

    protected FlinkStreamingContext context;

    protected void init(String[] args) {
        try {
            String path = Optional.ofNullable(ParameterTool.fromArgs(args).get("conf"))
                    .orElse(Objects.requireNonNull(this.getClass().getClassLoader().getResource("application.yml")).getPath());
            Configuration config = ConfigurationUtils.loadYamlConfigFromPath(path);
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

            this.context = new FlinkStreamingContext.Builder()
                    .withEnv(env)
                    .withConfig(config)
                    .withSourceConnectors(ConnectorUtils.initSourceConnectors(env, config))
                    .withSinkConnectors(ConnectorUtils.initSinkConnectors(env, config))
                    .build();
        } catch (NullPointerException e) {
            throw new FlinkException(ErrCode.MISSING_CONFIG_FILE, "Failed to initialize application, configuration files must be provided");
        }
    }

    protected void config(FlinkStreamingInitializer initializer) throws FlinkException {
        initializer.init(context);
    }

    protected abstract void execute(FlinkStreamingContext context) throws FlinkException;

    protected void terminate() throws FlinkException {
    }

    protected void run(String[] args, FlinkStreamingInitializer... initializer) throws Exception {
        LOG.info("[QuantaLink]: Initializing the Flink job");
        init(args);

        if (initializer.length > 0) {
            LOG.info("[QuantaLink]: Setting configuration of Flink job");
            config(initializer[0]);
        }

        LOG.info("[QuantaLink]: Executing the Flink job");
        execute(context);

        LOG.info("[QuantaLink]: Running the Flink job");
        JobExecutionResult executionRes = context.getEnv().execute(context.getConfigOption(FlinkOptions.JOB_NAME));

        LOG.info("[QuantaLink]: Terminating the Flink job");
        terminate();

        LOG.info("[QuantaLink]: Flink job finished with result: {}", executionRes);
    }
}

