package controllers;

import entities.Comment;
import entities.Post;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import services.CommentService;
import services.PostService;
import utils.AppNavigator;
import utils.CategoryCache;
import utils.CategoryColor;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import services.DeepSeekService;

public class ModerationController {

    @FXML private TableView<Post>    tblPosts;
    @FXML private TableView<Comment> tblComments;

    @FXML private TableColumn<Post, Integer> colPostId;
    @FXML private TableColumn<Post, String>  colPostTitle;
    @FXML private TableColumn<Post, String>  colPostAuthor;
    @FXML private TableColumn<Post, String>  colPostCategory;
    @FXML private TableColumn<Post, String>  colPostStatus;
    @FXML private TableColumn<Post, String>  colPostDate;
    @FXML private TableColumn<Post, Void>    colPostActions;

    @FXML private TableColumn<Comment, Integer> colCommentId;
    @FXML private TableColumn<Comment, String>  colCommentContent;
    @FXML private TableColumn<Comment, String>  colCommentAuthor;
    @FXML private TableColumn<Comment, String>  colCommentRefPost;
    @FXML private TableColumn<Comment, String>  colCommentDate;
    @FXML private TableColumn<Comment, Void>    colCommentActions;

    private final PostService    postService    = new PostService();
    private final CommentService commentService = new CommentService();
    private final DeepSeekService deepSeekService = new DeepSeekService();

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupPostsTable();
        setupCommentsTable();
        reload();
    }

    @FXML
    private void onRefresh() {
        reload();
    }

    private void reload() {
        try {
            tblPosts.setItems(FXCollections.observableArrayList(postService.getAll()));
        } catch (Exception e) {
            System.err.println("[Moderation] Failed to load posts: " + e.getMessage());
        }
        try {
            tblComments.setItems(FXCollections.observableArrayList(commentService.getAll()));
        } catch (Exception e) {
            System.err.println("[Moderation] Failed to load comments: " + e.getMessage());
        }
    }
    @FXML
    private void onScanAllPostsWithAi() {
        try {
            for (Post post : postService.getAll()) {
                DeepSeekService.ModerationResult result =
                        deepSeekService.moderatePost(post.getTitle(), post.getContent());

                if (result.flagged()) {
                    postService.updateModeration(
                            post.getId(),
                            true,
                            "flagged",
                            result.reason()
                    );
                } else {
                    postService.updateModeration(
                            post.getId(),
                            false,
                            "approved",
                            result.reason()
                    );
                }
            }

            reload();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("AI Moderation");
            alert.setHeaderText(null);
            alert.setContentText("DeepSeek AI scan completed.");
            alert.showAndWait();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("AI Moderation Error");
            alert.setHeaderText(null);
            alert.setContentText("DeepSeek scan failed:\n" + e.getMessage());
            alert.showAndWait();
        }
    }

    // ── Posts table ──────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void setupPostsTable() {
        tblPosts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // ID — grey bold "#N"
        colPostId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colPostId.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText("#" + item);
                setStyle("-fx-text-fill: #A0AEC0; -fx-font-weight: bold;");
            }
        });

        // Title
        colPostTitle.setCellValueFactory(new PropertyValueFactory<>("title"));

        // Author
        colPostAuthor.setCellValueFactory(data ->
                new SimpleStringProperty("user #" + data.getValue().getUserId()));

        // Category — colored pill
        colPostCategory.setCellValueFactory(data ->
                new SimpleStringProperty(CategoryCache.nameOf(data.getValue().getCategoryId())));
        colPostCategory.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String catName, boolean empty) {
                super.updateItem(catName, empty);
                TableView<Post> tv = getTableView();
                int idx = getIndex();
                if (empty || catName == null || tv == null
                        || idx < 0 || idx >= tv.getItems().size()) {
                    setGraphic(null); setText(null); return;
                }
                int catId = tv.getItems().get(idx).getCategoryId();
                String color = CategoryColor.forId(catId);
                Label pill = new Label(catName);
                pill.setStyle(String.format(
                        "-fx-background-color: %s33; -fx-text-fill: %s; " +
                        "-fx-border-color: %s; -fx-border-radius: 10; -fx-background-radius: 10; " +
                        "-fx-padding: 3 10 3 10; -fx-font-size: 11; -fx-font-weight: bold;",
                        color, color, color));
                setGraphic(pill);
                setText(null);
            }
        });

        // Status — colored pill
        colPostStatus.setCellValueFactory(new PropertyValueFactory<>("moderationStatus"));
        colPostStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); setText(null); return; }
                String[] c = switch (status.toLowerCase()) {
                    case "approved" -> new String[]{"#C6F6D5", "#22543D"};
                    case "flagged"  -> new String[]{"#FED7AA", "#9C4221"};
                    case "pending"  -> new String[]{"#FEF3C7", "#92400E"};
                    default         -> new String[]{"#E2E8F0", "#4A5568"};
                };
                Label pill = new Label(status);
                pill.setStyle(String.format(
                        "-fx-background-color: %s; -fx-text-fill: %s; " +
                        "-fx-background-radius: 10; -fx-padding: 3 10 3 10; " +
                        "-fx-font-size: 11; -fx-font-weight: bold;",
                        c[0], c[1]));
                setGraphic(pill);
                setText(null);
            }
        });

        // Date
        colPostDate.setCellValueFactory(data -> {
            var dt = data.getValue().getCreatedAt();
            return new SimpleStringProperty(dt != null ? dt.format(DATE_FMT) : "");
        });

        // Actions
        colPostActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnView    = makeIconBtn("👁",  "#EDF2F7", "#4A5568", null);
            private final Button btnApprove = makeIconBtn("✓",   "#C6F6D5", "#22543D", null);
            private final Button btnFlag    = makeIconBtn("⚑",   "#FED7AA", "#9C4221", null);
            private final Button btnDelete  = makeIconBtn("🗑",  "#FFF5F5", "#E53E3E", "#FED7D7");
            private final HBox   box        = new HBox(4, btnView, btnApprove, btnFlag, btnDelete);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnView.setOnAction(e -> {
                    Post p = rowItem(tblPosts);
                    if (p != null) AppNavigator.goToWith("/PostDetail.fxml", "postId", p.getId());
                });
                btnApprove.setOnAction(e -> {
                    Post p = rowItem(tblPosts);
                    if (p == null) return;
                    p.setModerationStatus("approved");
                    postService.update(p);
                    reload();
                });
                btnFlag.setOnAction(e -> {
                    Post p = rowItem(tblPosts);
                    if (p == null) return;
                    p.setFlagged(true);
                    p.setModerationStatus("flagged");
                    postService.update(p);
                    reload();
                });
                btnDelete.setOnAction(e -> {
                    Post p = rowItem(tblPosts);
                    if (p == null) return;
                    if (confirm("Delete Post", "Delete \"" + p.getTitle() + "\"?")) {
                        postService.delete(p.getId());
                        reload();
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(!empty && rowItem(tblPosts) != null ? box : null);
            }

            private Post rowItem(TableView<Post> tv) {
                int idx = getIndex();
                return (idx >= 0 && idx < tv.getItems().size()) ? tv.getItems().get(idx) : null;
            }
        });
    }

    // ── Comments table ───────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void setupCommentsTable() {
        tblComments.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // ID — grey bold "#N"
        colCommentId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCommentId.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText("#" + item);
                setStyle("-fx-text-fill: #A0AEC0; -fx-font-weight: bold;");
            }
        });

        // Content
        colCommentContent.setCellValueFactory(new PropertyValueFactory<>("content"));

        // Author
        colCommentAuthor.setCellValueFactory(data ->
                new SimpleStringProperty("user #" + data.getValue().getUserId()));

        // Referenced post (truncated title or "(deleted)")
        colCommentRefPost.setCellValueFactory(data -> {
            Post ref = postService.getById(data.getValue().getPostId());
            if (ref == null) return new SimpleStringProperty("(deleted)");
            String t = ref.getTitle();
            return new SimpleStringProperty(t.length() > 36 ? t.substring(0, 36) + "…" : t);
        });

        // Date
        colCommentDate.setCellValueFactory(data -> {
            var dt = data.getValue().getCreatedAt();
            return new SimpleStringProperty(dt != null ? dt.format(DATE_FMT) : "");
        });

        // Actions — single delete button
        colCommentActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDelete = makeIconBtn("🗑", "#FFF5F5", "#E53E3E", "#FED7D7");

            {
                btnDelete.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx < 0 || idx >= tblComments.getItems().size()) return;
                    Comment c = tblComments.getItems().get(idx);
                    if (confirm("Delete Comment", "Delete this comment?")) {
                        commentService.delete(c.getId());
                        reload();
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                int idx = getIndex();
                setGraphic(!empty && idx >= 0 && idx < tblComments.getItems().size()
                        ? btnDelete : null);
            }
        });
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static Button makeIconBtn(String icon, String bg, String fg, String borderColor) {
        Button btn = new Button(icon);
        String border = borderColor != null
                ? "-fx-border-color: " + borderColor + "; -fx-border-radius: 6;"
                : "";
        btn.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; " +
                "-fx-background-radius: 6; -fx-padding: 3 7 3 7; " +
                "-fx-cursor: hand; -fx-font-size: 12; %s",
                bg, fg, border));
        return btn;
    }

    private boolean confirm(String title, String header) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText("This action cannot be undone.");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
