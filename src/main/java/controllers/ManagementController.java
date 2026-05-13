package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import services.CommentService;
import services.PostService;

public class ManagementController {

    @FXML private Label lblTotalPosts;
    @FXML private Label lblTotalComments;
    @FXML private Label lblActiveUsers;
    @FXML private Label lblFlaggedPosts;

    @FXML private Label lblPostLikes;
    @FXML private Label lblPostDislikes;
    @FXML private Label lblCommentLikes;
    @FXML private Label lblCommentDislikes;

    private final PostService postService = new PostService();
    private final CommentService commentService = new CommentService();

    @FXML
    public void initialize() {
        loadStats();
    }

    private void loadStats() {
        lblTotalPosts.setText(String.valueOf(postService.countPosts()));
        lblTotalComments.setText(String.valueOf(commentService.countComments()));
        lblActiveUsers.setText(String.valueOf(postService.countActiveForumUsers()));
        lblFlaggedPosts.setText(String.valueOf(postService.countFlaggedPosts()));

        lblPostLikes.setText(String.valueOf(postService.totalPostLikes()));
        lblPostDislikes.setText(String.valueOf(postService.totalPostDislikes()));
        lblCommentLikes.setText(String.valueOf(commentService.totalCommentLikes()));
        lblCommentDislikes.setText(String.valueOf(commentService.totalCommentDislikes()));
    }

    @FXML
    private void onExportPdf() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("PDF Export");
        alert.setHeaderText(null);
        alert.setContentText("PDF export will be added in the next feature.");
        alert.showAndWait();
    }
}