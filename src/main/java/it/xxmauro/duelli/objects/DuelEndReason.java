package it.xxmauro.duelli.objects;

public enum DuelEndReason {
    PLAYER1_WIN("Vittoria Giocatore 1"),
    PLAYER2_WIN("Vittoria Giocatore 2"),
    DRAW("Pareggio"),
    TIMEOUT("Timeout"),
    FORFEIT_P1("Forfeit Giocatore 1"),
    FORFEIT_P2("Forfeit Giocatore 2"),
    ADMIN("Admin");

    private final String description;

    DuelEndReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
