package org.openlca.app.collaboration.dialogs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.time.DateUtils;
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
		var uinfoFile = new File(Workspace.root(), ".userinfo");
		if (!uinfoFile.exists())
			return null;
		try {
			byte[] bytes = Files.readAllBytes(uinfoFile.toPath());
			String str = new String(bytes, StandardCharsets.UTF_8);
			if (str == null || str.isEmpty())
				return null;
			var ui = new Gson().fromJson(str, UserInfo.class);
			var lastTime = DateUtils.parseDate(ui.lastLoginTime, Locale.CHINA, "yyyy-MM-dd HH:mm:ss");
			var lastTimeNum = lastTime.getTime();
			var beforeTime = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 30);
			var beforeTimeNum = beforeTime.getTime();
			if (beforeTimeNum < lastTimeNum)
				return null;
			return ui;
		} catch (Exception e) {
			return null;
		}
	}

	private static void saveUserInfo(UserInfo uinfo) {
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
		if (uinfo != null)
			return uinfo;
		while (true) {
			var dialog = new LoginAICP();
			var auth = dialog.auth;
			auth.withUser().withPassword();
			if (dialog.open() == LoginAICP.CANCEL) {
				return null;
			}
			try {
				var bodyStr = "{\"name\":\"" + auth.user() + "\", \"password\":\"" + auth.password() + "\"}";
				var reqres = Req.httpPost("/api/base/login", bodyStr, ResUserInfo.class);
				if (reqres.data == null) {
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
		// doLogin
		var uinfo = showLogin();
		if (uinfo == null) {
			return null;
		}
		var gitCP = new GitCredentialsProvider(uinfo.gitlabAccessToken, uinfo.branch);
		gitCP.user = uinfo.gitlabUserName;
		gitCP.repoName = uinfo.gitlabProjectName;
		return gitCP;
	}

	@Override
	protected void createFormContent(IManagedForm form) {
		var formBody = UI.header(form, form.getToolkit(), "Login to AICP", "");
		var body = UI.composite(formBody, form.getToolkit());
		UI.gridLayout(body, 1);
		UI.gridData(body, true, true).widthHint = 500;
		auth.onChange(this::updateButtons).render(body, form.getToolkit(), SWT.FOCUSED);
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
		public String user = "user";
		public String email = "noreply@gitlab.gtech.world";
		public String repoName;
		public String token;

		public GitCredentialsProvider(String token, String branch) {
			super("oauth2", token);
			this.branch = branch;
			this.token = token;
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
		public String gitlabAccessToken = "";
		public String gitlabProjectName = "";
		public String gitlabUserId = "";
		public String gitlabUserName = "";
	}

	public static class ResUserInfo extends Req.RES<UserInfo> {
		public ResUserInfo() {
			super();
		}
	}
}
