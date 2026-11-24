package io.jenkins.jenkinsfile.runner.bootstrap.parameters;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Final outcome of merging all parameter sources, including the decision which
 * parameters must be treated as secrets.
 *
 * <p>This class is plain data: it does not depend on Jenkins, so the merge rules
 * and the secret-name decision can be tested without starting a Jenkins instance.
 * The payload layer only converts these values into {@code ParameterValue} instances.</p>
 */
public final class ResolvedParameters {

    private final Map<String, String> values;
    private final Set<String> secretNames;

    ResolvedParameters(Map<String, String> values, Set<String> secretNames) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.secretNames = Collections.unmodifiableSet(new LinkedHashSet<>(secretNames));
    }

    /**
     * @return merged values in a stable order (argument file order, then parameters added by
     *         the environment, then parameters added on the command line), never {@code null}
     */
    public Map<String, String> getValues() {
        return values;
    }

    /**
     * @return names marked by {@code --secret-arg} in the order they were declared, never {@code null}
     */
    public Set<String> getSecretNames() {
        return secretNames;
    }

    /**
     * @param name parameter name
     * @return {@code true} if the parameter must be created as a secret parameter
     */
    public boolean isSecret(String name) {
        return secretNames.contains(name);
    }
}
