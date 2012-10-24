/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package deploybuild;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import hudson.util.FlushProofOutputStream;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;
import javax.servlet.ServletException;
import org.acegisecurity.AccessDeniedException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author shaddy
 */
public class ScriptAction implements ProminentProjectAction {
    public AbstractBuild<?, ?> build;
    private DeployTarget target;
    private boolean isLogUpdated = false;
    protected String charset;
    private File logFile = null;

    public ScriptAction(AbstractBuild<?, ?> build, DeployTarget deployTarget) {
        this.build = build;
        this.target = deployTarget;
    }
    
    public boolean allowed() {
        return this.build.hasPermission(Run.UPDATE);
    }
    
    public void doDeploy(StaplerRequest req, StaplerResponse rsp) throws FileNotFoundException, IOException, ServletException, InterruptedException {
        this.build.checkPermission(Permission.UPDATE);
        Computer computer = Computer.currentComputer();
        Charset charset = null;
        if (computer != null) {
            charset = computer.getDefaultCharset();
            this.charset = charset.name();
        }
        FilePath workspace = this.target.getArchiveTarget(build);
        OutputStream logger = new FileOutputStream(getLogFile());
        TaskListener listener = new StreamTaskListener(logger,charset);
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
        
        isLogUpdated = true;
        rsp.forward(this, "index", req);
        tmpFile = batchRunner.createScriptFile(workspace);
        int r = launcher.launch().cmds(batchRunner.buildCommandLine(tmpFile)).stdout(listener).pwd(workspace).join();
        isLogUpdated = false;
    }
    
    public boolean isDeployed() {
        if (logFile == null) {
            logFile = getLogFile();
        }
        
        return logFile.exists();
    }
    
    public Charset getCharset() {
        if(charset==null)   return Charset.defaultCharset();
        return Charset.forName(charset);
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
    
    public boolean isLogUpdated() {
        return isLogUpdated;
    }
    
    public InputStream getLogInputStream() throws IOException {
    	File logFile = getLogFile();
    	if (logFile.exists() ) {
            return new FileInputStream(logFile);
    	}

    	File compressedLogFile = new File(logFile.getParentFile(), logFile.getName()+ ".gz");
    	if (compressedLogFile.exists()) {
            return new GZIPInputStream(new FileInputStream(compressedLogFile));
    	}
    	
    	return new NullInputStream(0);
    }
    
    public final File getLogFile() {
        return new File(this.build.getRootDir(),  this.target.getDeployFile() + "log");
    }

    public Reader getLogReader() throws IOException {
        if (charset==null)  return new InputStreamReader(getLogInputStream());
        else                return new InputStreamReader(getLogInputStream(),charset);
    }
    
    public void writeLogTo(long offset, XMLOutput out) throws IOException {
        try {
			getLogText().writeHtmlTo(offset,out.asWriter());
		} catch (IOException e) {
			// try to fall back to the old getLogInputStream()
			// mainly to support .gz compressed files
			// In this case, console annotation handling will be turned off.
			InputStream input = getLogInputStream();
			try {
				IOUtils.copy(input, out.asWriter());
			} finally {
				IOUtils.closeQuietly(input);
			}
		}
    }
    
    public void writeWholeLogTo(OutputStream out) throws IOException, InterruptedException {
        long pos = 0;
        AnnotatedLargeText logText;
        do {
            logText = getLogText();
            pos = logText.writeLogTo(pos, out);
        } while (!logText.isComplete());
    }
    
    public AnnotatedLargeText getLogText() {
        return new AnnotatedLargeText(getLogFile(),getCharset(),!isLogUpdated(),this);
    }
    
    public void doScriptOutput(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException, AccessDeniedException {
        rsp.setContentType("text/plain;charset=UTF-8");
        // Prevent jelly from flushing stream so Content-Length header can be added afterwards
        FlushProofOutputStream out = new FlushProofOutputStream(rsp.getCompressedOutputStream(req));
        try{
        	getLogText().writeLogTo(0,out);
        } catch (IOException e) {
			// see comment in writeLogTo() method
			InputStream input = getLogInputStream();
			try {
				IOUtils.copy(input, out);
			} finally {
				IOUtils.closeQuietly(input);
			}
		}
        out.close();
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