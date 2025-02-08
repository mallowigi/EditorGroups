# Changelog

## Changelog

## 3.1.0

### Fixes

- Fix **Switch File Action** to show files

## 3.0.0

### Features

* Add action to remove files from current favorites group ([f56306f](https://github.com/mallowigi/EditorGroups/commit/f56306f522904800a09bedb4cde7ce045b573fa4))
* Add bookmarks icon to EditorGroupsIcons ([85e9b40](https://github.com/mallowigi/EditorGroups/commit/85e9b4015d4444f81edb85278a8379dafa1a0622))
* Add EditorGroupsOpenListener and update plugin.xml with required listener ([acc3a49](https://github.com/mallowigi/EditorGroups/commit/acc3a49eef0bf286d6066115280fbacecbb2b00c))
* Add panel to editor when file is opened ([0cb8320](https://github.com/mallowigi/EditorGroups/commit/0cb832021adf1871105c560d389aec5a7f6ae5d7))
* Add SettingsNotifier interface and create TOPIC for EditorGroupsSettings ([930165c](https://github.com/mallowigi/EditorGroups/commit/930165ca4551e427bbfe6189f9035af1d47999b7))
* Add toggle actions for compact mode and colorization of tabs ([2fad42e](https://github.com/mallowigi/EditorGroups/commit/2fad42ed748cce2140d8a858598cf9ef5d12d406))
* **editor:** add functionality to remove files from favorites group ([1a49d0e](https://github.com/mallowigi/EditorGroups/commit/1a49d0eff55e7cfef4804a6d37af51494325151b))
* **EditorGroup:** Improve code readability and maintainability ([fba392b](https://github.com/mallowigi/EditorGroups/commit/fba392b7c73589289117c00a5f959b3b73497e05))
* **EditorGroupPanel:** Add logic to set tab placement based on UISettings. ([c48dc16](https://github.com/mallowigi/EditorGroups/commit/c48dc169bcf34456e32f3d2a720a71175c78e76a))
* **editorGroupPanel:** Add UiCompatibleDataProvider interface to EditorGroupPanel class ([767c57e](https://github.com/mallowigi/EditorGroups/commit/767c57ed9a5de812ee575633371a6f83a05445d2))
* **EditorGroupPanel:** rewrite in kotlin, trying to simplify ([467b609](https://github.com/mallowigi/EditorGroups/commit/467b6093d68836f022e3e6617cb36b162eef6fed))
* **editorGroups:** add SameNameGroup class and methods ([c151c13](https://github.com/mallowigi/EditorGroups/commit/c151c136a7317d3b5c17388e453d5809a60e3cdc))
* **editorGroups:** refactor addBookmarkGroup into addBookmarkGroups ([bdeee87](https://github.com/mallowigi/EditorGroups/commit/bdeee87570cdeaa387c29f72a45e72db2addd326))
* **gui:** Remove unnecessary code for RegexModelTable. ([3fc8fe0](https://github.com/mallowigi/EditorGroups/commit/3fc8fe098152372665181788e17d8b429eb48d03))
* Import necessary classes in KrTabTheme.kt ([d02ac78](https://github.com/mallowigi/EditorGroups/commit/d02ac78e328b14ed40acc2326c6815a1d8849065))
* Improve loading bookmarks in BookmarksGroup ([941d34a](https://github.com/mallowigi/EditorGroups/commit/941d34a92bf0e6930db96eb69a1727b4c867b9ce))
* Optimize stale configuration, update issue templates, and add workflow files. ([bb5f4d5](https://github.com/mallowigi/EditorGroups/commit/bb5f4d56366561130d7ff266d4dd37cfbcf2cb97))
* **panelRefresher:** Refactor BookmarksListener methods and add new methods to handle group events ([20223c9](https://github.com/mallowigi/EditorGroups/commit/20223c9d9cb0db8ae9592a475d2b6b8d18b606a2))
* **plugin:** Update plugin.xml with new configurable settings ID ([935ebd5](https://github.com/mallowigi/EditorGroups/commit/935ebd5411f5931f9cb0a86d472ed21781b9ece8))
* **RegexFileResolver:** Refactor shouldSkipDirectory and matches method ([7568e26](https://github.com/mallowigi/EditorGroups/commit/7568e26d4d278a5132fdc09a2a0ccad709a52090))
* Update method calls for Registry and OpenFileAction ([85ff968](https://github.com/mallowigi/EditorGroups/commit/85ff96809112c8a45655d2c35ea2a0196eb0356f))
* Use custom logger in EditorGroupManager ([666ab0e](https://github.com/mallowigi/EditorGroups/commit/666ab0e52da7d4e1ac6b30a696692c01ad323a8c))

### Bug Fixes

* changing opened tabs did reset scrolling ([5995688](https://github.com/mallowigi/EditorGroups/commit/5995688eeb6364f751317c0d8ded49d28e2ae224))
* FolderGroup - fix switching group not working due to isValid ([8e31bbc](https://github.com/mallowigi/EditorGroups/commit/8e31bbc90cf4c6830d2b6fd48ed2111072ea24fa))


### Removals

### Other

## 2.0.0

### Fixes
- Refactored the project for 2023.2
- Support for 2023.2 versions
- Fix a lot of deprecations
- Display the same icons as the regular tabs
- Use the same size as the regular tabs
- Add support for Tab Separators
