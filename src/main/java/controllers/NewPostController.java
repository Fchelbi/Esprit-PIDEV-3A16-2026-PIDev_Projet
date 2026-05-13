package controllers;

import entities.Category;
import entities.Post;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import services.CategoryService;
import services.PostService;
import utils.AppNavigator;
import utils.Session;

import java.io.File;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class NewPostController {

    @FXML private Label            lblFormTitle;
    @FXML private TextField        tfTitle;
    @FXML private TextArea         taContent;
    @FXML private TextField        tfPhotoPath;
    @FXML private ComboBox<String> cbCategory;
    @FXML private Button           btnSave;

    private final PostService     postService     = new PostService();
    private final CategoryService categoryService = new CategoryService();

    private Post editing = null;
    private File selectedPhotoFile = null;

    private final Map<String, Integer> categoryNameToId = new LinkedHashMap<>();

    @FXML
    public void initialize() {
        try {
            List<Category> cats = categoryService.getAll();
            for (Category c : cats) {
                cbCategory.getItems().add(c.getName());
                categoryNameToId.put(c.getName(), c.getId());
            }
        } catch (Exception e) {
            System.err.println("[NewPost] Failed to load categories: " + e.getMessage());
        }

        Object param = AppNavigator.consumeParam("editPost");
        if (param instanceof Post p) {
            editing = p;
            lblFormTitle.setText("Edit Post");
            btnSave.setText("✔  Update");
            tfTitle.setText(p.getTitle());
            taContent.setText(p.getContent());
            tfPhotoPath.setText(p.getPhoto() != null ? p.getPhoto() : "");
            for (Map.Entry<String, Integer> entry : categoryNameToId.entrySet()) {
                if (entry.getValue() == p.getCategoryId()) {
                    cbCategory.getSelectionModel().select(entry.getKey());
                    break;
                }
            }
        } else if (!cbCategory.getItems().isEmpty()) {
            cbCategory.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void onChoosePhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose Photo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"
        ));

        File file = fc.showOpenDialog(AppNavigator.getPrimaryStage());

        if (file != null) {
            selectedPhotoFile = file;
            tfPhotoPath.setText(file.getAbsolutePath());
        }
    }
@FXML
    private String copyPhotoToUploads(File sourceFile) throws IOException {
        if (sourceFile == null) {
            return null;
        }

        String uploadDirPath = "C:/Users/user/OneDrive - ESPRIT/Bureau/emnaversion/public/uploads/posts";

        File uploadDir = new File(uploadDirPath);
    if (!uploadDir.exists() && !uploadDir.mkdirs()) {
        throw new IOException("Could not create upload directory: " + uploadDir.getAbsolutePath());
    }

        String originalName = sourceFile.getName();
        String extension = "";

        int dotIndex = originalName.lastIndexOf(".");
        if (dotIndex >= 0) {
            extension = originalName.substring(dotIndex);
            originalName = originalName.substring(0, dotIndex);
        }

        String safeName = originalName.replaceAll("[^a-zA-Z0-9-_]", "-");
        String newFileName = safeName + "-" + System.currentTimeMillis() + extension;

        File destinationFile = new File(uploadDir, newFileName);

        Files.copy(
                sourceFile.toPath(),
                destinationFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );

        return "/uploads/posts/" + newFileName;
    }
    @FXML
    private void onSave() {
        String title = tfTitle.getText().trim();
        String content = taContent.getText().trim();

        if (title.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Title is required");
            return;
        }

        if (content.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Content is required");
            return;
        }

        String selectedName = cbCategory.getSelectionModel().getSelectedItem();
        int categoryId;

        if (selectedName != null && categoryNameToId.containsKey(selectedName)) {
            categoryId = categoryNameToId.get(selectedName);
        } else if (!categoryNameToId.isEmpty()) {
            categoryId = categoryNameToId.values().iterator().next();
        } else {
            showAlert(Alert.AlertType.WARNING, "No categories available. Please add a category first.");
            return;
        }

        try {
            String photo = tfPhotoPath.getText().trim();

            if (selectedPhotoFile != null) {
                photo = copyPhotoToUploads(selectedPhotoFile);
            }

            if (editing == null) {
                Post newPost = new Post(
                        categoryId,
                        title,
                        content,
                        LocalDateTime.now(),
                        Session.CURRENT_USER_ID,
                        photo
                );
                postService.add(newPost);
            } else {
                editing.setCategoryId(categoryId);
                editing.setTitle(title);
                editing.setContent(content);
                editing.setPhoto(photo);
                postService.update(editing);
            }

            AppNavigator.goTo("/CommunityFeed.fxml");

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Failed to save post: " + e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        AppNavigator.goTo("/CommunityFeed.fxml");
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.WARNING ? "Validation" : "Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}