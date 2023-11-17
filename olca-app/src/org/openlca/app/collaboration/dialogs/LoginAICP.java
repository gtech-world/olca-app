package org.openlca.app.collaboration.dialogs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.FormDialog;
import org.eclipse.ui.forms.IManagedForm;
import org.openlca.app.rcp.Workspace;
import org.openlca.app.util.Req;
import org.openlca.app.util.UI;

import com.google.gson.Gson;

public class LoginAICP extends FormDialog {

	private final AuthenticationGroup auth = new AuthenticationGroup();


	private LoginAICP() {
		super(UI.shell());
		setBlockOnOpen(true);
	}

	public static UserInfo getSavedUserInfo() {
	  var uinfoFile =	new File(Workspace.root(), ".userinfo");
		if (!uinfoFile.exists())
			return null;
		try {
			byte[] bytes = Files.readAllBytes(uinfoFile.toPath());
			String str = new String(bytes, StandardCharsets.UTF_8);
			if(str == null || str.isEmpty()) return null;
			return new Gson().fromJson(str, UserInfo.class);
		} catch (Exception e) {
			return null;
		}
	}

	private static void saveUserInfo(UserInfo uinfo) throws Exception {
		try {
			File uinfoFile = new File(Workspace.root(), ".userinfo");
			if (!uinfoFile.exists()) {				
				uinfoFile.createNewFile();
			}
			Files.writeString(uinfoFile.toPath(), new Gson().toJson(uinfo));
		} catch (Exception e) {

		}
	}
	
	
	
	public static UserInfo showLogin() {
		var uinfo = getSavedUserInfo();
		if(uinfo != null) return uinfo;
		while(true) {
			var dialog = new LoginAICP();
			var auth = dialog.auth;
			auth.withUser().withPassword();
			if(dialog.open() == LoginAICP.CANCEL) {
				return null;
			}
			try {
				var body = new Object() {
					public String name = auth.user();
					public String password = auth.password();
				};
				@SuppressWarnings("unchecked")
				var reqres = Req.httpPost("/api/base/login", body,  (Class<Req.RES<UserInfo>>)new Req.RES<UserInfo>().getClass());
				if(reqres.status != 200|| reqres.data == null) {
					continue;
				}
				saveUserInfo(reqres.data);
				return reqres.data;
			} catch (Exception e) {
				continue;
			}
		}
		
	}

	public static GitCredentialsProvider promptCredentials() {
		var uinfo = getSavedUserInfo();
		if(uinfo != null) {
			return new GitCredentialsProvider(uinfo.gitToken, uinfo.branch); 
		}
		// doLogin
		uinfo = showLogin();
		if(uinfo == null) {
			return null;
		}
		return new GitCredentialsProvider(uinfo.gitToken, uinfo.branch);
	}

	@Override
	protected void createFormContent(IManagedForm form) {
		var formBody = UI.header(form, form.getToolkit(), "Login to AICP", "");
		var body = UI.composite(formBody, form.getToolkit());
		UI.gridLayout(body,  1);
		UI.gridData(body, true, true).widthHint = 500;
		auth.onChange(this::updateButtons)
				.render(body, form.getToolkit(), SWT.FOCUSED);
		form.getForm().reflow(true);
	}

	private void updateButtons() {
		getButton(IDialogConstants.OK_ID).setEnabled(auth.isComplete());
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		var ok = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		ok.setEnabled(auth.isComplete());
		setButtonLayoutData(ok);
	}

	public AuthenticationGroup values() {
		return auth;
	}

	public static class GitCredentialsProvider extends UsernamePasswordCredentialsProvider {
		public String branch;
		GitCredentialsProvider(String token, String branch) {
			super("oauth2", token);
			this.branch = branch;
		}
	}

	public static class Organization {
         public int id;
		 public String name;
		 public String type;
		 public String serialNumber;
		 public String publicKey;
		 public String privateKey;
		 public String createTime;
		 public String updateTime;
		 
	}
	public static class UserInfo {
       public int apiKeyId;
       public int orgId;
       public int id;
       public String name;
       public String mobile;
       public String email;
       public String role;
       public String publicKey;
       public String privateKey;
       public String lastLoginTime;
       public String createTime;
       public String updateTime;
       public String loginToken;
       public String address;
       public boolean system;
       public boolean admin;
       public Organization organization;
       
       public String branch;
       public String gitToken = "";
	}
}
