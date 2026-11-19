package ru.noslow.module;

public final class ItemUseContinuation {
    private int idleTicks;

    public boolean b(boolean useHeld, boolean usingSessionHand, boolean canRepeat) {
        if (!useHeld) {
            return true;
        } else if (!usingSessionHand && !canRepeat) {
            return ++this.idleTicks >= 3;
        } else {
            this.idleTicks = 0;
            return false;
        }
    }

    public void reset() {
        this.idleTicks = 0;
    }
}
