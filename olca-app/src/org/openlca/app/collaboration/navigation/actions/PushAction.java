package org.openlca.app.collaboration.navigation.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.openlca.app.M;
import org.openlca.app.collaboration.dialogs.HistoryDialog;
import org.openlca.app.db.Repository;
import org.openlca.app.navigation.actions.INavigationAction;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.MsgBox;
import org.openlca.git.actions.GitPush;

public class PushAction extends Action implements INavigationAction {

	@Override
	public String getText() {
		return M.Push;
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return Icon.PUSH.descriptor();
	}

	@Override
	public boolean isEnabled() {
		return !Repository.get().history.getAhead().isEmpty();
	}
	
	@Override
	public void run() {
		try {
			var result = Actions.run(GitPush
					.from(Repository.get().git)
					.authorizeWith(Actions.credentialsProvider()));
			if (result.newCommits().isEmpty()) {
				MsgBox.info("No commits to push - Everything up to date");
			} else if (result.status() == Status.REJECTED_NONFASTFORWARD) {
				MsgBox.error("Rejected - Not up to date - Please merge remote changes to continue");
			} else {
				Collections.reverse(result.newCommits());
				new HistoryDialog("Pushed commits", result.newCommits()).open();
			}
		} catch (GitAPIException | InvocationTargetException | InterruptedException e) {
			Actions.handleException("Error pushing to remote", e);
		} finally {
			Actions.refresh();
		}
	}

	@Override
	public boolean accept(List<INavigationElement<?>> elements) {
		if (!Repository.isConnected())
			return false;
		return true;
	}

}
