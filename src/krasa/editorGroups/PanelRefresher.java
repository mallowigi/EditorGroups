package krasa.editorGroups;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.MyFileManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.indexing.FileBasedIndex;
import krasa.editorGroups.index.EditorGroupIndex;
import krasa.editorGroups.model.EditorGroup;
import krasa.editorGroups.model.EditorGroupIndexValue;
import krasa.editorGroups.model.FolderGroup;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class PanelRefresher {
	private final Project project;
	private AtomicBoolean cacheReady = new AtomicBoolean();
	private final ExecutorService ourThreadExecutorsService;
	private IndexCache cache;

	public PanelRefresher(Project project) {
		this.project = project;
		cache = IndexCache.getInstance(project);
		ourThreadExecutorsService = AppExecutorUtil.createBoundedApplicationPoolExecutor("EditorGroups-" + project.getName(), 1);
		project.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
			@Override
			public void enteredDumbMode() {
			}

			@Override
			public void exitDumbMode() {
				onSmartMode();
			}
		});
	}

	public static PanelRefresher getInstance(@NotNull Project project) {
		return ServiceManager.getService(project, PanelRefresher.class);
	}

	void onSmartMode() {
		if (!cacheReady.get()) {
			return;
		}
		ApplicationManager.getApplication().invokeLater(new Runnable() {
			@Override
			public void run() {
				long start = System.currentTimeMillis();
				final FileEditorManagerImpl manager = (FileEditorManagerImpl) FileEditorManagerEx.getInstance(project);
				for (FileEditor selectedEditor : manager.getAllEditors()) {
					EditorGroupPanel panel = selectedEditor.getUserData(EditorGroupPanel.EDITOR_PANEL);
					if (panel != null) {
						EditorGroup displayedGroup = panel.getDisplayedGroup();
						if (displayedGroup instanceof FolderGroup) {
							continue;
						}
						panel.refresh(false, null);
						MyFileManager.updateTitle(project, selectedEditor.getFile());
					}
				}
				System.out.println("onSmartMode " + (System.currentTimeMillis() - start) + "ms " + Thread.currentThread().getName());
			}
		});

	}

	public EditorGroupIndexValue onIndexingDone(String ownerPath, EditorGroupIndexValue group) {
		group = cache.onIndexingDone(group);
		if (DumbService.isDumb(project)) { //optimization
			return group;
		}

		long start = System.currentTimeMillis();
		final FileEditorManagerImpl manager = (FileEditorManagerImpl) FileEditorManagerEx.getInstance(project);
		for (FileEditor selectedEditor : manager.getAllEditors()) {
			EditorGroupPanel panel = selectedEditor.getUserData(EditorGroupPanel.EDITOR_PANEL);
			if (panel != null) {
				panel.onIndexingDone(ownerPath, group);
			}
		}

		System.out.println("onIndexingDone " + (System.currentTimeMillis() - start) + "ms " + Thread.currentThread().getName());
		return group;
	}


	public void initCache() {
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			DumbService.getInstance(project).waitForSmartMode();
			ApplicationManager.getApplication().runReadAction(
				() -> {
					long start = System.currentTimeMillis();
					FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
					IndexCache cache = IndexCache.getInstance(project);
					fileBasedIndex.processAllKeys(EditorGroupIndex.NAME, new Processor<String>() {
						@Override
						public boolean process(String s) {
							List<EditorGroupIndexValue> values = fileBasedIndex.getValues(EditorGroupIndex.NAME, s, GlobalSearchScope.allScope(project));
							for (EditorGroupIndexValue value : values) {
								cache.initGroup(value);
							}
							return true;
						}
					}, project);
					cacheReady();
					System.out.println("initCache " + (System.currentTimeMillis() - start));
				}
			);
		});
	}

	public void cacheReady() {
		cacheReady.set(true);
		onSmartMode();
	}

	public void refreshOnBackground(Runnable task) {
		ourThreadExecutorsService.submit(task);
	}
}