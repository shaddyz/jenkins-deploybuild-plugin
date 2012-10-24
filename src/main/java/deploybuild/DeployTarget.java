package deploybuild;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Run;
import hudson.model.Descriptor;
import hudson.Extension;


import java.io.File;


import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A representation of an HTML directory to archive and publish.
 *
 * @author Mike Rooney
 *
 */
public class DeployTarget extends AbstractDescribableImpl<DeployTarget> {
    /**
     * The name of the deploy to display for the build/project, such as "Code Coverage"
     */
    private final String deployName;

    /**
     * The path to the HTML deploy directory relative to the workspace.
     */
    private final String deployDir;

    /**
     * The file[s] to provide links inside the deploy directory.
     */
    private final String deployFile;

    /**
     * If true, archive deploys for all successful builds, otherwise only the most recent.
     */
    private final boolean successOnly;

    /**
     * The name of the file which will be used as the wrapper index.
     */
    private final String wrapperName = "htmlpublisher-wrapper.html";

    @DataBoundConstructor
    public DeployTarget(String deployName, String deployDir, String deployFile, boolean successOnly) {
        this.deployName = deployName;
        this.deployDir = deployDir;
        this.deployFile = deployFile;
        this.successOnly = successOnly;
    }

    public String getDeployName() {
        return this.deployName;
    }

    public String getDeployDir() {
        return this.deployDir;
    }

    public String getDeployFile() {
        return this.deployFile;
    }

    public boolean getSuccessOnly() {
        return this.successOnly;
    }

    public String getSanitizedName() {
        String safeName = this.deployName;
        safeName = safeName.replace(" ", "_");
        return safeName;
    }

    public String getWrapperName() {
        return this.wrapperName;
    }

    public void handleAction(AbstractBuild<?, ?> build) {
        build.addAction(new ScriptAction(build, this));
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DeployTarget> {
        public String getDisplayName() { return ""; }
    }
}
