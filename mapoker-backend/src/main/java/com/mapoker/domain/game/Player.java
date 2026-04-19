package com.mapoker.domain.game;

import com.mapoker.domain.card.Card;

public class Player {
    private String id;
    private int stack;
    private Card[] hole; // length 2
    private boolean folded;
    private boolean allIn;
    private int contributed;
    private int totalContrib;

    public Player(String id, int stack) {
        this.id = id;
        this.stack = stack;
        this.hole = new Card[2];
        this.folded = false;
        this.allIn = false;
        this.contributed = 0;
        this.totalContrib = 0;
    }

    // copy constructor
    public Player(Player other) {
        this.id = other.id;
        this.stack = other.stack;
        this.hole = other.hole == null ? new Card[2] : other.hole.clone();
        this.folded = other.folded;
        this.allIn = other.allIn;
        this.contributed = other.contributed;
        this.totalContrib = other.totalContrib;
    }

    public String getId() { return id; }
    public int getStack() { return stack; }
    public void setStack(int stack) { this.stack = stack; }
    public Card[] getHole() { return hole; }
    public void setHole(Card[] hole) { this.hole = hole; }
    public boolean isFolded() { return folded; }
    public void setFolded(boolean folded) { this.folded = folded; }
    public boolean isAllIn() { return allIn; }
    public void setAllIn(boolean allIn) { this.allIn = allIn; }
    public int getContributed() { return contributed; }
    public void setContributed(int contributed) { this.contributed = contributed; }
    public int getTotalContrib() { return totalContrib; }
    public void setTotalContrib(int totalContrib) { this.totalContrib = totalContrib; }
}
