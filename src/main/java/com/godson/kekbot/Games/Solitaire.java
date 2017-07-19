package com.godson.kekbot.Games;

import com.godson.kekbot.KekBot;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Solitaire extends Game {
    private Random random = new Random();

    private CardPile deck = new CardPile(52);
    private CardPile discardPile = new CardPile();
    // The four piles where you place aces, then twos, etc., to win the game.
    private List<CardPile> foundation = Arrays.asList(
            new CardPile(13), new CardPile(13),
            new CardPile(13), new CardPile(13)
    );
    // The seven piles on the bottom row.
    private List<TableauPile> tableau = Arrays.asList(
            new TableauPile(), new TableauPile(), new TableauPile(),
            new TableauPile(), new TableauPile(), new TableauPile(),
            new TableauPile()
    );

    public Solitaire(TextChannel channel) {
        super(1, false, channel, "Solitaire");
    }

    @Override
    public void startGame() {
        // Make and shuffle deck
        for (final Card.Suit suit : Card.Suit.values()) {
            for (final Card.Face face : Card.Face.values()) {
                this.deck.add(new Solitaire.Card(suit, face));
            }
        }
        Collections.shuffle(this.deck);
        // Fill the seven piles
        assert this.tableau.size() == 7;
        for (byte pileNum = 1; pileNum < 7; pileNum++) {
            // Piles 1-7 get 1-7 cards, respectively. That works out well ^_^
            final TableauPile pile = this.tableau.get(pileNum);
            for (byte i = 0; i < pileNum; i++) {
                pile.faceDown.add(this.deck.pop());
            }
            // Flip over the top card
            pile.faceUp.add(pile.faceDown.pop());
        }
        drawBoard();
        channel.sendMessage("**Pick a slot to move that card.**").queue();
    }

    private void drawBoard() {
        channel.sendTyping().queue();
        StringBuilder board = new StringBuilder("```");

        String[] deckStrLines = this.deck.getLast().toString().split("\n");
        String[] discardStrLines = this.discardPile.getLast().toString().split("\n");
        String[][] foundationStrings = new String[4][0];
        assert this.foundation.size() == 4;
        for (byte i = 0; i < 4; i++) {
            foundationStrings[i] = this.foundation.get(i).getLast().toString().split("\n");
        }

        for (int line = 0; line < 4; line++) {
            board.append(deckStrLines[line]).append(discardStrLines[line]).append("    ");
            for (final String[] pileStrLines : foundationStrings) {
                // If the line exists, append it
                if (pileStrLines.length > line) board.append(pileStrLines[line]);
            }
            board.append("\n");
        }

        String[][] tableauStrings = new String[7][0];
        int mostLines = 0;
        for (byte i = 0; i < this.tableau.size(); i++) {
            tableauStrings[i] = this.tableau.get(i).toString().split("\n");
            if (tableauStrings[i].length > mostLines) mostLines = tableauStrings[i].length;
        }

        for (int line = 0; line < mostLines; line++) {
            for (final String[] pileStrLines : tableauStrings) {
                // If the line exists, append it
                if (pileStrLines.length > line) board.append(pileStrLines[line]);
            }
            board.append("\n");
        }
        board.append("```");
        channel.sendMessage(board.toString()).queue();
    }

    public void fillSlot(int slot, User player) {
        if (turn == getPlayerNumber(player)) {
            if (slot < board.length && slot > -1) {
                if (board[slot] == 0) {
                    board[slot] = getPlayerNumber(player);
                    if (!check(player)) {
                        if (!checkForDraw()) {
                            if (players.size() == 1) {
                                aiFillSlot();
                            } else {
                                drawBoard();
                                if (turn == 1) turn = 2;
                                else turn = 1;
                            }
                        }
                    }
                } else {
                    if (board[slot] == getPlayerNumber(player)) channel.sendMessage("You already own that space.").queue();
                    else channel.sendMessage("Your opponent already owns that space.").queue();
                }
            }
        } else {
            channel.sendMessage("It's not your turn yet!").queue();
        }
    }

    //Credits to Tech_Hutch for making this.
    private void prepareAI() {
        int[][] secondary = {{1, 3, 4, 2, 6, 8}, {1, 5, 4, 0, 6, 8}, {1, 3, 5, 7}, {3, 7, 4, 0, 2, 8}, {7, 5, 4, 0, 2, 6}};

        secondarySlots.put(0, secondary[0]);
        secondarySlots.put(2, secondary[1]);
        secondarySlots.put(4, secondary[2]);
        secondarySlots.put(6, secondary[3]);
        secondarySlots.put(8, secondary[4]);

        for (int primarySlot : primarySlots) {
            tertiarySlots.put(primarySlot, new HashMap<>());
        }
        tertiarySlots.get(0).put(1, 2);
        tertiarySlots.get(0).put(3, 6);
        tertiarySlots.get(0).put(4, 8);
        tertiarySlots.get(0).put(2, 1);
        tertiarySlots.get(0).put(6, 3);
        tertiarySlots.get(0).put(8, 4);
        tertiarySlots.get(2).put(1, 0);
        tertiarySlots.get(2).put(4, 6);
        tertiarySlots.get(2).put(5, 8);
        tertiarySlots.get(2).put(0, 1);
        tertiarySlots.get(2).put(6, 4);
        tertiarySlots.get(2).put(8, 5);
        tertiarySlots.get(4).put(1, 7);
        tertiarySlots.get(4).put(7, 1);
        tertiarySlots.get(4).put(3, 5);
        tertiarySlots.get(4).put(5, 3);
        tertiarySlots.get(6).put(3, 0);
        tertiarySlots.get(6).put(4, 2);
        tertiarySlots.get(6).put(7, 8);
        tertiarySlots.get(6).put(0, 3);
        tertiarySlots.get(6).put(2, 4);
        tertiarySlots.get(6).put(8, 7);
        tertiarySlots.get(8).put(4, 0);
        tertiarySlots.get(8).put(5, 2);
        tertiarySlots.get(8).put(7, 6);
        tertiarySlots.get(8).put(0, 4);
        tertiarySlots.get(8).put(2, 5);
        tertiarySlots.get(8).put(6, 7);
    }

    private void aiFillSlot() {
        int slot;
        ArrayList<Integer> dangerousSlots = new ArrayList<>();
        ArrayList<Integer> winningSlots = new ArrayList<>();

        for (int primarySlot : primarySlots) {
            if (board[primarySlot] == 1) {
                // The player has a token in one of the corners
                int[] secondaries = secondarySlots.get(primarySlot);
                for (int secondarySlot : secondaries) {
                    if (board[secondarySlot] == 1) {
                        int tertiarySlot = tertiarySlots.get(primarySlot).get(secondarySlot);
                        if (board[tertiarySlot] == 0) {
                            dangerousSlots.add(tertiarySlot);
                        }
                    }
                }
            }
            if (board[primarySlot] == 2) {
                // KekBot has a token in one of the corners
                int[] secondaries = secondarySlots.get(primarySlot);
                for (int secondarySlot : secondaries) {
                    if (board[secondarySlot] == 2) {
                        int tertiarySlot = tertiarySlots.get(primarySlot).get(secondarySlot);
                        if (board[tertiarySlot] == 0) {
                            winningSlots.add(tertiarySlot);
                        }
                    }
                }
            }
        }

        if (winningSlots.size() > 0) {
            slot = winningSlots.get(random.nextInt(winningSlots.size()));
        } else {
            if (dangerousSlots.size() > 0) {
                slot = dangerousSlots.get(random.nextInt(dangerousSlots.size()));
            } else {
                do {
                    slot = random.nextInt(board.length);
                } while (board[slot] != 0);
            }
        }
        board[slot] = 2;
        if (!checkAI()) {
            if (!checkForDraw()) {
                drawBoard();
            }
        }
    }

    private boolean check(User player) {
        if (this.foundation.stream().filter(pile -> pile.size() >= 13).count() == 4) {
            drawBoard();
            channel.sendMessage("\uD83C\uDF89 **" + player.getName() + " wins!** \uD83C\uDF89").queue();
            if (players.size() == numberOfPlayers) endGame(player, random.nextInt(8), ThreadLocalRandom.current().nextInt(4, 7));
            else endGame(player, random.nextInt(3) + 1, random.nextInt(3) + 1);
            return true;
        }
        return false;
    }

    private boolean checkAI() {
        boolean winner = false;
        for (int i = 0; i < 9; i += 3) {
            if (board[i] == 2 && board[i+1] == 2 && board[i+2] == 2) {
                winner = true;
                break;
            }
        }
        if (!winner) {
            for (int i = 0; i < 3; i++) {
                if (board[i] == 2 && board[i+3] == 2 && board[i+6] == 2) {
                    winner = true;
                    break;
                }
            }
        }
        if (!winner) {
            if (board[0] == 2 && board[4] == 2 && board[8] == 2) winner = true;
            else if (board[2] == 2 && board[4] == 2 && board[6] == 2) winner = true;
        }
        if (winner) {
            drawBoard();
            channel.sendMessage("\uD83C\uDF89 **KekBot wins!** \uD83C\uDF89").queue();
            endGame();
        }
        return winner;
    }

    private boolean checkForDraw() {
        boolean draw = true;
        for (int i = 0; i < 9; i++) {
            if (board[i] == 0) {
                draw = false;
                break;
            }
        }
        if (draw) {
            drawBoard();
            channel.sendMessage("**It's a draw!**").queue();
            endTie(random.nextInt(2), random.nextInt(3));
            KekBot.gamesManager.closeGame(channel);
        }
        return draw;
    }

    private static class Card {
        Suit suit;
        Face face;

        Card(Suit suit, Face face) {
            this.suit = suit;
            this.face = face;
        }

        Face getFace() {
            return this.face;
        }

        Suit.SuitColor getSuitColor() {
            return this.suit.color;
        }

        String getShorthand() {
            return this.face.rank + this.suit.icon;
        }

        String getMiddlePattern() {
            switch (this.face) {
                case ACE:
                    return "  ";
                case TWO:
                case THREE:
                case FOUR:
                    return " " + this.suit.icon;
                case FIVE:
                case SIX:
                case SEVEN:
                    return this.suit.icon + " ";
                case EIGHT:
                case NINE:
                case TEN:
                    return Character.toString(this.suit.icon) + this.suit.icon;
                case JACK:
                    return ":3";
                case QUEEN:
                    switch (this.suit) {
                        case SPADE:
                            return ":|";
                        case CLUB:
                            return ":<";
                        case HEART:
                            return ":*";
                        case DIAMOND:
                            return ":>";
                    }
                case KING:
                    switch (this.suit) {
                        case SPADE:
                            return ":V";
                        case CLUB:
                            return ":S";
                        case HEART:
                            return ":*";
                        case DIAMOND:
                            return ":♦";
                    }
                default:
                    return "  ";
            }
        }

        @Override
        public String toString() {
            return "+--+\n" +
                    "|" + this.getShorthand() + "|\n" +
                    "|" + this.getMiddlePattern() + "|\n" +
                    "+--+";
        }

        String getTopHalf() {
            return String.join("\n", Arrays.copyOfRange(this.toString().split("\n"), 0, 2));
        }

        String getBottomHalf() {
            return String.join("\n", Arrays.copyOfRange(this.toString().split("\n"), 2, 4));
        }

        enum Suit {
            SPADE("spade", '♠', SuitColor.BLACK), CLUB("club", '♣', SuitColor.BLACK),
            HEART("heart", '♥', SuitColor.RED), DIAMOND("diamond", '♦', SuitColor.RED);
            String name;
            char icon;
            SuitColor color;

            enum SuitColor {
                BLACK, RED
            }

            Suit(String name, char icon, SuitColor color) {
                this.name = name;
                this.icon = icon;
                this.color = color;
            }

            public String toString() {
                return Character.toString(this.icon);
            }
        }

        enum Face {
            ACE("A"), TWO("2"), THREE("3"), FOUR("4"), FIVE("5"), SIX("6"), SEVEN("7"),
            EIGHT("8"), NINE("9"), TEN("10"), JACK("J"), QUEEN("Q"), KING("K");
            String rank;

            Face(String rank) {
                this.rank = rank;
            }
        }
    }

    private static class CardPile extends ArrayList<Card> {
        CardPile() {}

        CardPile(int i) {
            super(i);
        }

        Card getLast() {
            return this.get(this.size() - 1);
        }

        Card pop() {
            final int lastIndex = this.size() - 1;
            final Card lastCard = this.get(lastIndex);
            this.remove(lastIndex);
            return lastCard;
        }
    }

    /**
     * One pile in the tableau.
     */
    private static class TableauPile {
        CardPile faceDown = new CardPile();
        TableauPileFaceUp faceUp = new TableauPileFaceUp();

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder()
                    .append("+--+\n|")
                    .append(String.format("%1$2s", this.faceDown.size()))
                    .append("|\n+--+\n");
            for (Card card : this.faceUp) {
                str.append(card.getTopHalf());
            }
            str.append(this.faceUp.getLast().getBottomHalf());
            return str.toString();
        }
    }

    private static class TableauPileFaceUp extends CardPile {
        boolean validCardToAdd(Card card) {
            if (this.isEmpty()) {
                if (card.getFace() != Card.Face.KING) {
                    // Only kings can be placed on empty spaces
                    return false;
                }
            } else {
                final Card lastCard = this.getLast();
                if (lastCard.getSuitColor() == card.getSuitColor()) {
                    // Same-color cards cannot be laid on top of each other in the tableau
                    return false;
                }
                if (lastCard.face.ordinal() + 1 != card.face.ordinal()) {
                    // Cards can only be laid on higher cards in the tableau
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean add(Card card) {
            return this.validCardToAdd(card) && super.add(card);
        }

        @Override
        public boolean addAll(Collection<? extends Card> collection) {
            return false;
        }

        public boolean addAll(CardPile cards) {
            final Card highestCard = cards.get(0);
            return this.validCardToAdd(highestCard) && super.addAll(cards);
        }
    }
}
