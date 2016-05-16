package me.kenzierocks.ttt;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import me.kenzierocks.ttt.packets.LanClientFinder;

public class Controller {

    private final PlayerData playerData = new PlayerData();
    private final Pattern MATCH_INPUT_TEXT =
            Pattern.compile("^([^ ]+) at ([^ ]+)$");
    @FXML
    private Label currentPlayerLabel;
    @FXML
    private GridPane gameplayPane;
    @FXML
    private Label xScore;
    @FXML
    private Label oScore;
    private Game game;

    @FXML
    public void connectToClient() {
        String defaultChoice = "No client";
        ChoiceDialog<String> clientChooser = new ChoiceDialog<>(defaultChoice,
                LanClientFinder.getInstance().getClients().keySet().stream()
                        .map(e -> e.getKey() + " at " + e.getValue())
                        .collect(Collectors.toList()));
        clientChooser.setTitle("Choose a Client");
        clientChooser.setContentText("Select a client to connect to:");
        clientChooser.setGraphic(null);
        clientChooser.setHeaderText(null);
        clientChooser.showAndWait().ifPresent(s -> {
            if (s.equals(defaultChoice)) {
                // ignore
                return;
            }
            Matcher matcher = MATCH_INPUT_TEXT.matcher(s);
            checkState(matcher.matches(), "wat is in this argument %s", s);
            LanClientFinder.getInstance().negotiateConnection(matcher.group(1))
                    .whenComplete((sock, err) -> {
                        /* java is the if err != null pistol */
                        if (err != null) {
                            err.printStackTrace();
                            /* bail. TODO report this */
                            return;
                        }
                        System.err.println("AQUIRED SOCKET " + sock);
                        try {
                            // lol we don't need a socket.
                            sock.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    });
        });
    }

    @FXML
    public void quit() {
        quit(null, null);
    }

    public void quit(Runnable onClose, Runnable onStay) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Confirm Quit");
        dialog.setContentText("Are you sure you want quit?");
        dialog.setResultConverter(button -> {
            if (button == ButtonType.CANCEL) {
                return false;
            }
            if (button == ButtonType.OK) {
                return true;
            }
            throw new IllegalArgumentException();
        });
        List<ButtonType> buttons = dialog.getDialogPane().getButtonTypes();
        buttons.add(ButtonType.OK);
        buttons.add(ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(doQuit -> {
            if (doQuit) {
                if (onClose != null) {
                    onClose.run();
                }
                // EXPLICIT EXIT
                Platform.exit();
            } else if (!doQuit && onStay != null) {
                onStay.run();
            }
        });
    }

    @FXML
    public void reset() {
        this.playerData.setXScore(0);
        this.playerData.setOScore(0);
        restart();
    }

    @FXML
    public void restart() {
        game = new Game();
        updateScores();
        updateCurrentPlayer();
        gameplayPane.getChildren().stream().map(Label.class::cast)
                .forEach(label -> {
                    label.setText("[  ]");
                    label.setOnMouseClicked(event -> {
                        Integer x = GridPane.getRowIndex(label);
                        Integer y = GridPane.getColumnIndex(label);
                        x = x == null ? 0 : x;
                        y = y == null ? 0 : y;
                        WinState win = game.clickAndWin(x, y);
                        updateScores();
                        updateCurrentPlayer();
                        label.setText(String.valueOf(game.get(x, y)));
                        if (win != WinState.NEUTRAL) {
                            Platform.runLater(() -> {
                                Alert alert =
                                        new Alert(Alert.AlertType.INFORMATION);
                                String text;
                                if (win == WinState.WIN) {
                                    char currentPlayer =
                                            game.getCurrentPlayer();
                                    text = currentPlayer + " is the winner!";
                                    if (currentPlayer == 'X') {
                                        playerData.setXScore(
                                                playerData.getXScore() + 1);
                                    } else if (currentPlayer == 'O') {
                                        playerData.setOScore(
                                                playerData.getOScore() + 1);
                                    } else {
                                        throw new IllegalStateException(
                                                "Player who now? "
                                                        + currentPlayer);
                                    }
                                } else if (win == WinState.TIE) {
                                    text = "Cat's game!";
                                } else {
                                    text = "You did something. WinState." + win;
                                }
                                alert.setContentText(text);
                                alert.showAndWait();
                                restart();
                            });
                        }
                    });
                });
    }

    private void updateScores() {
        xScore.setText(String.valueOf(playerData.getXScore()));
        oScore.setText(String.valueOf(playerData.getOScore()));
    }

    private void updateCurrentPlayer() {
        currentPlayerLabel.setText(String.valueOf(game.getCurrentPlayer()));
    }

    @FXML
    public void initialize() {
        gameplayPane.getChildren().stream().map(Label.class::cast)
                .forEach(Util::attachColorChange);
        Util.attachColorChange(currentPlayerLabel);
        restart();
    }

}
