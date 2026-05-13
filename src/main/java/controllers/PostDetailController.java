package controllers;

import entities.Comment;
import entities.Post;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import services.CommentService;
import services.PostService;
import utils.AppConfig;
import utils.AppNavigator;
import utils.CategoryCache;
import utils.CategoryColor;
import utils.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PostDetailController {

    @FXML private VBox     postBox;
    @FXML private VBox     commentsBox;
    @FXML private TextArea taReply;
    @FXML private Label    lblCommentCount;

    private final PostService    postService    = new PostService();
    private final CommentService commentService = new CommentService();

    private Post post;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        Object param = AppNavigator.consumeParam("postId");
        if (!(param instanceof Integer id)) {
            showError("No post selected.");
            return;
        }
        post = postService.getById(id);
        if (post == null) {
            showError("Post #" + id + " not found.");
            return;
        }
        renderPost();
        renderComments();
    }

    // ── Render post ──────────────────────────────────────────────────────────

    private void renderPost() {
        postBox.getChildren().clear();

        // Avatar
        Circle circle = new Circle(19);
        circle.setStyle("-fx-fill: #E8956D;");
        Label lblAvatarId = new Label(String.valueOf(post.getUserId()));
        lblAvatarId.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11;");
        StackPane avatar = new StackPane(circle, lblAvatarId);

        // User + date
        Label lblUser = new Label("user #" + post.getUserId());
        lblUser.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #2D3748;");
        Label lblDate = new Label(post.getCreatedAt() != null ? post.getCreatedAt().format(DATE_FMT) : "");
        lblDate.setStyle("-fx-font-size: 11; -fx-text-fill: #A0AEC0;");
        VBox userBox = new VBox(2, lblUser, lblDate);
        userBox.setAlignment(Pos.CENTER_LEFT);

        // Category pill
        String catName  = CategoryCache.nameOf(post.getCategoryId());
        String catColor = CategoryColor.forId(post.getCategoryId());
        Label lblCat = new Label(catName);
        lblCat.setStyle(String.format(
                "-fx-background-color: %s33; -fx-text-fill: %s; " +
                "-fx-border-color: %s; -fx-border-radius: 12; -fx-background-radius: 12; " +
                "-fx-padding: 3 12 3 12; -fx-font-size: 11; -fx-font-weight: bold;",
                catColor, catColor, catColor));

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, avatar, userBox, headerSpacer, lblCat);
        header.setAlignment(Pos.CENTER_LEFT);

        // Title
        Label lblTitle = new Label(post.getTitle());
        lblTitle.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #2D3748;");
        lblTitle.setWrapText(true);

        // Content
        Label lblContent = new Label(post.getContent());
        lblContent.setStyle("-fx-font-size: 14; -fx-text-fill: #4A5568; -fx-line-spacing: 3;");
        lblContent.setWrapText(true);

        postBox.getChildren().addAll(header, lblTitle, lblContent);
        buildPhoto(post).ifPresent(iv -> postBox.getChildren().add(iv));
    }

    private Optional<ImageView> buildPhoto(Post p) {
        String photo = p.getPhoto();

        if (photo == null || photo.isBlank()) {
            return Optional.empty();
        }

        if (photo.toLowerCase().endsWith(".avif")) {
            return Optional.empty();
        }

        try {
            String symfonyPublicPath = "C:/Users/user/OneDrive - ESPRIT/Bureau/emnaversion/public";

            String cleanPhoto = photo.trim();

            // Remove first slash only for clean path joining
            if (cleanPhoto.startsWith("/")) {
                cleanPhoto = cleanPhoto.substring(1);
            }

            java.io.File imageFile = new java.io.File(symfonyPublicPath, cleanPhoto);



            if (!imageFile.exists()) {
                return Optional.empty();
            }

            Image image = new Image(imageFile.toURI().toString());

            if (image.isError()) {
                System.err.println("[Photo] Failed to load: " + imageFile.getAbsolutePath() + " → " + image.getException());
                return Optional.empty();
            }

            ImageView iv = new ImageView(image);
            iv.setFitWidth(700);
            iv.setPreserveRatio(true);

            return Optional.of(iv);

        } catch (Exception e) {
            System.err.println("[Photo] Exception loading photo: " + e.getMessage());
            return Optional.empty();
        }
    }
    // ── Render comments ──────────────────────────────────────────────────────

    private void renderComments() {
        commentsBox.getChildren().clear();

        List<Comment> comments = commentService.getAll().stream()
                .filter(c -> c.getPostId() == post.getId())
                .sorted(Comparator.comparing(Comment::getCreatedAt))
                .toList();

        lblCommentCount.setText(comments.size() + " comments");

        if (comments.isEmpty()) {
            Label empty = new Label("Be the first to reply.");
            empty.setStyle("-fx-text-fill: #A0AEC0; -fx-padding: 18 0 18 0;");
            commentsBox.getChildren().add(empty);
        } else {
            for (Comment c : comments) {
                commentsBox.getChildren().add(buildCommentCard(c));
            }
        }
    }

    private HBox buildCommentCard(Comment c) {
        // Blue avatar
        Circle circle = new Circle(17);
        circle.setStyle("-fx-fill: #4A6FA5;");
        Label lblAvatarId = new Label(String.valueOf(c.getUserId()));
        lblAvatarId.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10;");
        StackPane avatar = new StackPane(circle, lblAvatarId);

        // Meta row: "user #N" bold + " · date" grey
        Label lblUser = new Label("user #" + c.getUserId());
        lblUser.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #2D3748;");
        String dateStr = c.getCreatedAt() != null ? "  ·  " + c.getCreatedAt().format(DATE_FMT) : "";
        Label lblMeta = new Label(dateStr);
        lblMeta.setStyle("-fx-font-size: 11; -fx-text-fill: #A0AEC0;");
        HBox metaRow = new HBox(0, lblUser, lblMeta);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        // Content
        Label lblContent = new Label(c.getContent());
        lblContent.setStyle("-fx-font-size: 13; -fx-text-fill: #4A5568;");
        lblContent.setWrapText(true);

        VBox textBox = new VBox(4, metaRow, lblContent);
        textBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        //like dislike comments
        String reactionStyle =
                "-fx-background-color: #F7FAFC; -fx-border-color: #E2E8F0; " +
                        "-fx-background-radius: 10; -fx-border-radius: 10; " +
                        "-fx-cursor: hand; -fx-padding: 4 10 4 10; -fx-font-size: 11;";

        Button btnLike = new Button("👍 " + commentService.countCommentLikes(c.getId()));
        Button btnDislike = new Button("👎 " + commentService.countCommentDislikes(c.getId()));

        btnLike.setStyle(reactionStyle);
        btnDislike.setStyle(reactionStyle);

        btnLike.setOnAction(e -> handleLikeComment(c));
        btnDislike.setOnAction(e -> handleDislikeComment(c));

        // Edit button — comment owner only
        Button btnEdit = new Button("✎");
        btnEdit.setStyle(
                "-fx-background-color: #EDF2F7; -fx-text-fill: #4A5568; " +
                "-fx-border-radius: 6; -fx-background-radius: 6; " +
                "-fx-cursor: hand; -fx-padding: 4 8 4 8;");
        btnEdit.setOnAction(e -> handleEditComment(c));

        boolean isCommentOwner = c.getUserId() == Session.CURRENT_USER_ID;
        btnEdit.setVisible(isCommentOwner);
        btnEdit.setManaged(isCommentOwner);

        // Delete button — owner or admin
        Button btnDelete = new Button("🗑");
        btnDelete.setStyle(
                "-fx-background-color: #FFF5F5; -fx-text-fill: #E53E3E; " +
                "-fx-border-color: #FED7D7; -fx-border-radius: 6; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 4 8 4 8;");
        btnDelete.setOnAction(e -> confirmDeleteComment(c));

        boolean canDelete = Session.isAdmin() || c.getUserId() == Session.CURRENT_USER_ID;
        btnDelete.setVisible(canDelete);
        btnDelete.setManaged(canDelete);

        HBox card = new HBox(12, avatar, textBox, btnLike, btnDislike, btnEdit, btnDelete);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-padding: 14; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 6, 0, 0, 1);");
        return card;
    }
    private void handleLikeComment(Comment c) {
        commentService.likeComment(c.getId(), Session.CURRENT_USER_ID);
        renderComments();
    }

    private void handleDislikeComment(Comment c) {
        commentService.dislikeComment(c.getId(), Session.CURRENT_USER_ID);
        renderComments();
    }

    private void handleEditComment(Comment c) {
        TextInputDialog dialog = new TextInputDialog(c.getContent());
        dialog.setTitle("Edit Comment");
        dialog.setHeaderText(null);
        dialog.setContentText("Edit your comment:");
        dialog.showAndWait().ifPresent(newText -> {
            String trimmed = newText.trim();
            if (trimmed.isEmpty() || trimmed.equals(c.getContent())) return;
            c.setContent(trimmed);
            commentService.update(c);
            renderComments();
        });
    }

    private void confirmDeleteComment(Comment c) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Comment");
        confirm.setHeaderText("Delete this comment?");
        confirm.setContentText("This action cannot be undone.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            commentService.delete(c.getId());
            renderComments();
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @FXML
    private void onBack() {
        AppNavigator.goTo("/CommunityFeed.fxml");
    }

    @FXML
    private void onPostReply() {
        String text = taReply.getText().trim();
        if (text.isEmpty() || post == null) return;
        try {
            commentService.add(new Comment(post.getId(), text, LocalDateTime.now(),
                                           Session.CURRENT_USER_ID, null));
            taReply.clear();
            renderComments();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("Failed to post reply: " + e.getMessage());
            alert.showAndWait();
        }
    }

    // ── Error display ─────────────────────────────────────────────────────────

    private void showError(String msg) {
        postBox.getChildren().clear();
        Label lbl = new Label(msg);
        lbl.setStyle("-fx-text-fill: #C53030; -fx-font-size: 14; -fx-padding: 20;");
        postBox.getChildren().add(lbl);
    }
}