package io.jenkins.jenkinsfile.runner.bootstrap.parameters;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the merge rules and secret-name decision of {@link WorkflowParameterResolver}.
 *
 * <p>The precedence matrix (command line &gt; environment &gt; argument file) is pinned
 * cell by cell below, so changing a rule requires changing a test on purpose.</p>
 */
public class WorkflowParameterResolverTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File argumentFile(String content) throws IOException {
        File file = tmp.newFile();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static Map<String, String> map(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private List<String> resolveNotices(List<String> notices, File file, Map<String, String> env,
                                        Map<String, String> cli, List<String> secrets) throws Exception {
        WorkflowParameterResolver.resolve(file, env, cli, secrets, notices::add);
        return notices;
    }

    // ---------------------------------------------------------------------
    // The example from the requirements.
    // ---------------------------------------------------------------------

    @Test
    public void documentedExample() throws Exception {
        File file = argumentFile("A=1\nB=2\n");
        Map<String, String> env = map("JFR_ARG_B", "3");
        Map<String, String> cli = map("B", "4");

        List<String> notices = new ArrayList<>();
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                file, env, cli, Arrays.asList("A"), notices::add);

        assertThat(resolved.getValues().get("A"), equalTo("1"));
        assertThat(resolved.getValues().get("B"), equalTo("4"));
        assertTrue("A must be secret", resolved.isSecret("A"));
        assertFalse("B must not be secret", resolved.isSecret("B"));

        assertThat("Expected exactly one notice, got " + notices, notices.size(), equalTo(1));
        String notice = notices.get(0);
        assertThat(notice, containsString("B"));
        assertThat(notice, containsString("command line"));
        // No values of any parameter may appear in the notice.
        for (String value : Arrays.asList("1", "2", "3", "4")) {
            assertThat("Notice must not contain a value: " + notice, notice, not(containsString(value)));
        }
    }

    // ---------------------------------------------------------------------
    // Precedence matrix: every pair of sources, in both directions.
    // ---------------------------------------------------------------------

