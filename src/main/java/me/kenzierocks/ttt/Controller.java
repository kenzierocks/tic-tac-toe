package me.kenzierocks.ttt;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.eventbus.Subscribe;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.GridPane;
import me.kenzierocks.ttt.event.EventBuses;
import me.kenzierocks.ttt.event.IdentChangeEvent;
import me.kenzierocks.ttt.packets.LanClientFinder;
import me.kenzierocks.ttt.packets.NetworkInterfaceManager;
import me.kenzierocks.ttt.packets.NetworkManager;

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
    @FXML
    private Label idHolder;
    @FXML
    private MenuItem connectMenuItem;
    private Game game;

    @FXML
    public void connectToClient() {
        List<String> clients = LanClientFinder.getInstance().getClients()
                .keySet().stream().map(e -> e.getKey() + " at " + e.getValue())
                .collect(Collectors.toList());
        if (clients.isEmpty()) {
            Alert alert = Util.newStandardAlert(AlertType.INFORMATION);
            alert.setHeaderText("There are no other clients.");
            alert.showAndWait();
            return;
        }
        ChoiceDialog<String> clientChooser = Util
                .configureDialog(new ChoiceDialog<>(clients.get(0), clients));
        clientChooser.setTitle("Choose a Client");
        clientChooser.setContentText("Select a client to connect to:");
        clientChooser.setGraphic(null);
        clientChooser.setHeaderText(null);
        clientChooser.showAndWait().ifPresent(s -> {
            Matcher matcher = MATCH_INPUT_TEXT.matcher(s);
            checkState(matcher.matches(), "wat is in this argument %s", s);
            LanClientFinder.getInstance()
                    .negotiateConnection(matcher.group(1).trim())
                    .whenComplete((sock, err) -> {
                        /* java is the if err != null pistol */
                        if (err != null) {
                            err.printStackTrace();
                            /* bail. TODO report this */
                            return;
                        }
                        connectMenuItem.setDisable(true);
                        try {
                            new NetworkManager(sock);
                        } catch (IOException e1) {
                        }
                    });
        });
    }

    @FXML
    public void resetId() {
        Identification.getInstance().newUuid();
    }

    @FXML
    public void quit() {
        quit(null, null);
    }

    public void quit(Runnable onClose, Runnable onStay) {
        Dialog<Boolean> dialog = Util.newOkCancelDialog();
        dialog.setTitle("Confirm Quit");
        dialog.setContentText("Are you sure you want quit?");
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
                                Alert alert = Util.newStandardAlert(
                                        AlertType.INFORMATION);
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
        EventBuses.DEFAULT.register(this);
        // initialize network
        if (NetworkInterfaceManager.getInstance().selectedProperty()
                .getValue() == null) {
            selectNetworkInterface();
        }
        LanClientFinder.getInstance().getClients();
        gameplayPane.getChildren().stream().map(Label.class::cast)
                .forEach(Util::attachColorChange);
        Util.attachColorChange(currentPlayerLabel);
        restart();
    }

    @Subscribe
    public void onIdentificationChange(IdentChangeEvent event) {
        idHolder.setText(event.newId.toString().replace('-', '\n'));
    }

    @FXML
    public void selectNetworkInterface() {
        NetworkInterfaceManager nim = NetworkInterfaceManager.getInstance();
        List<NetworkInterface> interfaces = nim.getInterfaces();
        if (interfaces.isEmpty()) {
            Alert alert = Util.newStandardAlert(AlertType.ERROR);
            alert.setHeaderText(
                    "You cannot play this game, you have no network.");
            alert.showAndWait();
            throw new RuntimeException("player had no network");
        }
        NetworkInterface def = interfaces.get(0);
        if (nim.selectedProperty().get() != null) {
            def = nim.getSelected();
        }
        ChoiceDialog<NetworkInterface> dialog =
                Util.configureDialog(new ChoiceDialog<>(def, interfaces));
        dialog.setGraphic(null);
        dialog.setHeaderText(null);
        dialog.showAndWait().ifPresent(nim::select);
    }

    public void promptForConnection(String client, String addr, int port) {
        Dialog<Boolean> dialog = Util.newOkCancelDialog();
        dialog.setTitle("Incoming Connection");
        dialog.setContentText("Accept incoming connection from " + addr + ":"
                + port + ", client " + client + "?");
        dialog.showAndWait().ifPresent(doConnect -> {
            if (doConnect) {
                connectMenuItem.setDisable(true);
                try {
                    NetworkManager conn = new NetworkManager(addr, port);
                    Util.runLaterLoop(t -> true, () -> {
                        conn.getNextPacket().ifPresent(x -> {
                            System.err.println(x);
                        });
                        return null;
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
