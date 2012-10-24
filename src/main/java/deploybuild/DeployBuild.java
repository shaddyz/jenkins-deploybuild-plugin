/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt, Peter Hayes
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package deploybuild;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DeployBuild extends Recorder {
    private final ArrayList<DeployTarget> deployTargets;

    @DataBoundConstructor
    public DeployBuild(List<DeployTarget> deployTargets) {
        this.deployTargets = deployTargets != null ? new ArrayList<DeployTarget>(deployTargets) : new ArrayList<DeployTarget>();
    }
    
    public ArrayList<DeployTarget> getDeployTargets() {
        return this.deployTargets;
    }

    protected static String resolveParametersInString(AbstractBuild<?, ?> build, BuildListener listener, String input) {
        try {
            return build.getEnvironment(listener).expand(input);
        } catch (Exception e) {
            listener.getLogger().println("Failed to resolve parameters in string \""+
            input+"\" due to following error:\n"+e.getMessage());
        }
        return input;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        listener.getLogger().println("[DeployBuild] Checking for recipes...");
        
        for (int i=0; i < this.deployTargets.size(); i++) {
            DeployTarget deployTarget = this.deployTargets.get(i); 

            if (deployTarget.getSuccessOnly() && build.getResult().isWorseThan(Result.SUCCESS)) {
                listener.getLogger().println("[DeployBuild] Build did not succeed. Not enabling \"" + deployTarget.getDeployName() + "\".");
                continue;
            }
            
            FilePath archiveDir = null;
            try {
                FilePath[] matches = build.getWorkspace().list(resolveParametersInString(build, listener, deployTarget.getDeployDir()));
                
                if (matches.length == 0) {
                    listener.error("[DeployBuild] Script directory \"" + deployTarget.getDeployDir() + "\" is empty or does not exist.");
                } else if (matches.length > 1) {
                    listener.error("[DeployBuild] More than one path matched the pattern:");
                    for(FilePath p: matches) {
                        listener.error("- " + p);
                    }
                } else {
                    archiveDir = matches[0].getParent();
                    if (!archiveDir.isDirectory()) {
                        listener.error("[DeployBuild] Pattern did not match a directory.");
                    }
                }
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("[DeployBuild] Script access failed."));
                build.setResult(Result.FAILURE);
                return true;
            }

            if (archiveDir == null) {
                build.setResult(Result.FAILURE);
                return true;
            }
            
            listener.getLogger().println("[DeployBuild] Enabling \"" + deployTarget.getDeployName() + "\".");
            deployTarget.handleAction(build);
        }

        return true;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        if (this.deployTargets.isEmpty()) {
            return Collections.emptyList();
        } else {
            ArrayList<Action> actions = new ArrayList<Action>();
            for (DeployTarget target : this.deployTargets) {
                if (project instanceof MatrixProject && ((MatrixProject) project).getActiveConfigurations() != null){
                    for (MatrixConfiguration mc : ((MatrixProject) project).getActiveConfigurations()){
                        try {
                          mc.onLoad(mc.getParent(), mc.getName());
                        }
                        catch (IOException e){
                            //Could not reload the configuration.
                        }
                    }
                }
            }
            return actions;
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            // return Messages.JavadocArchiver_DisplayName();
            return "Enable Capistrano deployments";
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheck(@AncestorInPath AbstractProject project,
                @QueryParameter String value) throws IOException, ServletException {
            FilePath ws = project.getSomeWorkspace();
            return ws != null ? ws.validateRelativeDirectory(value) : FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
}
