package org.openlca.app.navigation.actions.db;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.ui.PlatformUI;
import org.openlca.app.M;
import org.openlca.app.collaboration.dialogs.LoginAICP;
import org.openlca.app.collaboration.navigation.actions.ConflictResolutionMap;
import org.openlca.app.db.Database;
import org.openlca.app.db.Repository;
import org.openlca.app.editors.Editors;
import org.openlca.app.navigation.Navigator;
import org.openlca.app.navigation.actions.INavigationAction;
import org.openlca.app.navigation.elements.DatabaseElement;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.Popup;
import org.openlca.core.database.Daos;
import org.openlca.core.database.config.DatabaseConfig;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.Version;
import org.openlca.core.model.descriptors.RootDescriptor;
import org.openlca.git.actions.ConflictResolver;
import org.openlca.git.actions.GitMerge;
import org.openlca.git.actions.GitStashApply;
import org.openlca.git.actions.GitStashCreate;
import org.openlca.git.model.Change;
import org.openlca.git.model.Commit;
import org.openlca.git.model.Diff;
import org.openlca.git.model.ModelRef;
import org.openlca.git.model.Reference;
import org.openlca.git.util.Diffs;
import org.openlca.git.util.TypedRefIdMap;
import org.openlca.jsonld.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

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
		var gcp = LoginAICP.promptCredentials();
		if (gcp == null)
			return;
		run(config, gcp);
	}

	public void run(DatabaseConfig config, LoginAICP.GitCredentialsProvider gcp) {
		var active = Database.isActive(config);
		if (!active || !Editors.closeAll())
			return;
		var runner = new SyncRunner(config, gcp);
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
		private final LoginAICP.GitCredentialsProvider gcp;
		private final PersonIdent pi;
		private SyncRunner(DatabaseConfig config, LoginAICP.GitCredentialsProvider gcp) {
			this.config = config;
			this.gcp = gcp;
			this.pi = new PersonIdent(gcp.user, gcp.email);
		}

		private Repository initDbRepo() throws Exception {
			var gitDir = Repository.gitDir(config.name());
			if (!gitDir.exists()) {
//				GitInit.in(gitDir).remoteUrl("").run();
				var git = Git.init().setInitialBranch(gcp.branch).setBare(true).setGitDir(gitDir).call();
				git.remoteAdd().setName("origin").setUri(new URIish("")).call();
				var repo = Repository.initialize(gitDir);
				if (repo == null)
					throw new Exception("Repo init error");
				repo.user(gcp.user);
				return repo;
			} else {
				return Repository.open(gitDir);
			}
		}

		private static String string(Map<String, Object> map, String field) {
			var value = map.get(field);
			if (value == null)
				return null;
			return value.toString();
		}

		private static long date(Map<String, Object> map, String field) {
			var value = map.get(field);
			if (value == null)
				return 0;
			try {
				return Long.parseLong(value.toString());
			} catch (NumberFormatException e) {
				var date = Json.parseDate(value.toString());
				if (date == null)
					return 0;
				return date.getTime();
			}
		}

		private static boolean equalsDescriptor(Diff diff, RootDescriptor d) {
			if (d == null)
				return false;
			if (ObjectId.zeroId().equals(diff.oldObjectId))
				return false;
			var ref = new Reference(diff.path, diff.oldCommitId, diff.oldObjectId);
			var remoteModel = Repository.get().datasets.parse(ref, "lastChange", "version");
			if (remoteModel == null)
				return false;
			var version = Version.fromString(string(remoteModel, "version")).getValue();
			var lastChange = date(remoteModel, "lastChange");
			return version == d.version && lastChange == d.lastChange;
		}

		private boolean stashDifferences(Repository repo, Commit commit, TypedRefIdMap<RootDescriptor> descriptors)
				throws IOException, InvocationTargetException, InterruptedException, GitAPIException {
			var differences = Diffs.of(repo.git, commit).with(Database.get(), repo.gitIndex).stream()
					.filter(diff -> !equalsDescriptor(diff, descriptors.get(diff))).map(diff -> new Change(diff))
					.collect(Collectors.toList());
			if (differences.isEmpty())
				return false;
			GitStashCreate.from(Database.get()).to(repo.git).as(pi).reference(commit).update(repo.gitIndex)
					.changes(differences).run();
			return true;
		}

		private void stashAndPull(Repository repo) throws Exception {
//			var commits = GitFetch.to(repo.git).authorizeWith(gcp).run();
			// checkFetch
			var lastId = repo.commits.find().refs("refs/remotes/origin/" + gcp.branch).latestId();
			var result = Git.wrap(repo.git).fetch().setCredentialsProvider(gcp).setRemote("origin")
					.setRefSpecs("+refs/heads/" + gcp.branch + ":refs/remotes/origin/" + gcp.branch).call();
			var commits = repo.commits.find().refs("refs/remotes/origin/" + gcp.branch).after(lastId).all();
			Collections.reverse(commits);
//			var libraryResolver = WorkspaceLibraryResolver.forRemote();
//			if (libraryResolver == null)
//				return false;
			var descriptors = new TypedRefIdMap<RootDescriptor>();
			for (var type : ModelType.values()) {
				Daos.root(Database.get(), type).getDescriptors().forEach(d -> descriptors.put(d.type, d.refId, d));
			}
			var commit = repo.commits.find().refs("refs/remotes/origin/" + gcp.branch).latest();
			boolean wasStashed = stashDifferences(repo, commit, descriptors);
			GitMerge.from(repo.git).into(Database.get()).as(pi).update(repo.gitIndex)
				.resolveConflictsWith(new EqualResolver(descriptors)).run();
			if (wasStashed) {
//				var libraryResolver = WorkspaceLibraryResolver.forStash();
//				if (libraryResolver == null)
//					return false;
				var conflictResult = ConflictResolutionMap.forStash();
				if (conflictResult == null)
					return;
				GitStashApply.from(repo.git)
						.to(Database.get())
						.update(repo.gitIndex)
						.resolveConflictsWith(conflictResult.resolutions())
						.run();
			
			}
		}

		@Override
		public void run() {
			try {
				// 1. init repo
				var repo = initDbRepo();
				// 2. stash locale and pull
				stashAndPull(repo);
				// 3. push to remote;
				
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
	
	
	private class EqualResolver implements ConflictResolver {

		private final TypedRefIdMap<RootDescriptor> descriptors;

		private EqualResolver(TypedRefIdMap<RootDescriptor> descriptors) {
			this.descriptors = descriptors;
		}

		@Override
		public boolean isConflict(ModelRef ref) {
			return descriptors.contains(ref);
		}

		@Override
		public ConflictResolutionType peekConflictResolution(ModelRef ref) {
			return isConflict(ref) ? ConflictResolutionType.IS_EQUAL : null;
		}

		@Override
		public ConflictResolution resolveConflict(ModelRef ref, JsonObject fromHistory) {
			return isConflict(ref) ? ConflictResolution.isEqual() : null;
		}

	}

}
