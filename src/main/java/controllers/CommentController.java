package controllers;

import entities.Comment;
import entities.Post;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import services.CommentService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class CommentController {

    @FXML private Label                      postTitleLabel;
    @FXML private TableView<Comment>         commentsTable;
    @FXML private TableColumn<Comment, Integer> colId;
    @FXML private TableColumn<Comment, String>  colContent;
    @FXML private TableColumn<Comment, Integer> colUserId;
    @FXML private TableColumn<Comment, String>  colCreatedAt;
    @FXML private TableColumn<Comment, Integer> colParentId;

    private static final int CURRENT_USER_ID = 2;

    @FXML private TextArea  contentField;
    @FXML private TextField parentIdField;

    private final CommentService commentService = new CommentService();
    private Post currentPost;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colContent.setCellValueFactory(new PropertyValueFactory<>("content"));
        colUserId.setCellValueFactory(new PropertyValueFactory<>("userId"));
        colCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colParentId.setCellValueFactory(new PropertyValueFactory<>("parentId"));

        // Fill form when a row is selected
        commentsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> { if (selected != null) fillForm(selected); }
        );
    }

    // Called by PostController before showing this scene
    public void setPost(Post post) {
        this.currentPost = post;
        postTitleLabel.setText("Comments for: " + post.getTitle());
        loadComments();
    }

    private void loadComments() {
        List<Comment> filtered = commentService.getAll().stream()
                .filter(c -> c.getPostId() == currentPost.getId())
                .collect(Collectors.toList());
        commentsTable.setItems(FXCollections.observableArrayList(filtered));
    }

    private void fillForm(Comment comment) {
        contentField.setText(comment.getContent());
        parentIdField.setText(comment.getParentId() != null ? String.valueOf(comment.getParentId()) : "");
    }

    @FXML
    private void onSave() {
        if (contentField.getText().isBlank()) { showAlert("Content is required."); return; }

        Integer parentId = parseNullableInt(parentIdField.getText());
        if (parentId == null && !parentIdField.getText().isBlank()) {
            showAlert("Parent ID must be a number or left empty.");
            return;
        }

        Comment comment = new Comment(
                currentPost.getId(),
                contentField.getText().trim(),
                LocalDateTime.now(),
                CURRENT_USER_ID,
                parentId
        );
        commentService.add(comment);
        onClear();
        loadComments();
    }

    @FXML
    private void onUpdate() {
        Comment selected = commentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Select a comment to update."); return; }
        if (contentField.getText().isBlank()) { showAlert("Content is required."); return; }

        Integer parentId = parseNullableInt(parentIdField.getText());
        if (parentId == null && !parentIdField.getText().isBlank()) {
            showAlert("Parent ID must be a number or left empty.");
            return;
        }

        selected.setContent(contentField.getText().trim());
        selected.setParentId(parentId);
        commentService.update(selected);
        onClear();
        loadComments();
    }

    @FXML
    private void onDelete() {
        Comment selected = commentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Select a comment to delete."); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this comment?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                commentService.delete(selected.getId());
                loadComments();
            }
        });
    }

    @FXML
    private void onRefresh() {
        loadComments();
    }

    @FXML
    private void onClear() {
        contentField.clear();
        parentIdField.clear();
        commentsTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void onBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/posts.fxml"));
            Scene scene = new Scene(loader.load(), 1100, 720);
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            Stage stage = (Stage) commentsTable.getScene().getWindow();
            stage.setScene(scene);
        } catch (Exception e) {
            showAlert("Error returning to posts: " + e.getMessage());
        }
    }

    // Returns null if blank, the integer if valid, or signals invalid if non-blank and non-numeric
    private Integer parseNullableInt(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showAlert(String message) {
        new Alert(Alert.AlertType.WARNING, message, ButtonType.OK).showAndWait();
    }
}