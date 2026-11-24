package io.jenkins.jenkinsfile.runner.bootstrap.commands;

import io.jenkins.jenkinsfile.runner.bootstrap.parameters.ParameterSourceException;
import io.jenkins.jenkinsfile.runner.bootstrap.parameters.ResolvedParameters;
import io.jenkins.jenkinsfile.runner.bootstrap.parameters.WorkflowParameterResolver;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import picocli.CommandLine;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
@CommandLine.Command(name="run", description = "Runs Jenkinsfile", mixinStandardHelpOptions = true)
public class RunJenkinsfileCommand extends JenkinsfileCommand {

    @CommandLine.Mixin
    public PipelineRunOptions pipelineRunOptions;

    private static final String WORKSPACES_DIR_SYSTEM_PROPERTY = "jenkins.model.Jenkins.workspacesDir";

    @Override
    public String getAppClassName() {
        return "io.jenkins.jenkinsfile.runner.App";
    }

    public PipelineRunOptions getPipelineRunOptions() {
        return pipelineRunOptions;
    }

    @Override
    public void postConstruct() throws IOException {

        validateJenkinsfileInput(pipelineRunOptions);

        if (pipelineRunOptions.runWorkspace != null){
            if (System.getProperty(WORKSPACES_DIR_SYSTEM_PROPERTY) != null) {
                //TODO(oleg_nenashev): It would have been better to keep it other way, but made it as is to retain compatibility
                System.out.println("Ignoring the --runWorkspace argument, because an explicit System property is set (-D" + WORKSPACES_DIR_SYSTEM_PROPERTY + ")");
            } else {
                System.setProperty(WORKSPACES_DIR_SYSTEM_PROPERTY, pipelineRunOptions.runWorkspace.getAbsolutePath());
            }
        }

        if (!resolveWorkflowParameters(System.getenv(), System.out::println)) {
            System.exit(-1);
        }

        if (pipelineRunOptions.cause != null) {
            pipelineRunOptions.cause = pipelineRunOptions.cause.trim();
            if (pipelineRunOptions.cause.isEmpty())  {
                pipelineRunOptions.cause = null;
            }
        }

        if (pipelineRunOptions.jobName.isEmpty()) {
            pipelineRunOptions.jobName = PipelineRunOptions.DEFAULT_JOBNAME;
        }

        if (pipelineRunOptions.keepUndefinedParameters) {
            System.setProperty("hudson.model.ParametersAction.keepUndefinedParameters", "true");
        }

        super.postConstruct();
    }

    /**
     * Merges parameters from {@code --arg-file}, {@code JFR_ARG_*} environment variables and
     * {@code -a}/{@code --arg} into {@link PipelineRunOptions#workflowParameters}, and records
     * the names that must be created as secret parameters.
     *
     * <p>Fails the run (exit code {@code -1}) when a source cannot be read or the combination
     * is inconsistent. Error and notice messages never contain parameter values.</p>
     *
     * @param environment environment variables, passed explicitly so that it can be substituted in tests
     * @param notices     consumer for override notices
     * @return {@code true} if resolution succeeded, {@code false} if the run must not proceed
     */
    public boolean resolveWorkflowParameters(Map<String, String> environment, Consumer<String> notices) {
        final ResolvedParameters resolved;
        try {
            resolved = WorkflowParameterResolver.resolve(
                    pipelineRunOptions.argumentFile,
                    environment,
                    pipelineRunOptions.workflowParameters,
                    pipelineRunOptions.secretArgNames,
                    notices);
        } catch (ParameterSourceException e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }

        // Keep the legacy post-condition: a null option becomes an empty map.
        pipelineRunOptions.workflowParameters = new LinkedHashMap<>(resolved.getValues());
        pipelineRunOptions.secretParameters = resolved.getSecretNames();
        return true;
    }

}
