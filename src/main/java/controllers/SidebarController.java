package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import utils.AppNavigator;
import utils.Session;

public class SidebarController {

    @FXML private VBox navForumAdmin;
    @FXML private VBox navModeration;
    @FXML private VBox navPublicForum;
    @FXML private VBox navNewPost;

    @FXML private HBox indicForumAdmin;
    @FXML private HBox indicModeration;
    @FXML private HBox indicPublicForum;
    @FXML private HBox indicNewPost;

    @FXML private Label lblUserInitial;
    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;

    private static final String NAV_ACTIVE =
            "-fx-padding: 10 16 10 12; -fx-cursor: hand; " +
            "-fx-background-color: rgba(255,255,255,0.13); " +
            "-fx-background-radius: 0 8 8 0;";
    private static final String INDIC_ACTIVE = "-fx-background-color: #E8956D;";

    @FXML
    public void initialize() {
        String name = Session.CURRENT_USER_DISPLAY_NAME;
        lblUserInitial.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
        lblUserName.setText(name);
        lblUserRole.setText(Session.CURRENT_USER_ROLE.toUpperCase());

        String route = AppNavigator.getCurrentRoute();
        if (route == null) return;
        switch (route) {
            case "/Management.fxml"    -> activate(navForumAdmin,  indicForumAdmin);
            case "/Moderation.fxml"    -> activate(navModeration,  indicModeration);
            case "/CommunityFeed.fxml",
                 "/PostDetail.fxml"    -> activate(navPublicForum, indicPublicForum);
            case "/NewPost.fxml"       -> activate(navNewPost,     indicNewPost);
        }
    }

    private void activate(VBox nav, HBox indicator) {
        nav.setStyle(NAV_ACTIVE);
        indicator.setStyle(INDIC_ACTIVE);
    }

    @FXML
    private void goForumAdmin() {
        AppNavigator.goTo("/Management.fxml");
    }

    @FXML
    private void goModeration() {
        AppNavigator.goTo("/Moderation.fxml");
    }

    @FXML
    private void goPublicForum() {
        AppNavigator.goTo("/CommunityFeed.fxml");
    }

    @FXML
    private void goNewPost() {
        AppNavigator.goTo("/NewPost.fxml");
    }

    @FXML
    private void onExportPDF() {
        System.out.println("[Sidebar] Export PDF stub");
    }

    @FXML
    private void onLogout() {
        System.out.println("[Sidebar] Logout stub");
    }
}