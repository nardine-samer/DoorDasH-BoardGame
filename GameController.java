package gui;

import game.engine.Constants;
import game.engine.Game;
import game.engine.Role;
import game.engine.Board;
import game.engine.cards.Card;
import game.engine.exceptions.InvalidMoveException;
import game.engine.exceptions.OutOfEnergyException;
import game.engine.monsters.Monster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GameController {

    private Game game;
    private Role playerRole;

    // ── Info exposed to the View ──────────────────────────────────────────────
    public String  lastMessage    = "";
    public Card    lastCardDrawn  = null;   // card drawn by the PLAYER this turn
    public Card    oppCardDrawn   = null;   // card drawn by the OPPONENT this turn
    public int     lastDiceRoll   = 0;
    public int     oppDiceRoll    = 0;
    public int     turnNumber     = 1;      // increments after both players move

    // Raw positions BEFORE each turn move (used for overshoot win detection)
    public int playerRawPosBefore = 0;
    public int oppRawPosBefore    = 0;

    // Energy snapshots taken BEFORE each turn so the View can compute deltas
    public int[] energyBefore = {0, 0};

    // Shield-block events
    public List<String> shieldBlockEvents = new ArrayList<>();

    // Position snapshots
    public int playerPosBefore   = 0;
    public int oppPosBefore      = 0;

    // Whether each side was frozen at the START of their turn this round
    public boolean playerWasFrozen = false;
    public boolean oppWasFrozen    = false;

    // ─────────────────────────────────────────────────────────────────────────

    public GameController(Role role) throws IOException {
        this.playerRole = role;
        this.game       = new Game(role);
    }

    // ── Simple pass-throughs ──────────────────────────────────────────────────
    public Game    getGame()     { return game; }
    public Monster getPlayer()   { return game.getPlayer(); }
    public Monster getOpponent() { return game.getOpponent(); }
    public Monster getCurrent()  { return game.getCurrent(); }

    /**
     * Returns the winner, checking both the engine's result AND a GUI-side
     * guard for the case where the engine's setPosition uses modulo (% 100),
     * which would wrap position 100 back to 0 and miss the win condition.
     * We track the highest position seen this turn via playerPosBefore /
     * oppPosBefore and the raw move distance to detect an overshot win.
     */
    public Monster getWinner() {
        // Engine check (works when position lands exactly on 99)
        Monster engineWinner = game.getWinner();
        if (engineWinner != null) return engineWinner;

        // GUI-side safety net: if a monster reached or passed cell 99 with
        // enough energy this turn, they win — even if the engine wrapped their
        // position back to 0 via modulo arithmetic.
        Monster p = getPlayer();
        Monster o = getOpponent();

        if (playerRawPosBefore + lastDiceRoll >= Constants.WINNING_POSITION
                && p.getEnergy() >= Constants.WINNING_ENERGY) return p;

        if (oppRawPosBefore + oppDiceRoll >= Constants.WINNING_POSITION
                && o.getEnergy() >= Constants.WINNING_ENERGY) return o;

        return null;
    }

    // ── Powerup ───────────────────────────────────────────────────────────────
    public boolean tryUsePowerup() {
        try {
            snapshotEnergies();
            game.usePowerup();
            lastMessage = getCurrent().getName() + " used their powerup! (-500 energy)";
            return true;
        } catch (OutOfEnergyException e) {
            lastMessage = "Not enough energy to use powerup! You need at least 500 energy.";
            return false;
        }
    }

    // ── Play the PLAYER'S turn ────────────────────────────────────────────────
    public boolean playPlayerTurn() throws InvalidMoveException {
        shieldBlockEvents.clear();
        lastCardDrawn = null;

        playerWasFrozen = getPlayer().isFrozen();
        snapshotEnergies();
        playerPosBefore = getPlayer().getPosition();
        playerRawPosBefore = getPlayer().getPosition();

        if (playerWasFrozen) {
            game.playTurn();
            return false;
        }

        // ── Snapshot card deck BEFORE the turn ───────────────────────────────
        List<Card> cardsBefore = new ArrayList<>(Board.getCards());

        game.playTurn();

        // ── Detect which card was drawn by comparing deck before vs after ─────
        lastCardDrawn = findDrawnCard(cardsBefore, Board.getCards(), getPlayer());

        lastDiceRoll = inferDiceRoll(playerPosBefore, getPlayer().getPosition());
        // If inferDiceRoll returned 0 the position may have wrapped (engine bug).
        // Reconstruct the true roll: how far did we move before the modulo?
        if (lastDiceRoll == 0 && !playerWasFrozen) {
            int wrapped = getPlayer().getPosition(); // position after % 100
            // The true destination = playerRawPosBefore + roll, and we see wrapped = dest % 100
            // So roll = (wrapped - playerRawPosBefore + 100) % 100, capped to 1..6
            int candidate = ((wrapped - playerRawPosBefore) + 100) % 100;
            if (candidate >= 1 && candidate <= 6) lastDiceRoll = candidate;
        }

        return true;
    }

    // ── Play the OPPONENT'S turn ──────────────────────────────────────────────
    public boolean playOpponentTurn() throws InvalidMoveException {
        oppCardDrawn = null;
        oppWasFrozen = getOpponent().isFrozen();
        snapshotEnergies();
        oppPosBefore  = getOpponent().getPosition();
        oppRawPosBefore = getOpponent().getPosition();

        if (oppWasFrozen) {
            game.playTurn();
            return false;
        }

        // ── Snapshot card deck BEFORE the turn ───────────────────────────────
        List<Card> cardsBefore = new ArrayList<>(Board.getCards());

        game.playTurn();

        // ── Detect which card was drawn ───────────────────────────────────────
        oppCardDrawn = findDrawnCard(cardsBefore, Board.getCards(), getOpponent());

        oppDiceRoll = inferDiceRoll(oppPosBefore, getOpponent().getPosition());
        if (oppDiceRoll == 0 && !oppWasFrozen) {
            int wrapped = getOpponent().getPosition();
            int candidate = ((wrapped - oppRawPosBefore) + 100) % 100;
            if (candidate >= 1 && candidate <= 6) oppDiceRoll = candidate;
        }

        turnNumber++;
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Compares the deck before and after a turn to find the card that was drawn.
     * The deck shrinks by one when a card is drawn (drawCard removes index 0).
     * If the deck was reloaded (empty → refilled) we can still find it by
     * checking whether the monster landed on a card cell.
     */
    private Card findDrawnCard(List<Card> before, List<Card> after, Monster monster) {
        // Normal case: deck shrank by 1 — the drawn card was before.get(0)
        if (before.size() == after.size() + 1) {
            return before.get(0);
        }

        // Reload case: deck was empty and got refilled, then one was drawn.
        // We can't recover the exact card, but we know one was drawn if the
        // monster landed on a card cell. Return null; the view's fallback handles it.
        if (before.isEmpty() && !after.isEmpty()) {
            // After reload the deck is refilled minus the one just drawn;
            // the drawn card is any card not in 'after' that would have been first.
            // We can't know for certain, so return null.
            return null;
        }

        // No card drawn (monster didn't land on a card cell, or deck unchanged)
        return null;
    }

    private void snapshotEnergies() {
        energyBefore[0] = getPlayer().getEnergy();
        energyBefore[1] = getOpponent().getEnergy();
    }

    private int inferDiceRoll(int posBefore, int posAfter) {
        int diff = posAfter - posBefore;
        if (diff >= 1 && diff <= 6) return diff;
        return 0;
    }

    // Energy delta helpers for the View
    public int playerEnergyDelta()   { return getPlayer().getEnergy()   - energyBefore[0]; }
    public int opponentEnergyDelta() { return getOpponent().getEnergy() - energyBefore[1]; }
}


