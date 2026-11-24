package io.jenkins.jenkinsfile.runner;

import hudson.model.ParameterValue;
import hudson.model.PasswordParameterValue;
import hudson.model.StringParameterValue;
import hudson.util.Secret;
import io.jenkins.jenkinsfile.runner.bootstrap.commands.PipelineRunOptions;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Verifies the thin conversion from resolved parameters to Jenkins parameter values.
 *
 * <p>This does not start Jenkins: {@link PasswordParameterValue} only wraps the value into a
 * {@link Secret}, and {@link Secret#fromString(String)} falls back to an in-memory key store
 * when no Jenkins instance is available.</p>
 */
public class RunnerParameterValuesTest {

    private static List<ParameterValue> convert(Map<String, String> values, Iterable<String> secretNames) {
        PipelineRunOptions options = new PipelineRunOptions();
        options.workflowParameters = new LinkedHashMap<>(values);
        options.secretParameters = new LinkedHashSet<>();
        secretNames.forEach(options.secretParameters::add);
        return Runner.createParameterValues(options);
    }

    @Test
    public void secretParametersBecomePasswordParameterValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("USER", "alice");
        values.put("PWD", "s3cr3t");

        List<ParameterValue> result = convert(values, List.of("PWD"));

        assertThat(result.size(), equalTo(2));

        ParameterValue user = result.get(0);
        assertThat(user.getName(), equalTo("USER"));
        assertThat(user, instanceOf(StringParameterValue.class));
        assertThat(((StringParameterValue) user).getValue(), equalTo("alice"));

        ParameterValue pwd = result.get(1);
        assertThat(pwd.getName(), equalTo("PWD"));
        assertThat(pwd, instanceOf(PasswordParameterValue.class));
        assertThat(pwd.isSensitive(), equalTo(true));
    }

    @Test
    public void passwordValueRoundTripsThePlainText() {
        List<ParameterValue> result = convert(Map.of("PWD", "s3cr3t"), List.of("PWD"));
        PasswordParameterValue pwd = (PasswordParameterValue) result.get(0);
        assertThat(pwd.getValue().getPlainText(), equalTo("s3cr3t"));
        // getShortDescription is what Jenkins may print in summaries; it must not contain the value.
        assertThat(pwd.getShortDescription(), equalTo("PWD=****"));
    }

    @Test
    public void emptySecretValueIsSupported() {
        List<ParameterValue> result = convert(Map.of("PWD", ""), List.of("PWD"));
        PasswordParameterValue pwd = (PasswordParameterValue) result.get(0);
        assertThat(pwd.getValue().getPlainText(), equalTo(""));
        assertThat(pwd.isSensitive(), equalTo(true));
    }

    @Test
    public void nonSecretParametersAreNormalStrings() {
        List<ParameterValue> result = convert(Map.of("A", "1"), List.of());
        assertThat(result.get(0), instanceOf(StringParameterValue.class));
        assertThat(result.get(0).isSensitive(), equalTo(false));
    }
}
