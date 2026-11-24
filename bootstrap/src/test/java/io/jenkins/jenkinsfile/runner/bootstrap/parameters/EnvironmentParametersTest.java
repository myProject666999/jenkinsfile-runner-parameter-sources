package io.jenkins.jenkinsfile.runner.bootstrap.parameters;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests {@link EnvironmentParameters} with in-memory maps, never the real environment.
 */
public class EnvironmentParametersTest {

    @Test
    public void extractsPrefixedVariablesAndStripsPrefix() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("JFR_ARG_FOO", "bar");
        env.put("JFR_ARG_BAZ", "qux");
        Map<String, String> params = EnvironmentParameters.parse(env);
        assertThat(params.size(), equalTo(2));
        assertThat(params.get("FOO"), equalTo("bar"));
        assertThat(params.get("BAZ"), equalTo("qux"));
    }

    @Test
    public void ignoresVariablesWithoutPrefix() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("FOO", "1");
        env.put("JFR_ARGX_FOO", "2");
        env.put("jfr_arg_FOO", "3");
        env.put("ARG_FOO", "4");
        assertTrue(EnvironmentParameters.parse(env).isEmpty());
    }

    @Test
    public void parameterNamesAreCaseSensitive() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("JFR_ARG_NAME", "upper");
        env.put("JFR_ARG_name", "lower");
        Map<String, String> params = EnvironmentParameters.parse(env);
        assertThat(params.size(), equalTo(2));
        assertThat(params.get("NAME"), equalTo("upper"));
        assertThat(params.get("name"), equalTo("lower"));
    }

    @Test
    public void nullValueBecomesEmptyString() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("JFR_ARG_EMPTY", null);
        Map<String, String> params = EnvironmentParameters.parse(env);
        assertThat(params.get("EMPTY"), equalTo(""));
    }

    @Test
    public void emptyValueIsKept() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("JFR_ARG_EMPTY", "");
        assertThat(EnvironmentParameters.parse(env).get("EMPTY"), equalTo(""));
    }

    @Test
    public void barePrefixIsAnError() {
        Map<String, String> env = new HashMap<>();
        env.put("JFR_ARG_", "value");
        try {
            EnvironmentParameters.parse(env);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("JFR_ARG_"));
            assertThat(e.getMessage(), containsString("empty parameter name"));
        }
    }

    @Test
    public void nullEnvironmentMeansNoParameters() throws Exception {
        assertTrue(EnvironmentParameters.parse(null).isEmpty());
    }
}
