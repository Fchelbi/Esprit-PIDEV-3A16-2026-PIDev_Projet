package utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class AppNavigator {

    private static Stage primaryStage;
    private static String currentRoute;
    private static final Map<String, Object> params = new HashMap<>();

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static String getCurrentRoute() {
        return currentRoute;
    }

    public static void goTo(String fxmlPath) {
        if (primaryStage == null) {
            System.err.println("[AppNavigator] Primary stage not set.");
            return;
        }
        URL resource = AppNavigator.class.getResource(fxmlPath);
        if (resource == null) {
            System.err.println("[AppNavigator] FXML not found: " + fxmlPath);
            return;
        }
        // Set route before loading so controllers can read it in initialize()
        currentRoute = fxmlPath;
        try {
            Parent root = FXMLLoader.load(resource);
            primaryStage.getScene().setRoot(root);
        } catch (IOException e) {
            System.err.println("[AppNavigator] Failed to load FXML: " + fxmlPath);
            System.err.println(e.getMessage());
        }
    }

    public static void goToWith(String fxmlPath, String key, Object value) {
        params.put(key, value);
        goTo(fxmlPath);
    }

    public static Object consumeParam(String key) {
        return params.remove(key);
    }
}