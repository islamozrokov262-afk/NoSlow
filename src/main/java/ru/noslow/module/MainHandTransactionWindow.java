package ru.noslow.module;

public final class MainHandTransactionWindow {
    private int lastPassedPong = Integer.MIN_VALUE;
    private int expectedPostPong;
    private boolean awaitingPostPong;
    private boolean holding;

    public void tK() {
        if (!this.holding && this.lastPassedPong != Integer.MIN_VALUE) {
            this.expectedPostPong = this.lastPassedPong == -32767 ? 0 : this.lastPassedPong - 1;
            this.awaitingPostPong = true;
        }
    }

    public boolean Y(int id) {
        if (id > 0 || id < -32767) {
            return false;
        } else if (this.holding) {
            return true;
        } else if (this.awaitingPostPong && id == this.expectedPostPong) {
            this.awaitingPostPong = false;
            this.holding = true;
            return true;
        } else {
            this.awaitingPostPong = false;
            this.lastPassedPong = id;
            return false;
        }
    }

    public boolean bZ() {
        return this.holding;
    }

    public void reset() {
        this.lastPassedPong = Integer.MIN_VALUE;
        this.tL();
    }

    public void tL() {
        this.awaitingPostPong = false;
        this.holding = false;
    }
}
