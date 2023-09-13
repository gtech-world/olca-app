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

import com.google.gson.Gson;

public class DbSyncAction extends Action implements INavigationAction {

	private final Logger log = LoggerFactory.getLogger(getClass());
	private DatabaseElement element;
	private HttpClient client;
	// beta
	// private final String base = "https://pre-api.gtech.world";
	// prod
	private final String base = "https://api-v2.gtech.world";

	private HttpClient instanceClinet() {
		if (client == null) {
			client = HttpClient.newHttpClient();
		}
		return client;
	}

	private File getDBDir(String dbName) {
		File database = new File(Workspace.root(), "databases");
		return new File(database, dbName);
	}

	private int getSyncDBVersion(String dbName) {
		File versionFile = new File(getDBDir(dbName), ".version");
		if (!versionFile.exists())
			return 0;
		try {
			byte[] bytes = Files.readAllBytes(versionFile.toPath());
			String version = new String(bytes, StandardCharsets.UTF_8);
			return Integer.parseInt(version);
		} catch (Exception e) {
			log.error("failed to read HTML folder version", e);
			return 0;
		}
	}

	private void setSyncDBVersion(String dbName, int version) throws Exception {
		File versionFile = new File(getDBDir(dbName), ".version");
		if (!versionFile.exists())
			versionFile.createNewFile();
		String versionString = version + "";
		Files.writeString(versionFile.toPath(), versionString);
	}

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

	private <T> T httpGET(String path, Class<T> classz) throws Exception {
		var req = HttpRequest.newBuilder(URI.create(base + path)).build();
		var res = instanceClinet().send(req, HttpResponse.BodyHandlers.ofString());
		return new Gson().fromJson(res.body(), classz);
	}

	public static class RES<T> {
		public int status;
		public String message;
		public T data;
	}

	private static final int BUFFER_SIZE = 1024 * 1024;

	private long fileSize(String path) {
		try {
			return Files.size(Path.of(path));
		} catch (Exception e) {
			return 0;
		}
	}

	private String versionZipName(int version, String uuid) {
		return "v" + version + "-" + uuid + ".zip";
	}

	private void downloadFile(String fileUrl, String saveDir, String fileName) throws Exception {
		var filePath = saveDir + File.separator + fileName;
		var old = new File(filePath);
		if (old.exists())
			return;
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(fileUrl));
		var tempPath = filePath + ".temp";
		var startPos = fileSize(tempPath);
		if (startPos > 0) {
			builder.setHeader("Range", "bytes=" + startPos + "-");
		}
		HttpResponse<InputStream> httpResponse = instanceClinet().send(builder.build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (httpResponse.statusCode() == 200 || httpResponse.statusCode() == 206) {
			long fileSize = httpResponse.headers().firstValueAsLong("Content-Length").orElse(-1L);
			if (fileSize <= 0)
				throw new IOException("Down Error");
			new File(saveDir).mkdirs();
			try (InputStream inputStream = httpResponse.body(); var raf = new RandomAccessFile(tempPath, "rw");) {
				@SuppressWarnings("unused")
				long downloadedBytes = 0L;
				if (startPos > 0) {
					downloadedBytes = startPos;
					raf.seek(startPos);
				}
				byte[] buffer = new byte[BUFFER_SIZE];
				int len;
				while ((len = inputStream.read(buffer)) != -1) {
					raf.write(buffer, 0, len);
					downloadedBytes += len;
				}

				var fileTemp = new File(tempPath);
				var file = new File(filePath);
				boolean success = fileTemp.renameTo(file);
				if (!success)
					throw new IOException("Rename Error");
			} catch (Exception e) {
				throw e;
			}

		} else {
			throw new IOException("Network Error");
		}
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

		private ArrayList<String> getCloudVersions() throws Exception {
			@SuppressWarnings("unchecked")
			var res = httpGET("/api/model/history/all",
					(Class<RES<ArrayList<String>>>) new RES<ArrayList<String>>().getClass());
			if (res != null && res.data != null)
				return res.data;
			return new ArrayList<>();
		}

		private void loadData(ArrayList<String> cloudVs) throws Exception {
			for (int i = 0; i < cloudVs.size(); i++) {
				downloadFile(base + "/api/model/history/" + cloudVs.get(i) + "/download", cachePath,
						versionZipName(i + 1, cloudVs.get(i)));
			}
		}

		private void importZips(ArrayList<String> cloudVs, int localV) throws IOException {
			for (int i = localV + 1; i <= cloudVs.size(); i++) {
				var store = ZipStore.open(new File(cachePath, versionZipName(i, cloudVs.get(i - 1))));
				new SyncJsonImport(store, Database.get()).setUpdateMode(UpdateMode.NEVER).run();
				store.close();
			}
		}

		@Override
		public void run() {
			try {
				// 0. checkVersion
				var cloudVs = getCloudVersions();
				var cloudV = cloudVs.size();
				int version = getSyncDBVersion(config.name());
				if (cloudV <= version || cloudV <= 0) {
					state = SYNC_STATE.SYNCED;
					return;
				}
				// 1. load data
				loadData(cloudVs);
				// 2. import to database
				importZips(cloudVs, version);
				// 3. save version
				setSyncDBVersion(config.name(), cloudV);
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
