package controllers;

import entities.Category;
import entities.Post;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import services.CategoryService;
import services.PostService;

import java.time.LocalDateTime;
import java.util.List;

public class PostController {

    @FXML private TableView<Post> postsTable;
    @FXML private TableColumn<Post, Integer> colId;
    @FXML private TableColumn<Post, String>  colTitle;
    @FXML private TableColumn<Post, Integer> colCategory;
    @FXML private TableColumn<Post, String>  colContent;
    @FXML private TableColumn<Post, String>  colCreatedAt;
    @FXML private TableColumn<Post, String>  colStatus;
    @FXML private TableColumn<Post, Integer> colUserId;

    private static final int CURRENT_USER_ID = 2;

    @FXML private TextField          titleField;
    @FXML private ComboBox<Category> categoryCombo;
    @FXML private TextField          photoField;
    @FXML private TextArea           contentField;

    private final PostService     postService     = new PostService();
    private final CategoryService categoryService = new CategoryService();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("categoryId"));
        colContent.setCellValueFactory(new PropertyValueFactory<>("content"));
        colCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("moderationStatus"));
        colUserId.setCellValueFactory(new PropertyValueFactory<>("userId"));

        // Fill form when a row is selected
        postsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> { if (selected != null) fillForm(selected); }
        );

        loadCategories();
        loadPosts();
    }

    private void loadCategories() {
        List<Category> list = categoryService.getAll();
        categoryCombo.setItems(FXCollections.observableArrayList(list));

        // Show category name instead of toString()
        categoryCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        categoryCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
    }

    private void loadPosts() {
        postsTable.setItems(FXCollections.observableArrayList(postService.getAll()));
    }

    private void fillForm(Post post) {
        titleField.setText(post.getTitle());
        contentField.setText(post.getContent());
        photoField.setText(post.getPhoto() != null ? post.getPhoto() : "");
        for (Category c : categoryCombo.getItems()) {
            if (c.getId() == post.getCategoryId()) {
                categoryCombo.setValue(c);
                break;
            }
        }
    }

    @FXML
    private void onSave() {
        if (titleField.getText().isBlank() || contentField.getText().isBlank()) {
            showAlert("Title and content are required.");
            return;
        }
        Category selectedCategory = categoryCombo.getValue();
        if (selectedCategory == null) {
            showAlert("Please select a category.");
            return;
        }

        Post post = new Post(
                selectedCategory.getId(),
                titleField.getText().trim(),
                contentField.getText().trim(),
                LocalDateTime.now(),
                CURRENT_USER_ID,
                photoField.getText().isBlank() ? null : photoField.getText().trim()
        );
        postService.add(post);
        onClear();
        loadPosts();
    }

    @FXML
    private void onUpdate() {
        Post selected = postsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Select a post to update."); return; }
        if (titleField.getText().isBlank() || contentField.getText().isBlank()) {
            showAlert("Title and content are required.");
            return;
        }
        Category selectedCategory = categoryCombo.getValue();
        if (selectedCategory == null) { showAlert("Please select a category."); return; }

        selected.setTitle(titleField.getText().trim());
        selected.setContent(contentField.getText().trim());
        selected.setCategoryId(selectedCategory.getId());
        selected.setUserId(CURRENT_USER_ID);
        selected.setPhoto(photoField.getText().isBlank() ? null : photoField.getText().trim());

        postService.update(selected);
        onClear();
        loadPosts();
    }

    @FXML
    private void onDelete() {
        Post selected = postsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Select a post to delete."); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete post \"" + selected.getTitle() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                postService.delete(selected.getId());
                loadPosts();
            }
        });
    }

    @FXML
    private void onRefresh() {
        loadPosts();
    }

    @FXML
    private void onClear() {
        titleField.clear();
        contentField.clear();
        photoField.clear();
        categoryCombo.setValue(null);
        postsTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void onViewComments() {
        Post selected = postsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Select a post to view its comments."); return; }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/comments.fxml"));
            Scene scene = new Scene(loader.load(), 1100, 720);
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

            CommentController controller = loader.getController();
            controller.setPost(selected);

            Stage stage = (Stage) postsTable.getScene().getWindow();
            stage.setScene(scene);
        } catch (Exception e) {
            showAlert("Error opening comments: " + e.getMessage());
        }
    }

    private void showAlert(String message) {
        new Alert(Alert.AlertType.WARNING, message, ButtonType.OK).showAndWait();
    }
}