package mains;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.stage.Stage;
import utils.AppNavigator;

public class MainFX extends Application {

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(new Group(), 1280, 780);

        stage.setTitle("EchoCare Forum");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);

        AppNavigator.setPrimaryStage(stage);
        AppNavigator.goTo("/CommunityFeed.fxml");

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

