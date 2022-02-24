package io.kestra.plugin.sodasql;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.scripts.AbstractPython;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPythonSodasql extends AbstractPython {
    

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

        this.run(
            runContext,
            logger,
            workingDirectory,
            this.finalCommandsWithInterpreter(this.virtualEnvCommand(runContext, finalRequirements)),
            ImmutableMap.of(),
            null
        );
    }



    protected Map<String, String> environnementVariable(RunContext runContext) throws IllegalVariableEvaluationException, IOException {
        return ImmutableMap.of("LOGGING_CONF_FILE", "logging.conf");
    }



    public void init(RunContext runContext, Logger logger) throws Exception {
        if (this.workingDirectory == null) {
            this.workingDirectory = runContext.tempDir();
        }

        this.initVirtualEnv(runContext, logger);
    }

    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        // prepare
        this.init(runContext, logger);

        runContext.logger().info("Ended singer ");

        // outputs
        Output.OutputBuilder builder = Output.builder();

        return builder.build();
    }



    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The reverse string "
        )
        private final String reverse;
    }

}