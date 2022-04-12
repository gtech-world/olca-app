package org.openlca.app.collaboration.navigation.actions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.openlca.app.collaboration.dialogs.FetchDialog;
import org.openlca.app.collaboration.util.InMemoryConflictResolver;
import org.openlca.app.collaboration.viewers.diff.DiffNodeBuilder;
import org.openlca.app.collaboration.viewers.diff.DiffResult;
import org.openlca.app.db.Database;
import org.openlca.app.db.Repository;
import org.openlca.git.actions.ConflictResolver.ConflictResolution;
import org.openlca.git.model.Commit;
import org.openlca.git.model.Diff;
import org.openlca.git.util.Constants;
import org.openlca.git.util.DiffEntries;
import org.openlca.git.util.TypeRefIdMap;

class Conflicts {

	static InMemoryConflictResolver identifyAndSolve(Commit remoteCommit) throws IOException {
		var commits = Repository.get().commits;
		var localCommit = commits.get(commits.resolve(Constants.LOCAL_BRANCH));
		var conflicts = identify(localCommit, remoteCommit);
		var solved = solve(conflicts);
		return new InMemoryConflictResolver(remoteCommit, solved);
	}

	static List<DiffResult> identify(Commit localCommit, Commit remoteCommit) throws IOException {
		if (localCommit == null)
			return new ArrayList<>();
		var repo = Repository.get();
		var head = repo.commits.head();
		if (head != null && localCommit.id.equals(head.id))
			return new ArrayList<>();
		var localChanges = diffsBetween(repo.git, head, localCommit);
		var remoteChanges = diffsBetween(repo.git, head, remoteCommit);
		return conflictsOf(localChanges, remoteChanges);
	}

	static TypeRefIdMap<ConflictResolution> solve(List<DiffResult> changes) {
		var node = new DiffNodeBuilder(Database.get()).build(changes);
		var dialog = new FetchDialog(node);
		if (dialog.open() == FetchDialog.CANCEL)
			return null;
		return dialog.getResolvedConflicts();
	}

	private static List<Diff> diffsBetween(FileRepository repo, Commit left, Commit right) throws IOException {
		return DiffEntries.between(repo, left, right).stream()
				.map(d -> new Diff(d))
				.collect(Collectors.toList());
	}

	private static List<DiffResult> conflictsOf(List<Diff> localChanges, List<Diff> remoteChanges) {
		var conflicts = new ArrayList<DiffResult>();
		new ArrayList<>(localChanges).forEach(local -> {
			var remote = remoteChanges.stream()
					.filter(r -> r.ref().type == local.ref().type && r.ref().refId.equals(local.ref().refId))
					.findFirst()
					.orElse(null);
			if (remote != null) {
				conflicts.add(new DiffResult(local, remote));
				localChanges.remove(local);
			}
		});
		remoteChanges.forEach(remote -> {
			var local = localChanges.stream()
					.filter(l -> l.ref().type == remote.ref().type && l.ref().refId.equals(remote.ref().refId))
					.findFirst()
					.orElse(null);
			if (local != null) {
				conflicts.add(new DiffResult(local, remote));
			}
		});
		return conflicts;
	}

}
