package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import services.CommentService;
import services.PostService;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;

import javafx.stage.FileChooser;
import services.PdfExportService;
import java.io.File;

public class ManagementController {

    @FXML private Label lblTotalPosts;
    @FXML private Label lblTotalComments;
    @FXML private Label lblActiveUsers;
    @FXML private Label lblFlaggedPosts;

    @FXML private Label lblPostLikes;
    @FXML private Label lblPostDislikes;
    @FXML private Label lblCommentLikes;
    @FXML private Label lblCommentDislikes;

    @FXML private LineChart<String, Number> postsActivityChart;
    @FXML private PieChart postsByCategoryChart;

    private final PdfExportService pdfExportService = new PdfExportService();
    private final PostService postService = new PostService();
    private final CommentService commentService = new CommentService();

    @FXML
    public void initialize() {
        loadStats();
        loadPostsActivityChart();
        loadPostsByCategoryChart();
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
    private void loadPostsActivityChart() {
        postsActivityChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Posts");

        for (PostService.DailyPostCount item : postService.getPostsActivityLast30Days()) {
            series.getData().add(new XYChart.Data<>(item.day(), item.count()));
        }

        postsActivityChart.getData().add(series);
    }

    private void loadPostsByCategoryChart() {
        postsByCategoryChart.getData().clear();

        for (PostService.CategoryPostCount item : postService.getPostsByCategory()) {
            if (item.count() > 0) {
                postsByCategoryChart.getData().add(
                        new PieChart.Data(item.category(), item.count())
                );
            }
        }
    }

    @FXML
    private void onExportPdf() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Forum PDF Report");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
            );
            fileChooser.setInitialFileName("forum-statistics-report.pdf");

            File file = fileChooser.showSaveDialog(null);

            if (file == null) {
                return;
            }

            pdfExportService.exportForumReport(
                    file.getAbsolutePath(),
                    postService.countPosts(),
                    commentService.countComments(),
                    postService.countActiveForumUsers(),
                    postService.countFlaggedPosts(),
                    postService.totalPostLikes(),
                    postService.totalPostDislikes(),
                    commentService.totalCommentLikes(),
                    commentService.totalCommentDislikes(),
                    postService
            );

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("PDF Export");
            alert.setHeaderText(null);
            alert.setContentText("PDF exported successfully:\n" + file.getAbsolutePath());
            alert.showAndWait();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("PDF Export Error");
            alert.setHeaderText(null);
            alert.setContentText("Failed to export PDF:\n" + e.getMessage());
            alert.showAndWait();
        }
    }
}