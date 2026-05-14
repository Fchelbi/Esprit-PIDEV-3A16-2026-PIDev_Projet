package mains;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URL;
import utils.AppNavigator;

public class MainFX extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        AppNavigator.setPrimaryStage(primaryStage);

        System.out.println(". Starting the application...");

        // We choose Login.fxml as the starting page
        String fxmlFile = "/Login.fxml";

        try {
            // Find the FXML file
            URL fxmlUrl = getClass().getResource(fxmlFile);

            // Check if it exists
            if (fxmlUrl == null) {
                System.out.println(" ERROR: Could not find '" + fxmlFile + "'! Check your resources folder.");
                return;
            } else {
                System.out.println(" Found it! Path: " + fxmlUrl);
            }

            // Load the FXML
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            System.out.println(". FXML loaded successfully.");

            // Create and set the scene
            Scene scene = new Scene(root);

            // Apply CSS if it exists
            try {
                URL cssUrl = getClass().getResource("/style.css");
                if (cssUrl != null) {
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                    System.out.println(" Stylesheet applied.");
                }
            } catch (Exception e) {
                System.out.println(" Note: style.css not found, skipping styles.");
            }

            primaryStage.setTitle("EchoCare");
            primaryStage.setScene(scene);
            primaryStage.show();
            System.out.println(". Window is now open!");

        } catch (IOException e) {
            System.out.println(" FATAL ERROR: Loading FXML failed!");
            e.printStackTrace();
        }
    }
}