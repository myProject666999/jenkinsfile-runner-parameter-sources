package io.jenkins.jenkinsfile.runner.bootstrap.commands;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import picocli.CommandLine;

// TODO: Split Pipeline and generic options?
/**
 * Contains options required for a Jenkins build run.
 */
public class PipelineRunOptions extends PipelineOptions {

    /**package**/ static final String DEFAULT_JOBNAME = "job";

    /**
     * Workspace for the Run
     */
    @CheckForNull
    @CommandLine.Option(names = "--runWorkspace",
            description = "Path to the workspace of the run to be used within the node{} context. " +
                    "It applies to both Jenkins master and agents (or side containers) if any. " +
                    "Requires Jenkins 2.119 or above")
    public File runWorkspace;



    /**
     * Job name for the Run.
     */
    @CommandLine.Option(names = { "-n", "--job-name"},
            description = "Name of the job the run belongs to")
    public String jobName = DEFAULT_JOBNAME;

    /**
     * Cause of the Run.
     */
    @CommandLine.Option(names = { "-c", "--cause"},
            description = "Cause of the run")
    public String cause;

    /**
     * BuildNumber of the Run.
     */
    @CommandLine.Option(names = { "-b", "--build-number"},
            description = "Build number of the run")
    public int buildNumber = 1;

    @CommandLine.Option(names = { "-a", "--arg" },
            description = "Parameters to be passed to the build. Use multiple -a switches for multiple params. " +
                    "Takes precedence over JFR_ARG_* environment variables and --arg-file")
    @CheckForNull
    public Map<String,String> workflowParameters;

    @CommandLine.Option(names = "--arg-file",
            description = "UTF-8 file with one key=value parameter per line. Blank lines and lines starting " +
                    "with # are ignored, values may contain '='. Lower precedence than JFR_ARG_* environment " +
                    "variables and --arg")
    @CheckForNull
    public File argumentFile;

    @CommandLine.Option(names = "--secret-arg", split = "",
            description = "Name of a parameter that must be treated as a secret and exposed as a " +
                    "PasswordParameterValue. Repeatable. The value is taken from --arg, --arg-file or a " +
                    "JFR_ARG_* environment variable; it cannot be given here")
    @CheckForNull
    public List<String> secretArgNames;

    /**
     * Names of parameters to be created as secret parameters.
     * Computed by {@code RunJenkinsfileCommand#postConstruct()} from {@link #secretArgNames},
     * so that the payload layer does not have to apply the matching rules again.
     */
    public Set<String> secretParameters = Collections.emptySet();

    @CommandLine.Option(names = { "-ns", "--no-sandbox" },
            description = "Disable workflow job execution within sandbox environment")
    public boolean noSandBox;

    @CommandLine.Option(names = { "-u", "--keep-undefined-parameters" },
            description = "Keep undefined parameters if set")
    public boolean keepUndefinedParameters = false;

    @CommandLine.Option(names = "--scm",
            description = "YAML definition of the SCM, with optional credentials, to use for the project")
    public File scm;

    @CommandLine.Option(names = { "-jte", "--jenkins-templating-engine" },
            description = "Use the Jenkins Templating Engine for the build")
    public boolean isJTE = false;

    @CommandLine.Option(names = { "-pc", "--pipeline-configuration" },
            description = "The Pipeline Configuration File when using the Jenkins Templating Engine")
    public File pipelineConfiguration;

    @CommandLine.Option(names = { "-nbl", "--no-build-logs" },
            description = "Disable writing build logs to stdout. " +
                    "Plugins that handle build logs will process them as usual")
    public boolean noBuildLogs = false;

    @CommandLine.Option(names = { "-wlit", "--write-log-init-timeout" },
            description = "Initializing build log forwarding to stdout may fail if the build log has not been created yet. " +
                    " This option defines the maximum time (in seconds) to wait for build log creation." +
                    " Defaults to 1 second.")
    public int writeLogInitTimeoutSeconds = 1;
}
