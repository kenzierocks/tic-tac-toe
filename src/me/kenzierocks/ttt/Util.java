package me.kenzierocks.ttt;

import java.util.HashMap;
import java.util.Map;

import javafx.scene.control.Labeled;

public class Util {

	private static final Map<String, String> COLORS = new HashMap<>();
	static {
		COLORS.put("X", "red");
		COLORS.put("O", "green");
		COLORS.put("[  ]", "blue");
	}

	public static void attachColorChange(Labeled label) {
		label.textProperty().addListener((ob, oldV, newV) -> {
			String color = COLORS.get(newV);
			String style = "-fx-text-inner-color: " + color + "; -fx-text-fill: " + color;
			label.setStyle(style);
		});
	}

}