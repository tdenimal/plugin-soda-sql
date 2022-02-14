package io.kestra.plugin.sodasql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tasks.scripts.AbstractLogThread;
import io.kestra.core.tasks.scripts.AbstractPython;
import io.kestra.core.utils.IdUtils;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPythonSodasql extends AbstractPython {
    transient static final protected ObjectMapper MAPPER = JacksonMapper.ofJson()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    transient static final TypeReference<Map<String, String>> TYPE_REFERENCE = new TypeReference<>() {};

    @Schema(
        title = "Override default pip packages to use a specific version"
    )
    @PluginProperty(dynamic = true)
    protected List<String> pipPackages;


    abstract public Map<String, Object> configuration(RunContext runContext) throws IllegalVariableEvaluationException, IOException;

    abstract public List<String> pipPackages();

    @Schema(
        title = "Override default soda-sql command"
    )
    @PluginProperty(dynamic = true)
    protected String command;

    abstract protected String command();

    protected String finalCommand(RunContext runContext) throws IllegalVariableEvaluationException {
        return this.command != null ? runContext.render(this.command) : this.command();
    }

    protected void initVirtualEnv(RunContext runContext, Logger logger) throws Exception {
        this.setupVirtualEnv(logger, runContext, this.pipPackages != null ? runContext.render(this.pipPackages) : this.pipPackages());
    }


    protected void setupVirtualEnv(Logger logger, RunContext runContext, List<String> requirements) throws Exception {
        ArrayList<String> finalRequirements = new ArrayList<>(requirements);
        finalRequirements.add("python-json-logger");

        this.run(
            runContext,
            logger,
            workingDirectory,
            this.finalCommandsWithInterpreter(this.virtualEnvCommand(runContext, finalRequirements)),
            ImmutableMap.of(),
            this.logThreadSupplier(logger, null)
        );
    }



    protected Map<String, String> environnementVariable(RunContext runContext) throws IllegalVariableEvaluationException, IOException {
        return ImmutableMap.of("LOGGING_CONF_FILE", "logging.conf");
    }



    protected LogSupplier logThreadSupplier(Logger logger, Consumer<String> consumer) {
        return (inputStream, isStdErr) -> {
            AbstractLogThread thread;
            if (isStdErr || consumer == null) {
                thread = new sodasqlLogParser(inputStream, logger);
                thread.setName("sodasql-log-err");
            } else {
                thread = new SodasqlLogSync(inputStream, consumer);
                thread.setName("sodasql-log-out");
            }

            thread.start();

            return thread;
        };
    }

    public static class SodasqlLogSync extends AbstractLogThread {
        private final Consumer<String> consumer;

        public SodasqlLogSync(InputStream inputStream, Consumer<String> consumer) {
            super(inputStream);
            this.consumer = consumer;
        }

        @Override
        protected void call(String line) {
            this.consumer.accept(line);
        }
    }

    protected static class sodasqlLogParser extends AbstractLogThread {
        private final Logger logger;

        public sodasqlLogParser(InputStream inputStream, Logger logger) {
            super(inputStream);
            this.logger = logger;
        }

        @Override
        protected void call(String line) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> jsonLog = (Map<String, String>) MAPPER.readValue(line, Object.class);

                HashMap<String, String> additional = new HashMap<>(jsonLog);
                additional.remove("asctime");
                additional.remove("name");
                additional.remove("message");
                additional.remove("levelname");

                String format = "[Date: {}] [Name: {}] {}{}";
                String[] args = new String[]{
                    jsonLog.get("asctime"),
                    jsonLog.get("name"),
                    jsonLog.get("message") != null ? jsonLog.get("message") + " " : "",
                    additional.size() > 0 ? additional.toString() : ""
                };

                switch (jsonLog.get("levelname")) {
                    case "DEBUG":
                        logger.debug(format, (Object[]) args);
                        break;
                    case "INFO":
                        logger.info(format, (Object[]) args);
                        break;
                    case "WARNING":
                        logger.warn(format, (Object[]) args);
                        break;
                    default:
                        logger.error(format, (Object[]) args);
                }
            } catch (JsonProcessingException e) {
                logger.info(line.trim());
            }
        }
    }

}