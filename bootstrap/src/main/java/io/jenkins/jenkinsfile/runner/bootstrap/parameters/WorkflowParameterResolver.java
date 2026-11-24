package io.jenkins.jenkinsfile.runner.bootstrap.parameters;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Merges pipeline parameters coming from the argument file, the environment and the command line.
 *
 * <p>Precedence, from the lowest to the highest:</p>
 * <ol>
 *   <li>{@code --arg-file}: parameters declared in the file;</li>
 *   <li>environment variables prefixed with {@code JFR_ARG_};</li>
 *   <li>{@code -a}/{@code --arg}: parameters given on the command line.</li>
 * </ol>
 *
 * <p>Rationale: the closer a source is to this particular invocation, the more it should win.
 * A file or image usually ships defaults, deployments adjust them through the environment and
 * one-off invocations override both on the command line.</p>
 *
 * <p>Repeated keys inside the command line are resolved by picocli itself (the last switch wins,
 * which is verified by tests); the argument file rejects duplicates as it is a hand-edited
 * document where a repeat is almost certainly a mistake.</p>
 *
 * <p>Every time a parameter from a lower precedence source is overridden, one notice line is
 * emitted. Notices contain the parameter name and the source names only, never a value.</p>
 *
 * <p>The class is plain Java: environment variables are supplied by the caller and the argument
 * file is the only file system access. It does not depend on Jenkins, so the merge is testable
 * without starting an instance.</p>
 */
public final class WorkflowParameterResolver {

    private WorkflowParameterResolver() {
    }

    /**
     * Resolves parameters. The method returns a plain data object; converting it to
     * Jenkins {@code ParameterValue} instances is the payload layer's job.
     *
     * @param argumentFile  value of {@code --arg-file}, {@code null} if not used
     * @param environment   environment variables, {@code null} is treated as empty
     * @param commandLine   value of {@code -a}/{@code --arg}, {@code null} if not used
     * @param secretNames   values of {@code --secret-arg} in declaration order, {@code null} if not used
     * @param notices       consumer receiving one line per overridden parameter, {@code null} to discard
     * @return merged parameters and the set of secret names
     * @throws ParameterSourceException if a source cannot be read or the combination is inconsistent
     */
    public static ResolvedParameters resolve(
            @CheckForNull File argumentFile,
            @CheckForNull Map<String, String> environment,
            @CheckForNull Map<String, String> commandLine,
            @CheckForNull List<String> secretNames,
            @CheckForNull Consumer<String> notices) throws ParameterSourceException {

        final Consumer<String> sink = notices == null ? ignored -> { } : notices;

        final Map<String, String> fromFile = ArgumentFileParameters.parse(argumentFile);
        final Map<String, String> fromEnvironment = EnvironmentParameters.parse(environment);
        final Map<String, String> fromCommandLine = normalize(commandLine, ParameterSource.COMMAND_LINE);
        final Set<String> secrets = validateSecretNames(secretNames);

        final Map<String, String> merged = new LinkedHashMap<>();
        final Map<String, List<ParameterSource>> definitions = new LinkedHashMap<>();

        apply(merged, definitions, fromFile, ParameterSource.ARGUMENT_FILE);
        apply(merged, definitions, fromEnvironment, ParameterSource.ENVIRONMENT_VARIABLE);
        apply(merged, definitions, fromCommandLine, ParameterSource.COMMAND_LINE);

        for (Map.Entry<String, List<ParameterSource>> entry : definitions.entrySet()) {
            final List<ParameterSource> sources = entry.getValue();
            if (sources.size() < 2) {
                continue;
            }
            final ParameterSource winner = sources.get(sources.size() - 1);
            final List<String> overridden = new ArrayList<>();
            for (ParameterSource source : sources.subList(0, sources.size() - 1)) {
                overridden.add(source.getDisplayName());
            }
            sink.accept("Parameter '" + entry.getKey() + "': using value from " + winner.getDisplayName()
                    + " (overrides " + String.join(", ", overridden) + ")");
        }

        for (String secretName : secrets) {
            if (!merged.containsKey(secretName)) {
                throw new ParameterSourceException("Secret parameter '" + secretName
                        + "' is marked by --secret-arg, but no parameter source provides a value for it");
            }
        }

        return new ResolvedParameters(merged, secrets);
    }

    private static Map<String, String> normalize(
            @CheckForNull Map<String, String> parameters, ParameterSource source) throws ParameterSourceException {
        final Map<String, String> normalized = new LinkedHashMap<>();
        if (parameters == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            final String name = entry.getKey();
            if (name == null || name.isEmpty()) {
                throw new ParameterSourceException(
                        "Empty parameter name provided by " + source.getDisplayName());
            }
            normalized.put(name, Objects.toString(entry.getValue(), ""));
        }
        return normalized;
    }

    private static void apply(Map<String, String> merged,
                              Map<String, List<ParameterSource>> definitions,
                              Map<String, String> parameters,
                              ParameterSource source) {
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            merged.put(entry.getKey(), entry.getValue());
            definitions.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(source);
        }
    }

    private static Set<String> validateSecretNames(@CheckForNull List<String> secretNames)
            throws ParameterSourceException {
        final Set<String> secrets = new LinkedHashSet<>();
        if (secretNames == null) {
            return secrets;
        }
        for (String secretName : secretNames) {
            if (secretName == null || secretName.trim().isEmpty()) {
                throw new ParameterSourceException(
                        "--secret-arg requires a parameter name, but the name is empty");
            }
            if (secretName.indexOf('=') >= 0) {
                // Do not echo the argument: everything after the first '=' may be a secret value.
                throw new ParameterSourceException("--secret-arg takes a parameter name only and cannot carry"
                        + " a value; pass the value via --arg, --arg-file or a JFR_ARG_* environment variable");
            }
            secrets.add(secretName);
        }
        return secrets;
    }
}
