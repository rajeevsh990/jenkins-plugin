package com.openshift.jenkins.plugins.pipeline;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.capability.ICapability;
import com.openshift.restclient.model.IImageStream;

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.Serializable;
import java.util.StringTokenizer;

import jenkins.tasks.SimpleBuildStep;

public class OpenShiftImageTagger extends Builder implements SimpleBuildStep, Serializable {

    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String testTag = "origin-nodejs-sample:latest";
    private String prodTag = "origin-nodejs-sample:prod";
    private String namespace = "test";
    private String authToken = "";
    private String verbose = "false";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftImageTagger(String apiURL, String testTag, String prodTag, String namespace, String authToken, String verbose) {
        this.apiURL = apiURL;
        this.testTag = testTag;
        this.namespace = namespace;
        this.prodTag = prodTag;
        this.authToken = authToken;
        this.verbose = verbose;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getApiURL() {
		return apiURL;
	}

	public String getTestTag() {
		return testTag;
	}

	public String getNamespace() {
		return namespace;
	}
	
	public String getProdTag() {
		return prodTag;
	}
	
	public String getAuthToken() {
		return authToken;
	}

    public String getVerbose() {
		return verbose;
	}
    
    protected boolean coreLogic(AbstractBuild build, Launcher launcher, TaskListener listener) {
		boolean chatty = Boolean.parseBoolean(verbose);
    	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftImageTagger in perform on namespace " + namespace);
    	
    	TokenAuthorizationStrategy bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(build, authToken, listener, chatty));
    	Auth auth = Auth.createInstance(chatty ? listener : null);
    	    	
    	// get oc client (sometime REST, sometimes Exec of oc command
    	IClient client = new ClientFactory().create(apiURL, auth);
    	
    	if (client != null) {
    		// seed the auth
        	client.setAuthorizationStrategy(bearerToken);
        	
        	//tag image
			StringTokenizer st = new StringTokenizer(prodTag, ":");
			String imageStreamName = null;
			String tagName = null;
			if (st.countTokens() > 1) {
				imageStreamName = st.nextToken();
				tagName = st.nextToken();
				
				IImageStream is = client.get(ResourceKind.IMAGE_STREAM, imageStreamName, namespace);
				is.setTag(tagName, testTag);
				client.update(is);
			}
			
			
    	} else {
    		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftImageTagger could not get oc client");
    		return false;
    	}

		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftImageTagger image stream now has tags: " + testTag + ", " + prodTag);
		return true;
    }

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		coreLogic(null, launcher, listener);
	}

	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		return coreLogic(build, launcher, listener);
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftImageTagger}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the various fields.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckApiURL(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set apiURL");
            return FormValidation.ok();
        }

        public FormValidation doCheckTestTag(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set testTag");
            return FormValidation.ok();
        }

        public FormValidation doCheckProdTag(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set prodTag");
            return FormValidation.ok();
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set namespace");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Tag an image in OpenShift";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
            save();
            return super.configure(req,formData);
        }

    }

}
