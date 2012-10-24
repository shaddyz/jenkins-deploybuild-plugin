/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package deploybuild;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.ProminentProjectAction;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import javax.servlet.ServletException;
import org.acegisecurity.AccessDeniedException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author shaddy
 */
public class ScriptAction implements ProminentProjectAction {
    public AbstractBuild<?, ?> build;
    private DeployTarget target;
    private boolean isLogUpdated;

    public ScriptAction(AbstractBuild<?, ?> build, DeployTarget deployTarget) {
        this.build = build;
        this.target = deployTarget;
        this.isLogUpdated = true;
    }

    public String getUrlName() {
        return this.target.getSanitizedName();
    }

    public String getDisplayName() {
        return this.target.getDeployName();
    }

    public String getIconFileName() {
        return "redo.gif";
    }
    
    public boolean getIsLogUpdated() {
        return this.isLogUpdated;
    }
    
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException, AccessDeniedException {
        this.build.checkPermission(SCM.PERMISSIONS.find("Tag"));
        FilePath workspace = this.target.getArchiveTarget(build);
        TaskListener listener = new StreamTaskListener(rsp.getOutputStream());
        Launcher launcher = workspace.createLauncher(listener);
        CommandInterpreter batchRunner;
        FilePath script = new FilePath(workspace, this.target.getDeployFile());
        String scriptContent = getResolvedContentWithEnvVars(script);
        listener.getLogger().println(String.format("Evaluating the script: \"%s\"", this.target.getDeployFile()));
        FilePath tmpFile;

        if (launcher.isUnix()) {
            batchRunner = new Shell(scriptContent);
        } else {
            batchRunner = new BatchFile(scriptContent);
        }

        tmpFile = batchRunner.createScriptFile(workspace);
        int r = launcher.launch().cmds(batchRunner.buildCommandLine(tmpFile)).stdout(listener).pwd(workspace).join();
        this.isLogUpdated = false;
    }
    
    private String getResolvedContentWithEnvVars(FilePath filePath) throws ServletException {
        String scriptContentResolved;
        try {
            scriptContentResolved =
                    filePath.act(new FilePath.FileCallable<String>() {
                        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                            String scriptContent = Util.loadFile(f);
                            return Util.replaceMacro(scriptContent, EnvVars.masterEnvVars);
                        }
                    });
        } catch (IOException ioe) {
            throw new ServletException("Error to resolve environment variables", ioe);
        } catch (InterruptedException ie) {
            throw new ServletException("Error to resolve environment variables", ie);
        }
        return scriptContentResolved;
    }
}