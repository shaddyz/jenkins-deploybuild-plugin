package deploybuild;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import hudson.model.Descriptor;
import hudson.Extension;


import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
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
    private final String deployFiles;

    /**
     * If true, archive deploys for all successful builds, otherwise only the most recent.
     */
    private final boolean successOnly;

    /**
     * The name of the file which will be used as the wrapper index.
     */
    private final String wrapperName = "htmlpublisher-wrapper.html";

    @DataBoundConstructor
    public DeployTarget(String deployName, String deployDir, String deployFiles, boolean successOnly) {
        this.deployName = deployName;
        this.deployDir = deployDir;
        this.deployFiles = deployFiles;
        this.successOnly = successOnly;
    }

    public String getDeployName() {
        return this.deployName;
    }

    public String getDeployDir() {
        return this.deployDir;
    }

    public String getDeployFiles() {
        return this.deployFiles;
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

    public FilePath getArchiveTarget(AbstractBuild build) {
        return new FilePath(this.successOnly ? getBuildArchiveDir(build) : getProjectArchiveDir(build.getProject()));
    }

    /**
     * Gets the directory where the HTML deploy is stored for the given project.
     */
    private File getProjectArchiveDir(AbstractItem project) {
        return new File(new File(project.getRootDir(), "deploys"), this.getSanitizedName());
    }
    /**
     * Gets the directory where the HTML deploy is stored for the given build.
     */
    private File getBuildArchiveDir(Run run) {
        return new File(new File(run.getRootDir(), "deploys"), this.getSanitizedName());
    }

    public class ScriptAction implements ProminentProjectAction {
        private AbstractBuild<?, ?> build;
        private DeployTarget target;

        public ScriptAction(AbstractBuild<?, ?> build, DeployTarget deployTarget) {
            this.build = build;
            this.target = deployTarget;
        }
        
        public String getUrlName() {
            return this.target.getSanitizedName();
        }

        public String getDisplayName() {
            return this.target.deployName;
        }

        public String getIconFileName() {
            return "redo.gif";
        }
    }

    public void handleAction(AbstractBuild<?, ?> build) {
        // Add build action, if coverage is recorded for each build
        if (this.successOnly) {
            build.addAction(new ScriptAction(build, this));
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DeployTarget> {
        public String getDisplayName() { return ""; }
    }
}
