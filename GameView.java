package gui;

import game.engine.Board;
import game.engine.Role;
import game.engine.cards.Card;
import game.engine.cells.*;
import game.engine.monsters.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class GameView {

    // ── Colours ───────────────────────────────────────────────────────────────
    static final String COL_NORMAL    = "#fffde7";
    static final String COL_DOOR_S    = "#ef9a9a";
    static final String COL_DOOR_L    = "#a5d6a7";
    static final String COL_DOOR_USED = "#b0bec5";
    static final String COL_MONSTER   = "#90caf9";
    static final String COL_CARD      = "#ef9a9a";
    static final String COL_CONVEYOR  = "#c8e6c9";
    static final String COL_SOCK      = "#ffcc80";
    static final String COL_BOARD_BG  = "#f5f5f5";

    static Pane[]          cellPanes = new Pane[100];
    static GameController  controller;

    // We track whose turn it is ourselves
    static boolean isMyTurn = true;

    // Prevents any further turns after the game ends
    static boolean gameOver = false;

    // ── UI labels ─────────────────────────────────────────────────────────────
    static Label turnLabel;
    static Label playerNameLabel, playerRoleLabel, playerCurRoleLabel;
    static Label playerTypeLabel,  playerEnergyLabel, playerPosLabel, playerStatusLabel;
    static Label oppNameLabel,    oppRoleLabel,    oppCurRoleLabel;
    static Label oppTypeLabel,    oppEnergyLabel,  oppPosLabel,    oppStatusLabel;
    static Label diceLabel;
    static Label oppDiceLabel;
    static TextArea logArea;
    static Button rollBtn;   // kept static so handleRoll can disable/re-enable it
    static Label cardLabel;          // player's card
    static Label oppCardLabel;       // opponent's card

    // ── System.out interceptor ────────────────────────────────────────────────
    static final ByteArrayOutputStream engineOut = new ByteArrayOutputStream();
    static final PrintStream originalOut = System.out;

    static void installEngineCapture() {
        System.setOut(new PrintStream(engineOut) {
            @Override public void println(String x) {
                originalOut.println(x);
                engineOut.write((x + "\n").getBytes(), 0, (x + "\n").length());
            }
        });
    }

    static String drainEngineLog() {
        String captured = engineOut.toString();
        engineOut.reset();
        return captured;
    }

    // ── Image helpers ─────────────────────────────────────────────────────────
    static Image loadImage(String filename) {
        try {
            InputStream is = GameView.class.getResourceAsStream("/" + filename);
            if (is == null) { System.out.println("IMAGE NOT FOUND: [" + filename + "]"); return null; }
            return new Image(is);
        } catch (Exception e) {
            System.out.println("IMAGE ERROR: " + filename + " → " + e.getMessage());
            return null;
        }
    }

    static ImageView makeImageView(String filename, double w, double h) {
        Image img = loadImage(filename);
        if (img == null) return null;
        ImageView iv = new ImageView(img);
        iv.setFitWidth(w); iv.setFitHeight(h); iv.setPreserveRatio(true);
        return iv;
    }

    // ── START SCREEN ──────────────────────────────────────────────────────────
    public static void showStartScreen() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: #1b1b2f;");

        Label title = new Label("DoorDasH");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 60));
        title.setTextFill(Color.web("#f5a623"));

        Label sub = new Label("Scare vs Laugh Touchdown");
        sub.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        sub.setTextFill(Color.WHITE);

        Label choose = new Label("Choose your side:");
        choose.setFont(Font.font("Arial", 18));
        choose.setTextFill(Color.web("#cccccc"));

        TextArea instructions = new TextArea(
            "HOW TO PLAY:\n" +
            "• Each turn: optionally use your powerup (costs 500 energy), then roll the dice.\n" +
            "• Land on doors to gain/lose energy depending on your role.\n" +
            "• Monster Cells: same role = free powerup, different role = energy swap.\n" +
            "• Card Cells: draw a mystery card.\n" +
            "• Conveyor Belts move you forward, Contamination Socks move you back.\n" +
            "• WIN: reach cell 99 with at least 1000 energy!"
        );
        instructions.setEditable(false);
        instructions.setPrefSize(600, 120);
        instructions.setFont(Font.font("Arial", 13));
        instructions.setStyle("-fx-control-inner-background: #2a2a4a; -fx-text-fill: #cccccc;");

        HBox buttons = new HBox(30);
        buttons.setAlignment(Pos.CENTER);
        Button scarerBtn  = makeButton("SCARER (Screams)",   "#c62828");
        Button laugherBtn = makeButton("LAUGHER (Laughter)", "#2e7d32");
        scarerBtn.setOnAction(e  -> startGame(Role.SCARER));
        laugherBtn.setOnAction(e -> startGame(Role.LAUGHER));
        buttons.getChildren().addAll(scarerBtn, laugherBtn);

        root.getChildren().addAll(title, sub, choose, instructions, buttons);
        Main.mainStage.setScene(new Scene(root, 1100, 750));
        Main.mainStage.show();
    }

    // ── START GAME ────────────────────────────────────────────────────────────
    static void startGame(Role role) {
        try {
            controller = new GameController(role);
        } catch (Exception e) {
            showPopup("Error", "Could not load game data: " + e.getMessage());
            return;
        }
        isMyTurn = true;
        gameOver = false;
        installEngineCapture();
        buildGameScreen();
    }

    // ── BUILD GAME SCREEN ─────────────────────────────────────────────────────
    static void buildGameScreen() {

        // ── Top bar ──────────────────────────────────────────────────────────
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(8, 14, 8, 14));
        topBar.setStyle("-fx-background-color: #1b1b2f;");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label gameTitle = new Label("DoorDasH");
        gameTitle.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        gameTitle.setTextFill(Color.web("#f5a623"));

        turnLabel = new Label("Turn 1 — YOUR TURN");
        turnLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        turnLabel.setTextFill(Color.web("#00e5ff"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topBar.getChildren().addAll(gameTitle, spacer, turnLabel);

        // ── Board ─────────────────────────────────────────────────────────────
        GridPane board = buildBoard();

        // ── Left panel (player) ───────────────────────────────────────────────
        VBox playerPanel = buildMonsterPanel("YOU", true);

        // ── Right panel (opponent + controls) ────────────────────────────────
        VBox rightPanel = new VBox(8);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setPrefWidth(230);
        rightPanel.setStyle("-fx-background-color: #1b1b2f;");

        VBox oppPanel = buildMonsterPanel("OPPONENT", false);

        // Dice section — player
        Label diceTitle = new Label("Your Dice Roll:");
        diceTitle.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        diceTitle.setTextFill(Color.WHITE);

        diceLabel = new Label("-");
        diceLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        diceLabel.setTextFill(Color.web("#f5a623"));
        diceLabel.setMinWidth(50);
        diceLabel.setAlignment(Pos.CENTER);

        // Dice section — opponent
        Label oppDiceTitle = new Label("Opponent Dice Roll:");
        oppDiceTitle.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        oppDiceTitle.setTextFill(Color.WHITE);

        oppDiceLabel = new Label("-");
        oppDiceLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        oppDiceLabel.setTextFill(Color.web("#ff6b6b"));
        oppDiceLabel.setMinWidth(50);
        oppDiceLabel.setAlignment(Pos.CENTER);

        // Card label — player
        Label cardTitle = new Label("Your Card:");
        cardTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        cardTitle.setTextFill(Color.web("#ce93d8"));

        cardLabel = new Label("");
        cardLabel.setFont(Font.font("Arial", 11));
        cardLabel.setTextFill(Color.web("#ce93d8"));
        cardLabel.setWrapText(true);
        cardLabel.setMaxWidth(210);

        // Card label — opponent
        Label oppCardTitle = new Label("Opponent's Card:");
        oppCardTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        oppCardTitle.setTextFill(Color.web("#ffb74d"));

        oppCardLabel = new Label("");
        oppCardLabel.setFont(Font.font("Arial", 11));
        oppCardLabel.setTextFill(Color.web("#ffb74d"));
        oppCardLabel.setWrapText(true);
        oppCardLabel.setMaxWidth(210);

        Button powerupBtn = makeButton("Use Powerup (-500)", "#6a1b9a");
        powerupBtn.setPrefWidth(210);
        powerupBtn.setOnAction(e -> handlePowerup());

        rollBtn = makeButton("Roll Dice", "#b71c1c");
        rollBtn.setPrefWidth(210);
        rollBtn.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        rollBtn.setOnAction(e -> handleRoll());

        rightPanel.getChildren().addAll(
            oppPanel, makeSep(),
            diceTitle, diceLabel,
            oppDiceTitle, oppDiceLabel,
            makeSep(),
            cardTitle, cardLabel,
            oppCardTitle, oppCardLabel,
            makeSep(),
            powerupBtn, rollBtn
        );

        // ── Log area ──────────────────────────────────────────────────────────
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(100);
        logArea.setFont(Font.font("Arial", 12));
        logArea.setStyle("-fx-control-inner-background: #0d0d1a; -fx-text-fill: #cccccc;");

        // ── Layout ────────────────────────────────────────────────────────────
        BorderPane center = new BorderPane();
        center.setLeft(playerPanel);
        center.setCenter(board);
        center.setRight(rightPanel);

        VBox main = new VBox(0);
        main.getChildren().addAll(topBar, center, buildLegend(), logArea);

        Main.mainStage.setScene(new Scene(main, 1100, 780));
        refreshAll();

        log("Game started! You are " + controller.getPlayer().getName()
            + " (" + controller.getPlayer().getOriginalRole() + ")");
        log("Opponent: " + controller.getOpponent().getName()
            + " (" + controller.getOpponent().getOriginalRole() + ")");
        log("Goal: reach cell 99 with 1000+ energy!");
        log("─── Your turn! Use Powerup (optional) then Roll Dice. ───");

        // ── Dev shortcuts ─────────────────────────────────────────────────────
        // W → teleport player to cell 99
        // E → give player +1000 energy
        // addEventFilter captures keys before any focused node (e.g. the log
        // TextArea) can consume them, so W and E work with a direct key press.
        Main.mainStage.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, evt -> {
            switch (evt.getCode()) {
                case W: {
                    Monster p = controller.getPlayer();
                    p.setPosition(99);
                    log("🛠 [DEV] Player teleported to cell 99.");
                    refreshAll();
                    evt.consume();
                    break;
                }
                case E: {
                    Monster p = controller.getPlayer();
                    p.setEnergy(p.getEnergy() + 1000);
                    log("🛠 [DEV] Player energy +1000 → " + p.getEnergy());
                    refreshAll();
                    evt.consume();
                    break;
                }
                default:
                    break;
            }
        });
    }

    // ── BUILD BOARD ───────────────────────────────────────────────────────────
    static GridPane buildBoard() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(8));
        grid.setHgap(2); grid.setVgap(2);
        grid.setStyle("-fx-background-color: " + COL_BOARD_BG + ";");

        for (int displayRow = 0; displayRow < 10; displayRow++) {
            for (int displayCol = 0; displayCol < 10; displayCol++) {
                int gameRow = 9 - displayRow;
                int gameCol = (gameRow % 2 == 0) ? displayCol : (9 - displayCol);
                int cellIndex = gameRow * 10 + gameCol;
                Pane pane = buildCellPane(cellIndex);
                cellPanes[cellIndex] = pane;
                grid.add(pane, displayCol, displayRow);
            }
        }
        return grid;
    }

    // ── BUILD ONE CELL ────────────────────────────────────────────────────────
    static Pane buildCellPane(int index) {
        StackPane pane = new StackPane();
        pane.setPrefSize(60, 58);

        Cell cell = getRealCell(index);
        String bgColor = getRealCellColor(index, cell);
        pane.setStyle("-fx-background-color: " + bgColor
            + "; -fx-border-color: #aaa; -fx-border-width: 0.5;");

        // Cell image
        String imageFile = getCellImageFilename(index, cell);
        if (imageFile != null) {
            ImageView iv = makeImageView(imageFile, 36, 36);
            if (iv != null) {
                iv.setId("cellImg_" + index);
                StackPane.setAlignment(iv, Pos.CENTER);
                pane.getChildren().add(iv);
            }
        }

        // Door energy value visible on cell
        if (cell instanceof DoorCell && index != 99) {
            DoorCell dc = (DoorCell) cell;
            Label energyLbl = new Label((dc.getEnergy() >= 0 ? "+" : "") + dc.getEnergy());
            energyLbl.setId("doorEnergy_" + index);
            energyLbl.setFont(Font.font("Arial", FontWeight.BOLD, 8));
            energyLbl.setTextFill(dc.getEnergy() >= 0 ? Color.web("#1b5e20") : Color.web("#b71c1c"));
            energyLbl.setStyle("-fx-background-color: rgba(255,255,255,0.75); -fx-padding: 0 1 0 1;");
            StackPane.setAlignment(energyLbl, Pos.BOTTOM_RIGHT);
            energyLbl.setTranslateY(-2);
            pane.getChildren().add(energyLbl);
        }

        // Fallback monster name if no image matched
        if (cell instanceof MonsterCell && imageFile == null) {
            MonsterCell mc = (MonsterCell) cell;
            String monName = (mc.getCellMonster() != null) ? mc.getCellMonster().getName() : "???";
            Label nameLbl = new Label(monName);
            nameLbl.setFont(Font.font("Arial", FontWeight.BOLD, 7));
            nameLbl.setTextFill(Color.web("#000080"));
            nameLbl.setWrapText(true);
            nameLbl.setMaxWidth(55);
            nameLbl.setAlignment(Pos.CENTER);
            pane.getChildren().add(nameLbl);
        }

        // Cell number — always on top
        Label numLbl = new Label(String.valueOf(index));
        numLbl.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        numLbl.setTextFill(Color.web("#111111"));
        numLbl.setStyle("-fx-background-color: rgba(255,255,255,0.65); -fx-padding: 0 2 0 2;");
        StackPane.setAlignment(numLbl, Pos.TOP_LEFT);
        numLbl.setPadding(new Insets(1, 0, 0, 2));
        pane.getChildren().add(numLbl);

        return pane;
    }

    // ── GET CELL OBJECT ───────────────────────────────────────────────────────
    static Cell getRealCell(int index) {
        Cell[][] cells = controller.getGame().getBoard().getBoardCells();
        int row = index / 10;
        int col = (row % 2 == 0) ? (index % 10) : (9 - index % 10);
        return cells[row][col];
    }

    static String getRealCellColor(int index, Cell cell) {
        if (index == 0 || index == 99) return "#f5a623";
        if (cell instanceof DoorCell) {
            DoorCell dc = (DoorCell) cell;
            if (dc.isActivated()) return COL_DOOR_USED;
            return dc.getRole() == Role.SCARER ? COL_DOOR_S : COL_DOOR_L;
        }
        if (cell instanceof CardCell)          return COL_CARD;
        if (cell instanceof ConveyorBelt)      return COL_CONVEYOR;
        if (cell instanceof ContaminationSock) return COL_SOCK;
        if (cell instanceof MonsterCell)       return COL_MONSTER;
        return COL_NORMAL;
    }

    static String getCellImageFilename(int index, Cell cell) {
        if (index == 99) return "last_door.jpeg";
        if (cell instanceof DoorCell) {
            DoorCell dc = (DoorCell) cell;
            return dc.getRole() == Role.SCARER ? "door scarer.png" : "door laugher.png";
        }
        if (cell instanceof MonsterCell) {
            int[] monsterIndices = game.engine.Constants.MONSTER_CELL_INDICES;
            java.util.ArrayList<Monster> stationed = Board.getStationedMonsters();
            for (int mi = 0; mi < monsterIndices.length; mi++) {
                if (monsterIndices[mi] == index && mi < stationed.size())
                    return getMonsterImageFilename(stationed.get(mi));
            }
            return null;
        }
        if (cell instanceof CardCell)          return "card.png";
        if (cell instanceof ConveyorBelt)      return "coveryor_belt.jpeg";
        if (cell instanceof ContaminationSock) return "contanination sock.png";
        return null;
    }

    static String getMonsterImageFilename(Monster m) {
        if (m == null) return null;
        String name = m.getName().toLowerCase().trim();
        if (name.contains("celia"))                              return "sally.png";
        if (name.contains("roz"))                               return "Rose.png";
        if (name.contains("fungus"))                             return "fungus.png";
        if (name.contains("james") || name.contains("shelby"))  return "shalby.png";
        if (name.contains("yeti")  || name.contains("yety"))    return "yety.png";
        if (name.contains("randall"))                            return "bors.png";
        if (name.contains("mike"))                               return "mike.png";
        if (name.contains("henry"))                              return "abo el3eneen.png";
        System.out.println("NO IMAGE MATCH for monster: [" + m.getName() + "]");
        return null;
    }

    // ── MONSTER PANELS ────────────────────────────────────────────────────────
    static VBox buildMonsterPanel(String title, boolean isPlayer) {
        VBox panel = new VBox(5);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(185);
        panel.setStyle("-fx-background-color: #1b1b2f;");

        Label header = new Label(title);
        header.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        header.setTextFill(isPlayer ? Color.web("#00e5ff") : Color.web("#ff6b6b"));

        if (isPlayer) {
            playerNameLabel    = infoLabel("Name: —");
            playerRoleLabel    = infoLabel("Original Role: —");
            playerCurRoleLabel = infoLabel("Current Role: —");
            playerTypeLabel    = infoLabel("Type: —");
            playerEnergyLabel  = infoLabel("Energy: —");
            playerPosLabel     = infoLabel("Position: —");
            playerStatusLabel  = infoLabel("Status: Normal");
            playerStatusLabel.setWrapText(true);
            panel.getChildren().addAll(header, makeSep(),
                playerNameLabel, playerRoleLabel, playerCurRoleLabel,
                playerTypeLabel, playerEnergyLabel, playerPosLabel, playerStatusLabel);
        } else {
            oppNameLabel    = infoLabel("Name: —");
            oppRoleLabel    = infoLabel("Original Role: —");
            oppCurRoleLabel = infoLabel("Current Role: —");
            oppTypeLabel    = infoLabel("Type: —");
            oppEnergyLabel  = infoLabel("Energy: —");
            oppPosLabel     = infoLabel("Position: —");
            oppStatusLabel  = infoLabel("Status: Normal");
            oppStatusLabel.setWrapText(true);
            panel.getChildren().addAll(header, makeSep(),
                oppNameLabel, oppRoleLabel, oppCurRoleLabel,
                oppTypeLabel, oppEnergyLabel, oppPosLabel, oppStatusLabel);
        }
        return panel;
    }

    // ── LEGEND ────────────────────────────────────────────────────────────────
    static HBox buildLegend() {
        HBox legend = new HBox(10);
        legend.setPadding(new Insets(4, 10, 4, 10));
        legend.setStyle("-fx-background-color: #2a2a4a;");
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getChildren().addAll(
            legendItem(COL_DOOR_S,    "SCARER Door"),
            legendItem(COL_DOOR_L,    "LAUGHER Door"),
            legendItem(COL_DOOR_USED, "Exhausted Door"),
            legendItem(COL_MONSTER,   "Monster Cell"),
            legendItem(COL_CARD,      "Card Cell"),
            legendItem(COL_CONVEYOR,  "Conveyor Belt"),
            legendItem(COL_SOCK,      "Contamination Sock"),
            legendItem(COL_NORMAL,    "Normal Cell"),
            legendItem("#00e5ff",     "You (P)"),
            legendItem("#ff6b6b",     "Opponent (O)")
        );
        return legend;
    }

    static HBox legendItem(String color, String text) {
        Rectangle rect = new Rectangle(14, 14);
        rect.setFill(Color.web(color));
        rect.setStroke(Color.web("#888"));
        rect.setStrokeWidth(0.5);
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Arial", 11));
        lbl.setTextFill(Color.web("#cccccc"));
        HBox item = new HBox(4, rect, lbl);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    // ── HANDLE POWERUP ────────────────────────────────────────────────────────
    static void handlePowerup() {
        if (gameOver) return;
        if (!isMyTurn) {
            showPopup("Not Your Turn", "Wait for your turn before using a powerup.");
            return;
        }
        boolean ok = controller.tryUsePowerup();
        if (!ok) showPopup("Cannot Use Powerup", controller.lastMessage);
        else     { log("⚡ " + controller.lastMessage); refreshAll(); }
    }

    // ── HANDLE ROLL ───────────────────────────────────────────────────────────
    static void handleRoll() {
        if (gameOver) return;
        if (!isMyTurn) {
            log("(It's not your turn yet!)");
            return;
        }

        // Disable roll button immediately — re-enabled after opponent's turn
        rollBtn.setDisable(true);

        // Reset card labels each round
        cardLabel.setText("");
        oppCardLabel.setText("");

        // ══ PLAYER TURN ═══════════════════════════════════════════════════════
        int playerEnergyBefore = controller.getPlayer().getEnergy();
        int playerPosBefore    = controller.getPlayer().getPosition();
        boolean wasShieldedP   = controller.getPlayer().isShielded();

        drainEngineLog();
        log("─── Rolling for " + controller.getPlayer().getName() + "... ───");

        try {
            controller.playPlayerTurn();
        } catch (Exception ex) {
            // The engine already reverted the player's position to oldPosition
            // and did NOT switch the turn — so per the spec the player must
            // roll again. Re-enable the roll button and return without touching
            // the opponent's turn at all.
            diceLabel.setText("✖");
            showPopup("Invalid Move", ex.getMessage());
            log("⛔ Your move was blocked: " + ex.getMessage()
                + " — roll again!");
            refreshAll();
            rollBtn.setDisable(false);
            return;
        }

        String engineLog = drainEngineLog();

        if (!controller.playerWasFrozen) {
            int moved = controller.getPlayer().getPosition() - playerPosBefore;
            diceLabel.setText(moved > 0 && moved <= 6 ? String.valueOf(moved) : "?");
            log("🎲 You rolled and moved " + moved + " cell(s) → position "
                + controller.getPlayer().getPosition());
        } else {
            diceLabel.setText("❄");
            log("❄️  YOU were FROZEN — turn skipped!");
        }

        int playerEnergyAfter = controller.getPlayer().getEnergy();
        int playerDelta       = playerEnergyAfter - playerEnergyBefore;
        if (playerDelta != 0) {
            String sign = playerDelta > 0 ? "+" : "";
            log("⚡ Your energy changed: " + sign + playerDelta
                + "  (" + playerEnergyBefore + " → " + playerEnergyAfter + ")");
        }

        if (wasShieldedP && !controller.getPlayer().isShielded())
            log("🛡️  YOUR shield absorbed an energy loss! Shield is now gone.");

        parseShieldMessages(engineLog, true);
        detectAndShowCard(playerPosBefore, controller.getPlayer(), true, engineLog);
        logPositionChanges(playerPosBefore, controller.getPlayer().getPosition(),
                           controller.getPlayer().getName(), true, engineLog);

        isMyTurn = false;
        refreshAll();

        // Check win after player's turn — stop before opponent moves
        if (controller.getWinner() != null) {
            gameOver = true;
            showWinScreen(controller.getWinner());
            return;
        }

        // ══ OPPONENT TURN — delayed 1.5 s so the player can see the board ════
        log("⏳ Opponent is thinking...");
        PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
        pause.setOnFinished(e -> playOpponentPhase());
        pause.play();
    }

    // ── OPPONENT PHASE (fires after the 1.5 s pause) ─────────────────────────
    static void playOpponentPhase() {
        if (gameOver) return;

        int oppEnergyBefore  = controller.getOpponent().getEnergy();
        int oppPosBefore     = controller.getOpponent().getPosition();
        boolean wasShieldedO = controller.getOpponent().isShielded();

        drainEngineLog();
        log("─── Rolling for " + controller.getOpponent().getName() + "... ───");

        // ── Track whether the opponent's move was blocked by a collision ─────
        boolean oppMoveBlocked = false;

        try {
            controller.playOpponentTurn();
        } catch (Exception ex) {
            // Engine reverted position and did NOT switch turn — opponent retries.
            oppMoveBlocked = true;
            log("⛔ Opponent move blocked: " + ex.getMessage()
                + " — opponent rolls again!");
            oppDiceLabel.setText("✖");
        }

        // If opponent was blocked, retry their turn immediately (spec: roll again)
        if (oppMoveBlocked) {
            drainEngineLog();
            log("─── Re-rolling for " + controller.getOpponent().getName() + "... ───");
            try {
                controller.playOpponentTurn();
                oppMoveBlocked = false;  // second attempt succeeded
            } catch (Exception ex2) {
                // Still blocked (very rare — both at same cell edge case).
                // Accept the skip to prevent freezing.
                log("⛔ Opponent blocked again — turn forfeited.");
                oppDiceLabel.setText("✖");
                isMyTurn = true;
                refreshAll();
                rollBtn.setDisable(false);
                log("─── Turn " + controller.turnNumber + " — Your turn! ───");
                return;
            }
        }

        String oppEngineLog = drainEngineLog();

        if (controller.oppWasFrozen) {
            oppDiceLabel.setText("❄");
            log("❄️  OPPONENT was FROZEN — their turn skipped!");
        } else {
            int oppMoved = controller.getOpponent().getPosition() - oppPosBefore;
            oppDiceLabel.setText(oppMoved > 0 && oppMoved <= 6 ? String.valueOf(oppMoved) : "?");
            log("🎲 Opponent moved " + oppMoved + " cell(s) → position "
                + controller.getOpponent().getPosition());
        }

        int oppEnergyAfter = controller.getOpponent().getEnergy();
        int oppDelta       = oppEnergyAfter - oppEnergyBefore;
        if (oppDelta != 0) {
            String sign = oppDelta > 0 ? "+" : "";
            log("⚡ Opponent energy changed: " + sign + oppDelta
                + "  (" + oppEnergyBefore + " → " + oppEnergyAfter + ")");
        }

        if (wasShieldedO && !controller.getOpponent().isShielded())
            log("🛡️  OPPONENT'S shield absorbed an energy loss! Their shield is now gone.");

        parseShieldMessages(oppEngineLog, false);
        detectAndShowCard(oppPosBefore, controller.getOpponent(), false, oppEngineLog);
        logPositionChanges(oppPosBefore, controller.getOpponent().getPosition(),
                           controller.getOpponent().getName(), false, oppEngineLog);

        isMyTurn = true;
        refreshAll();

        if (controller.getWinner() != null) {
            gameOver = true;
            showWinScreen(controller.getWinner());
            return;
        }

        // Both turns done — re-enable the roll button for the next round
        rollBtn.setDisable(false);
        log("─── Turn " + controller.turnNumber + " — Your turn! ───");
    }

    // ── Detect card from landing on card cell and show it ─────────────────────
    static void detectAndShowCard(int posBefore, Monster m, boolean isPlayer, String engineLog) {
        // ── Primary method: use the card object captured in GameController ────
        game.engine.cards.Card drawnCard = isPlayer
            ? controller.lastCardDrawn
            : controller.oppCardDrawn;

        if (drawnCard != null) {
            String cardText = drawnCard.getName() + " — " + drawnCard.getDescription();
            if (isPlayer) {
                cardLabel.setText("🃏 " + cardText);
                log("🃏 Card drawn (you): " + cardText);
            } else {
                oppCardLabel.setText("🃏 " + cardText);
                log("🃏 Card drawn (opponent): " + cardText);
            }
            return;
        }

        // ── Fallback: scrape engine log for side-effect messages ──────────────
        int pos = m.getPosition();
        boolean landedOnCardCell = false;
        for (int idx : game.engine.Constants.CARD_CELL_INDICES) {
            if (idx == pos) { landedOnCardCell = true; break; }
        }
        if (!landedOnCardCell && !engineLog.contains("shield blocked")
                && !engineLog.contains("stole") && !engineLog.contains("Swapped")
                && !engineLog.contains("protected")) return;

        for (String line : engineLog.split("\n")) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            if (l.contains("shield") || l.contains("Shield")
                    || l.contains("Swapped") || l.contains("swapped")
                    || l.contains("stole") || l.contains("protected")
                    || l.contains("frozen") || l.contains("Frozen")
                    || l.contains("Confusion") || l.contains("confusion")
                    || l.contains("start") || l.contains("Start")) {
                if (isPlayer) {
                    cardLabel.setText("🃏 " + l);
                    log("🃏 Card drawn (you): " + l);
                } else {
                    oppCardLabel.setText("🃏 " + l);
                    log("🃏 Card drawn (opponent): " + l);
                }
                return;
            }
        }

        // ── Last resort: at least confirm a card was drawn ─────────────────────
        if (landedOnCardCell) {
            String msg = "Card drawn (effect applied)";
            if (isPlayer) { cardLabel.setText("🃏 " + msg); log("🃏 " + msg + " (you)"); }
            else           { oppCardLabel.setText("🃏 " + msg); log("🃏 " + msg + " (opponent)"); }
        }
    }

    // ── Parse shield block messages from engine System.out ────────────────────
    static void parseShieldMessages(String engineLog, boolean isPlayer) {
        for (String line : engineLog.split("\n")) {
            if (line.contains("shield blocked") || line.contains("Shield blocked")) {
                if (isPlayer)
                    log("🛡️  [PLAYER SHIELD] " + line.trim());
                else
                    log("🛡️  [OPP SHIELD] " + line.trim());
            }
        }
    }

    // ── Log position changes caused by cells (conveyors, socks, cards) ────────
    static void logPositionChanges(int posBefore, int posAfter, String name,
                                   boolean isPlayer, String engineLog) {
        int diff = posAfter - posBefore;
        if (Math.abs(diff) > 18 || diff < 0) {
            String reason = "cell effect";
            if (engineLog.contains("Conveyor") || engineLog.contains("conveyor")) reason = "Conveyor Belt";
            else if (engineLog.contains("Sock") || engineLog.contains("sock"))    reason = "Contamination Sock";
            else if (engineLog.contains("start") || engineLog.contains("Start"))  reason = "Start Over card";
            else if (engineLog.contains("Swapped") || engineLog.contains("swap")) reason = "Position Swap";
            log("📍 " + name + " moved from " + posBefore + " → " + posAfter + " (" + reason + ")");
        }
    }

    // ── REFRESH ───────────────────────────────────────────────────────────────
    static void refreshAll() {
        refreshBoard();
        refreshMonsterPanels();
        refreshTurnLabel();
    }

    static void refreshBoard() {
        Cell[][] cells = controller.getGame().getBoard().getBoardCells();
        Monster player   = controller.getPlayer();
        Monster opponent = controller.getOpponent();

        int[] monsterIndices          = game.engine.Constants.MONSTER_CELL_INDICES;
        java.util.ArrayList<Monster> stationed = Board.getStationedMonsters();

        for (int i = 0; i < 100; i++) {
            StackPane pane = (StackPane) cellPanes[i];
            if (pane == null) continue;

            pane.getChildren().removeIf(n -> n instanceof Label && "marker".equals(n.getId()));
            pane.getChildren().removeIf(
                n -> n instanceof ImageView && n.getId() != null && n.getId().startsWith("cellImg_"));
            pane.getChildren().removeIf(
                n -> n instanceof Label && n.getId() != null && n.getId().startsWith("doorEnergy_"));

            int row = i / 10;
            int col = (row % 2 == 0) ? (i % 10) : (9 - i % 10);
            Cell cell = cells[row][col];

            pane.setStyle("-fx-background-color: " + getRealCellColor(i, cell)
                + "; -fx-border-color: #aaa; -fx-border-width: 0.5;");

            String imageFile = null;
            if (i == 99) {
                imageFile = "last_door.jpeg";
            } else if (cell instanceof DoorCell) {
                DoorCell dc = (DoorCell) cell;
                imageFile = dc.getRole() == Role.SCARER ? "door scarer.png" : "door laugher.png";
            } else if (cell instanceof CardCell) {
                imageFile = "card.png";
            } else if (cell instanceof ConveyorBelt) {
                imageFile = "coveryor_belt.jpeg";
            } else if (cell instanceof ContaminationSock) {
                imageFile = "contanination sock.png";
            } else if (cell instanceof MonsterCell) {
                for (int mi = 0; mi < monsterIndices.length; mi++) {
                    if (monsterIndices[mi] == i && mi < stationed.size()) {
                        imageFile = getMonsterImageFilename(stationed.get(mi));
                        break;
                    }
                }
            }

            if (imageFile != null) {
                ImageView iv = makeImageView(imageFile, 36, 36);
                if (iv != null) {
                    iv.setId("cellImg_" + i);
                    StackPane.setAlignment(iv, Pos.CENTER);
                    pane.getChildren().add(0, iv);
                }
            }

            // ── Monster Cell identity fallback: show name label when no image matched ──
            if (cell instanceof MonsterCell && imageFile == null) {
                MonsterCell mc = (MonsterCell) cell;
                String monName = (mc.getCellMonster() != null)
                    ? mc.getCellMonster().getName() : "???";
                Label monNameLbl = new Label(monName);
                monNameLbl.setId("cellImg_" + i);   // reuse id so it is cleared on next refresh
                monNameLbl.setFont(Font.font("Arial", FontWeight.BOLD, 7));
                monNameLbl.setTextFill(Color.web("#000080"));
                monNameLbl.setWrapText(true);
                monNameLbl.setMaxWidth(55);
                monNameLbl.setAlignment(Pos.CENTER);
                StackPane.setAlignment(monNameLbl, Pos.CENTER);
                pane.getChildren().add(monNameLbl);
            }

            if (cell instanceof DoorCell && i != 99) {
                DoorCell dc = (DoorCell) cell;
                Label energyLbl = new Label((dc.getEnergy() >= 0 ? "+" : "") + dc.getEnergy());
                energyLbl.setId("doorEnergy_" + i);
                energyLbl.setFont(Font.font("Arial", FontWeight.BOLD, 8));
                energyLbl.setTextFill(dc.getEnergy() >= 0 ? Color.web("#1b5e20") : Color.web("#b71c1c"));
                energyLbl.setStyle("-fx-background-color: rgba(255,255,255,0.75); -fx-padding: 0 1 0 1;");
                StackPane.setAlignment(energyLbl, Pos.BOTTOM_RIGHT);
                energyLbl.setTranslateY(-2);
                pane.getChildren().add(energyLbl);
            }

            // Player marker (P)
            if (player.getPosition() == i) {
                Label marker = new Label("P");
                marker.setId("marker");
                marker.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                marker.setTextFill(Color.web("#00e5ff"));
                marker.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-padding: 1 3 1 3;");
                StackPane.setAlignment(marker, Pos.BOTTOM_RIGHT);
                pane.getChildren().add(marker);
            }

            // Opponent marker (O)
            if (opponent.getPosition() == i) {
                Label marker = new Label("O");
                marker.setId("marker");
                marker.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                marker.setTextFill(Color.web("#ff6b6b"));
                marker.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-padding: 1 3 1 3;");
                StackPane.setAlignment(marker, Pos.BOTTOM_LEFT);
                pane.getChildren().add(marker);
            }

            // ── Role-confusion badge: shown on the cell where a confused monster stands ──
            // A small "?" icon with orange background makes it immediately obvious
            // that the monster on this cell is currently playing with a swapped role.
            boolean playerConfusedHere   = player.isConfused()   && player.getPosition()   == i;
            boolean opponentConfusedHere = opponent.isConfused() && opponent.getPosition() == i;
            if (playerConfusedHere || opponentConfusedHere) {
                Label confBadge = new Label("😵");
                confBadge.setId("marker");
                confBadge.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                confBadge.setStyle("-fx-background-color: rgba(255,140,0,0.85); "
                    + "-fx-background-radius: 3; -fx-padding: 0 2 0 2;");
                StackPane.setAlignment(confBadge, Pos.TOP_RIGHT);
                pane.getChildren().add(confBadge);
            }
        }
    }

    static void refreshMonsterPanels() {
        Monster p = controller.getPlayer();
        Monster o = controller.getOpponent();

        playerNameLabel.setText("Name: " + p.getName());
        playerRoleLabel.setText("Original: " + p.getOriginalRole());
        playerCurRoleLabel.setText("Current: " + p.getRole());
        playerCurRoleLabel.setTextFill(p.isConfused() ? Color.ORANGE : Color.web("#cccccc"));
        playerTypeLabel.setText("Type: " + getMonsterType(p));
        playerEnergyLabel.setText("Energy: " + p.getEnergy());
        playerPosLabel.setText("Position: " + p.getPosition());
        playerStatusLabel.setText("Status: " + getStatusText(p));
        playerStatusLabel.setTextFill(getStatusColor(p));

        oppNameLabel.setText("Name: " + o.getName());
        oppRoleLabel.setText("Original: " + o.getOriginalRole());
        oppCurRoleLabel.setText("Current: " + o.getRole());
        oppCurRoleLabel.setTextFill(o.isConfused() ? Color.ORANGE : Color.web("#cccccc"));
        oppTypeLabel.setText("Type: " + getMonsterType(o));
        oppEnergyLabel.setText("Energy: " + o.getEnergy());
        oppPosLabel.setText("Position: " + o.getPosition());
        oppStatusLabel.setText("Status: " + getStatusText(o));
        oppStatusLabel.setTextFill(getStatusColor(o));
    }

    static void refreshTurnLabel() {
        String name = isMyTurn ? controller.getPlayer().getName()
                               : controller.getOpponent().getName();
        String prefix = "Turn " + controller.turnNumber + " — ";
        turnLabel.setText(isMyTurn
            ? prefix + name + " ← YOUR TURN"
            : prefix + name + " (OPPONENT)");
        turnLabel.setTextFill(isMyTurn ? Color.web("#00e5ff") : Color.web("#ff6b6b"));
    }

    // ── WIN SCREEN ────────────────────────────────────────────────────────────
    static void showWinScreen(Monster winner) {
        Monster p = controller.getPlayer();
        Monster o = controller.getOpponent();
        boolean playerWon = (winner == p);

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(50));
        root.setStyle("-fx-background-color: #1b1b2f;");

        Label result = new Label(playerWon ? "YOU WIN! 🎉" : "GAME OVER");
        result.setFont(Font.font("Arial", FontWeight.BOLD, 60));
        result.setTextFill(playerWon ? Color.web("#f5a623") : Color.web("#ff6b6b"));

        Label winnerInfo = new Label(
            "Winner: " + winner.getName()
            + "  |  Original Role: " + winner.getOriginalRole()
            + "  |  Current Role: " + winner.getRole());
        winnerInfo.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        winnerInfo.setTextFill(Color.WHITE);

        Label energyInfo = new Label(
            p.getName() + " final energy: " + p.getEnergy() + "\n" +
            o.getName() + " final energy: " + o.getEnergy());
        energyInfo.setFont(Font.font("Arial", 18));
        energyInfo.setTextFill(Color.web("#cccccc"));
        energyInfo.setAlignment(Pos.CENTER);

        Button backBtn = makeButton("Back to Start", "#1565c0");
        backBtn.setOnAction(e -> showStartScreen());

        root.getChildren().addAll(result, winnerInfo, energyInfo, backBtn);
        Main.mainStage.setScene(new Scene(root, 1100, 750));
    }

    // ── POPUP ─────────────────────────────────────────────────────────────────
    static void showPopup(String title, String message) {
        javafx.stage.Stage popup = new javafx.stage.Stage();
        popup.setTitle(title);
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.initOwner(Main.mainStage);

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: #2a2a4a;");

        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLbl.setTextFill(Color.web("#ff6b6b"));

        Label msgLbl = new Label(message);
        msgLbl.setFont(Font.font("Arial", 14));
        msgLbl.setTextFill(Color.WHITE);
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(360);
        msgLbl.setAlignment(Pos.CENTER);

        Label reasonLbl = new Label("Reason: " + message);
        reasonLbl.setFont(Font.font("Arial", 12));
        reasonLbl.setTextFill(Color.web("#aaaaaa"));
        reasonLbl.setWrapText(true);
        reasonLbl.setMaxWidth(360);

        Button okBtn = makeButton("OK — Return to Game", "#b71c1c");
        okBtn.setOnAction(e -> popup.close());

        box.getChildren().addAll(titleLbl, msgLbl, reasonLbl, okBtn);
        popup.setScene(new Scene(box, 420, 220));
        popup.showAndWait();
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────
    static Button makeButton(String text, String color) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        btn.setTextFill(Color.WHITE);
        btn.setStyle("-fx-background-color: " + color
            + "; -fx-background-radius: 6; -fx-padding: 8 16 8 16;");
        return btn;
    }

    static Label infoLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Arial", 12));
        l.setTextFill(Color.web("#cccccc"));
        l.setWrapText(true);
        return l;
    }

    static Region makeSep() {
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color: #444466;");
        return sep;
    }

    static void log(String message) {
        if (logArea != null) logArea.appendText(message + "\n");
    }

    static String getMonsterType(Monster m) {
        if (m instanceof Dasher)      return "Dasher";
        if (m instanceof Dynamo)      return "Dynamo";
        if (m instanceof MultiTasker) return "MultiTasker";
        if (m instanceof Schemer)     return "Schemer";
        return "Unknown";
    }

    static String getStatusText(Monster m) {
        StringBuilder sb = new StringBuilder();
        if (m.isShielded()) sb.append("🛡 Shielded  ");
        if (m.isFrozen())   sb.append("❄ FROZEN  ");
        if (m.isConfused()) sb.append("😵 Confused(" + m.getConfusionTurns() + ")  ");
        if (m instanceof Dasher && ((Dasher) m).getMomentumTurns() > 0)
            sb.append("💨 Momentum(" + ((Dasher) m).getMomentumTurns() + ")  ");
        if (m instanceof MultiTasker && ((MultiTasker) m).getNormalSpeedTurns() > 0)
            sb.append("🎯 FocusMode(" + ((MultiTasker) m).getNormalSpeedTurns() + ")  ");
        return sb.length() == 0 ? "Normal" : sb.toString().trim();
    }

    static Color getStatusColor(Monster m) {
        if (m.isFrozen())   return Color.CYAN;
        if (m.isConfused()) return Color.ORANGE;
        if (m.isShielded()) return Color.LIGHTGREEN;
        return Color.web("#cccccc");
    }
}