    @Test
    public void commandLineBeatsEnvironment() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                null, map("JFR_ARG_K", "env"), map("K", "cli"), null, null);
        assertThat(resolved.getValues().get("K"), equalTo("cli"));
    }

    @Test
    public void commandLineBeatsArgumentFile() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                argumentFile("K=file\n"), null, map("K", "cli"), null, null);
        assertThat(resolved.getValues().get("K"), equalTo("cli"));
    }

    @Test
    public void environmentBeatsArgumentFile() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                argumentFile("K=file\n"), map("JFR_ARG_K", "env"), null, null, null);
        assertThat(resolved.getValues().get("K"), equalTo("env"));
    }

    @Test
    public void commandLineBeatsEnvironmentAndArgumentFile() throws Exception {
        List<String> notices = new ArrayList<>();
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                argumentFile("K=file\n"), map("JFR_ARG_K", "env"), map("K", "cli"), null, notices::add);
        assertThat(resolved.getValues().get("K"), equalTo("cli"));
        assertThat("One notice for the winning parameter", notices.size(), equalTo(1));
        assertThat(notices.get(0), containsString("command line"));
        assertThat(notices.get(0), containsString("environment variable"));
        assertThat(notices.get(0), containsString("argument file"));
    }

    @Test
    public void lowerSourcesKeepParametersThatAreNotOverridden() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                argumentFile("FROM_FILE=1\nSHARED=file\n"),
                map("JFR_ARG_FROM_ENV", "2", "JFR_ARG_SHARED", "env"),
                map("SHARED", "cli"),
                null, null);
        assertThat(resolved.getValues().get("FROM_FILE"), equalTo("1"));
        assertThat(resolved.getValues().get("FROM_ENV"), equalTo("2"));
        assertThat(resolved.getValues().get("SHARED"), equalTo("cli"));
    }

    @Test
    public void parameterNamesAreCaseSensitiveAcrossSources() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                argumentFile("name=file\n"), map("JFR_ARG_NAME", "env"), null, null, null);
        assertThat(resolved.getValues().get("name"), equalTo("file"));
        assertThat(resolved.getValues().get("NAME"), equalTo("env"));
    }

    // ---------------------------------------------------------------------
    // Notices
    // ---------------------------------------------------------------------

    @Test
    public void noNoticeWhenNothingIsOverridden() throws Exception {
        List<String> notices = new ArrayList<>();
        WorkflowParameterResolver.resolve(
                argumentFile("A=1\n"), map("JFR_ARG_B", "2"), map("C", "3"), null, notices::add);
        assertThat(notices.size(), equalTo(0));
    }

    @Test
    public void oneNoticePerOverriddenParameter() throws Exception {
        List<String> notices = new ArrayList<>();
        WorkflowParameterResolver.resolve(
                argumentFile("A=1\nB=1\n"),
                map("JFR_ARG_A", "env", "JFR_ARG_B", "env"),
                map("A", "cli", "B", "cli"),
                null, notices::add);
        assertThat(notices.size(), equalTo(2));
    }

    @Test
    public void overrideBySameValueStillNoticesWithoutValues() throws Exception {
        List<String> notices = new ArrayList<>();
        WorkflowParameterResolver.resolve(
                argumentFile("A=same\n"), map("JFR_ARG_A", "same"), null, null, notices::add);
        assertThat(notices.size(), equalTo(1));
        assertThat(notices.get(0), not(containsString("same")));
    }

    // ---------------------------------------------------------------------
    // Secret parameters
    // ---------------------------------------------------------------------

    @Test
    public void secretNameTakesValueFromAnySource() throws Exception {
        ResolvedParameters fromEnv = WorkflowParameterResolver.resolve(
                null, map("JFR_ARG_PWD", "s3cret"), null, Arrays.asList("PWD"), null);
        assertTrue(fromEnv.isSecret("PWD"));
        assertThat(fromEnv.getValues().get("PWD"), equalTo("s3cret"));

        ResolvedParameters fromCli = WorkflowParameterResolver.resolve(
                null, null, map("PWD", "s3cret"), Arrays.asList("PWD"), null);
        assertTrue(fromCli.isSecret("PWD"));

        ResolvedParameters fromFile = WorkflowParameterResolver.resolve(
                argumentFile("PWD=s3cret\n"), null, null, Arrays.asList("PWD"), null);
        assertTrue(fromFile.isSecret("PWD"));
    }

    @Test
    public void secretNameWithoutAnyValueIsAnError() throws Exception {
        try {
            WorkflowParameterResolver.resolve(
                    argumentFile("A=1\n"), null, null, Arrays.asList("PWD"), null);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("PWD"));
            assertThat(e.getMessage(), containsString("--secret-arg"));
        }
    }

    @Test
    public void secretArgCannotCarryAValue() throws Exception {
        try {
            WorkflowParameterResolver.resolve(
                    null, null, map("A", "1"), Arrays.asList("PWD=s3cr3t"), null);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("--secret-arg"));
            // The supplied value must not be echoed back.
            assertThat(e.getMessage(), not(containsString("s3cr3t")));
        }
    }

    @Test
    public void emptySecretNameIsAnError() throws Exception {
        try {
            WorkflowParameterResolver.resolve(null, null, map("A", "1"), Arrays.asList(" "), null);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("--secret-arg"));
        }
    }

    @Test
    public void emptyValueCanBeSecret() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                argumentFile("PWD=\n"), null, null, Arrays.asList("PWD"), null);
        assertTrue(resolved.isSecret("PWD"));
        assertThat(resolved.getValues().get("PWD"), equalTo(""));
    }

    @Test
    public void duplicateSecretNamesAreDeduplicated() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                null, null, map("PWD", "x"), Arrays.asList("PWD", "PWD"), null);
        assertThat(resolved.getSecretNames().size(), equalTo(1));
    }

    // ---------------------------------------------------------------------
    // Edge cases and backward compatibility
    // ---------------------------------------------------------------------

    @Test
    public void emptyParameterNameFromCommandLineIsAnError() throws Exception {
        try {
            WorkflowParameterResolver.resolve(null, null, map("", "value"), null, null);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("Empty parameter name"));
        }
    }

    @Test
    public void nullCommandLineValueBecomesEmptyString() throws Exception {
        Map<String, String> cli = new HashMap<>();
        cli.put("A", null);
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(null, null, cli, null, null);
        assertThat(resolved.getValues().get("A"), equalTo(""));
    }

    @Test
    public void missingArgumentFileFailsEvenWhenOtherSourcesExist() throws Exception {
        File missing = new File(tmp.getRoot(), "missing.properties");
        try {
            WorkflowParameterResolver.resolve(missing, map("JFR_ARG_A", "1"), null, null, null);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("does not exist"));
        }
    }

    @Test
    public void nothingProvidedYieldsEmptyResult() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(null, null, null, null, null);
        assertTrue(resolved.getValues().isEmpty());
        assertTrue(resolved.getSecretNames().isEmpty());
    }

    @Test
    public void commandLineOrderIsStableAndFileOrderComesFirst() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                argumentFile("F1=1\nF2=2\n"), map("JFR_ARG_E1", "3"), map("C1", "4"), null, null);
        assertThat(new ArrayList<>(resolved.getValues().keySet()),
                equalTo(Arrays.asList("F1", "F2", "E1", "C1")));
    }

    @Test
    public void resolvedParametersAreImmutable() throws Exception {
        ResolvedParameters resolved = WorkflowParameterResolver.resolve(
                null, null, map("A", "1"), null, null);
        try {
            resolved.getValues().put("B", "2");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}
