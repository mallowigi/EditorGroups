package krasa.editorGroups;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import krasa.editorGroups.model.*;
import krasa.editorGroups.support.RegexFileResolver;
import krasa.editorGroups.support.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RegexGroupProvider {
	private static final Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance(RegexGroupProvider.class);

	public static RegexGroupProvider getInstance(@NotNull Project project) {
		return ServiceManager.getService(project, RegexGroupProvider.class);
	}

	public EditorGroup findFirstMatchingRegexGroup_stub(VirtualFile file) {
		if (LOG.isDebugEnabled())
			LOG.debug("findFirstMatchingRegexGroup: " + file);


		long start = System.currentTimeMillis();
		String fileName = file.getName();
		RegexGroupModel matching = ApplicationConfiguration.state().getRegexGroupModels().findFirstMatching(fileName);
		if (LOG.isDebugEnabled()) LOG.debug("findMatchingRegexGroups: " + (System.currentTimeMillis() - start) + "ms");

		if (matching == null) {
			return EditorGroup.EMPTY;
		}

		return new RegexGroup(matching, file.getParent().getPath(), fileName);
	}

	public List<RegexGroup> findMatchingRegexGroups_stub(VirtualFile file) {
		if (LOG.isDebugEnabled())
			LOG.debug("findMatchingRegexGroups: " + file);


		long start = System.currentTimeMillis();
		String fileName = file.getName();
		List<RegexGroupModel> matching = ApplicationConfiguration.state().getRegexGroupModels().findMatching(fileName);
		if (LOG.isDebugEnabled()) LOG.debug("findMatchingRegexGroups: " + (System.currentTimeMillis() - start) + "ms");


		return toRegexGroup_stub(file, fileName, matching);
	}

	public List<RegexGroup> findProjectRegexGroups_stub() {
		List<RegexGroupModel> globalRegexGroups = ApplicationConfiguration.state().getRegexGroupModels().findProjectRegexGroups();
		return toRegexGroups_stub(globalRegexGroups);
	}


	@NotNull
	public List<RegexGroup> toRegexGroup_stub(VirtualFile file, String fileName, List<RegexGroupModel> matching) {
		List<RegexGroup> result = new ArrayList<>(matching.size());
		for (RegexGroupModel regexGroupModel : matching) {
			result.add(new RegexGroup(regexGroupModel, file.getParent().getPath(), Collections.emptyList(), fileName));
		}
		return result;
	}

	private List<RegexGroup> toRegexGroups_stub(List<RegexGroupModel> globalRegexGroups) {
		List<RegexGroup> result = new ArrayList<>(globalRegexGroups.size());
		for (RegexGroupModel regexGroupModel : globalRegexGroups) {
			result.add(new RegexGroup(regexGroupModel));
		}
		return result;
	}

	public RegexGroup getRegexGroup(RegexGroup group, Project project, @Nullable VirtualFile currentFile) {
		List<Link> links;
		if (currentFile != null) {
			links = new RegexFileResolver(project).resolveRegexGroupLinks(group, currentFile);
			if (links.isEmpty()) {
				LOG.error("should contain the current file at least: " + group);
			}
		} else {
			links = new RegexFileResolver(project).resolveRegexGroupLinks(group);
		}
		return new RegexGroup(group.getRegexGroupModel(), group.getFolderPath(), links, group.getFileName());
	}

	public EditorGroup findRegexGroup_stub(String filePath, String substring) {
		RegexGroupModels regexGroupModels = ApplicationConfiguration.state().getRegexGroupModels();
		RegexGroupModel regexGroupModel = regexGroupModels.find(substring);
		if (regexGroupModel == null) {
			return EditorGroup.EMPTY;
		}

		File file = new File(filePath);
		return new RegexGroup(regexGroupModel, Utils.getCanonicalPath(file.getParentFile()), Collections.emptyList(), file.getName());
	}
}