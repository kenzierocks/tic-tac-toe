package me.kenzierocks.ttt;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

	public static void main(String[] args) {
		launch(args);
	}

	private Parent root;

	@Override
	public void start(Stage stage) throws Exception {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/ttt.fxml"));
		root = loader.load();
		Controller controller = loader.getController();
		root.autosize();
		Scene scene = new Scene(root);

		stage.setTitle("Tic-Tac-Toe");
		stage.setScene(scene);
		Platform.setImplicitExit(false);
		stage.setOnCloseRequest(event -> {
			controller.quit(null, event::consume);
		});
		stage.show();
	}

}