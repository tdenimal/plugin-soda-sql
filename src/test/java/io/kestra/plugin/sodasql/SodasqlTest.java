package io.kestra.plugin.sodasql;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@MicronautTest
class SodasqlTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of("variable", "John Doe"));

        AbstractPythonSodasql task = AbstractPythonSodasql.builder()
            .format("Hello {{ variable }}")
            .build();

        AbstractPythonSodasql.Output runOutput = task.run(runContext);

        assertThat(runOutput.getChild().getValue(), is(StringUtils.reverse("Hello John Doe")));
    }
}
