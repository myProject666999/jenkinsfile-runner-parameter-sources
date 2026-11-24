package io.jenkins.jenkinsfile.runner.bootstrap;

import io.jenkins.jenkinsfile.runner.bootstrap.commands.PipelineRunOptions;
import java.io.File;
import java.util.Arrays;
import org.junit.Test;
import picocli.CommandLine;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies how picocli 4.7.5 populates the parameter options of {@link Bootstrap}.
 *
 * <p>Only parsing is exercised here: no Jenkins instance is started.</p>
 */
public class ParameterOptionsParsingTest {

    private static PipelineRunOptions parse(String... args) {
        Bootstrap bootstrap = new Bootstrap();
        new CommandLine(bootstrap).parseArgs(args);
        return bootstrap.pipelineRunOptions;
    }

    @Test
    public void repeatedArgLastOneWins() {
        // Requirement: confirm picocli's behavior with a test instead of assuming it.
        PipelineRunOptions options = parse("--arg=K=1", "--arg=K=2", "--arg=K=3");
        assertThat(options.workflowParameters.size(), equalTo(1));
        assertThat(options.workflowParameters.get("K"), equalTo("3"));
    }

    @Test
    public void repeatedArgIsNotAnError() {
        PipelineRunOptions options = parse("-a", "K=1", "-a", "K=2");
        assertThat(options.workflowParameters.get("K"), equalTo("2"));
    }

    @Test
    public void argValueMayContainEquals() {
        PipelineRunOptions options = parse("--arg=K=a=b");
        assertThat(options.workflowParameters.get("K"), equalTo("a=b"));
    }

    @Test
    public void argWithoutEqualsIsRejected() {
        Bootstrap bootstrap = new Bootstrap();
        try {
            new CommandLine(bootstrap).parseArgs("--arg=K");
            org.junit.Assert.fail("Expected a picocli ParameterException");
        } catch (CommandLine.ParameterException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("KEY=VALUE"));
        }
    }

    @Test
    public void argFileOptionIsParsed() {
        PipelineRunOptions options = parse("--arg-file=/tmp/params.properties");
        assertThat(options.argumentFile, equalTo(new File("/tmp/params.properties")));
    }

    @Test
    public void argFileGivenTwiceIsRejected() {
        Bootstrap bootstrap = new Bootstrap();
        try {
            new CommandLine(bootstrap).parseArgs("--arg-file=a", "--arg-file=b");
            org.junit.Assert.fail("Expected a picocli exception");
        } catch (CommandLine.ParameterException expected) {
            // picocli reports OverwrittenOptionException, a ParameterException subclass
            assertTrue(expected.getMessage(), expected.getMessage().contains("--arg-file"));
        }
    }

    @Test
    public void secretArgIsRepeatable() {
        PipelineRunOptions options = parse("--secret-arg=A", "--secret-arg", "B");
        assertThat(options.secretArgNames, equalTo(Arrays.asList("A", "B")));
    }

    @Test
    public void secretArgIsNotSplitOnCommas() {
        // A name containing a comma would be unusual, but silently splitting it into two
        // parameter names would be worse: each switch takes exactly one name.
        PipelineRunOptions options = parse("--secret-arg=A,B");
        assertThat(options.secretArgNames, equalTo(Arrays.asList("A,B")));
    }

    @Test
    public void newOptionsAreAbsentByDefault() {
        PipelineRunOptions options = parse("--arg=A=1");
        assertNull(options.argumentFile);
        assertNull(options.secretArgNames);
        assertTrue(options.secretParameters.isEmpty());
    }
}
