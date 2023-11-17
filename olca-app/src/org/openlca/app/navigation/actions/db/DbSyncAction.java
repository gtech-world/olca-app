package org.openlca.app.navigation.actions.db;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.PlatformUI;
import org.openlca.app.M;
import org.openlca.app.collaboration.views.CompareView;
import org.openlca.app.collaboration.views.HistoryView;
import org.openlca.app.db.Database;
import org.openlca.app.editors.Editors;
import org.openlca.app.navigation.Navigator;
import org.openlca.app.navigation.actions.INavigationAction;
import org.openlca.app.navigation.elements.DatabaseElement;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.rcp.Workspace;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.Popup;
import org.openlca.core.database.config.DatabaseConfig;
import org.openlca.jsonld.ZipStore;
import org.openlca.jsonld.input.SyncJsonImport;
import org.openlca.jsonld.input.UpdateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openlca.app.collaboration.dialogs.LoginAICP;

import com.google.gson.Gson;

public class DbSyncAction extends Action implements INavigationAction {

	private final Logger log = LoggerFactory.getLogger(getClass());
	private DatabaseElement element;

	public DbSyncAction() {
		setText(M.SyncDB);
		setImageDescriptor(Icon.UPDATE.descriptor());
	}

	@Override
	public boolean accept(List<INavigationElement<?>> selection) {
		if (selection.size() != 1)
			return false;
		var first = selection.get(0);
		if (!(first instanceof DatabaseElement))
			return false;
		this.element = (DatabaseElement) first;
		return true;
	}

	@Override
	public void run() {
		var config = element == null || element.getContent() == null ? Database.getActiveConfiguration()
				: element.getContent();
		if (config == null)
			return;
		var cre = LoginAICP.promptCredentials();
		if(cre == null) return;
		run(config);
	}

	public void run(DatabaseConfig config) {
		var active = Database.isActive(config);
		if (!active)
			return;
		if (active)
			if (!Editors.closeAll())
				return;
		var runner = new SyncRunner(config);
		var task = new Thread(runner);
		task.start();
		var progress = PlatformUI.getWorkbench().getProgressService();
		try {
			progress.run(true, true, (m) -> {
				m.beginTask(M.SyncDB, IProgressMonitor.UNKNOWN);
				while (task.isAlive()) {
					try {
						Thread.sleep(1000);
						if (m.isCanceled()) {
							task.interrupt();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				m.done();
			});
		} catch (Exception e) {
			log.error("Error while running progress " + M.SyncDB, e);
		}
		updateUI(runner.state);
	}


	public enum SYNC_STATE {
		IDEL, SUCCESS, FAILED, SYNCED
	}

	private class SyncRunner implements Runnable {

		public SYNC_STATE state = SYNC_STATE.IDEL;
		private final DatabaseConfig config;
		private final String cachePath = Workspace.root() + File.separator + "sync-cache";


		private SyncRunner(DatabaseConfig config) {
			this.config = config;
		}

		private void initDbRepo() {
			
		}
		
		@Override
		public void run() {
			try {
				// 1. init repo
				initDbRepo();
				// 2. stash locale
				// 3. pull remote
				// 4. push to remote
				state = SYNC_STATE.SUCCESS;
			} catch (Exception e) {
				var isCanceled = e instanceof InterruptedException;
				if (!isCanceled) {
					state = SYNC_STATE.FAILED;
				}
				e.printStackTrace();
			}
		}

	}

	private void updateUI(SYNC_STATE state) {
		switch (state) {
		case SUCCESS:
			Navigator.refresh();
			CompareView.clear();
			HistoryView.refresh();
			Popup.info(M.SyncDB, "Sync success.");
			break;
		case SYNCED:
			Popup.info(M.SyncDB, "Already up to date.");
			break;
		case FAILED:
			Popup.error(M.SyncDB, "Network error, please try again later.");
			break;
		default:
			break;
		}

	}
}
