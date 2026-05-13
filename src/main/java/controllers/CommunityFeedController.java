package controllers;

import entities.Post;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import services.PostService;
import utils.AppNavigator;
import utils.CategoryCache;
import utils.CategoryColor;
import utils.Session;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CommunityFeedController {

    @FXML private VBox  postsContainer;
    @FXML private Label lblPostCount;

    private final PostService postService = new PostService();

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    @FXML
    public void initialize() {
        loadFeed();
    }

    @FXML
    private void onCreatePost() {
        AppNavigator.goTo("/NewPost.fxml");
    }

    @FXML
    private void onRefresh() {
        loadFeed();
    }

    // ── Feed loading ─────────────────────────────────────────────────────────

    private void loadFeed() {
        try {
            postsContainer.getChildren().clear();
            List<Post> posts = postService.getAll();
            posts.sort(Comparator.comparing(Post::getCreatedAt).reversed());
            lblPostCount.setText(posts.size() + " posts");
            for (Post post : posts) {
                postsContainer.getChildren().add(buildPostCard(post));
            }
        } catch (Exception e) {
            System.err.println("[CommunityFeed] Failed to load posts: " + e.getMessage());
        }
    }

    // ── Card builder ─────────────────────────────────────────────────────────

    private VBox buildPostCard(Post p) {
        VBox card = new VBox(12);
        card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 14; " +
                "-fx-padding: 22; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 12, 0, 0, 2);");

        card.getChildren().addAll(
                buildCardHeader(p),
                buildTitle(p),
                buildContent(p));

        buildPhoto(p).ifPresent(iv -> card.getChildren().add(iv));
        card.getChildren().add(buildActionsRow(p));
        return card;
    }

    private HBox buildCardHeader(Post p) {
        // Avatar
        Circle circle = new Circle(19);
        circle.setStyle("-fx-fill: #E8956D;");
        Label lblId = new Label(String.valueOf(p.getUserId()));
        lblId.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11;");
        StackPane avatar = new StackPane(circle, lblId);

        // User + date
        Label lblUser = new Label("user #" + p.getUserId());
        lblUser.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #2D3748;");
        Label lblDate = new Label(p.getCreatedAt() != null ? p.getCreatedAt().format(DATE_FMT) : "");
        lblDate.setStyle("-fx-font-size: 11; -fx-text-fill: #A0AEC0;");
        VBox userBox = new VBox(2, lblUser, lblDate);
        userBox.setAlignment(Pos.CENTER_LEFT);

        // Category pill
        String catName  = CategoryCache.nameOf(p.getCategoryId());
        String catColor = CategoryColor.forId(p.getCategoryId());
        Label lblCat = new Label(catName);
        lblCat.setStyle(String.format(
                "-fx-background-color: %s33; -fx-text-fill: %s; " +
                "-fx-border-color: %s; -fx-border-radius: 12; -fx-background-radius: 12; " +
                "-fx-padding: 3 12 3 12; -fx-font-size: 11; -fx-font-weight: bold;",
                catColor, catColor, catColor));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, avatar, userBox, spacer, lblCat);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Label buildTitle(Post p) {
        Label lbl = new Label(p.getTitle());
        lbl.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #2D3748;");
        lbl.setWrapText(true);
        return lbl;
    }

    private Label buildContent(Post p) {
        Label lbl = new Label(p.getContent());
        lbl.setStyle("-fx-font-size: 13; -fx-text-fill: #4A5568;");
        lbl.setWrapText(true);
        return lbl;
    }

    private Optional<ImageView> buildPhoto(Post p) {
        String photo = p.getPhoto();
        if (photo == null || photo.isBlank()) return Optional.empty();
        try {
            ImageView iv = new ImageView(new Image(photo, true));
            iv.setFitWidth(600);
            iv.setPreserveRatio(true);
            return Optional.of(iv);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private HBox buildActionsRow(Post p) {
        String pillStyle =
                "-fx-background-color: #F7FAFC; -fx-border-color: #E2E8F0; " +
                "-fx-background-radius: 12; -fx-border-radius: 12; " +
                "-fx-padding: 4 12 4 12; -fx-font-size: 12;";
        Label lblLikes    = new Label("👍 " + p.getLikes());
        Label lblDislikes = new Label("👎 " + p.getDislikes());
        lblLikes.setStyle(pillStyle);
        lblDislikes.setStyle(pillStyle);

        // Flag — visible to non-owners on posts that aren't already flagged
        Button btnFlag = new Button("🚩  Flag");
        btnFlag.setStyle(
                "-fx-background-color: #FFF7ED; -fx-text-fill: #C05621; " +
                "-fx-border-color: #FBD38D; -fx-border-radius: 8; " +
                "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 6 14 6 14;");
        btnFlag.setOnAction(e -> handleFlag(p));

        boolean isOwner = p.getUserId() == Session.CURRENT_USER_ID;
        boolean alreadyFlagged = p.isFlagged();
        btnFlag.setVisible(!isOwner && !alreadyFlagged);
        btnFlag.setManaged(!isOwner && !alreadyFlagged);

        // Edit / Delete — owner only
        Button btnEdit = new Button("✎  Edit");
        btnEdit.setStyle(
                "-fx-background-color: #EDF2F7; -fx-text-fill: #4A5568; " +
                "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 6 14 6 14;");
        btnEdit.setOnAction(e -> AppNavigator.goToWith("/NewPost.fxml", "editPost", p));

        Button btnDelete = new Button("🗑  Delete");
        btnDelete.setStyle(
                "-fx-background-color: #FFF5F5; -fx-text-fill: #E53E3E; " +
                "-fx-border-color: #FED7D7; -fx-border-radius: 8; " +
                "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 6 14 6 14;");
        btnDelete.setOnAction(e -> handleDelete(p));

        btnEdit.setVisible(isOwner);
        btnEdit.setManaged(isOwner);
        btnDelete.setVisible(isOwner);
        btnDelete.setManaged(isOwner);

        Button btnDetail = new Button("→  Detail");
        btnDetail.setStyle(
                "-fx-background-color: #E8956D; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 6 14 6 14;");
        btnDetail.setOnAction(e -> AppNavigator.goToWith("/PostDetail.fxml", "postId", p.getId()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(8, lblLikes, lblDislikes, spacer, btnFlag, btnEdit, btnDelete, btnDetail);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Delete / Flag confirmations ───────────────────────────────────────────

    private void handleDelete(Post p) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Post");
        confirm.setHeaderText("Delete \"" + p.getTitle() + "\"?");
        confirm.setContentText("This action cannot be undone.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            postService.delete(p.getId());
            loadFeed();
        }
    }

    private void handleFlag(Post p) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Flag Post");
        confirm.setHeaderText("Flag \"" + p.getTitle() + "\"?");
        confirm.setContentText("This will send the post for admin review.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            p.setFlagged(true);
            p.setModerationStatus("flagged");
            postService.update(p);
            loadFeed();
        }
    }
}