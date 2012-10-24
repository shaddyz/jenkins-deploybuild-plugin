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
import hudson.model.Hudson;
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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

    private static void writeFile(ArrayList<String> lines, File path) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(path));
        for (int i = 0; i < lines.size(); i++) {
            bw.write(lines.get(i));
            bw.newLine();
        }
        bw.close();
        return;
    }

    public ArrayList<String> readFile(String filePath) throws java.io.FileNotFoundException,
            java.io.IOException {
        ArrayList<String> aList = new ArrayList<String>();

        try {
            final InputStream is = this.getClass().getResourceAsStream(filePath);
            try {
                final Reader r = new InputStreamReader(is);
                try {
                    final BufferedReader br = new BufferedReader(r);
                    try {
                        String line = null;
                        while ((line = br.readLine()) != null) {
                            aList.add(line);
                        }
                        br.close();
                        r.close();
                        is.close();
                    } finally {
                        try {
                            br.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } finally {
                    try {
                        r.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            // failure
            e.printStackTrace();
        }

        return aList;
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
        listener.getLogger().println("Checking for recipes...");
        
        for (int i=0; i < this.deployTargets.size(); i++) {
            // Create an array of lines we will eventually write out, initially the header.
            ArrayList<String> deployLines = new ArrayList<String>();
            DeployTarget deployTarget = this.deployTargets.get(i); 
            boolean successOnly = deployTarget.getSuccessOnly();

            FilePath archiveDir = null;
            try {
                FilePath[] matches = build.getWorkspace().list(resolveParametersInString(build, listener, deployTarget.getDeployDir()));
                if (matches.length == 0) {
                    listener.error("script directory \"" + deployTarget.getDeployDir() + "\" is empty or does not exist");
                } else if (matches.length > 1) {
                    listener.error("more than one path matched the pattern:");
                    for(FilePath p: matches) {
                        listener.error("- " + p);
                    }
                } else {
                    archiveDir = matches[0].getParent();
                    if (!archiveDir.isDirectory()) {
                        listener.error("Pattern did not match a directory");
                    }
                }
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("Deploy failure"));
                build.setResult(Result.FAILURE);
                return true;
            }

            if (archiveDir == null) {
                build.setResult(Result.FAILURE);
                return true;
            }

            FilePath targetDir = deployTarget.getArchiveTarget(build);
            
            listener.getLogger().println("Found \"" + targetDir.getName() + "\"");

            // The index name might be a comma separated list of names, so let's figure out all the pages we should index.
            String[] csvDeploys = resolveParametersInString(build, listener, deployTarget.getDeployFile()).split(",");
            ArrayList<String> deploys = new ArrayList<String>();
            for (int j=0; j < csvDeploys.length; j++) {
                String deploy = csvDeploys[j];
                deploy = deploy.trim();
                
                // Ignore blank deploy names caused by trailing or double commas.
                if (deploy.equals("")) {continue;}
                
                deploys.add(deploy);
                String tabNo = "tab" + (j + 1);
                // Make the deploy name the filename without the extension.
                int end = deploy.lastIndexOf(".");
                String deployName;
                if (end > 0) {
                    deployName = deploy.substring(0, end);
                } else {
                    deployName = deploy;
                }
                String tabItem = "<li id=\"" + tabNo + "\" class=\"unselected\" onclick=\"updateBody('" + tabNo + "');\" value=\"" + deploy + "\">" + deployName + "</li>";
                deployLines.add(tabItem);
            }
            // Add the JS to change the link as appropriate.
            String hudsonUrl = Hudson.getInstance().getRootUrl();
            AbstractProject job = build.getProject();
            deployLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").innerHTML=\"Back to " + job.getName() + "\";</script>");
            // If the URL isn't configured in Hudson, the best we can do is attempt to go Back.
            if (hudsonUrl == null) {
                deployLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").onclick = function() { history.go(-1); return false; };</script>");
            } else {
                String jobUrl = hudsonUrl + job.getUrl();
                deployLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").href=\"" + jobUrl + "\";</script>");
            }
    
            deployLines.add("<script type=\"text/javascript\">document.getElementById(\"zip_link\").href=\"*zip*/" + deployTarget.getSanitizedName() + ".zip\";</script>");

            try {
                targetDir.deleteRecursive();
    
                if (archiveDir.copyRecursiveTo("**/*", targetDir) == 0) {
                    listener.error("Directory '" + archiveDir + "' exists but failed copying to '" + targetDir + "'.");
                    if (build.getResult().isBetterOrEqualTo(Result.UNSTABLE)) {
                        // If the build failed, don't complain that there was no coverage.
                        // The build probably didn't even get to the point where it produces coverage.
                        listener.error("This is especially strange since your build otherwise succeeded.");
                    }
                    build.setResult(Result.FAILURE);
                    return true;
                }
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("HTML Publisher failure"));
                build.setResult(Result.FAILURE);
                return true;
            }
    
            deployTarget.handleAction(build);
    
            // write this as the index
            try {
                writeFile(deployLines, new File(targetDir.getRemote(), deployTarget.getWrapperName()));
            } catch (IOException e) {
                e.printStackTrace();
            }
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
