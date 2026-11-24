package io.jenkins.jenkinsfile.runner.bootstrap.parameters;

/**
 * Signals that pipeline parameters could not be read or merged.
 *
 * <p>The message is intended to be shown to the user. Implementations must not
 * include parameter values, only names, source descriptions and line numbers.</p>
 */
public class ParameterSourceException extends Exception {

    private static final long serialVersionUID = 1L;

    public ParameterSourceException(String message) {
        super(message);
    }

    public ParameterSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
