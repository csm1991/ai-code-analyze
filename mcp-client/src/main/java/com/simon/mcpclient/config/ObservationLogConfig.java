package com.simon.mcpclient.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationLogConfig {

    @Bean
    public ObservationHandler<Observation.Context> observationLoggingHandler() {
        return new ObservationHandler<>() {
            private final Logger logger = LoggerFactory.getLogger("ObservationLogger");

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStart(Observation.Context context) {
                logger.info("[obs:start] name={} contextualName={} lowCardinalityTags={} highCardinalityTags={}",
                        context.getName(), context.getContextualName(),
                        context.getLowCardinalityKeyValues(), context.getHighCardinalityKeyValues());
            }

            @Override
            public void onError(Observation.Context context) {
                Throwable error = context.getError();
                if (error != null) {
                    logger.warn("[obs:error] name={} error={}", context.getName(), error.toString());
                }
            }

            @Override
            public void onStop(Observation.Context context) {
                logger.info("[obs:stop] name={} contextualName={}", context.getName(), context.getContextualName());
            }
        };
    }
}


