package gui;


	import javafx.application.Application;
	import javafx.scene.Scene;
	import javafx.stage.Stage;

	public class Main extends Application {

	    public static Stage mainStage;

	    @Override
	    public void start(Stage stage) {
	        mainStage = stage;
	        stage.setTitle("DoorDasH: Scare vs Laugh Touchdown");
	        stage.setResizable(false);
	        GameView.showStartScreen();
	    }

	    public static void main(String[] args) {
	        launch(args);
	    }

}
