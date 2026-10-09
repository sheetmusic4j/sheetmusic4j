package com.sheetmusic4j.fxdemo.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * The "Resources" pane: a lazily-populated file tree that lists folders and
 * loadable score files, and opens a score when it is selected.
 */
public final class ResourceTreePane {

    private final TreeView<Path> tree = new TreeView<>();
    private final BorderPane pane = new BorderPane(tree);
    private Path root;

    /**
     * @param onOpen called with the path of every score file the user selects
     */
    public ResourceTreePane(Consumer<Path> onOpen) {
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : item.getFileName() != null ? item.getFileName().toString() : item.toString());
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && selected.getValue() != null && !Files.isDirectory(selected.getValue())) {
                onOpen.accept(selected.getValue());
            }
        });
        pane.setTop(Ui.sectionTitle("Resources"));
        setRoot(defaultRoot());
    }

    public BorderPane node() {
        return pane;
    }

    /**
     * Let the user pick a new root folder.
     *
     * @return the chosen folder, or empty when the dialog was cancelled
     */
    public Optional<Path> chooseRoot(Window owner) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose resources folder");
        if (Files.isDirectory(root)) {
            chooser.setInitialDirectory(root.toFile());
        }
        var dir = chooser.showDialog(owner);
        if (dir == null) {
            return Optional.empty();
        }
        setRoot(dir.toPath());
        return Optional.of(dir.toPath());
    }

    private void setRoot(Path newRoot) {
        root = newRoot;
        PathTreeItem rootItem = new PathTreeItem(newRoot);
        rootItem.setExpanded(true);
        tree.setRoot(rootItem);
    }

    /**
     * Resolves the default root for the resource tree: {@code src/test/resources}
     * relative to the current working directory (when launched from the {@code fxdemo}
     * module) or relative to the repository root (when launched from elsewhere),
     * falling back to the working directory itself.
     */
    private static Path defaultRoot() {
        Path direct = Path.of("src/test/resources");
        if (Files.isDirectory(direct)) {
            return direct.toAbsolutePath();
        }
        Path fromRepoRoot = Path.of("fxdemo/src/test/resources");
        if (Files.isDirectory(fromRepoRoot)) {
            return fromRepoRoot.toAbsolutePath();
        }
        return Path.of(System.getProperty("user.dir"));
    }

    /**
     * Lazily-populated {@link TreeItem} backed by a filesystem {@link Path}. Directory
     * children are listed only when first expanded, and are filtered to
     * subdirectories plus files the library can load (see
     * {@link Ui#scoreExtensions()}). PDFs and images are shown as reference previews
     * alongside a loaded score, not as selectable tree entries.
     */
    private static final class PathTreeItem extends TreeItem<Path> {

        private boolean childrenLoaded = false;

        PathTreeItem(Path path) {
            super(path);
        }

        @Override
        public boolean isLeaf() {
            return !Files.isDirectory(getValue());
        }

        @Override
        public ObservableList<TreeItem<Path>> getChildren() {
            if (!childrenLoaded) {
                childrenLoaded = true;
                super.getChildren().setAll(listChildren(getValue()));
            }
            return super.getChildren();
        }

        private static boolean isDisplayable(Path path) {
            return Files.isDirectory(path) || Ui.isScoreFile(path.getFileName().toString());
        }

        private static List<TreeItem<Path>> listChildren(Path dir) {
            try (Stream<Path> stream = Files.list(dir)) {
                return stream
                        .filter(PathTreeItem::isDisplayable)
                        .sorted(Comparator
                                .comparing((Path p) -> !Files.isDirectory(p))
                                .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .map(p -> (TreeItem<Path>) new PathTreeItem(p))
                        .toList();
            } catch (IOException e) {
                return List.of();
            }
        }
    }
}
