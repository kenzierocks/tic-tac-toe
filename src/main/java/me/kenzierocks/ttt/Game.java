package me.kenzierocks.ttt;

import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Game {

    private enum Player {
        X('X'), O('O');

        public final char character;

        private Player(char character) {
            this.character = character;
        }

    }

    private enum Direction {
        N(0, -1), S(0, 1), W(-1, 0), E(1, 0), NW(-1, -1), NE(1, -1), SW(-1, 1),
        SE(1, 1);

        public final int dx;
        public final int dy;

        private Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    private final char[][] data = new char[3][3];
    private Player currentPlayer = Player.X; // X goes first! ALWAYS.

    public Game() {
    }

    public char getCurrentPlayer() {
        return currentPlayer.character;
    }

    public WinState clickAndWin(int x, int y) {
        if (this.data[x][y] != '\0') {
            return WinState.NEUTRAL;
        }
        this.data[x][y] = this.currentPlayer.character;
        if (winning(getCurrentPlayer(), x, y)) {
            return WinState.WIN;
        }
        if (allFilled()) {
            return WinState.TIE;
        }
        this.currentPlayer =
                this.currentPlayer == Player.X ? Player.O : Player.X;
        return WinState.NEUTRAL;
    }

    private boolean allFilled() {
        boolean anyOff = false;
        for (int i = 0; i < this.data.length; i++) {
            char[] row = this.data[i];
            for (int j = 0; j < row.length; j++) {
                char mark = row[j];
                anyOff |= mark != Player.X.character
                        && mark != Player.O.character;
            }
        }
        return !anyOff;
    }

    private char getOr0(Direction dir, int times, int x, int y) {
        x += dir.dx * times;
        y += dir.dy * times;
        if (x >= this.data.length || x < 0 || y >= this.data[0].length
                || y < 0) {
            return '\0';
        }
        return get(x, y);
    }

    private boolean winning(char p, int x, int y) {
        Stream<Direction> directions = Stream.of(Direction.values());
        return directions.anyMatch(dir -> IntStream.rangeClosed(-2, 2)
                .map(t -> getOr0(dir, t, x, y)).map(c -> c == p ? 1 : 0)
                .sum() == 3);
    }

    public char get(int x, int y) {
        return this.data[x][y];
    }

}