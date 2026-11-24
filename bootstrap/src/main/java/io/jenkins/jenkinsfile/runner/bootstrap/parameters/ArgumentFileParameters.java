package io.jenkins.jenkinsfile.runner.bootstrap.parameters;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads parameters from a UTF-8 text argument file.
 *
 * <p>The format is one {@code key=value} pair per line:</p>
 * <ul>
 *   <li>blank lines (or lines containing only whitespace) are ignored;</li>
 *   <li>lines starting with {@code #} (ignoring leading whitespace) are ignored;</li>
 *   <li>the first {@code =} separates the name from the value, so values may contain {@code =};</li>
 *   <li>the name is trimmed, the value is taken verbatim (including leading and trailing spaces);</li>
 *   <li>a leading UTF-8 byte order mark is ignored.</li>
 * </ul>
 *
 * <p>A missing or unreadable file is an error, never silently ignored. A line without
 * {@code =}, an empty parameter name and a name repeated within the same file are errors
 * too; the error message includes the line number(s) but never a value.</p>
 */
public final class ArgumentFileParameters {

    private ArgumentFileParameters() {
    }

    /**
     * Parses the given argument file.
     *
     * @param file argument file, {@code null} means "no file was given"
     * @return parameters in file order, never {@code null}
     * @throws ParameterSourceException if the file does not exist, cannot be read or is malformed
     */
    public static Map<String, String> parse(@CheckForNull File file) throws ParameterSourceException {
        if (file == null) {
            return Collections.emptyMap();
        }
        if (!file.exists()) {
            throw new ParameterSourceException("Argument file does not exist: " + file.getAbsolutePath());
        }
        if (file.isDirectory()) {
            throw new ParameterSourceException("Argument file is a directory: " + file.getAbsolutePath());
        }

        final List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ParameterSourceException(
                    "Cannot read argument file '" + file.getAbsolutePath() + "': " + e.getMessage(), e);
        }

        final String path = file.getAbsolutePath();
        final Map<String, String> parameters = new LinkedHashMap<>();
        final Map<String, Integer> nameLines = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            final int lineNumber = i + 1;
            String line = lines.get(i);
            if (i == 0 && !line.isEmpty() && line.charAt(0) == '﻿') {
                line = line.substring(1);
            }
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.trim().charAt(0) == '#') {
                continue;
            }

            final int separator = line.indexOf('=');
            if (separator < 0) {
                throw new ParameterSourceException("Line " + lineNumber + " of argument file '" + path
                        + "' is not in key=value format");
            }
            final String name = line.substring(0, separator).trim();
            final String value = line.substring(separator + 1);
            if (name.isEmpty()) {
                throw new ParameterSourceException(
                        "Line " + lineNumber + " of argument file '" + path + "' has an empty parameter name");
            }
            final Integer previousLine = nameLines.get(name);
            if (previousLine != null) {
                throw new ParameterSourceException("Duplicate parameter '" + name + "' in argument file '" + path
                        + "' at lines " + previousLine + " and " + lineNumber);
            }
            nameLines.put(name, lineNumber);
            parameters.put(name, value);
        }
        return parameters;
    }
}
