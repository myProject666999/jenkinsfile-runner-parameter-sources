package io.jenkins.jenkinsfile.runner.bootstrap.commands;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link RunJenkinsfileCommand#resolveWorkflowParameters(Map, java.util.function.Consumer)}.
 *
 * <p>The environment is supplied as a map, so the real process environment is never read.</p>
 */
public class RunJenkinsfileCommandParametersTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static RunJenkinsfileCommand command(PipelineRunOptions options) {
        RunJenkinsfileCommand command = new RunJenkinsfileCommand();
        command.pipelineRunOptions = options;
        return command;
    }

    @Test
    public void backwardCompatibleWhenNoNewOptionsAreUsed() {
        PipelineRunOptions options = new PipelineRunOptions();
        options.workflowParameters = new LinkedHashMap<>();
        options.workflowParameters.put("A", "1");
        options.jenkinsfile = new File("Jenkinsfile");

        RunJenkinsfileCommand command = command(options);
        assertTrue(command.resolveWorkflowParameters(new HashMap<>(), notice -> { }));

        assertThat(options.workflowParameters.size(), equalTo(1));
        assertThat(options.workflowParameters.get("A"), equalTo("1"));
        assertTrue(options.secretParameters.isEmpty());
    }

    @Test
    public void nullParametersBecomeEmptyMap() {
        PipelineRunOptions options = new PipelineRunOptions();
        options.workflowParameters = null;

        RunJenkinsfileCommand command = command(options);
        assertTrue(command.resolveWorkflowParameters(new HashMap<>(), notice -> { }));

        assertNotNull("postConstruct must replace null with an empty map", options.workflowParameters);
        assertTrue(options.workflowParameters.isEmpty());
    }

    @Test
    public void mergesFileEnvironmentAndCommandLine() throws IOException {
        File file = tmp.newFile();
        Files.write(file.toPath(), "A=1\nB=2\n".getBytes(StandardCharsets.UTF_8));

        PipelineRunOptions options = new PipelineRunOptions();
        options.argumentFile = file;
        options.workflowParameters = new LinkedHashMap<>();
        options.workflowParameters.put("B", "4");
        options.secretArgNames = new ArrayList<>();
        options.secretArgNames.add("A");

        Map<String, String> env = new HashMap<>();
        env.put("JFR_ARG_B", "3");

        List<String> notices = new ArrayList<>();
        RunJenkinsfileCommand command = command(options);
        assertTrue(command.resolveWorkflowParameters(env, notices::add));

        assertThat(options.workflowParameters.get("A"), equalTo("1"));
        assertThat(options.workflowParameters.get("B"), equalTo("4"));
        assertTrue(options.secretParameters.contains("A"));
        assertFalse(options.secretParameters.contains("B"));

        assertThat(notices.size(), equalTo(1));
        assertThat(notices.get(0), containsString("B"));
        assertThat(notices.get(0), not(containsString("4")));
        assertThat(notices.get(0), not(containsString("3")));
    }

    @Test
    public void missingArgumentFileFailsResolution() {
        PipelineRunOptions options = new PipelineRunOptions();
        options.argumentFile = new File(tmp.getRoot(), "absent.properties");
        options.workflowParameters = new LinkedHashMap<>();

        RunJenkinsfileCommand command = command(options);
        assertFalse("Resolution must fail so that the run is aborted",
                command.resolveWorkflowParameters(new HashMap<>(), notice -> { }));
    }

    @Test
    public void secretNameWithoutValueFailsResolution() {
        PipelineRunOptions options = new PipelineRunOptions();
        options.workflowParameters = new LinkedHashMap<>();
        options.workflowParameters.put("A", "1");
        options.secretArgNames = new ArrayList<>();
        options.secretArgNames.add("TYPO");

        RunJenkinsfileCommand command = command(options);
        assertFalse(command.resolveWorkflowParameters(new HashMap<>(), notice -> { }));
    }
}
