package io.jenkins.jenkinsfile.runner.bootstrap.parameters;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads parameters from environment variables.
 *
 * <p>Every environment variable whose name starts with {@link #PREFIX} defines a parameter.
 * The part after the prefix is the parameter name and is matched case-sensitively.
 * On Windows environment variable names are case-insensitive, so two variables differing
 * only in case cannot coexist there; this class does not compensate for that platform
 * difference and treats the names as given.</p>
 *
 * <p>The map is passed in by the caller so that this logic can be tested without touching
 * the real process environment.</p>
 */
public final class EnvironmentParameters {

    /** Prefix of environment variables that define pipeline parameters. */
    public static final String PREFIX = "JFR_ARG_";

    private EnvironmentParameters() {
    }

    /**
     * Extracts parameters from the given environment.
     *
     * @param environment environment variables, {@code null} is treated as empty
     * @return parameters in iteration order of the given map, never {@code null}
     * @throws ParameterSourceException if a variable has an empty parameter name (e.g. {@code JFR_ARG_})
     */
    public static Map<String, String> parse(@CheckForNull Map<String, String> environment)
            throws ParameterSourceException {
        if (environment == null || environment.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<String, String> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            final String variableName = entry.getKey();
            if (variableName == null || !variableName.startsWith(PREFIX)) {
                continue;
            }
            final String name = variableName.substring(PREFIX.length());
            if (name.isEmpty()) {
                throw new ParameterSourceException(
                        "Environment variable '" + variableName + "' has an empty parameter name");
            }
            final String value = entry.getValue();
            parameters.put(name, value == null ? "" : value);
        }
        return parameters;
    }
}
