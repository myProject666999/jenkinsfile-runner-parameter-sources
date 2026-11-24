package io.jenkins.jenkinsfile.runner.bootstrap.parameters;

/**
 * Sources of pipeline parameters supported by Jenkinsfile Runner.
 *
 * <p>The declaration order is also the precedence order, from the lowest precedence
 * to the highest one: a value from a source declared later overrides a value with
 * the same name from a source declared earlier.</p>
 *
 * @see WorkflowParameterResolver
 */
public enum ParameterSource {

    ARGUMENT_FILE("argument file"),
    ENVIRONMENT_VARIABLE("environment variable"),
    COMMAND_LINE("command line");

    private final String displayName;

    ParameterSource(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Human readable name of the source, used in override notices.
     * It never contains parameter values.
     *
     * @return display name, e.g. {@code command line}
     */
    public String getDisplayName() {
        return displayName;
    }
}
